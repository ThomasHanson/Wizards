package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.spell.Spell;

public class SpellGust extends Spell {

    public SpellGust(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        double range = getStat("range", level);
        double baseStrength = getStat("strength", level);
        double coneAngle = getStat("cone-angle", level, 60.0);

        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();

        // --- 1. Audio ---
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8F, 1.8F);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1F, 0.7F);

        // --- 2. Visuals ---
        renderGustCone(eyeLoc, direction, range, coneAngle);

        // --- 3. Mechanics ---
        
        // --- KNOCKBACK LOGIC RESTORED ---
        // This is the pre-mixed vector from your original file.
        Vector baseVector = player.getLocation().getDirection().setY(0).normalize().multiply(1.5).setY(0.3);
        // --- END RESTORED LOGIC ---

        Set<LivingEntity> hitTargets = new HashSet<>();
        for (LivingEntity target : player.getWorld().getNearbyLivingEntities(eyeLoc, range)) {
            if (target.equals(player) || !(target instanceof Player) || getWizard((Player) target).isEmpty()) {
                continue;
            }

            // Check if the target is within the cone
            Vector toTarget = target.getEyeLocation().toVector().subtract(eyeLoc.toVector());
            double distance = toTarget.length();
            if (distance > range) continue;
            if (toTarget.normalize().dot(direction) < Math.cos(Math.toRadians(coneAngle / 2.0))) {
                continue; // Not in cone
            }
            
            hitTargets.add(target);
        }
        
        if (hitTargets.isEmpty()) {
            return false; // The spell "missed"
        }

        // Apply effects to all valid targets
        for (LivingEntity target : hitTargets) {
            double distance = player.getEyeLocation().distance(target.getEyeLocation());
            
            // --- KNOCKBACK LOGIC RESTORED ---
            // This is the scaling logic from your original file.
            double proximityFactor = Math.max(0.2, 1.0 - (distance / range));
            Vector finalVelocity = baseVector.clone().multiply(baseStrength * proximityFactor);
            // --- END RESTORED LOGIC ---
            
            target.setVelocity(finalVelocity);

            // Deal 0 damage just to register the "hit"
            damage(target, new CustomDamageTick(0, EntityDamageEvent.DamageCause.CUSTOM, getKey(), Instant.now(), player, null));
        }

        return true;
    }

    /**
     * Spawns a cone of particles to visualize the gust.
     */
    private void renderGustCone(Location start, Vector direction, double range, double angle) {
        double angleTan = Math.tan(Math.toRadians(angle / 2.0));

        // Iterate through the length of the cone
        for (double d = 0.5; d < range; d += 0.4) {
            // Calculate the radius of the "disk" of particles at this distance
            double radius = d * angleTan;
            Location center = start.clone().add(direction.clone().multiply(d));

            // Spawn a cloud of particles that expands with distance
            start.getWorld().spawnParticle(Particle.CLOUD, center, 10, radius, radius, radius, 0.01);
            start.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center, 3, radius * 0.5, radius * 0.5, radius * 0.5, 0);
        }
    }
}