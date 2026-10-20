package dk.dutyc0re.newsfeed.core.news;

import com.google.gson.JsonArray;
import dk.dutyc0re.newsfeed.core.news.NewsItem.Fact;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches the newest cosmetics from the Laby shop API
 * ({@code https://laby.net/api/v3/shop/content}, the data behind laby.net/shop). Items that are
 * on sale and not just previewed are sorted by {@code releasedAt}.
 * {@code https://laby.net/shop/item/<id>} redirects to the item page.
 *
 * <p>The response is about 0.5 MB, so it is fetched far less often than the other sources.
 */
public class LabyCosmeticsFetcher implements NewsFetcher {

  private static final String API_URL = "https://laby.net/api/v3/shop/content";
  private static final String ITEM_URL = "https://laby.net/shop/item/";
  private static final int MAX_ITEMS = 15;

  private final Http http;

  public LabyCosmeticsFetcher(Http http) {
    this.http = http;
  }

  @Override
  public NewsSource source() {
    return NewsSource.COSMETICS;
  }

  @Override
  public Duration interval() {
    return Duration.ofMinutes(15);
  }

  @Override
  public List<NewsItem> fetch() throws IOException, InterruptedException {
    byte[] body = this.http.get(API_URL, "application/json");
    try (InputStreamReader reader = new InputStreamReader(
        new ByteArrayInputStream(body), StandardCharsets.UTF_8)) {
      return parse(JsonParser.parseReader(reader));
    } catch (JsonParseException | IllegalStateException | ClassCastException exception) {
      throw new IOException("Could not parse Laby shop", exception);
    }
  }

  private static List<NewsItem> parse(JsonElement root) {
    JsonArray shopItems = root.getAsJsonObject().getAsJsonArray("items");
    List<NewsItem> items = new ArrayList<>();
    if (shopItems == null) {
      return items;
    }
    Map<String, String> categoryNames = categoryNames(root.getAsJsonObject());

    for (JsonElement element : shopItems) {
      JsonObject item = element.getAsJsonObject();
      String id = Json.string(item, "id");
      String name = Json.string(item, "itemName");
      Instant released = parseTime(Json.string(item, "releasedAt"));
      if (id == null || name == null || name.isBlank() || released == null
          || !Json.bool(item, "onSale") || Json.bool(item, "onPreview")) {
        continue;
      }

      items.add(new NewsItem(
          NewsSource.COSMETICS,
          "cosmetics:" + id,
          name.strip(),
          ITEM_URL + id,
          released,
          null,
          null,
          facts(item, categoryNames)
      ));
    }

    items.sort(Comparator.comparing(NewsItem::published).reversed());
    return items.size() > MAX_ITEMS ? new ArrayList<>(items.subList(0, MAX_ITEMS)) : items;
  }

  /**
   * The shop has no item descriptions, so the detail view shows facts from the item instead:
   * its primary category (named via the shop's {@code categories} list), creator, and whether it
   * is limited or Plus-only. The price is left out because the API doesn't say which currency
   * {@code price0} is in.
   */
  private static List<Fact> facts(JsonObject item, Map<String, String> categoryNames) {
    List<Fact> facts = new ArrayList<>();

    JsonArray categories = item.getAsJsonArray("categories");
    if (categories != null) {
      for (JsonElement element : categories) {
        JsonObject category = element.getAsJsonObject();
        String name = categoryNames.get(Json.string(category, "categoryId"));
        if (Json.bool(category, "isPrimary") && name != null) {
          facts.add(new Fact("newsfeed.detail.fact.category", name));
          break;
        }
      }
    }

    String creator = Json.string(item, "creatorName");
    if (creator != null && !creator.isBlank()) {
      facts.add(new Fact("newsfeed.detail.fact.creator", creator));
    }
    if (Json.bool(item, "isLimited")) {
      facts.add(new Fact("newsfeed.detail.fact.limited", ""));
    }
    if (Json.bool(item, "plusOnly")) {
      facts.add(new Fact("newsfeed.detail.fact.plusOnly", ""));
    }
    return facts;
  }

  /** Category id → display name, from the shop's top-level {@code categories} list. */
  private static Map<String, String> categoryNames(JsonObject root) {
    Map<String, String> names = new HashMap<>();
    JsonArray categories = root.getAsJsonArray("categories");
    if (categories != null) {
      for (JsonElement element : categories) {
        JsonObject category = element.getAsJsonObject();
        String id = Json.string(category, "id");
        String name = Json.string(category, "name");
        if (id != null && name != null) {
          names.put(id, name);
        }
      }
    }
    return names;
  }

  private static Instant parseTime(String value) {
    if (value == null) {
      return null;
    }

    try {
      return OffsetDateTime.parse(value).toInstant();
    } catch (DateTimeParseException exception) {
      return null;
    }
  }
}
