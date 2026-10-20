package dk.dutyc0re.newsfeed.core.toast;

import static dk.dutyc0re.newsfeed.core.hud.Painter.WHITE;

import dk.dutyc0re.newsfeed.core.NewsFeedAddon;
import dk.dutyc0re.newsfeed.core.config.NewsFeedConfiguration;
import dk.dutyc0re.newsfeed.core.hud.NewsPanelHudWidget;
import dk.dutyc0re.newsfeed.core.hud.Painter;
import dk.dutyc0re.newsfeed.core.hud.TextWrap;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.state.ScreenCanvas;
import net.labymod.api.client.gui.window.Window;
import net.labymod.api.event.Phase;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.render.overlay.IngameOverlayRenderEvent;
import net.labymod.api.util.bounds.Rectangle;

/**
 * The toast from the design: 340 px (170 GUI units) wide, slides in from the side, stays about
 * 6 seconds with a countdown bar and slides out again. One toast at a time; the rest wait in a
 * short queue. Toasts are queued from any thread and drawn on the render thread.
 *
 * <p>Where it is drawn: if the player placed the "pop-up" HUD widget ({@link ToastHudWidget}),
 * the widget draws it at that spot. Otherwise this class draws it itself in the top-right corner
 * (or just below the panel when the panel sits there).
 */
public class ToastManager {

  public static final float WIDTH = 170F;
  private static final float PAD = 7F;
  private static final float MARGIN = 6F;
  private static final float RADIUS = 6F;
  private static final float SCALE_META = 0.56F;
  private static final float SCALE_TITLE = 0.69F;
  private static final float BAR_HEIGHT = 1F;
  private static final int MAX_QUEUED = 3;

  // Timing (seconds). Slide animations stay under 0.4 s.
  private static final float IN_SECONDS = 0.32F;
  private static final float SHOW_SECONDS = 6F;
  private static final float OUT_SECONDS = 0.25F;

  private final NewsFeedAddon addon;
  private final ConcurrentLinkedDeque<Toast> queue = new ConcurrentLinkedDeque<>();

  private Toast current;
  private List<String> currentLines;
  private long startedNanos;

  /** Set by the pop-up HUD widget while it is placed and switched on in the HUD editor. */
  private volatile boolean widgetPlaced;

  public ToastManager(NewsFeedAddon addon) {
    this.addon = addon;
  }

  /** Queues a toast. Safe to call from any thread. */
  public void enqueue(Toast toast) {
    this.queue.addLast(toast);
    while (this.queue.size() > MAX_QUEUED) {
      this.queue.pollFirst();
    }
  }

  void setWidgetPlaced(boolean placed) {
    this.widgetPlaced = placed;
  }

  @Subscribe
  public void onOverlayRender(IngameOverlayRenderEvent event) {
    if (event.phase() != Phase.POST || event.isGuiHidden()) {
      return;
    }

    // The overlay drives the timing every frame; it only draws when no widget was placed.
    if (!this.advance() || this.widgetPlaced) {
      return;
    }

    ScreenContext context = event.context();
    Window window = this.addon.labyAPI().minecraft().minecraftWindow();
    float x = window.getScaledWidth() - MARGIN - WIDTH;
    float y = this.topFor(window.getScaledWidth());

    context.pushStack();
    context.translate(x, y, 0);
    this.draw(context, true, MARGIN);
    context.popStack();
  }

  /**
   * Starts the next toast if possible and ends the current one when its time is up.
   *
   * @return whether a toast is showing right now
   */
  boolean advance() {
    // Whether a toast may be queued at all ("Show pop-ups" etc.) is decided when it is queued.
    NewsFeedConfiguration configuration = this.addon.configuration();
    if (!configuration.enabled().get()) {
      this.queue.clear();
      this.current = null;
      return false;
    }

    if (this.current == null && !this.startNext(configuration)) {
      return false;
    }

    if (this.elapsed() >= IN_SECONDS + SHOW_SECONDS + OUT_SECONDS) {
      this.current = null;
      return false;
    }
    return true;
  }

  boolean isShowing() {
    return this.current != null;
  }

  /** Picks the next toast if one may be shown now. */
  private boolean startNext(NewsFeedConfiguration configuration) {
    // Do not disturb: keep toasts waiting until the fight is over.
    if (configuration.doNotDisturbInCombat().get() && this.addon.combatTracker().isInCombat()) {
      return false;
    }

    Toast next;
    while ((next = this.queue.pollFirst()) != null) {
      NewsPanelHudWidget panel = this.addon.visiblePanel();
      if (panel != null && panel.isShowingFullPanel()) {
        continue; // The open panel already shows it with an unread dot.
      }
      break;
    }
    if (next == null) {
      return false;
    }

    this.current = next;
    this.currentLines = null;
    this.startedNanos = System.nanoTime();
    if (configuration.sound().get()) {
      this.addon.playNotificationSound();
    }
    return true;
  }

  private float elapsed() {
    return (System.nanoTime() - this.startedNanos) / 1_000_000_000F;
  }

  // -----------------------------------------------------------------------------------------------
  // Drawing (local coordinates: the toast's top-left corner is 0,0)

  /** Height of the current toast, or of {@code sample} when none is showing. */
  float height(ScreenCanvas canvas, Toast sample) {
    Toast toast = this.current != null ? this.current : sample;
    return heightFor(canvas, this.linesFor(canvas, toast));
  }

  /**
   * Draws the current toast with its slide animation.
   *
   * @param fromRight    slide in from (and out to) the right; otherwise the left
   * @param slideExtra   extra distance so it fully leaves the screen edge while sliding
   */
  void draw(ScreenContext context, boolean fromRight, float slideExtra) {
    Toast toast = this.current;
    if (toast == null) {
      return;
    }

    float elapsed = this.elapsed();
    float offset;
    float alpha;
    if (elapsed < IN_SECONDS) {
      float t = Painter.easeOut(elapsed / IN_SECONDS);
      offset = (1F - t) * (WIDTH + slideExtra);
      alpha = t;
    } else if (elapsed < IN_SECONDS + SHOW_SECONDS) {
      offset = 0F;
      alpha = 1F;
    } else {
      float t = Math.min(1F, (elapsed - IN_SECONDS - SHOW_SECONDS) / OUT_SECONDS);
      offset = t * t * (WIDTH + slideExtra);
      alpha = 1F - t;
    }

    float remaining = 1F - Math.max(0F, Math.min(1F, (elapsed - IN_SECONDS) / SHOW_SECONDS));
    if (this.currentLines == null) {
      this.currentLines = this.linesFor(context.canvas(), toast);
    }

    context.pushStack();
    context.translate(fromRight ? offset : -offset, 0, 0);
    this.drawToast(new Painter(context.canvas(), alpha), toast, this.currentLines, remaining);
    context.popStack();
  }

  /** Draws a toast without animation (the HUD editor preview). */
  void drawStatic(ScreenContext context, Toast toast, float remaining) {
    Painter painter = new Painter(context.canvas(), 1F);
    this.drawToast(painter, toast, this.linesFor(context.canvas(), toast), remaining);
  }

  private void drawToast(Painter painter, Toast toast, List<String> lines, float remaining) {
    float height = heightFor(painter.canvas(), lines);
    float opacity = Math.max(0.2F,
        Math.min(1F, this.addon.configuration().opacity().get() / 100F));
    painter.panel(0, 0, WIDTH, height, RADIUS, Math.max(opacity, 0.9F));

    // Meta line: label · detail          +2 more
    float metaY = PAD;
    float metaHeight = painter.lineHeight(SCALE_META);
    float textX = PAD;
    painter.text(toast.label(), textX, metaY, toast.accentColor(), 0.95F, SCALE_META);
    textX += painter.width(toast.label(), SCALE_META);
    painter.text(" · " + toast.detail(), textX, metaY, WHITE, 0.45F, SCALE_META);
    if (!toast.more().isEmpty()) {
      painter.text(toast.more(), WIDTH - PAD - painter.width(toast.more(), SCALE_META), metaY,
          WHITE, 0.45F, SCALE_META);
    }

    float titleLineHeight = painter.lineHeight(SCALE_TITLE) + 0.8F;
    float titleY = metaY + metaHeight + 2.5F;
    for (String line : lines) {
      painter.text(line, PAD, titleY, WHITE, 0.92F, SCALE_TITLE);
      titleY += titleLineHeight;
    }

    // Countdown bar
    float barY = height - 4F - BAR_HEIGHT;
    float barWidth = WIDTH - PAD * 2F;
    painter.rounded(PAD, barY, barWidth, BAR_HEIGHT, BAR_HEIGHT / 2F, WHITE, 0.08F);
    if (remaining > 0F) {
      painter.rounded(PAD, barY, barWidth * remaining, BAR_HEIGHT, BAR_HEIGHT / 2F,
          toast.accentColor(), 0.85F);
    }
  }

  private List<String> linesFor(ScreenCanvas canvas, Toast toast) {
    return TextWrap.wrap(canvas, toast.title(), (WIDTH - PAD * 2F) / SCALE_TITLE, 2);
  }

  private static float heightFor(ScreenCanvas canvas, List<String> lines) {
    float metaHeight = canvas.getLineHeight() * SCALE_META;
    float titleLineHeight = canvas.getLineHeight() * SCALE_TITLE + 0.8F;
    return PAD + metaHeight + 2.5F + lines.size() * titleLineHeight + 3F + BAR_HEIGHT + 4F;
  }

  /** Fallback position: top-right corner, or just below the panel when the panel sits there. */
  private float topFor(float screenWidth) {
    NewsPanelHudWidget panel = this.addon.visiblePanel();
    Rectangle bounds = panel == null ? null : panel.screenBounds();
    if (bounds == null) {
      return MARGIN;
    }

    float toastLeft = screenWidth - MARGIN - WIDTH;
    boolean overlapsColumn = bounds.getRight() > toastLeft;
    boolean nearTop = bounds.getTop() < MARGIN + 60F;
    return overlapsColumn && nearTop ? bounds.getBottom() + MARGIN : MARGIN;
  }
}
