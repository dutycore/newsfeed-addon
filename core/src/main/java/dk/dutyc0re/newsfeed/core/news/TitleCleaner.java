package dk.dutyc0re.newsfeed.core.news;

/**
 * Removes characters the Minecraft font can't draw (emoji and friends), which otherwise show up
 * as empty boxes, and tidies the whitespace that is left.
 */
final class TitleCleaner {

  private TitleCleaner() {
  }

  static String clean(String title) {
    StringBuilder result = new StringBuilder(title.length());
    // A plain loop: the addon store guidelines don't allow streams (IntStream from codePoints()).
    for (int i = 0; i < title.length(); ) {
      int codePoint = title.codePointAt(i);
      if (!isEmojiLike(codePoint)) {
        result.appendCodePoint(codePoint);
      }
      i += Character.charCount(codePoint);
    }
    return result.toString().replaceAll("\\s+", " ").strip();
  }

  /** Like {@link #clean(String)}, but keeps line breaks (paragraphs) in longer text. */
  static String cleanText(String text) {
    StringBuilder result = new StringBuilder();
    for (String line : text.split("\n")) {
      String cleaned = clean(line);
      if (!cleaned.isEmpty()) {
        if (!result.isEmpty()) {
          result.append('\n');
        }
        result.append(cleaned);
      }
    }
    return result.toString();
  }

  private static boolean isEmojiLike(int codePoint) {
    return codePoint > 0xFFFF                              // emoji, symbols outside the BMP
        || (codePoint >= 0x2600 && codePoint <= 0x27BF)    // misc symbols, dingbats
        || (codePoint >= 0x2B00 && codePoint <= 0x2BFF)    // arrows/stars used as emoji
        || (codePoint >= 0xFE00 && codePoint <= 0xFE0F)    // variation selectors
        || codePoint == 0x200D                             // zero width joiner
        || codePoint == 0x20E3;                            // combining keycap
  }
}
