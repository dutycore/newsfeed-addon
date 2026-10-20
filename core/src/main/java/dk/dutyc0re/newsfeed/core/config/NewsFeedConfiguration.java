package dk.dutyc0re.newsfeed.core.config;

import dk.dutyc0re.newsfeed.core.news.NewsSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.labymod.api.addon.AddonConfig;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.client.gui.screen.widget.widgets.input.KeybindWidget.KeyBindSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget.SliderSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.TagInputWidget.TagCollection;
import net.labymod.api.client.gui.screen.widget.widgets.input.TagInputWidget.TagCollection.Tag;
import net.labymod.api.client.gui.screen.widget.widgets.input.TagInputWidget.TagInputSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.DropdownWidget.DropdownSetting;
import net.labymod.api.configuration.loader.annotation.ConfigName;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import net.labymod.api.configuration.settings.annotation.SettingRequires;
import net.labymod.api.configuration.settings.annotation.SettingSection;

@ConfigName("settings")
public class NewsFeedConfiguration extends AddonConfig {

  @SwitchSetting
  private final ConfigProperty<Boolean> enabled = new ConfigProperty<>(true);

  @SettingSection("sources")
  @SwitchSetting
  private final ConfigProperty<Boolean> sourceMinecraft = new ConfigProperty<>(true);

  @SwitchSetting
  private final ConfigProperty<Boolean> sourceLabyMod = new ConfigProperty<>(true);

  @SwitchSetting
  private final ConfigProperty<Boolean> sourceReddit = new ConfigProperty<>(true);

  @SwitchSetting
  private final ConfigProperty<Boolean> sourceCosmetics = new ConfigProperty<>(true);

  @SettingSection("notifications")
  @SwitchSetting
  private final ConfigProperty<Boolean> showPopups = new ConfigProperty<>(true);

  // Pop-ups per source; only active while "Show pop-ups" is on.
  @SettingRequires("showPopups")
  @SwitchSetting
  private final ConfigProperty<Boolean> popupsMinecraft = new ConfigProperty<>(true);

  @SettingRequires("showPopups")
  @SwitchSetting
  private final ConfigProperty<Boolean> popupsLabyMod = new ConfigProperty<>(true);

  @SettingRequires("showPopups")
  @SwitchSetting
  private final ConfigProperty<Boolean> popupsReddit = new ConfigProperty<>(true);

  @SettingRequires("showPopups")
  @SwitchSetting
  private final ConfigProperty<Boolean> popupsCosmetics = new ConfigProperty<>(true);

  @SwitchSetting
  private final ConfigProperty<Boolean> doNotDisturbInCombat = new ConfigProperty<>(true);

  @SwitchSetting
  private final ConfigProperty<Boolean> sound = new ConfigProperty<>(false);

  @SettingSection("filter")
  @SwitchSetting
  private final ConfigProperty<Boolean> keywordsOnly = new ConfigProperty<>(false);

  @TagInputSetting
  private final ConfigProperty<TagCollection> keywords = new ConfigProperty<>(new TagCollection());

  @SettingSection("appearance")
  @SliderSetting(min = 20, max = 100, steps = 5)
  private final ConfigProperty<Integer> opacity = new ConfigProperty<>(82);

  @DropdownSetting
  private final ConfigProperty<FeedLanguage> language =
      new ConfigProperty<>(FeedLanguage.FOLLOW_LABYMOD);

  @SettingSection("controls")
  @KeyBindSetting
  private final ConfigProperty<Key> toggleKey = new ConfigProperty<>(Key.N);

  @Override
  public ConfigProperty<Boolean> enabled() {
    return this.enabled;
  }

  public ConfigProperty<Boolean> sourceMinecraft() {
    return this.sourceMinecraft;
  }

  public ConfigProperty<Boolean> sourceLabyMod() {
    return this.sourceLabyMod;
  }

  public ConfigProperty<Boolean> sourceReddit() {
    return this.sourceReddit;
  }

  public ConfigProperty<Boolean> sourceCosmetics() {
    return this.sourceCosmetics;
  }

  public boolean isSourceEnabled(NewsSource source) {
    return switch (source) {
      case MINECRAFT -> this.sourceMinecraft.get();
      case LABYMOD -> this.sourceLabyMod.get();
      case REDDIT -> this.sourceReddit.get();
      case COSMETICS -> this.sourceCosmetics.get();
    };
  }

  public ConfigProperty<Boolean> showPopups() {
    return this.showPopups;
  }

  /** Whether new items from this source may show a pop-up (also needs "Show pop-ups"). */
  public boolean isPopupEnabled(NewsSource source) {
    if (!this.showPopups.get()) {
      return false;
    }
    return switch (source) {
      case MINECRAFT -> this.popupsMinecraft.get();
      case LABYMOD -> this.popupsLabyMod.get();
      case REDDIT -> this.popupsReddit.get();
      case COSMETICS -> this.popupsCosmetics.get();
    };
  }

  public ConfigProperty<Boolean> doNotDisturbInCombat() {
    return this.doNotDisturbInCombat;
  }

  public ConfigProperty<Boolean> sound() {
    return this.sound;
  }

  public ConfigProperty<Boolean> keywordsOnly() {
    return this.keywordsOnly;
  }

  public ConfigProperty<TagCollection> keywords() {
    return this.keywords;
  }

  /**
   * The keywords to filter on, lower-cased and trimmed. Empty when "keywords only" is off or the
   * list is empty, which means everything passes.
   */
  public List<String> activeKeywords() {
    if (!this.keywordsOnly.get()) {
      return List.of();
    }

    List<String> result = new ArrayList<>();
    for (Tag tag : this.keywords.get().getTags()) {
      String content = tag.getContent();
      if (content != null && !content.isBlank()) {
        result.add(content.strip().toLowerCase(Locale.ROOT));
      }
    }
    return result;
  }

  /** Case-insensitive: does the text contain one of the keywords (or is the list empty)? */
  public static boolean matchesKeywords(String text, List<String> keywords) {
    if (keywords.isEmpty()) {
      return true;
    }

    String lower = text.toLowerCase(Locale.ROOT);
    for (String keyword : keywords) {
      if (lower.contains(keyword)) {
        return true;
      }
    }
    return false;
  }

  public ConfigProperty<Integer> opacity() {
    return this.opacity;
  }

  public ConfigProperty<FeedLanguage> language() {
    return this.language;
  }

  public ConfigProperty<Key> toggleKey() {
    return this.toggleKey;
  }
}
