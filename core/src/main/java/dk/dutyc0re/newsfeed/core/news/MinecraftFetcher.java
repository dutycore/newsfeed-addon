package dk.dutyc0re.newsfeed.core.news;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Fetches the latest Minecraft.net articles from the official RSS feed
 * ({@code https://www.minecraft.net/en-us/feeds/community-content/rss}). The feed holds the 10
 * newest articles; the link is in an Atom {@code <a10:link href>} element and dates look like
 * {@code Fri, 16 Oct 2026 19:15:00 Z}.
 */
public class MinecraftFetcher implements NewsFetcher {

  private static final String FEED_URL =
      "https://www.minecraft.net/en-us/feeds/community-content/rss";
  private static final String ATOM_NS = "http://www.w3.org/2005/Atom";

  private final Http http;

  public MinecraftFetcher(Http http) {
    this.http = http;
  }

  @Override
  public NewsSource source() {
    return NewsSource.MINECRAFT;
  }

  @Override
  public Duration interval() {
    return Duration.ofMinutes(10);
  }

  @Override
  public List<NewsItem> fetch() throws IOException, InterruptedException {
    Document document = Xml.parse(this.http.get(FEED_URL, "application/rss+xml"));

    List<NewsItem> items = new ArrayList<>();
    NodeList entries = document.getElementsByTagName("item");
    for (int i = 0; i < entries.getLength(); i++) {
      Element item = (Element) entries.item(i);
      String title = childText(item, "title");
      String link = link(item);
      if (title == null || title.isBlank() || link == null) {
        continue;
      }

      items.add(new NewsItem(
          NewsSource.MINECRAFT,
          "minecraft:" + link,
          title.strip(),
          link,
          parseTime(childText(item, "pubDate")),
          HtmlText.toPlainText(childText(item, "description")),
          null,
          null
      ));
    }
    return items;
  }

  private static String link(Element item) {
    NodeList atomLinks = item.getElementsByTagNameNS(ATOM_NS, "link");
    if (atomLinks.getLength() > 0) {
      String href = ((Element) atomLinks.item(0)).getAttribute("href");
      if (!href.isBlank()) {
        return href;
      }
    }

    String plain = childText(item, "link");
    return plain == null || plain.isBlank() ? null : plain.strip();
  }

  private static String childText(Element parent, String name) {
    NodeList nodes = parent.getElementsByTagName(name);
    return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
  }

  private static Instant parseTime(String value) {
    if (value == null) {
      return Instant.EPOCH;
    }

    // RFC 1123 wants "GMT" or "+0000"; the feed writes "Z".
    String normalized = value.strip().replaceAll(" Z$", " GMT");
    try {
      return ZonedDateTime.parse(normalized, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
    } catch (DateTimeParseException exception) {
      return Instant.EPOCH;
    }
  }
}
