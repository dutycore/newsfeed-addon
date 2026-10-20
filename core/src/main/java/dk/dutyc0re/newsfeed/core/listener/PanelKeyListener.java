package dk.dutyc0re.newsfeed.core.listener;

import dk.dutyc0re.newsfeed.core.NewsFeedAddon;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.input.KeyEvent;
import net.labymod.api.event.client.input.KeyEvent.State;

/** Toggles the news panel with the configured key (N by default). */
public class PanelKeyListener {

  private final NewsFeedAddon addon;

  public PanelKeyListener(NewsFeedAddon addon) {
    this.addon = addon;
  }

  @Subscribe
  public void onKey(KeyEvent event) {
    if (event.state() != State.PRESS || !this.addon.configuration().enabled().get()) {
      return;
    }

    Key toggleKey = this.addon.configuration().toggleKey().get();
    if (toggleKey == null || toggleKey == Key.NONE || !toggleKey.equals(event.key())) {
      return;
    }

    // Don't react while typing in chat or browsing a menu.
    if (this.addon.labyAPI().minecraft().minecraftWindow().isScreenOpened()) {
      return;
    }

    this.addon.togglePanel();
  }
}
