package dk.dutyc0re.newsfeed.core.toast;

/**
 * One toast about new news. All text is already translated.
 *
 * @param accentColor color of the label and countdown bar
 * @param label       colored source name at the start of the meta line
 * @param detail      gray text after the label (age)
 * @param title       the headline, up to 2 lines
 * @param more        right-aligned hint such as "+2 more", or empty
 */
public record Toast(
    int accentColor,
    String label,
    String detail,
    String title,
    String more
) {

}
