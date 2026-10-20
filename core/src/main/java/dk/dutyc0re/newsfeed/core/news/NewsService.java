package dk.dutyc0re.newsfeed.core.news;

import dk.dutyc0re.newsfeed.core.NewsFeedAddon;
import dk.dutyc0re.newsfeed.core.config.NewsFeedConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Fetches all sources on one background thread and caches the results. Each source has its own
 * interval (never under 2 minutes). The render/game thread only ever reads the cached lists.
 *
 * <p>A source's first fetch fills the panel right away, also if the newest article is days old,
 * but only items that show up in later fetches are reported as new (toast). Whether an item is
 * unseen, seen or read is tracked separately in {@link ItemStateStore}.
 */
public class NewsService {

  /** Minimum time between two requests to the same source. */
  public static final Duration MIN_INTERVAL = Duration.ofMinutes(2);

  private final NewsFeedAddon addon;
  private final List<NewsFetcher> fetchers;
  private final ScheduledExecutorService executor;

  // Written on the fetcher thread only.
  private final Map<NewsSource, List<NewsItem>> itemsBySource = new EnumMap<>(NewsSource.class);
  private final Set<String> seenNewsIds = new HashSet<>();
  private final Set<NewsSource> baselineDone = EnumSet.noneOf(NewsSource.class);

  private volatile List<NewsItem> items = List.of();
  private volatile Instant lastUpdated;

  private volatile Consumer<List<NewsItem>> newItemsListener;

  public NewsService(NewsFeedAddon addon) {
    this.addon = addon;

    Http http = new Http("NewsFeed-LabyAddon/" + addon.version() + " (by dutyc0re)");
    this.fetchers = List.of(
        new RedditFetcher(http),
        new MinecraftFetcher(http),
        new LabyBlogFetcher(http),
        new LabyCosmeticsFetcher(http)
    );

    this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "NewsFeed-Fetcher");
      thread.setDaemon(true);
      return thread;
    });
  }

  public void start() {
    // Stagger the first fetches a little so the panel fills up right after joining.
    long initialDelay = 2;
    for (NewsFetcher fetcher : this.fetchers) {
      long interval = Math.max(MIN_INTERVAL.toSeconds(), fetcher.interval().toSeconds());
      this.executor.scheduleWithFixedDelay(() -> this.refresh(fetcher), initialDelay, interval,
          TimeUnit.SECONDS);
      initialDelay += 2;
    }
  }

  /** Called on the fetcher thread with news items that are new since the last fetch. */
  public void setNewItemsListener(Consumer<List<NewsItem>> listener) {
    this.newItemsListener = listener;
  }

  private void refresh(NewsFetcher fetcher) {
    NewsFeedConfiguration configuration = this.addon.configuration();
    if (!configuration.enabled().get() || !configuration.isSourceEnabled(fetcher.source())) {
      return;
    }

    try {
      List<NewsItem> fetched = new ArrayList<>();
      for (NewsItem item : fetcher.fetch()) {
        String title = TitleCleaner.clean(item.title());
        if (!title.isEmpty()) {
          fetched.add(item.withText(title, TitleCleaner.cleanText(item.summary())));
        }
      }
      fetched.sort(Comparator.comparing(NewsItem::published).reversed());
      this.itemsBySource.put(fetcher.source(), List.copyOf(fetched));
      this.rebuildItems();
      this.lastUpdated = Instant.now();
      this.detectNewItems(fetcher.source(), fetched);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    } catch (Exception exception) {
      // Keep the previous cache, try again on the next interval.
      this.addon.logger().warn("Could not fetch " + fetcher.source().id() + ": "
          + exception.getMessage());
    }
  }

  private void rebuildItems() {
    List<NewsItem> all = new ArrayList<>();
    for (List<NewsItem> sourceItems : this.itemsBySource.values()) {
      all.addAll(sourceItems);
    }
    all.sort(Comparator.comparing(NewsItem::published).reversed());
    this.items = List.copyOf(all);
  }

  private void detectNewItems(NewsSource source, List<NewsItem> fetched) {
    boolean baseline = this.baselineDone.add(source);
    List<NewsItem> newItems = new ArrayList<>();
    for (NewsItem item : fetched) {
      if (this.seenNewsIds.add(item.id()) && !baseline) {
        newItems.add(item);
      }
    }

    Consumer<List<NewsItem>> listener = this.newItemsListener;
    if (listener != null && !newItems.isEmpty()) {
      listener.accept(List.copyOf(newItems));
    }
  }

  /** All cached news from every source, newest first. Safe to call from any thread. */
  public List<NewsItem> items() {
    return this.items;
  }

  /** When any news source was last refreshed, or {@code null} if none was yet. */
  public Instant lastUpdated() {
    return this.lastUpdated;
  }
}
