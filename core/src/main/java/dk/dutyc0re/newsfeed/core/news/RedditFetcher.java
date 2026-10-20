package dk.dutyc0re.newsfeed.core.news;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Fetches the newest posts from r/Minecraft via Reddit's public Atom feed
 * ({@code https://www.reddit.com/r/Minecraft/new/.rss}).
 */
public class RedditFetcher implements NewsFetcher {

  private static final String FEED_URL = "https://www.reddit.com/r/Minecraft/new/.rss?limit=25";
  private static final String ATOM_NS = "http://www.w3.org/2005/Atom";
  private static final Pattern POST_TEXT =
      Pattern.compile("<div class=\"md\">(.*?)</div>", Pattern.DOTALL);

  private final Http http;

  public RedditFetcher(Http http) {
    this.http = http;
  }

  @Override
  public NewsSource source() {
    return NewsSource.REDDIT;
  }

  @Override
  public Duration interval() {
    return Duration.ofMinutes(2);
  }

  @Override
  public List<NewsItem> fetch() throws IOException, InterruptedException {
    Document document = Xml.parse(this.http.get(FEED_URL, "application/atom+xml"));

    List<NewsItem> items = new ArrayList<>();
    NodeList entries = document.getElementsByTagNameNS(ATOM_NS, "entry");
    for (int i = 0; i < entries.getLength(); i++) {
      Element entry = (Element) entries.item(i);
      String id = childText(entry, "id");
      String title = childText(entry, "title");
      if (id == null || title == null) {
        continue;
      }

      String link = null;
      NodeList links = entry.getElementsByTagNameNS(ATOM_NS, "link");
      if (links.getLength() > 0) {
        link = ((Element) links.item(0)).getAttribute("href");
      }

      items.add(new NewsItem(
          NewsSource.REDDIT,
          "reddit:" + id,
          title.strip(),
          link,
          parseTime(childText(entry, "published")),
          postText(childText(entry, "content")),
          author(entry),
          null
      ));
    }
    return items;
  }

  /**
   * The post's own text. Reddit's {@code <content>} is HTML: a table with a thumbnail, the post
   * text in {@code <div class="md">} (only for text posts) and a "submitted by … [link]
   * [comments]" footer. Image and video posts have no text and return "".
   */
  private static String postText(String contentHtml) {
    if (contentHtml == null) {
      return "";
    }

    Matcher matcher = POST_TEXT.matcher(contentHtml);
    return matcher.find() ? HtmlText.toPlainText(matcher.group(1)) : "";
  }

  /** "/u/name" → "u/name". */
  private static String author(Element entry) {
    NodeList authors = entry.getElementsByTagNameNS(ATOM_NS, "author");
    if (authors.getLength() == 0) {
      return "";
    }
    String name = childText((Element) authors.item(0), "name");
    if (name == null) {
      return "";
    }
    name = name.strip();
    return name.startsWith("/") ? name.substring(1) : name;
  }

  private static String childText(Element parent, String name) {
    NodeList nodes = parent.getElementsByTagNameNS(ATOM_NS, name);
    return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
  }

  private static Instant parseTime(String value) {
    if (value == null) {
      return Instant.now();
    }

    try {
      return OffsetDateTime.parse(value.strip()).toInstant();
    } catch (DateTimeParseException exception) {
      return Instant.now();
    }
  }
}
