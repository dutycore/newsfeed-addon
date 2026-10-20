package dk.dutyc0re.newsfeed.core;

import dk.dutyc0re.newsfeed.core.config.NewsFeedConfiguration;
import dk.dutyc0re.newsfeed.core.hud.NewsPanelHudWidget;
import dk.dutyc0re.newsfeed.core.listener.CombatTracker;
import dk.dutyc0re.newsfeed.core.listener.PanelKeyListener;
import dk.dutyc0re.newsfeed.core.listener.PanelScrollListener;
import dk.dutyc0re.newsfeed.core.news.ItemStateStore;
import dk.dutyc0re.newsfeed.core.news.NewsItem;
import dk.dutyc0re.newsfeed.core.news.NewsService;
import dk.dutyc0re.newsfeed.core.toast.Toast;
import dk.dutyc0re.newsfeed.core.toast.ToastHudWidget;
import dk.dutyc0re.newsfeed.core.toast.ToastManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.labymod.api.Constants;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.client.resources.ResourceLocation;
import net.labymod.api.client.resources.sound.MinecraftSounds;
import net.labymod.api.models.OperatingSystem;
import net.labymod.api.models.addon.annotation.AddonMain;
import net.labymod.api.util.I18n;

@AddonMain
public class NewsFeedAddon extends LabyAddon<NewsFeedConfiguration> {

  /** The panel counts as on screen if it was drawn this recently. */
  private static final long PANEL_RENDER_TIMEOUT_NANOS = 250_000_000L;

  /**
   * A soft "pop" (the item pick-up sound), newest name first: 1.9+ and 1.8. Only names the
   * running version knows are played.
   */
  private static final ResourceLocation[] POP_SOUNDS = {
      ResourceLocation.create("minecraft", "entity.item.pickup"),
      ResourceLocation.create("minecraft", "random.pop"),
  };

  /** Quiet and a little lower than the game's own pick-up pop, so it never gets annoying. */
  private static final float POP_VOLUME = 0.3F;
  private static final float POP_PITCH = 1.25F;

  private NewsService newsService;
  private ItemStateStore itemStates;
  private CombatTracker combatTracker;
  private ToastManager toastManager;
  private volatile boolean panelVisible = true;
  /** The panel starts as the small pill; the toggle key opens it. */
  private volatile boolean panelMinimized = true;

  private volatile NewsPanelHudWidget renderedPanel;
  private volatile long panelRenderedNanos;

  @Override
  protected void enable() {
    this.registerSettingCategory();

    this.itemStates = new ItemStateStore(
        this.labyAPI().labyModLoader().getGameDirectory()
            .resolve(Constants.Files.CONFIGS)
            .resolve("newsfeed")
            .resolve("item-states.json"),
        message -> this.logger().warn(message)
    );
    this.itemStates.load();

    this.combatTracker = new CombatTracker(this);
    this.toastManager = new ToastManager(this);
    this.newsService = new NewsService(this);
    this.newsService.setNewItemsListener(this::onNewItems);

    this.labyAPI().hudWidgetRegistry().register(() -> new NewsPanelHudWidget(this));
    this.labyAPI().hudWidgetRegistry()
        .register(() -> new ToastHudWidget(this, this.toastManager));
    this.registerListener(new PanelKeyListener(this));
    this.registerListener(new PanelScrollListener(this));
    this.registerListener(this.combatTracker);
    this.registerListener(this.toastManager);
    this.newsService.start();

    this.logger().info("News Feed enabled");
  }

  @Override
  protected Class<NewsFeedConfiguration> configurationClass() {
    return NewsFeedConfiguration.class;
  }

  public NewsService newsService() {
    return this.newsService;
  }

  /** Unseen / seen / read per news item. */
  public ItemStateStore itemStates() {
    return this.itemStates;
  }

  public CombatTracker combatTracker() {
    return this.combatTracker;
  }

  public String version() {
    return this.addonInfo().getVersion();
  }

  /** Whether the panel is shown in-game. Toggled with the configured key (N by default). */
  public boolean isPanelVisible() {
    return this.panelVisible;
  }

  /** Whether the panel is shrunk to the small pill. */
  public boolean isPanelMinimized() {
    return this.panelMinimized;
  }

  public void setPanelMinimized(boolean minimized) {
    this.panelMinimized = minimized;
  }

  /**
   * The toggle key: hidden → open panel, pill → open panel, open panel → hidden. Showing the
   * panel always opens it fully, so the key can never leave you stuck with only the pill.
   */
  public void togglePanel() {
    if (!this.panelVisible) {
      this.panelVisible = true;
      this.panelMinimized = false;
    } else if (this.panelMinimized) {
      this.panelMinimized = false;
    } else {
      this.panelVisible = false;
    }
  }

  /** Called by the panel every frame it is drawn in-game. */
  public void markPanelRendered(NewsPanelHudWidget panel) {
    this.renderedPanel = panel;
    this.panelRenderedNanos = System.nanoTime();
  }

  /** The panel if it was drawn in-game a moment ago, otherwise {@code null}. */
  public NewsPanelHudWidget visiblePanel() {
    boolean recent = System.nanoTime() - this.panelRenderedNanos < PANEL_RENDER_TIMEOUT_NANOS;
    return recent && this.panelVisible ? this.renderedPanel : null;
  }

  /** Opens a URL in the player's browser. */
  public void openUrl(String url) {
    OperatingSystem.getPlatform().openUrl(url);
  }

  /** Plays a short, soft "pop". Render thread only. */
  public void playNotificationSound() {
    MinecraftSounds sounds = this.labyAPI().minecraft().sounds();
    for (ResourceLocation sound : POP_SOUNDS) {
      if (sounds.isRegistered(sound)) {
        sounds.playSound(sound, POP_VOLUME, POP_PITCH);
        return;
      }
    }
    sounds.playChatFilterSound();
  }

  /** Called on the fetcher thread with news that arrived since the last fetch. */
  private void onNewItems(List<NewsItem> items) {
    NewsFeedConfiguration configuration = this.configuration();
    if (!configuration.enabled().get() || !configuration.showPopups().get()) {
      return;
    }

    List<String> keywords = configuration.activeKeywords();
    List<NewsItem> shown = new ArrayList<>();
    for (NewsItem item : items) {
      if (configuration.isSourceEnabled(item.source())
          && configuration.isPopupEnabled(item.source())
          && NewsFeedConfiguration.matchesKeywords(item.title(), keywords)) {
        shown.add(item);
      }
    }
    if (shown.isEmpty()) {
      return;
    }

    // One toast per fetch: the newest item, plus how many more came in.
    NewsItem newest = shown.get(0);
    String more = shown.size() > 1
        ? "+" + (shown.size() - 1) + " " + I18n.translate("newsfeed.toast.more")
        : "";
    this.toastManager.enqueue(new Toast(
        newest.source().color(),
        I18n.translate(newest.source().translationKey()).toLowerCase(Locale.ROOT),
        NewsPanelHudWidget.formatAge(newest.published(), Instant.now()),
        newest.title(),
        more
    ));
  }
}
