package dk.dutyc0re.newsfeed.core.news;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dk.dutyc0re.newsfeed.core.news.NewsItem.Fact;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Remembers per news item whether it is unseen, seen (the mouse rested on it) or read (clicked),
 * and which items the player saved for later.
 *
 * <p>States only move forward: unseen → seen → read. Unknown items are unseen. Saved items are
 * stored in full (title, link, date), so they stay after they have dropped out of their feed.
 *
 * <p>Saved to {@code labymod-neo/configs/newsfeed/item-states.json} so everything survives a
 * restart. Saving happens on its own background thread, a moment after the last change.
 */
public class ItemStateStore {

  public enum State {
    UNSEEN,
    SEEN,
    READ
  }

  /** Only the newest this many states are kept; older items have long left every feed. */
  private static final int MAX_STATES = 1000;
  private static final int MAX_SAVED = 200;
  private static final long SAVE_DELAY_MILLIS = 2000L;

  private final Path file;
  private final Consumer<String> warn;
  private final ScheduledExecutorService saver;
  private final Map<String, State> states = new LinkedHashMap<>();
  /** Saved items in the order they were saved (oldest first). */
  private final Map<String, NewsItem> saved = new LinkedHashMap<>();
  private boolean saveScheduled;

  public ItemStateStore(Path file, Consumer<String> warn) {
    this.file = file;
    this.warn = warn;
    this.saver = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "NewsFeed-StateSaver");
      thread.setDaemon(true);
      return thread;
    });
  }

  // -----------------------------------------------------------------------------------------------
  // Unseen / seen / read

  public synchronized State get(String id) {
    return this.states.getOrDefault(id, State.UNSEEN);
  }

  /** Unseen → seen. Does nothing if the item is already seen or read. */
  public synchronized void markSeen(String id) {
    if (this.get(id) == State.UNSEEN) {
      this.putState(id, State.SEEN);
    }
  }

  public synchronized void markRead(String id) {
    if (this.get(id) != State.READ) {
      this.putState(id, State.READ);
    }
  }

  /** Marks all given items as read in one go (one save). */
  public synchronized void markAllRead(Iterable<String> ids) {
    boolean changed = false;
    for (String id : ids) {
      if (this.get(id) != State.READ) {
        this.states.remove(id);
        this.states.put(id, State.READ);
        changed = true;
      }
    }
    if (changed) {
      trim(this.states, MAX_STATES);
      this.scheduleSave();
    }
  }

  private void putState(String id, State state) {
    this.states.remove(id); // re-insert so it counts as newest
    this.states.put(id, state);
    trim(this.states, MAX_STATES);
    this.scheduleSave();
  }

  // -----------------------------------------------------------------------------------------------
  // Saved for later

  public synchronized boolean isSaved(String id) {
    return this.saved.containsKey(id);
  }

  /** Saves the item, or removes it again if it was saved. Returns whether it is saved now. */
  public synchronized boolean toggleSaved(NewsItem item) {
    boolean nowSaved;
    if (this.saved.remove(item.id()) != null) {
      nowSaved = false;
    } else {
      this.saved.put(item.id(), item);
      trim(this.saved, MAX_SAVED);
      nowSaved = true;
    }
    this.scheduleSave();
    return nowSaved;
  }

  /** The saved items, most recently saved first. */
  public synchronized List<NewsItem> savedItems() {
    List<NewsItem> items = new ArrayList<>(this.saved.values());
    Collections.reverse(items);
    return items;
  }

  public synchronized int savedCount() {
    return this.saved.size();
  }

  private static void trim(Map<String, ?> map, int max) {
    Iterator<String> oldest = map.keySet().iterator();
    while (map.size() > max && oldest.hasNext()) {
      oldest.next();
      oldest.remove();
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Loading and saving

  /** Loads the saved file. A missing or broken file just means "nothing saved yet". */
  public synchronized void load() {
    if (!Files.exists(this.file)) {
      return;
    }

    try {
      String json = Files.readString(this.file, StandardCharsets.UTF_8);
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      this.loadStates(root.getAsJsonObject("states"));
      this.loadSaved(root.getAsJsonArray("saved"));
    } catch (Exception exception) {
      this.warn.accept("Could not read " + this.file + ": " + exception.getMessage());
    }
  }

  private void loadStates(JsonObject json) {
    if (json == null) {
      return;
    }
    for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
      try {
        this.states.put(entry.getKey(), State.valueOf(entry.getValue().getAsString()));
      } catch (IllegalArgumentException | IllegalStateException | ClassCastException ignored) {
        // Skip entries we don't understand.
      }
    }
  }

  private void loadSaved(JsonArray json) {
    if (json == null) {
      return;
    }
    for (JsonElement element : json) {
      try {
        JsonObject object = element.getAsJsonObject();
        List<Fact> facts = new ArrayList<>();
        JsonArray factsJson = object.getAsJsonArray("facts");
        if (factsJson != null) {
          for (JsonElement factElement : factsJson) {
            JsonObject fact = factElement.getAsJsonObject();
            facts.add(new Fact(Json.string(fact, "label"), Json.string(fact, "value")));
          }
        }
        NewsItem item = new NewsItem(
            NewsSource.valueOf(Json.string(object, "source")),
            Json.string(object, "id"),
            Json.string(object, "title"),
            Json.string(object, "link"),
            Instant.parse(Json.string(object, "published")),
            Json.string(object, "summary"),
            Json.string(object, "author"),
            facts
        );
        if (item.id() != null && item.title() != null) {
          this.saved.put(item.id(), item);
        }
      } catch (IllegalArgumentException | IllegalStateException | ClassCastException
               | NullPointerException | DateTimeParseException ignored) {
        // Skip entries we don't understand.
      }
    }
  }

  private void scheduleSave() {
    if (this.saveScheduled) {
      return;
    }
    this.saveScheduled = true;
    this.saver.schedule(this::save, SAVE_DELAY_MILLIS, TimeUnit.MILLISECONDS);
  }

  private void save() {
    JsonObject root = new JsonObject();
    JsonObject statesJson = new JsonObject();
    JsonArray savedJson = new JsonArray();
    synchronized (this) {
      this.saveScheduled = false;
      for (Map.Entry<String, State> entry : this.states.entrySet()) {
        statesJson.addProperty(entry.getKey(), entry.getValue().name());
      }
      for (NewsItem item : this.saved.values()) {
        JsonObject object = new JsonObject();
        object.addProperty("source", item.source().name());
        object.addProperty("id", item.id());
        object.addProperty("title", item.title());
        object.addProperty("link", item.link());
        object.addProperty("published", item.published().toString());
        object.addProperty("summary", item.summary());
        object.addProperty("author", item.author());
        JsonArray factsJson = new JsonArray();
        for (Fact fact : item.facts()) {
          JsonObject factJson = new JsonObject();
          factJson.addProperty("label", fact.labelKey());
          factJson.addProperty("value", fact.value());
          factsJson.add(factJson);
        }
        object.add("facts", factsJson);
        savedJson.add(object);
      }
    }
    root.addProperty("version", 1);
    root.add("states", statesJson);
    root.add("saved", savedJson);

    try {
      Files.createDirectories(this.file.getParent());
      Path temp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
      Files.writeString(temp, root.toString(), StandardCharsets.UTF_8);
      Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException exception) {
      this.warn.accept("Could not save " + this.file + ": " + exception.getMessage());
    }
  }
}
