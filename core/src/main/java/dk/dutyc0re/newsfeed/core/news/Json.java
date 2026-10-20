package dk.dutyc0re.newsfeed.core.news;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Null-safe field access for Gson objects. */
final class Json {

  private Json() {
  }

  static String string(JsonObject object, String key) {
    JsonElement element = object.get(key);
    return element == null || element.isJsonNull() ? null : element.getAsString();
  }

  static boolean bool(JsonObject object, String key) {
    JsonElement element = object.get(key);
    return element != null && !element.isJsonNull() && element.getAsBoolean();
  }
}
