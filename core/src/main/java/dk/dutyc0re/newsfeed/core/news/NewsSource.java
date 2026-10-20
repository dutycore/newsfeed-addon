package dk.dutyc0re.newsfeed.core.news;

public enum NewsSource {
  MINECRAFT("minecraft", 0x7BD389),
  LABYMOD("labymod", 0x6FA8FF),
  REDDIT("reddit", 0xFF8A4C),
  COSMETICS("cosmetics", 0xC4A3FF);

  private final String id;
  private final int color;

  NewsSource(String id, int color) {
    this.id = id;
    this.color = color;
  }

  public String id() {
    return this.id;
  }

  public int color() {
    return this.color;
  }

  public String translationKey() {
    return "newsfeed.source." + this.id;
  }
}
