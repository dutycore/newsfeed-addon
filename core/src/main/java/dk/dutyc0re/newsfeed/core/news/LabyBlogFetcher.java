package dk.dutyc0re.newsfeed.core.news;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches LabyMod news from the Laby blog API ({@code https://laby.net/api/v3/blog}), the same
 * data laby.net/blog shows. The blog's RSS feed only carries a few Minecraft guides, so the JSON
 * API is used instead. {@code publishedAt} has no zone; it is read as UTC.
 */
public class LabyBlogFetcher implements NewsFetcher {

  private static final String API_URL = "https://laby.net/api/v3/blog?pageSize=20";

  private final Http http;

  public LabyBlogFetcher(Http http) {
    this.http = http;
  }

  @Override
  public NewsSource source() {
    return NewsSource.LABYMOD;
  }

  @Override
  public Duration interval() {
    return Duration.ofMinutes(10);
  }

  @Override
  public List<NewsItem> fetch() throws IOException, InterruptedException {
    String body = new String(this.http.get(API_URL, "application/json"), StandardCharsets.UTF_8);
    try {
      return parse(body);
    } catch (JsonParseException | IllegalStateException | ClassCastException exception) {
      throw new IOException("Could not parse Laby blog", exception);
    }
  }

  private static List<NewsItem> parse(String body) {
    JsonArray articles = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("articles");
    List<NewsItem> items = new ArrayList<>();
    if (articles == null) {
      return items;
    }

    for (JsonElement element : articles) {
      JsonObject article = element.getAsJsonObject();
      String id = Json.string(article, "id");
      String title = Json.string(article, "title");
      String url = Json.string(article, "url");
      if (id == null || title == null || title.isBlank()) {
        continue;
      }

      items.add(new NewsItem(
          NewsSource.LABYMOD,
          "labymod:" + id,
          title.strip(),
          url,
          parseTime(Json.string(article, "publishedAt")),
          HtmlText.toPlainText(Json.string(article, "description")),
          Json.string(article, "authorName"),
          null
      ));
    }
    return items;
  }

  private static Instant parseTime(String value) {
    if (value == null) {
      return Instant.EPOCH;
    }

    try {
      return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
    } catch (DateTimeParseException exception) {
      return Instant.EPOCH;
    }
  }
}
