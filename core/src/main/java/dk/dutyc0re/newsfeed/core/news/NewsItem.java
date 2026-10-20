package dk.dutyc0re.newsfeed.core.news;

import java.time.Instant;
import java.util.List;

/**
 * One news item from any source.
 *
 * @param summary a short plain-text description for the detail view, paragraphs separated by
 *                {@code \n}; empty if the source has none
 * @param author  who wrote it (e.g. "u/name" or a blog author), or empty
 * @param facts   extra details shown as small chips in the detail view (used by cosmetics)
 */
public record NewsItem(
    NewsSource source,
    String id,
    String title,
    String link,
    Instant published,
    String summary,
    String author,
    List<Fact> facts
) {

  public NewsItem {
    summary = summary == null ? "" : summary;
    author = author == null ? "" : author;
    facts = facts == null ? List.of() : List.copyOf(facts);
  }

  /** A copy with a cleaned-up title and summary. */
  public NewsItem withText(String title, String summary) {
    return new NewsItem(this.source, this.id, title, this.link, this.published, summary,
        this.author, this.facts);
  }

  /**
   * One extra detail.
   *
   * @param labelKey translation key of the label
   * @param value    the value, or empty to show just the label (e.g. "Limited")
   */
  public record Fact(String labelKey, String value) {

    public Fact {
      value = value == null ? "" : value;
    }
  }
}
