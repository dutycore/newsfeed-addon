package dk.dutyc0re.newsfeed.core.listener;

import dk.dutyc0re.newsfeed.core.NewsFeedAddon;
import dk.dutyc0re.newsfeed.core.hud.NewsPanelHudWidget;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.input.MouseScrollEvent;

/**
 * Scrolls the panel's list with the mouse wheel. HUD widgets get no scroll callback, so this
 * listens globally. It only reacts while a screen (e.g. chat) is open, because in-game the wheel
 * switches hotbar slots, and only when the panel says the mouse is over its list.
 */
public class PanelScrollListener {

  private final NewsFeedAddon addon;

  public PanelScrollListener(NewsFeedAddon addon) {
    this.addon = addon;
  }

  @Subscribe
  public void onScroll(MouseScrollEvent event) {
    if (!this.addon.labyAPI().minecraft().minecraftWindow().isScreenOpened()) {
      return;
    }

    NewsPanelHudWidget panel = this.addon.visiblePanel();
    if (panel != null && panel.scroll(event.delta())) {
      // Don't also scroll the chat underneath.
      event.setCancelled(true);
    }
  }
}
