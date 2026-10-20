package dk.dutyc0re.newsfeed.core.news;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns the small HTML snippets in feeds into plain text with paragraphs kept as newlines. */
final class HtmlText {

  private static final Pattern BREAKS =
      Pattern.compile("(?i)<br\\s*/?>|</p>|</li>|</h[1-6]>|</blockquote>");
  private static final Pattern TAGS = Pattern.compile("<[^>]+>");
  private static final Pattern COMMENTS = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
  private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?)([0-9a-fA-F]+);");

  private HtmlText() {
  }

  static String toPlainText(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }

    String text = COMMENTS.matcher(html).replaceAll("");
    text = BREAKS.matcher(text).replaceAll("\n");
    text = TAGS.matcher(text).replaceAll("");
    text = decodeEntities(text);

    // Tidy whitespace per line and drop empty lines.
    StringBuilder result = new StringBuilder();
    for (String line : text.split("\n")) {
      String trimmed = line.replaceAll("[ \\t\\u00A0]+", " ").strip();
      if (!trimmed.isEmpty()) {
        if (!result.isEmpty()) {
          result.append('\n');
        }
        result.append(trimmed);
      }
    }
    return result.toString();
  }

  private static String decodeEntities(String text) {
    Matcher matcher = NUMERIC_ENTITY.matcher(text);
    StringBuilder decoded = new StringBuilder();
    while (matcher.find()) {
      String replacement;
      try {
        int codePoint = Integer.parseInt(matcher.group(2), matcher.group(1).isEmpty() ? 10 : 16);
        replacement = new String(Character.toChars(codePoint));
      } catch (IllegalArgumentException exception) {
        replacement = matcher.group();
      }
      matcher.appendReplacement(decoded, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(decoded);

    return decoded.toString()
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&");
  }
}
