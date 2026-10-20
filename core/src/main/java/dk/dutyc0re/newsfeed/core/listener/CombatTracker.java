package dk.dutyc0re.newsfeed.core.listener;

import dk.dutyc0re.newsfeed.core.NewsFeedAddon;
import java.util.EnumSet;
import java.util.Set;
import net.labymod.api.client.entity.Entity;
import net.labymod.api.client.entity.LivingEntity;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.entity.EntityDamageEvent;
import net.labymod.api.event.client.entity.EntityDamageEvent.Cause;
import net.labymod.api.event.client.entity.player.ClientPlayerInteractEvent;
import net.labymod.api.event.client.entity.player.ClientPlayerInteractEvent.InteractionType;

/**
 * Decides whether the player is "in combat" for "Do not disturb in combat": the player hit a
 * living entity or was hurt by an attack in the last {@link #COMBAT_MILLIS} ms.
 */
public class CombatTracker {

  private static final long COMBAT_MILLIS = 10_000L;

  /**
   * Damage that comes from fighting. {@link Cause#UNKNOWN} is included because servers before
   * 1.19.4 report almost all damage that way, so we can't tell a hit from a fall there.
   */
  private static final Set<Cause> COMBAT_CAUSES = EnumSet.of(
      Cause.UNKNOWN, Cause.MELEE, Cause.ARROW, Cause.TRIDENT, Cause.THROWN, Cause.PROJECTILE,
      Cause.EXPLOSION, Cause.THORNS
  );

  private final NewsFeedAddon addon;
  private volatile long lastCombatMillis;

  public CombatTracker(NewsFeedAddon addon) {
    this.addon = addon;
  }

  public boolean isInCombat() {
    return System.currentTimeMillis() - this.lastCombatMillis < COMBAT_MILLIS;
  }

  @Subscribe
  public void onDamage(EntityDamageEvent event) {
    ClientPlayer player = this.addon.labyAPI().minecraft().getClientPlayer();
    if (player == null) {
      return;
    }

    Entity attacker = event.attacker();
    boolean playerHurt = player.equals(event.entity())
        && (attacker != null || COMBAT_CAUSES.contains(event.cause()));
    boolean playerHit = player.equals(attacker);
    if (playerHurt || playerHit) {
      this.markCombat();
    }
  }

  @Subscribe
  public void onInteract(ClientPlayerInteractEvent event) {
    // Attacks report no attacker on old servers, so also count swinging at a living target.
    if (event.type() != InteractionType.ATTACK) {
      return;
    }

    Entity target = this.addon.labyAPI().minecraft().getTargetEntity();
    if (target instanceof LivingEntity) {
      this.markCombat();
    }
  }

  private void markCombat() {
    this.lastCombatMillis = System.currentTimeMillis();
  }
}
