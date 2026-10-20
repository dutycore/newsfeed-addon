package dk.dutyc0re.newsfeed.core.hud;

import java.util.ArrayList;
import java.util.List;
import net.labymod.api.client.gui.screen.state.ScreenCanvas;

/** Word wrapping measured with the game font. Widths are in unscaled font units. */
public final class TextWrap {

  private static final String ELLIPSIS = "…";

  private TextWrap() {
  }

  /** Word-wraps text to at most {@code maxLines} lines, ending with "…" if cut. */
  public static List<String> wrap(ScreenCanvas canvas, String text, float maxWidth, int maxLines) {
    List<String> lines = new ArrayList<>();
    String[] words = text.strip().split("\\s+");
    StringBuilder line = new StringBuilder();
    int index = 0;

    while (index < words.length && lines.size() < maxLines) {
      String word = words[index];
      String candidate = line.isEmpty() ? word : line + " " + word;
      if (canvas.getTextWidth(candidate) <= maxWidth) {
        line.setLength(0);
        line.append(candidate);
        index++;
        continue;
      }

      if (line.isEmpty()) {
        // A single word wider than the line: hard cut it.
        line.append(cut(canvas, word, maxWidth));
        index++;
      }
      lines.add(line.toString());
      line.setLength(0);
    }

    if (!line.isEmpty() && lines.size() < maxLines) {
      lines.add(line.toString());
    }

    if (index < words.length && !lines.isEmpty()) {
      int last = lines.size() - 1;
      String shortened = cut(canvas, lines.get(last), maxWidth - canvas.getTextWidth(ELLIPSIS));
      lines.set(last, shortened + ELLIPSIS);
    }
    return lines;
  }

  /** Cuts text to fit in {@code maxWidth}, without adding an ellipsis. */
  public static String cut(ScreenCanvas canvas, String text, float maxWidth) {
    if (canvas.getTextWidth(text) <= maxWidth) {
      return text;
    }

    int end = text.length();
    while (end > 1 && canvas.getTextWidth(text.substring(0, end)) > maxWidth) {
      end--;
    }
    return text.substring(0, end).stripTrailing();
  }
}
