package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.SpellType;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class SpellFlash extends Spell {

    private final Map<UUID, Instant> casted = new HashMap<>();

    @Override
    public void castSpell(Player player, int level) {

        int maxRange = (level * 10) + 20; //(int) getValue(player, "Range");
        double currentRange = 0;

        while (currentRange <= maxRange) {

            Location newTarget = player.getEyeLocation()
                    .add(new Vector(0, 0.2, 0))
                    .add(player.getLocation().getDirection().multiply(currentRange));

            if (newTarget.getBlock().getType().isSolid())
                break;

            currentRange += 0.2;

            Objects.requireNonNull(newTarget.getWorld()).spawnParticle(Particle.FIREWORKS_SPARK, newTarget, 1, 0, 0.5, 0);
        }

        currentRange = Math.max(0, currentRange - 0.4);

        Location location = player.getEyeLocation()
                .add(new Vector(0, 0.2, 0))
                .add(player.getLocation().getDirection().multiply(currentRange))
                .add(new Vector(0, 0.4, 0));

        if (currentRange <= 0)
            return;

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1F, 1.2F);
        player.getWorld().playEffect(player.getLocation(), Effect.ENDER_SIGNAL, 9);

        player.setFallDistance(0);

        player.eject();
        player.leaveVehicle();

        casted.put(player.getUniqueId(), Instant.now());
        player.teleport(location);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1F, 1.2F);
        player.getWorld().playEffect(player.getLocation(), Effect.ENDER_SIGNAL, 9);

        Bukkit.getScheduler().scheduleSyncDelayedTask(getGame().getPlugin(), () -> casted.remove(player.getUniqueId()), 20L * level);
    }

    @Override
    public void cleanup() {
        casted.clear();
    }

    /*
     * Disable fall damage for SL seconds
     */
    @EventHandler
    public void onFall(EntityDamageEvent event) {

        if (!casted.containsKey(event.getEntity().getUniqueId()))
            return;

        if (event.getCause() != EntityDamageEvent.DamageCause.FALL)
            return;

        Player player = (Player) event.getEntity();
        Wizard wizard = getWizard(player);

        if (wizard == null)
            return;

        int spellLevel = wizard.getLevel(SpellType.FLASH);
        Duration since = Duration.between(Instant.now(), casted.get(player.getUniqueId()));

        if (since.toSeconds() > spellLevel)
            return;

        event.setCancelled(true);
        casted.remove(player.getUniqueId());
    }
}