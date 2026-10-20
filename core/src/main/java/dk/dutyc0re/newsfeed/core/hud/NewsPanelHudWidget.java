package dk.dutyc0re.newsfeed.core.hud;

import static dk.dutyc0re.newsfeed.core.hud.Painter.WHITE;

import dk.dutyc0re.newsfeed.core.NewsFeedAddon;
import dk.dutyc0re.newsfeed.core.config.NewsFeedConfiguration;
import dk.dutyc0re.newsfeed.core.news.ItemStateStore;
import dk.dutyc0re.newsfeed.core.news.ItemStateStore.State;
import dk.dutyc0re.newsfeed.core.news.NewsItem;
import dk.dutyc0re.newsfeed.core.news.NewsSource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.labymod.api.client.gui.hud.hudwidget.HudWidgetConfig;
import net.labymod.api.client.gui.hud.hudwidget.SimpleHudWidget;
import net.labymod.api.client.gui.hud.position.HudSize;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.mouse.MutableMouse;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.key.MouseButton;
import net.labymod.api.client.gui.screen.state.ScreenCanvas;
import net.labymod.api.client.gui.screen.widget.widgets.hud.HudWidgetWidget;
import net.labymod.api.util.I18n;
import net.labymod.api.util.bounds.Rectangle;

/**
 * The news panel from the design in CLAUDE.md: header, filter buttons, news list and footer, plus
 * the minimized pill it morphs into. Sizes are in GUI units (1 unit = 2 design px at GUI scale 2).
 * The widget only reads the {@link dk.dutyc0re.newsfeed.core.news.NewsService} cache; it never
 * fetches anything.
 */
public class NewsPanelHudWidget extends SimpleHudWidget<HudWidgetConfig> {

  // Layout (GUI units)
  private static final float WIDTH = 152F;
  private static final float PAD = 7F;
  private static final float RADIUS = 6F;
  private static final float HEADER_HEIGHT = 9F;
  private static final float ICON_SIZE = 7F;
  private static final float FILTER_HEIGHT = 10F;
  private static final float FILTER_PAD_X = 5F;
  private static final float FILTER_GAP = 3F;
  private static final float ITEM_PAD_Y = 4.5F;
  private static final float PILL_HEIGHT = 14F;
  private static final float PILL_PAD = 4F;
  /** Max news rows in "All" and in a single-source filter; the list scrolls beyond the viewport. */
  private static final int MAX_ITEMS_ALL = 40;
  private static final int MAX_ITEMS_SOURCE = 25;
  /** Height of the scrollable list area (about 5 news rows). */
  private static final float LIST_MAX_HEIGHT = 135F;
  private static final float SCROLL_STEP = 22F;
  /** Fraction of the scroll distance still left after one second (smooth scrolling). */
  private static final double SCROLL_SMOOTHING = 0.0001;
  private static final int MAX_TITLE_LINES = 2;

  // Text scales (font line height is 9 units at scale 1)
  private static final float SCALE_HEADER = 0.72F;
  private static final float SCALE_FILTER = 0.58F;
  private static final float SCALE_META = 0.56F;
  private static final float SCALE_TITLE = 0.69F;
  private static final float SCALE_FOOTER = 0.53F;
  private static final float SCALE_PILL = 0.62F;
  private static final float SCALE_EMPTY = 0.6F;
  /** The small "New" / "Seen" / "Read" word on each row. */
  private static final float SCALE_STATE = 0.5F;

  private static final int LIVE_GREEN = 0x7BD389;
  private static final int PANEL_RGB = Painter.PANEL_RGB;

  /** The footer bar covers the list scrolling behind it. */
  private static final float FOOTER_OPACITY = 0.94F;
  private static final float FOOTER_FADE = 8F;
  /** Distance over which list text fades out at the top and bottom edge of the list. */
  private static final float LIST_EDGE_FADE = 6F;
  /** How long the mouse must rest on an item before it counts as seen. */
  private static final long HOVER_SEEN_MILLIS = 300L;
  /** Fade between unseen / seen / read. */
  private static final float STATE_SECONDS = 0.25F;
  /** Detail view: max height of the scrollable content, and the actions band below it. */
  private static final float DETAIL_MAX_HEIGHT = 150F;
  private static final float ACTIONS_HEIGHT = 16F;
  private static final float SCALE_DETAIL_TITLE = 0.8F;
  private static final float SCALE_BODY = 0.62F;
  private static final float SCALE_CHIP = 0.55F;
  private static final float CONTENT_FADE_SECONDS = 0.2F;
  /** The "N new · Mark all as read" bar: height when open, and open/close time. */
  private static final float BANNER_HEIGHT = 13F;
  private static final float BANNER_SECONDS = 0.25F;
  /** Header icons: stroke thickness and the inset of the icon box inside the 9-unit button. */
  private static final float ICON_STROKE = 0.8F;
  private static final float BUTTON_ICON_INSET = 2.5F;
  /** Size of the small floppy-disk "save" icon on rows and the Save button. */
  private static final float SAVE_ICON = 4F;

  // Animation durations (seconds), all under 0.4 s
  private static final float FADE_SECONDS = 0.2F;
  private static final float MORPH_SECONDS = 0.28F;
  private static final float FILTER_SECONDS = 0.15F;
  private static final float PULSE_SECONDS = 0.35F;
  private static final float SLIDE_DISTANCE = 10F;

  private final NewsFeedAddon addon;
  private final Icon feedIcon;

  private NewsFilter filter = NewsFilter.ALL;
  /** Show the "saved for later" list instead of the news for {@link #filter}. */
  private boolean showSaved;

  // Detail view: when an item is open, the panel shows it instead of the list.
  private NewsItem detailItem;
  private List<NewsItem> detailSequence = List.of();
  private int detailIndex;
  private DetailLayout detailLayout;
  private float listScrollBeforeDetail;
  /** 0 right after the view changed, eases to 1 (fades the content below the header). */
  private float contentFade = 1F;
  /** The "N new · Mark all as read" bar: 1 = open, 0 = closed, animated. */
  private float bannerFade;

  private List<NewsItem> processedItems;

  // Unseen → seen → read (see ItemStateStore). An item becomes seen once the mouse has rested on
  // it for HOVER_SEEN_MILLIS, and read when it is clicked.
  private String hoveredId;
  private long hoveredSinceMillis;
  private long lastHoverMillis;
  /** Per item: how far its dot has faded from unseen (1) to seen (0), animated. */
  private final Map<String, Float> unseenFade = new HashMap<>();
  /** Per item: how far it has dimmed towards read (1), animated. */
  private final Map<String, Float> readFade = new HashMap<>();
  private float frameDelta;

  // Scrolling (list area only), in GUI units
  private float scrollTarget;
  private float scrollOffset;
  private float maxScroll;
  private float listTop;
  private float listHeight;
  // The list viewport of the current frame (the list clips itself to it)
  private float viewTop;
  private float viewBottom;
  private int lastUnseenCount;

  // Animation state
  private long lastFrameNanos;
  private float visibility = 1F;
  /** 0 = full panel, 1 = minimized pill. */
  private float morph;
  /** 1 right after the unseen count grew, fades to 0. */
  private float pillPulse;
  private final Map<NewsFilter, Float> filterHighlight = new EnumMap<>(NewsFilter.class);

  // Hit areas from the last frame, in widget-local units
  private final List<HitArea> hitAreas = new ArrayList<>();
  private float mouseX = -1;
  private float mouseY = -1;

  private final Map<String, List<String>> wrapCache = new HashMap<>();
  private HudWidgetWidget ingameWidget;
  /** Whether the current frame is drawn in the HUD editor. */
  private boolean editorFrame;

  public NewsPanelHudWidget(NewsFeedAddon addon) {
    super("news_panel", HudWidgetConfig.class);
    this.addon = addon;
    this.feedIcon = Icon.texture(addon.resource("textures/icon.png"));
    this.setIcon(this.feedIcon);
    for (NewsFilter value : NewsFilter.values()) {
      this.filterHighlight.put(value, value == this.filter ? 1F : 0F);
    }
    // Start in the current state (the panel starts minimized) instead of animating into it.
    this.morph = addon.isPanelMinimized() ? 1F : 0F;
  }

  @Override
  public boolean isVisibleInGame() {
    return this.addon.configuration().enabled().get()
        && (this.addon.isPanelVisible() || this.visibility > 0.01F);
  }

  @Override
  public void onTick(boolean isEditorContext) {
    this.updateReadState();

    int unseen = this.unseenCount();
    if (unseen > this.lastUnseenCount) {
      this.pillPulse = 1F;
    }
    this.lastUnseenCount = unseen;
  }

  private void updateReadState() {
    List<NewsItem> items = this.addon.newsService().items();
    if (items != this.processedItems) {
      this.processedItems = items;
      this.wrapCache.clear();
      Set<String> ids = new HashSet<>();
      for (NewsItem item : items) {
        ids.add(item.id());
      }
      this.unseenFade.keySet().retainAll(ids);
      this.readFade.keySet().retainAll(this.unseenFade.keySet());
    }
  }

  private State stateOf(NewsItem item) {
    return this.addon.itemStates().get(item.id());
  }

  @Override
  public void initialize(HudWidgetWidget widget) {
    super.initialize(widget);
    if (!widget.accessor().isEditor()) {
      this.ingameWidget = widget;
    }
  }

  /** Whether the full panel is on screen (toasts for what the panel already shows are skipped). */
  public boolean isShowingFullPanel() {
    return !this.addon.isPanelMinimized() && this.morph < 0.5F && this.addon.isPanelVisible();
  }

  /** The panel's bounds on screen in GUI units, or {@code null} if it hasn't been placed. */
  public Rectangle screenBounds() {
    HudWidgetWidget widget = this.ingameWidget;
    return widget == null ? null : widget.scaledBounds();
  }

  @Override
  public void render(
      RenderPhase phase,
      ScreenContext context,
      boolean isEditorContext,
      HudSize size
  ) {
    ScreenCanvas canvas = context.canvas();
    this.editorFrame = isEditorContext;
    if (phase == RenderPhase.UPDATE_SIZE) {
      this.animate(isEditorContext);
      float shape = Painter.easeInOut(this.morph);
      size.set(
          Painter.lerp(WIDTH, this.pillWidth(canvas), shape),
          Painter.lerp(this.morph >= 1F ? PILL_HEIGHT : this.panelHeight(canvas), PILL_HEIGHT,
              shape)
      );
      return;
    }

    if (!isEditorContext && this.addon.isPanelVisible()) {
      this.addon.markPanelRendered(this);
    }

    float scale = size.getScale() == 0 ? 1F : size.getScale();
    this.mouseX = (float) (context.mouse().getXDouble() / scale);
    this.mouseY = (float) (context.mouse().getYDouble() / scale);
    this.hitAreas.clear();

    float alpha = isEditorContext ? 1F : this.visibility;
    if (alpha <= 0.01F) {
      return;
    }

    // Slide in from the side the widget is anchored to.
    float slide = (1F - alpha) * SLIDE_DISTANCE * (this.anchor().isRight() ? 1F : -1F);
    context.pushStack();
    context.translate(slide, 0, 0);

    Painter painter = new Painter(canvas, alpha);
    float shape = Painter.easeInOut(this.morph);
    float width = size.getActualWidth();
    float height = size.getActualHeight();

    // The background morphs between panel and pill; the contents cross-fade on top of it.
    painter.panel(0, 0, width, height, Painter.lerp(RADIUS, PILL_HEIGHT / 2F, shape),
        this.backgroundOpacity());

    float panelContent = Math.max(0F, 1F - this.morph / 0.4F);
    float pillContent = Math.max(0F, (this.morph - 0.6F) / 0.4F);
    if (panelContent > 0F) {
      this.renderPanelContent(painter.withAlpha(panelContent), height, panelContent >= 1F);
    }
    if (pillContent > 0F) {
      this.renderPillContent(painter.withAlpha(pillContent), width, pillContent >= 1F);
    }

    context.popStack();
  }

  // ---------------------------------------------------------------------------------------------
  // Animation

  private void animate(boolean isEditorContext) {
    long now = System.nanoTime();
    float delta = this.lastFrameNanos == 0 ? 0F : (now - this.lastFrameNanos) / 1_000_000_000F;
    this.lastFrameNanos = now;
    delta = Math.min(delta, 0.1F);
    this.frameDelta = delta;
    this.contentFade = Painter.approach(this.contentFade, 1F, delta / CONTENT_FADE_SECONDS);
    this.bannerFade = Painter.approach(this.bannerFade, this.wantsBanner() ? 1F : 0F,
        delta / BANNER_SECONDS);

    float target = isEditorContext || this.addon.isPanelVisible() ? 1F : 0F;
    this.visibility = Painter.approach(this.visibility, target, delta / FADE_SECONDS);
    // The HUD editor always previews the full panel, even if it is minimized in-game.
    float morphTarget = !isEditorContext && this.addon.isPanelMinimized() ? 1F : 0F;
    this.morph = Painter.approach(this.morph, morphTarget, delta / MORPH_SECONDS);
    this.scrollOffset += (this.scrollTarget - this.scrollOffset)
        * (float) (1.0 - Math.pow(SCROLL_SMOOTHING, delta));
    this.pillPulse = Painter.approach(this.pillPulse, 0F, delta / PULSE_SECONDS);

    for (NewsFilter value : NewsFilter.values()) {
      float goal = !this.showSaved && value == this.filter ? 1F : 0F;
      this.filterHighlight.put(
          value,
          Painter.approach(this.filterHighlight.get(value), goal, delta / FILTER_SECONDS)
      );
    }
  }

  private float backgroundOpacity() {
    return Math.max(0.2F, Math.min(1F, this.addon.configuration().opacity().get() / 100F));
  }

  // ---------------------------------------------------------------------------------------------
  // Panel

  private float panelHeight(ScreenCanvas canvas) {
    float y = PAD + HEADER_HEIGHT + 5F + FILTER_HEIGHT + 5F;
    if (this.detailItem != null) {
      float contentHeight = this.detailLayout(canvas).height();
      this.listTop = y;
      this.listHeight = Math.min(contentHeight, DETAIL_MAX_HEIGHT);
      this.clampScroll(contentHeight);
      return y + this.listHeight + ACTIONS_HEIGHT + this.footerHeight(canvas) + PAD * 0.6F;
    }

    y += this.bannerHeight();
    List<Row> rows = this.buildRows();
    float contentHeight = 0F;
    for (Row row : rows) {
      contentHeight += this.rowHeight(canvas, row);
    }

    this.listTop = y;
    this.listHeight = rows.isEmpty()
        ? this.emptyHeight(canvas)
        : Math.min(contentHeight, LIST_MAX_HEIGHT);
    this.clampScroll(rows.isEmpty() ? this.listHeight : contentHeight);

    return y + this.listHeight + this.footerHeight(canvas) + PAD * 0.6F;
  }

  private void clampScroll(float contentHeight) {
    this.maxScroll = Math.max(0F, contentHeight - this.listHeight);
    this.scrollTarget = Math.max(0F, Math.min(this.scrollTarget, this.maxScroll));
    this.scrollOffset = Math.max(0F, Math.min(this.scrollOffset, this.maxScroll));
  }

  // ---------------------------------------------------------------------------------------------
  // Detail view: read a bit about an item before opening it

  /** Opens the detail view for {@code items[index]}; "next" walks on through {@code items}. */
  private void openDetail(List<NewsItem> items, int index) {
    if (this.detailItem == null) {
      this.listScrollBeforeDetail = this.scrollTarget;
    }
    this.detailSequence = List.copyOf(items);
    this.showDetail(index);
  }

  private void showDetail(int index) {
    this.detailIndex = index;
    this.detailItem = this.detailSequence.get(index);
    // Reading the summary counts as reading the item.
    this.addon.itemStates().markRead(this.detailItem.id());
    this.resetScroll();
    this.contentFade = 0F;
  }

  private void nextDetail() {
    if (this.detailIndex + 1 < this.detailSequence.size()) {
      this.showDetail(this.detailIndex + 1);
    }
  }

  private void closeDetail() {
    this.detailItem = null;
    this.detailLayout = null;
    this.scrollTarget = this.listScrollBeforeDetail;
    this.scrollOffset = this.listScrollBeforeDetail;
    this.contentFade = 0F;
  }

  /** Lays out the detail content once per item (it only changes when the item does). */
  private DetailLayout detailLayout(ScreenCanvas canvas) {
    if (this.detailLayout != null && this.detailLayout.item() == this.detailItem) {
      return this.detailLayout;
    }

    NewsItem item = this.detailItem;
    List<DetailOp> ops = new ArrayList<>();
    float lineHeight = canvas.getLineHeight();
    float textWidth = WIDTH - PAD * 2F - 4F;
    float y = 5F;

    // Meta: source · age · author
    String source = I18n.translate(item.source().translationKey()).toLowerCase(Locale.ROOT);
    ops.add(DetailOp.text(source, PAD, y, SCALE_META, item.source().color(), 0.95F));
    String meta = " · " + formatAge(item.published(), Instant.now())
        + (item.author().isEmpty() ? "" : " · " + item.author());
    ops.add(DetailOp.text(meta, PAD + canvas.getTextWidth(source) * SCALE_META, y, SCALE_META,
        WHITE, 0.45F));
    y += lineHeight * SCALE_META + 3F;

    // Title
    for (String line : TextWrap.wrap(canvas, item.title(), textWidth / SCALE_DETAIL_TITLE, 4)) {
      ops.add(DetailOp.text(line, PAD, y, SCALE_DETAIL_TITLE, WHITE, 0.95F));
      y += lineHeight * SCALE_DETAIL_TITLE + 1F;
    }
    y += 3F;

    // Facts as chips (cosmetics: category, creator, limited, Plus)
    if (!item.facts().isEmpty()) {
      float x = PAD;
      float chipHeight = lineHeight * SCALE_CHIP + 3F;
      for (NewsItem.Fact fact : item.facts()) {
        String label = I18n.translate(fact.labelKey());
        String text = fact.value().isEmpty() ? label : label + ": " + fact.value();
        float chipWidth = canvas.getTextWidth(text) * SCALE_CHIP + 6F;
        if (x > PAD && x + chipWidth > PAD + textWidth) {
          x = PAD;
          y += chipHeight + 2F;
        }
        ops.add(DetailOp.chip(text, x, y, chipWidth, chipHeight, SCALE_CHIP,
            item.source().color()));
        x += chipWidth + 2F;
      }
      y += chipHeight + 4F;
    }

    // Summary paragraphs, or a hint when the source has nothing to say
    String summary = item.summary();
    if (summary.isEmpty() && item.facts().isEmpty()) {
      summary = I18n.translate(item.source() == NewsSource.REDDIT
          ? "newsfeed.detail.noTextReddit"
          : "newsfeed.detail.noSummary");
    }
    float bodyLineHeight = lineHeight * SCALE_BODY + 1.2F;
    for (String paragraph : summary.split("\n")) {
      for (String line : TextWrap.wrap(canvas, paragraph, textWidth / SCALE_BODY, 60)) {
        ops.add(DetailOp.text(line, PAD, y, SCALE_BODY, WHITE, 0.72F));
        y += bodyLineHeight;
      }
      y += 2.5F;
    }

    this.detailLayout = new DetailLayout(item, ops, y + 3F);
    return this.detailLayout;
  }

  private void renderDetailView(Painter painter, ScreenCanvas canvas, float y, float height,
      boolean interactive) {
    this.renderDetailNav(painter, y, interactive);
    y += FILTER_HEIGHT + 5F;
    painter.rect(0, y - 0.25F, WIDTH, 0.5F, WHITE, 0.06F);

    this.viewTop = y;
    this.viewBottom = y + this.listHeight;
    this.renderDetailContent(painter, canvas);

    // Actions and footer sit on a solid bar; the text fades out under it while there is more.
    float barTop = this.viewBottom;
    float below = Math.max(0F, Math.min(1F, (this.maxScroll - this.scrollOffset) / 12F));
    if (below > 0F) {
      painter.gradientDown(0, barTop - FOOTER_FADE, WIDTH, FOOTER_FADE, PANEL_RGB, 0F,
          FOOTER_OPACITY * below);
    }
    painter.bottomBar(0, barTop, WIDTH, height - barTop, RADIUS, FOOTER_OPACITY);
    painter.rect(0, barTop - 0.25F, WIDTH, 0.5F, WHITE, 0.06F);
    this.renderDetailActions(painter, barTop, interactive);

    float footerTop = barTop + ACTIONS_HEIGHT;
    painter.rect(PAD, footerTop - 0.25F, WIDTH - PAD * 2F, 0.5F, WHITE, 0.04F);
    this.renderFooter(painter, footerTop);

    if (this.maxScroll > 0F) {
      this.renderScrollbar(painter, this.listTop);
    }
  }

  private void renderDetailNav(Painter painter, float y, boolean interactive) {
    // "« Back" on the left, "3 / 40" on the right
    String back = "« " + I18n.translate("newsfeed.detail.back");
    float backWidth = painter.width(back, SCALE_FILTER) + FILTER_PAD_X * 2F;
    boolean backHovered = interactive && this.isHovered(PAD, y, backWidth, FILTER_HEIGHT);
    painter.rounded(PAD, y, backWidth, FILTER_HEIGHT, FILTER_HEIGHT / 2F, WHITE,
        backHovered ? 0.12F : 0.06F);
    painter.textCentered(back, PAD + FILTER_PAD_X, y, FILTER_HEIGHT, WHITE,
        backHovered ? 0.95F : 0.75F, SCALE_FILTER);
    this.addHitArea(interactive, PAD, y, backWidth, FILTER_HEIGHT, this::closeDetail);

    String position = (this.detailIndex + 1) + " / " + this.detailSequence.size();
    painter.textCentered(position, WIDTH - PAD - painter.width(position, SCALE_FILTER), y,
        FILTER_HEIGHT, WHITE, 0.4F, SCALE_FILTER);
  }

  private void renderDetailContent(Painter painter, ScreenCanvas canvas) {
    float top = this.viewTop - this.scrollOffset;
    for (DetailOp op : this.detailLayout(canvas).ops()) {
      float opY = top + op.y();
      float opHeight = op.chip() ? op.height() : painter.lineHeight(op.scale());
      float alpha = this.edgeAlpha(opY, opY + opHeight);
      if (alpha <= 0F) {
        continue;
      }

      Painter faded = painter.withAlpha(alpha);
      if (op.chip()) {
        faded.rounded(op.x(), opY, op.width(), op.height(), op.height() / 2F, op.color(), 0.16F);
        faded.textCentered(op.text(), op.x() + 3F, opY, op.height(), WHITE, 0.85F, op.scale());
      } else {
        faded.text(op.text(), op.x(), opY, op.color(), op.opacity(), op.scale());
      }
    }
  }

  /** Save · Open · Next, in the band between the content and the footer. */
  private void renderDetailActions(Painter painter, float y, boolean interactive) {
    NewsItem item = this.detailItem;
    float buttonY = y + (ACTIONS_HEIGHT - FILTER_HEIGHT) / 2F;

    // Save for later
    boolean saved = this.addon.itemStates().isSaved(item.id());
    String saveText = I18n.translate(saved ? "newsfeed.detail.saved" : "newsfeed.detail.save");
    float saveWidth = 4F + SAVE_ICON + 3F + painter.width(saveText, SCALE_FILTER) + 5F;
    boolean saveHovered = interactive && this.isHovered(PAD, buttonY, saveWidth, FILTER_HEIGHT);
    painter.rounded(PAD, buttonY, saveWidth, FILTER_HEIGHT, FILTER_HEIGHT / 2F, WHITE,
        saveHovered ? 0.12F : 0.06F);
    painter.floppy(PAD + 4F, buttonY + (FILTER_HEIGHT - SAVE_ICON) / 2F, SAVE_ICON,
        ICON_STROKE * 0.75F, WHITE, saved ? 0.95F : (saveHovered ? 0.9F : 0.55F), saved);
    painter.textCentered(saveText, PAD + 4F + SAVE_ICON + 3F, buttonY, FILTER_HEIGHT, WHITE,
        saveHovered || saved ? 0.95F : 0.7F, SCALE_FILTER);
    this.addHitArea(interactive, PAD, buttonY, saveWidth, FILTER_HEIGHT,
        () -> this.addon.itemStates().toggleSaved(item));

    // Next » (right) and Open (left of it)
    boolean hasNext = this.detailIndex + 1 < this.detailSequence.size();
    String next = I18n.translate("newsfeed.detail.next") + " »";
    float nextWidth = painter.width(next, SCALE_FILTER) + FILTER_PAD_X * 2F;
    float nextX = WIDTH - PAD - nextWidth;
    boolean nextHovered = interactive && hasNext
        && this.isHovered(nextX, buttonY, nextWidth, FILTER_HEIGHT);
    painter.rounded(nextX, buttonY, nextWidth, FILTER_HEIGHT, FILTER_HEIGHT / 2F, WHITE,
        nextHovered ? 0.12F : 0.06F);
    painter.textCentered(next, nextX + FILTER_PAD_X, buttonY, FILTER_HEIGHT, WHITE,
        hasNext ? (nextHovered ? 0.95F : 0.75F) : 0.25F, SCALE_FILTER);
    if (hasNext) {
      this.addHitArea(interactive, nextX, buttonY, nextWidth, FILTER_HEIGHT, this::nextDetail);
    }

    boolean hasLink = item.link() != null && !item.link().isBlank();
    String open = I18n.translate("newsfeed.detail.open");
    float openWidth = painter.width(open, SCALE_FILTER) + FILTER_PAD_X * 2F;
    float openX = nextX - 3F - openWidth;
    boolean openHovered = interactive && hasLink
        && this.isHovered(openX, buttonY, openWidth, FILTER_HEIGHT);
    int accent = item.source().color();
    painter.rounded(openX, buttonY, openWidth, FILTER_HEIGHT, FILTER_HEIGHT / 2F, accent,
        hasLink ? (openHovered ? 0.45F : 0.28F) : 0.08F);
    painter.textCentered(open, openX + FILTER_PAD_X, buttonY, FILTER_HEIGHT, WHITE,
        hasLink ? 0.95F : 0.3F, SCALE_FILTER);
    if (hasLink) {
      this.addHitArea(interactive, openX, buttonY, openWidth, FILTER_HEIGHT,
          () -> this.addon.openUrl(item.link()));
    }
  }

  /**
   * Scrolls the list if the mouse (as seen in the last frame) is over it.
   *
   * @return whether the scroll was used
   */
  public boolean scroll(float delta) {
    boolean overList = this.isHovered(0, this.listTop, WIDTH, this.listHeight);
    if (!overList || this.morph > 0F || this.maxScroll <= 0F) {
      return false;
    }

    this.scrollTarget = Math.max(0F, Math.min(this.maxScroll,
        this.scrollTarget - delta * SCROLL_STEP));
    return true;
  }

  private void renderPanelContent(Painter painter, float height, boolean interactive) {
    ScreenCanvas canvas = painter.canvas();

    // Header: icon, title, live dot, minimize button
    float y = PAD;
    painter.icon(this.feedIcon, PAD, y + (HEADER_HEIGHT - ICON_SIZE) / 2F, ICON_SIZE);
    String title = I18n.translate(this.showSaved
        ? "newsfeed.panel.savedTitle"
        : "newsfeed.panel.title");
    float titleX = PAD + ICON_SIZE + 4F;
    painter.textCentered(title, titleX, y, HEADER_HEIGHT, WHITE, 0.95F, SCALE_HEADER);

    float liveX = titleX + painter.width(title, SCALE_HEADER) + 5F;
    float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 1000.0 * Math.PI));
    float dotY = y + HEADER_HEIGHT / 2F;
    painter.circle(liveX + 1.5F, dotY, 1.5F + pulse * 1.5F, LIVE_GREEN, 0.25F * (1F - pulse));
    painter.circle(liveX + 1.5F, dotY, 1.5F, LIVE_GREEN, 0.7F + 0.3F * pulse);
    painter.textCentered(I18n.translate("newsfeed.panel.live"), liveX + 5F, y, HEADER_HEIGHT,
        LIVE_GREEN, 0.9F, SCALE_META);

    float buttonSize = HEADER_HEIGHT;
    float buttonX = WIDTH - PAD - buttonSize;
    boolean buttonHovered = interactive && this.isHovered(buttonX, y, buttonSize, buttonSize);
    if (buttonHovered) {
      painter.rounded(buttonX, y, buttonSize, buttonSize, 2.5F, WHITE, 0.08F);
    }
    // All header icons share the minimize button's look: thin white strokes of ICON_STROKE in
    // the same small box in the middle of the button, 55% normally and 90% on hover.
    float iconLeft = BUTTON_ICON_INSET;
    float iconSize = buttonSize - BUTTON_ICON_INSET * 2F;
    float iconMiddle = y + buttonSize / 2F;

    painter.rect(buttonX + iconLeft, iconMiddle - ICON_STROKE / 2F, iconSize, ICON_STROKE, WHITE,
        buttonHovered ? 0.9F : 0.55F);
    this.addHitArea(interactive, buttonX, y, buttonSize, buttonSize, this::minimize);

    // "Saved for later": a floppy disk outline, lit while the saved list is shown
    float savedX = buttonX - buttonSize - 2F;
    boolean savedHovered = interactive && this.isHovered(savedX, y, buttonSize, buttonSize);
    if (savedHovered || this.showSaved) {
      painter.rounded(savedX, y, buttonSize, buttonSize, 2.5F, WHITE,
          this.showSaved ? 0.12F : 0.08F);
    }
    float floppySize = iconSize + 0.5F;
    painter.floppy(savedX + (buttonSize - floppySize) / 2F, iconMiddle - floppySize / 2F,
        floppySize, ICON_STROKE * 0.75F, WHITE, this.showSaved || savedHovered ? 0.9F : 0.55F,
        false);
    this.addHitArea(interactive, savedX, y, buttonSize, buttonSize, this::toggleSavedView);

    // Everything below the header fades (and slides a little) when the view changes.
    y += HEADER_HEIGHT + 5F;
    float fade = Painter.easeOut(this.contentFade);
    painter = painter.withAlpha(fade);

    if (this.detailItem != null) {
      this.renderDetailView(painter, canvas, y, height, interactive);
      return;
    }

    // Filter buttons
    this.renderFilters(painter, y, interactive);
    y += FILTER_HEIGHT + 5F;
    painter.rect(0, y - 0.25F, WIDTH, 0.5F, WHITE, 0.06F);

    // "12 new · Mark all as read" bar, only while there is something new (slides open/closed)
    float bannerHeight = this.bannerHeight();
    if (bannerHeight > 0.01F) {
      this.renderBanner(painter.withAlpha(Painter.easeOut(this.bannerFade)), y, bannerHeight,
          interactive && this.bannerFade >= 1F);
      y += bannerHeight;
    }

    // News list: a viewport that scrolls. The canvas clip shapes have no effect in the HUD, so
    // the list clips itself: every element is only drawn inside the viewport and fades out as
    // it slides under the filter row at the top or behind the footer at the bottom.
    List<Row> rows = this.buildRows();
    float footerTop;
    if (rows.isEmpty()) {
      this.renderEmptyMessage(painter, y);
      footerTop = y + this.listHeight;
    } else {
      this.viewTop = y;
      this.viewBottom = y + this.listHeight;
      footerTop = this.viewBottom;

      float rowY = this.viewTop - this.scrollOffset;
      for (int i = 0; i < rows.size(); i++) {
        Row row = rows.get(i);
        float rowHeight = this.rowHeight(canvas, row);
        if (rowY + rowHeight > this.viewTop && rowY < this.viewBottom) {
          // Only what is inside the viewport counts as seen and is clickable.
          float hitTop = Math.max(rowY, this.viewTop);
          float hitBottom = Math.min(rowY + rowHeight, this.viewBottom);
          this.renderRow(painter, row, rowY, rowHeight, interactive, hitTop, hitBottom);

          float separatorY = rowY + rowHeight - 0.25F;
          if (i < rows.size() - 1) {
            painter.withAlpha(this.edgeAlpha(separatorY, separatorY + 0.5F))
                .rect(PAD, separatorY, WIDTH - PAD * 2F, 0.5F, WHITE, 0.04F);
          }
        }
        rowY += rowHeight;
      }
    }

    // Footer: a solid bar over the bottom of the list, with a soft fade above it while there is
    // more to scroll to.
    float below = Math.max(0F, Math.min(1F, (this.maxScroll - this.scrollOffset) / 12F));
    if (below > 0F) {
      painter.gradientDown(0, footerTop - FOOTER_FADE, WIDTH, FOOTER_FADE, PANEL_RGB, 0F,
          FOOTER_OPACITY * below);
    }
    painter.bottomBar(0, footerTop, WIDTH, height - footerTop, RADIUS, FOOTER_OPACITY);
    painter.rect(0, footerTop - 0.25F, WIDTH, 0.5F, WHITE, 0.06F);
    this.renderFooter(painter, footerTop);

    if (this.maxScroll > 0F) {
      this.renderScrollbar(painter, this.listTop);
    }
  }

  private List<NewsFilter> availableFilters() {
    return List.of(NewsFilter.values());
  }

  private void renderFilters(Painter painter, float y, boolean interactive) {
    List<NewsFilter> filters = this.availableFilters();
    String[] labels = new String[filters.size()];
    float available = WIDTH - PAD * 2F;

    float total = FILTER_GAP * (filters.size() - 1);
    for (int i = 0; i < filters.size(); i++) {
      labels[i] = I18n.translate(filters.get(i).translationKey());
      total += painter.width(labels[i], SCALE_FILTER) + FILTER_PAD_X * 2F;
    }

    // Shrink the row uniformly if the translated labels don't fit.
    float fit = Math.min(1F, available / total);
    float textScale = SCALE_FILTER * fit;
    float padX = FILTER_PAD_X * fit;
    float gap = FILTER_GAP * fit;

    float x = PAD;
    for (int i = 0; i < filters.size(); i++) {
      NewsFilter value = filters.get(i);
      float pillWidth = painter.width(labels[i], textScale) + padX * 2F;
      float highlight = Painter.easeOut(this.filterHighlight.get(value));
      boolean hovered = interactive && this.isHovered(x, y, pillWidth, FILTER_HEIGHT);

      float backgroundAlpha = 0.04F + 0.10F * highlight + (hovered ? 0.04F : 0F);
      painter.rounded(x, y, pillWidth, FILTER_HEIGHT, FILTER_HEIGHT / 2F,
          highlight > 0.5F ? value.accentColor() : WHITE, backgroundAlpha);

      float textAlpha = 0.55F + 0.4F * highlight + (hovered ? 0.05F : 0F);
      painter.textCentered(labels[i], x + padX, y, FILTER_HEIGHT, WHITE, textAlpha, textScale);

      this.addHitArea(interactive, x, y, pillWidth, FILTER_HEIGHT, () -> this.selectFilter(value));
      x += pillWidth + gap;
    }
  }

  /** The empty-list message, word-wrapped to the panel width. */
  private List<String> emptyLines(ScreenCanvas canvas) {
    return TextWrap.wrap(canvas, I18n.translate(this.emptyMessageKey()),
        (WIDTH - PAD * 2F) / SCALE_EMPTY, 3);
  }

  private float emptyHeight(ScreenCanvas canvas) {
    float lines = this.emptyLines(canvas).size();
    return Math.max(22F, lines * (canvas.getLineHeight() * SCALE_EMPTY + 1F) + 12F);
  }

  private void renderEmptyMessage(Painter painter, float y) {
    List<String> lines = this.emptyLines(painter.canvas());
    float lineHeight = painter.lineHeight(SCALE_EMPTY) + 1F;
    float lineY = y + (this.listHeight - lines.size() * lineHeight) / 2F + 0.5F;
    for (String line : lines) {
      float x = (WIDTH - painter.width(line, SCALE_EMPTY)) / 2F;
      painter.text(line, x, lineY, WHITE, 0.45F, SCALE_EMPTY);
      lineY += lineHeight;
    }
  }

  private float rowHeight(ScreenCanvas canvas, Row row) {
    float lineHeight = canvas.getLineHeight();
    int titleLines = this.wrapTitle(canvas, row).size();
    return ITEM_PAD_Y + lineHeight * SCALE_META + 2F
        + titleLines * (lineHeight * SCALE_TITLE + 0.8F) + ITEM_PAD_Y;
  }

  private void renderScrollbar(Painter painter, float viewTop) {
    float contentHeight = this.listHeight + this.maxScroll;
    float thumbHeight = Math.max(8F, this.listHeight * this.listHeight / contentHeight);
    float thumbY = viewTop + (this.listHeight - thumbHeight) * (this.scrollOffset / this.maxScroll);
    float x = WIDTH - 2.5F;
    painter.rounded(x, viewTop + 1F, 1F, this.listHeight - 2F, 0.5F, WHITE, 0.04F);
    painter.rounded(x, thumbY + 1F, 1F, thumbHeight - 2F, 0.5F, WHITE, 0.22F);
  }

  /**
   * How visible an element spanning {@code top..bottom} is in the list viewport: 0 once any part
   * of it is outside, fading up to 1 over {@link #LIST_EDGE_FADE} units from the edge.
   */
  private float edgeAlpha(float top, float bottom) {
    float fromTop = (top - this.viewTop) / LIST_EDGE_FADE;
    float fromBottom = (this.viewBottom - bottom) / LIST_EDGE_FADE;
    return Math.max(0F, Math.min(1F, Math.min(fromTop, fromBottom)));
  }

  private static String stateKey(State state) {
    return switch (state) {
      case UNSEEN -> "newsfeed.state.unseen";
      case SEEN -> "newsfeed.state.seen";
      case READ -> "newsfeed.state.read";
    };
  }

  /** Marks the hovered item as seen once the mouse has rested on it long enough. */
  private void trackHover(Row row) {
    long now = System.currentTimeMillis();
    // A new item, or the mouse came back after leaving: start counting again.
    boolean continued = row.newsId().equals(this.hoveredId)
        && now - this.lastHoverMillis < 150L;
    if (!continued) {
      this.hoveredId = row.newsId();
      this.hoveredSinceMillis = now;
    } else if (now - this.hoveredSinceMillis >= HOVER_SEEN_MILLIS) {
      this.addon.itemStates().markSeen(row.newsId());
    }
    this.lastHoverMillis = now;
  }

  /** Moves an item's animated state value towards its target and returns it. */
  private float animateState(Map<String, Float> values, String id, float target) {
    Float current = values.get(id);
    float next = current == null
        ? target // Items appear in their current state without animating.
        : Painter.approach(current, target, this.frameDelta / STATE_SECONDS);
    values.put(id, next);
    return next;
  }

  private void renderRow(Painter painter, Row row, float y, float height, boolean interactive,
      float hitTop, float hitBottom) {
    boolean hovered = interactive && this.isHovered(0, hitTop, WIDTH, hitBottom - hitTop);
    if (hovered) {
      // The hover background is cut to the viewport instead of faded.
      painter.rect(0, hitTop, WIDTH, hitBottom - hitTop, WHITE, 0.035F);
      this.trackHover(row);
    }

    // Unseen: filled dot, bright title. Seen: faint ring. Read: no dot, dimmed text.
    float unseen = this.animateState(this.unseenFade, row.newsId(),
        row.state() == State.UNSEEN ? 1F : 0F);
    float read = this.animateState(this.readFade, row.newsId(),
        row.state() == State.READ ? 1F : 0F);

    float metaY = y + ITEM_PAD_Y;
    float metaHeight = painter.lineHeight(SCALE_META);
    Painter meta = painter.withAlpha(this.edgeAlpha(metaY, metaY + metaHeight));
    float x = PAD;

    meta.text(row.label(), x, metaY, row.labelColor(), 0.95F - 0.4F * read, SCALE_META);
    x += meta.width(row.label(), SCALE_META);
    meta.text(" · " + row.detail(), x, metaY, WHITE, 0.4F - 0.15F * read, SCALE_META);

    // State as a small word at the right end of the meta line ("New" / "Seen" / "Read", in the
    // client's language). It cross-fades when the state changes.
    String stateText = I18n.translate(stateKey(row.state()));
    float stateWidth = meta.width(stateText, SCALE_STATE);
    float stateX = WIDTH - PAD - stateWidth;
    float stateY = metaY + (metaHeight - meta.lineHeight(SCALE_STATE)) / 2F;
    int stateColor = row.state() == State.UNSEEN ? row.labelColor() : WHITE;
    float stateOpacity = switch (row.state()) {
      case UNSEEN -> 0.95F;
      case SEEN -> 0.45F;
      case READ -> 0.28F;
    };
    float stateFade = row.state() == State.UNSEEN ? unseen
        : row.state() == State.READ ? read : 1F - unseen;
    meta.text(stateText, stateX, stateY, stateColor, stateOpacity * Math.max(0.3F, stateFade),
        SCALE_STATE);

    float titleLineHeight = painter.lineHeight(SCALE_TITLE);
    float titleOpacity = 0.95F - 0.05F * (1F - unseen) - 0.4F * read;
    float titleY = metaY + metaHeight + 2F;
    for (String line : this.wrapTitle(painter.canvas(), row)) {
      painter.withAlpha(this.edgeAlpha(titleY, titleY + titleLineHeight))
          .text(line, PAD, titleY, WHITE, titleOpacity, SCALE_TITLE);
      titleY += titleLineHeight + 0.8F;
    }

    this.addHitArea(interactive, 0, hitTop, WIDTH, hitBottom - hitTop, row.onClick());

    // Save icon (floppy): always shown when saved (filled), otherwise only while hovering the
    // row (outline). It sits just left of the state word, and its hit area is added last so it
    // wins over the row's own click.
    float markX = stateX - 3F - SAVE_ICON;
    float markY = metaY + (metaHeight - SAVE_ICON) / 2F;
    float slop = 2F;
    boolean markHovered = interactive && hovered && this.isHovered(markX - slop, markY - slop,
        SAVE_ICON + slop * 2F, SAVE_ICON + slop * 2F);
    if (row.saved() || hovered) {
      float opacity = row.saved() ? 0.9F : (markHovered ? 0.7F : 0.3F);
      meta.floppy(markX, markY, SAVE_ICON, ICON_STROKE * 0.75F, WHITE, opacity, row.saved());
    }
    if (hovered && markY - slop >= hitTop && markY + SAVE_ICON + slop <= hitBottom) {
      this.addHitArea(interactive, markX - slop, markY - slop, SAVE_ICON + slop * 2F,
          SAVE_ICON + slop * 2F, () -> this.addon.itemStates().toggleSaved(row.item()));
    }
  }

  private float footerHeight(ScreenCanvas canvas) {
    float row = canvas.getLineHeight() * SCALE_FOOTER;
    return 4F + (this.footerFitsOneRow(canvas) ? row : row * 2F + 1.5F);
  }

  private boolean footerFitsOneRow(ScreenCanvas canvas) {
    float total = canvas.getTextWidth(this.updatedText()) + canvas.getTextWidth(this.hideText())
        + canvas.getTextWidth(" · ") + canvas.getTextWidth(credit()) + canvas.getTextWidth("  ");
    return total * SCALE_FOOTER <= WIDTH - PAD * 2F;
  }

  private void renderFooter(Painter painter, float y) {
    float right = WIDTH - PAD;
    y += 4F;

    painter.text(this.updatedText(), PAD, y, WHITE, 0.4F, SCALE_FOOTER);
    if (this.footerFitsOneRow(painter.canvas())) {
      String text = this.hideText() + " · " + credit();
      painter.text(text, right - painter.width(text, SCALE_FOOTER), y, WHITE, 0.4F, SCALE_FOOTER);
      return;
    }

    String hide = this.hideText();
    painter.text(hide, right - painter.width(hide, SCALE_FOOTER), y, WHITE, 0.4F, SCALE_FOOTER);
    y += painter.lineHeight(SCALE_FOOTER) + 1.5F;
    painter.text(credit(), right - painter.width(credit(), SCALE_FOOTER), y, WHITE, 0.3F,
        SCALE_FOOTER);
  }

  private String updatedText() {
    Instant lastUpdated = this.addon.newsService().lastUpdated();
    if (lastUpdated == null) {
      return I18n.translate("newsfeed.panel.notUpdated");
    }

    return I18n.translate("newsfeed.panel.updatedPrefix")
        + formatAge(lastUpdated, Instant.now())
        + I18n.translate("newsfeed.panel.updatedSuffix");
  }

  private String hideText() {
    return this.addon.configuration().toggleKey().get().getName() + " = "
        + I18n.translate("newsfeed.panel.hide");
  }

  private static String credit() {
    return I18n.translate("newsfeed.panel.credit");
  }

  // ---------------------------------------------------------------------------------------------
  // Minimized pill

  private float pillWidth(ScreenCanvas canvas) {
    return PILL_PAD + ICON_SIZE + 4F + canvas.getTextWidth(this.pillText()) * SCALE_PILL
        + canvas.getTextWidth(this.pillKeyHint()) * SCALE_PILL + PILL_PAD + 2F;
  }

  private String pillKeyHint() {
    return " · " + this.addon.configuration().toggleKey().get().getName();
  }

  private String pillText() {
    int unseen = this.unseenCount();
    return unseen == 0
        ? I18n.translate("newsfeed.panel.title")
        : unseen + " " + I18n.translate("newsfeed.panel.newCount");
  }

  private void renderPillContent(Painter painter, float width, boolean interactive) {
    boolean hovered = interactive && this.isHovered(0, 0, width, PILL_HEIGHT);
    if (hovered) {
      painter.rounded(0, 0, width, PILL_HEIGHT, PILL_HEIGHT / 2F, WHITE, 0.06F);
    }

    // A soft ring that fades out when new items arrive.
    if (this.pillPulse > 0F) {
      float grow = (1F - this.pillPulse) * 2F;
      painter.rounded(-grow, -grow, width + grow * 2F, PILL_HEIGHT + grow * 2F,
          PILL_HEIGHT / 2F + grow, LIVE_GREEN, 0.25F * this.pillPulse);
    }

    painter.icon(this.feedIcon, PILL_PAD, (PILL_HEIGHT - ICON_SIZE) / 2F, ICON_SIZE);
    boolean hasUnread = this.unseenCount() > 0;
    float textX = PILL_PAD + ICON_SIZE + 4F;
    painter.textCentered(this.pillText(), textX, 0, PILL_HEIGHT,
        hasUnread ? LIVE_GREEN : WHITE, 0.95F, SCALE_PILL);
    // A quiet hint which key opens the panel.
    painter.textCentered(this.pillKeyHint(), textX + painter.width(this.pillText(), SCALE_PILL),
        0, PILL_HEIGHT, WHITE, 0.35F, SCALE_PILL);

    this.addHitArea(interactive, 0, 0, width, PILL_HEIGHT, this::expand);
  }

  // ---------------------------------------------------------------------------------------------
  // "12 new · Mark all as read" bar

  /** The bar is shown in the news list while anything is unseen (not in Saved or a detail). */
  private boolean wantsBanner() {
    return !this.showSaved && this.detailItem == null && this.unseenCount() > 0;
  }

  /** Current (animated) height of the bar; 0 when it is closed. */
  private float bannerHeight() {
    return BANNER_HEIGHT * Painter.easeInOut(this.bannerFade);
  }

  private void renderBanner(Painter painter, float y, float height, boolean interactive) {
    // Slightly lighter strip with a divider below, content vertically centered in the open bar
    painter.rect(0, y, WIDTH, height, WHITE, 0.025F);
    painter.rect(0, y + height - 0.25F, WIDTH, 0.5F, WHITE, 0.05F);
    float rowY = y + (height - BANNER_HEIGHT) / 2F;

    // Left: ● 12 new
    int unseen = this.unseenCount();
    float dotY = rowY + BANNER_HEIGHT / 2F;
    painter.circle(PAD + 1.3F, dotY, 1.3F, LIVE_GREEN, 0.95F);
    String count = unseen + " " + I18n.translate("newsfeed.panel.newCount");
    painter.textCentered(count, PAD + 5F, rowY, BANNER_HEIGHT, LIVE_GREEN, 0.95F, SCALE_FILTER);

    // Right: "Mark all as read" as a text button
    String action = I18n.translate("newsfeed.panel.markAllRead");
    float actionWidth = painter.width(action, SCALE_FILTER) + FILTER_PAD_X * 2F;
    float actionX = WIDTH - PAD - actionWidth + FILTER_PAD_X;
    float buttonY = rowY + (BANNER_HEIGHT - FILTER_HEIGHT) / 2F;
    boolean hovered = interactive
        && this.isHovered(actionX - FILTER_PAD_X, buttonY, actionWidth, FILTER_HEIGHT);
    if (hovered) {
      painter.rounded(actionX - FILTER_PAD_X, buttonY, actionWidth, FILTER_HEIGHT,
          FILTER_HEIGHT / 2F, WHITE, 0.08F);
    }
    painter.textCentered(action, actionX, rowY, BANNER_HEIGHT, WHITE, hovered ? 0.95F : 0.6F,
        SCALE_FILTER);

    List<String> unreadIds = this.unreadIds();
    this.addHitArea(interactive, actionX - FILTER_PAD_X, buttonY, actionWidth, FILTER_HEIGHT,
        () -> this.addon.itemStates().markAllRead(unreadIds));
  }

  /** Ids of everything loaded or saved that is not read yet (for "mark all as read"). */
  private List<String> unreadIds() {
    ItemStateStore states = this.addon.itemStates();
    List<String> ids = new ArrayList<>();
    for (NewsItem item : this.addon.newsService().items()) {
      if (states.get(item.id()) != State.READ) {
        ids.add(item.id());
      }
    }
    for (NewsItem item : states.savedItems()) {
      if (states.get(item.id()) != State.READ && !ids.contains(item.id())) {
        ids.add(item.id());
      }
    }
    return ids;
  }

  private int unseenCount() {
    int count = 0;
    for (NewsItem item : this.filteredItems(Integer.MAX_VALUE, NewsFilter.ALL)) {
      if (this.stateOf(item) == State.UNSEEN) {
        count++;
      }
    }
    return count;
  }

  private void selectFilter(NewsFilter value) {
    if (this.filter != value || this.showSaved) {
      this.filter = value;
      this.showSaved = false;
      this.resetScroll();
      this.contentFade = 0F;
    }
  }

  /** Switches between the news list and the "saved for later" list. */
  private void toggleSavedView() {
    this.detailItem = null;
    this.detailLayout = null;
    this.showSaved = !this.showSaved;
    this.resetScroll();
    this.contentFade = 0F;
  }

  private void resetScroll() {
    this.scrollTarget = 0F;
    this.scrollOffset = 0F;
  }

  private void minimize() {
    this.addon.setPanelMinimized(true);
  }

  private void expand() {
    this.addon.setPanelMinimized(false);
  }

  // ---------------------------------------------------------------------------------------------
  // Data

  /**
   * The rows for the current view: the saved list (in the order things were saved, ignoring
   * filters and keywords), or the news for the current filter.
   */
  private List<Row> buildRows() {
    ItemStateStore states = this.addon.itemStates();
    List<NewsItem> items;
    if (this.showSaved) {
      items = states.savedItems();
    } else {
      int limit = this.filter == NewsFilter.ALL ? MAX_ITEMS_ALL : MAX_ITEMS_SOURCE;
      items = this.filteredItems(limit, this.filter);
      if (items.isEmpty() && this.editorFrame) {
        items = sampleItems(); // so the HUD editor never shows an empty panel
      }
    }

    List<NewsItem> sequence = items;
    List<Row> rows = new ArrayList<>(sequence.size());
    Instant now = Instant.now();
    for (int i = 0; i < items.size(); i++) {
      NewsItem item = items.get(i);
      int index = i;
      rows.add(new Row(
          item,
          "news:" + item.id(),
          item.id(),
          I18n.translate(item.source().translationKey()).toLowerCase(Locale.ROOT),
          item.source().color(),
          formatAge(item.published(), now),
          item.title(),
          MAX_TITLE_LINES,
          states.get(item.id()),
          states.isSaved(item.id()),
          () -> this.openDetail(sequence, index)
      ));
    }
    return rows;
  }

  /** Example news for the HUD editor preview before anything has been fetched. */
  private static List<NewsItem> sampleItems() {
    Instant now = Instant.now();
    return List.of(
        sample(NewsSource.MINECRAFT, "minecraft", now.minusSeconds(60 * 12)),
        sample(NewsSource.LABYMOD, "labymod", now.minusSeconds(60 * 60 * 3)),
        sample(NewsSource.REDDIT, "reddit", now.minusSeconds(60 * 60 * 5)),
        sample(NewsSource.COSMETICS, "cosmetics", now.minusSeconds(60 * 60 * 26))
    );
  }

  private static NewsItem sample(NewsSource source, String key, Instant published) {
    return new NewsItem(source, "sample:" + key,
        I18n.translate("newsfeed.hudWidget.news_panel.sample." + key), null, published, null,
        null, null);
  }

  private List<NewsItem> filteredItems(int limit, NewsFilter filter) {
    NewsFeedConfiguration configuration = this.addon.configuration();
    List<String> keywords = configuration.activeKeywords();

    List<NewsItem> result = new ArrayList<>();
    for (NewsItem item : this.addon.newsService().items()) {
      if (!configuration.isSourceEnabled(item.source()) || !filter.matches(item)) {
        continue;
      }
      if (!NewsFeedConfiguration.matchesKeywords(item.title(), keywords)) {
        continue;
      }

      result.add(item);
      if (result.size() >= limit) {
        break;
      }
    }
    return result;
  }

  private String emptyMessageKey() {
    if (this.showSaved) {
      return "newsfeed.panel.savedEmpty";
    }
    if (this.addon.newsService().lastUpdated() == null
        && this.addon.configuration().sourceReddit().get()) {
      return "newsfeed.panel.loading";
    }
    if (this.addon.configuration().keywordsOnly().get()
        && !this.addon.configuration().activeKeywords().isEmpty()) {
      return "newsfeed.panel.noKeywordMatches";
    }
    return "newsfeed.panel.empty";
  }

  /** 1234 -> "1234", 12345 -> "12.3k". */
  private static String formatViewers(int viewers) {
    if (viewers < 10_000) {
      return String.valueOf(viewers);
    }
    return String.format(Locale.ROOT, "%.1fk", viewers / 1000.0);
  }

  private List<String> wrapTitle(ScreenCanvas canvas, Row row) {
    if (this.wrapCache.size() > 200) {
      this.wrapCache.clear();
    }
    return this.wrapCache.computeIfAbsent(row.key(), key -> TextWrap.wrap(
        canvas,
        row.title(),
        (WIDTH - PAD * 2F - 4F) / SCALE_TITLE,
        row.maxTitleLines()
    ));
  }

  public static String formatAge(Instant time, Instant now) {
    long minutes = Math.max(0, Duration.between(time, now).toMinutes());
    if (minutes < 1) {
      return I18n.translate("newsfeed.time.now");
    }
    if (minutes < 60) {
      return minutes + I18n.translate("newsfeed.time.minutesShort");
    }

    long hours = minutes / 60;
    if (hours < 24) {
      return hours + I18n.translate("newsfeed.time.hoursShort");
    }
    return (hours / 24) + I18n.translate("newsfeed.time.daysShort");
  }

  // ---------------------------------------------------------------------------------------------
  // Input

  private boolean isHovered(float x, float y, float width, float height) {
    return this.addon.labyAPI().minecraft().minecraftWindow().isScreenOpened()
        && this.mouseX >= x && this.mouseX < x + width
        && this.mouseY >= y && this.mouseY < y + height;
  }

  private void addHitArea(boolean interactive, float x, float y, float width, float height,
      Runnable action) {
    // Nothing is clickable halfway through the panel/pill animation.
    if (interactive) {
      this.hitAreas.add(new HitArea(x, y, width, height, action));
    }
  }

  @Override
  public boolean mouseClicked(MutableMouse mouse, MouseButton mouseButton) {
    // Hit areas are in local units from the last rendered frame; the mouse position recorded
    // during that frame is what the player clicked on.
    for (int i = this.hitAreas.size() - 1; i >= 0; i--) {
      HitArea area = this.hitAreas.get(i);
      if (this.mouseX >= area.x && this.mouseX < area.x + area.width
          && this.mouseY >= area.y && this.mouseY < area.y + area.height) {
        area.action.run();
        return true;
      }
    }
    return false;
  }

  private record HitArea(float x, float y, float width, float height, Runnable action) {

  }

  /** The laid-out detail content of one item; {@code height} is the full content height. */
  private record DetailLayout(NewsItem item, List<DetailOp> ops, float height) {

  }

  /** One piece of detail content: a line of text or a chip, at a y relative to the top. */
  private record DetailOp(
      String text,
      float x,
      float y,
      float width,
      float height,
      float scale,
      int color,
      float opacity,
      boolean chip
  ) {

    static DetailOp text(String text, float x, float y, float scale, int color, float opacity) {
      return new DetailOp(text, x, y, 0F, 0F, scale, color, opacity, false);
    }

    static DetailOp chip(String text, float x, float y, float width, float height, float scale,
        int color) {
      return new DetailOp(text, x, y, width, height, scale, color, 1F, true);
    }
  }

  /**
   * One news item in the list.
   *
   * @param item    the news item itself (needed to save it for later)
   * @param key     stable key for the wrap cache
   * @param newsId  the news item id
   * @param label   colored source name at the start of the meta line
   * @param detail  gray text after the label (age)
   * @param state   unseen / seen / read
   * @param saved   saved for later
   */
  private record Row(
      NewsItem item,
      String key,
      String newsId,
      String label,
      int labelColor,
      String detail,
      String title,
      int maxTitleLines,
      State state,
      boolean saved,
      Runnable onClick
  ) {

  }
}
