package dk.dutyc0re.newsfeed.core.hud;

import dk.dutyc0re.newsfeed.core.news.NewsItem;
import dk.dutyc0re.newsfeed.core.news.NewsSource;

public enum NewsFilter {
  ALL(null),
  MINECRAFT(NewsSource.MINECRAFT),
  LABYMOD(NewsSource.LABYMOD),
  REDDIT(NewsSource.REDDIT),
  COSMETICS(NewsSource.COSMETICS);

  private final NewsSource source;

  NewsFilter(NewsSource source) {
    this.source = source;
  }

  public NewsSource source() {
    return this.source;
  }

  public boolean matches(NewsItem item) {
    return this.source == null || item.source() == this.source;
  }

  public int accentColor() {
    return this.source == null ? 0xFFFFFF : this.source.color();
  }

  public String translationKey() {
    return this.source == null ? "newsfeed.panel.filter.all" : this.source.translationKey();
  }
}
