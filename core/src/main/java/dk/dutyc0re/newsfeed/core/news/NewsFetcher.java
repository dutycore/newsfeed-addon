package dk.dutyc0re.newsfeed.core.news;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/** One news source. {@link #fetch()} runs on the fetcher thread only. */
public interface NewsFetcher {

  NewsSource source();

  /** How often to ask the source. Never less than 2 minutes. */
  Duration interval();

  List<NewsItem> fetch() throws IOException, InterruptedException;
}
