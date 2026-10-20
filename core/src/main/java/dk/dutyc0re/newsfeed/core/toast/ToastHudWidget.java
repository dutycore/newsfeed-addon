package dk.dutyc0re.newsfeed.core.toast;

import dk.dutyc0re.newsfeed.core.NewsFeedAddon;
import dk.dutyc0re.newsfeed.core.news.NewsSource;
import java.util.Locale;
import net.labymod.api.client.gui.hud.hudwidget.HudWidgetConfig;
import net.labymod.api.client.gui.hud.hudwidget.SimpleHudWidget;
import net.labymod.api.client.gui.hud.position.HudSize;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.util.I18n;

/**
 * The pop-up as its own HUD widget, so the player can drag it anywhere in the HUD editor. It is
 * only visible in-game while a toast is showing, and slides in from the side it is anchored to.
 * In the editor it shows a sample toast. If it isn't placed, {@link ToastManager} draws toasts
 * in the top-right corner instead.
 */
public class ToastHudWidget extends SimpleHudWidget<HudWidgetConfig> {

  /** Extra slide distance so the toast clearly comes from off its edge. */
  private static final float SLIDE_EXTRA = 10F;

  private final NewsFeedAddon addon;
  private final ToastManager toasts;

  public ToastHudWidget(NewsFeedAddon addon, ToastManager toasts) {
    super("news_toast", HudWidgetConfig.class);
    this.addon = addon;
    this.toasts = toasts;
    this.setIcon(Icon.texture(addon.resource("textures/icon.png")));
  }

  @Override
  protected void onEnable() {
    this.toasts.setWidgetPlaced(true);
  }

  @Override
  protected void onDisable() {
    this.toasts.setWidgetPlaced(false);
  }

  @Override
  public boolean isVisibleInGame() {
    return this.toasts.isShowing();
  }

  @Override
  public void render(
      RenderPhase phase,
      ScreenContext context,
      boolean isEditorContext,
      HudSize size
  ) {
    if (phase == RenderPhase.UPDATE_SIZE) {
      size.set(ToastManager.WIDTH, this.toasts.height(context.canvas(), this.sample()));
      return;
    }

    if (isEditorContext && !this.toasts.isShowing()) {
      this.toasts.drawStatic(context, this.sample(), 0.7F);
      return;
    }

    if (this.toasts.advance()) {
      this.toasts.draw(context, this.anchor().isRight(), SLIDE_EXTRA);
    }
  }

  /** What the editor shows, so the player can see the size and look while placing it. */
  private Toast sample() {
    return new Toast(
        NewsSource.MINECRAFT.color(),
        I18n.translate(NewsSource.MINECRAFT.translationKey()).toLowerCase(Locale.ROOT),
        I18n.translate("newsfeed.time.now"),
        I18n.translate("newsfeed.hudWidget.news_toast.sample"),
        ""
    );
  }
}
