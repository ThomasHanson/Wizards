package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.spell.Spell;

public class SpellFlash extends Spell {

    private static final Map<UUID, Instant> FALL_IMMUNITY_EXPIRY = new ConcurrentHashMap<>();

    public SpellFlash(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        double maxRange = getStat("range", level);

        Location eyeLocation = player.getEyeLocation();
        RayTraceResult rayTrace = player.getWorld().rayTraceBlocks(eyeLocation, eyeLocation.getDirection(), maxRange, FluidCollisionMode.NEVER, true);

        Location teleportLocation;
        if (rayTrace != null && rayTrace.getHitBlock() != null) {
            teleportLocation = rayTrace.getHitPosition().toLocation(player.getWorld()).subtract(eyeLocation.getDirection().multiply(0.5));
        } else {
            teleportLocation = eyeLocation.clone().add(eyeLocation.getDirection().multiply(maxRange));
        }

        teleportLocation.setPitch(eyeLocation.getPitch());
        teleportLocation.setYaw(eyeLocation.getYaw());

        player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation(), 20, 0.5, 0.5, 0.5, 0.1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1F, 1.2F);

        player.setFallDistance(0);
        player.eject();
        player.leaveVehicle();
        player.teleport(teleportLocation);

        player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation(), 20, 0.5, 0.5, 0.5, 0.1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1F, 1.2F);

        // CORRECTED KEY
        long immunitySeconds = (long) getStat("fall-immunity", level);
        FALL_IMMUNITY_EXPIRY.put(player.getUniqueId(), Instant.now().plusSeconds(immunitySeconds));

        return true;
    }

    @Override
    public void cleanup() {
        FALL_IMMUNITY_EXPIRY.clear();
    }

    @EventHandler
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        
        Instant expiry = FALL_IMMUNITY_EXPIRY.get(event.getEntity().getUniqueId());
        if (expiry == null) return;

        if (Instant.now().isBefore(expiry)) {
            event.setCancelled(true);
        }
        
        // Always remove the entry after the first fall damage event (or successful prevention)
        FALL_IMMUNITY_EXPIRY.remove(event.getEntity().getUniqueId());
    }
}
