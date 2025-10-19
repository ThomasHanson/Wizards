package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.spell.Spell;

public class SpellSplash extends Spell {

    public SpellSplash(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        Location playerLocation = player.getLocation();
        World world = player.getWorld();

        applyCasterEffects(player, level);
        playSounds(playerLocation, world);
        spawnParticles(playerLocation, world, level);
        applyEntityEffects(player, playerLocation, level);

        return true;
    }

    private void applyCasterEffects(Player player, int level) {
        int fireResistanceTicks = (int) getStat("fire-resistance-ticks", level, 100.0);
        player.setFireTicks(0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, fireResistanceTicks, 0, false, false, true));
    }

    private void playSounds(Location location, World world) {
        world.playSound(location, Sound.ENTITY_GENERIC_SPLASH, 0.7F, 1.3F);
        world.playSound(location, Sound.ITEM_BUCKET_FILL, 0.56F, 1.43F);
    }

    private void spawnParticles(Location location, World world, int level) {
        Location origin = location.clone().add(0, 0.5, 0);

        world.spawnParticle(Particle.WATER_DROP, origin, (int) getStat("p-drop-count", level, 250), getStat("p-drop-spread", level, 2.0), getStat("p-drop-spread", level, 2.0), getStat("p-drop-spread", level, 2.0), getStat("p-drop-speed", level, 0.01));
        world.spawnParticle(Particle.FALLING_WATER, origin, (int) getStat("p-fall-count", level, 70), getStat("p-fall-spread", level, 2.2), getStat("p-fall-spread", level, 2.2), getStat("p-fall-spread", level, 2.2), getStat("p-fall-speed", level, 0.05));
        world.spawnParticle(Particle.WATER_SPLASH, origin, (int) getStat("p-splash-count", level, 120), getStat("p-splash-spread-xz", level, 1.5), getStat("p-splash-spread-y", level, 0.8), getStat("p-splash-spread-xz", level, 1.5), getStat("p-splash-speed", level, 0.25));
    }

    private void applyEntityEffects(Player player, Location castLocation, int level) {
        double radius = getStat("radius", level, 5.0);

        for (LivingEntity target : player.getWorld().getNearbyLivingEntities(castLocation, radius)) {
            if (target.equals(player)) continue;

            double distance = castLocation.distance(target.getLocation());
            if (distance > radius) continue;

            double falloff = 1.0 - (distance / radius);

            double knockbackStrength = getStat("knockback-strength", level, 4.0) * falloff;
            Vector direction = target.getLocation().toVector().subtract(castLocation.toVector()).normalize();
            if (direction.lengthSquared() < 0.01) direction = new Vector(0, 1, 0);

            Vector velocity = direction.multiply(knockbackStrength).setY(getStat("knockback-y-lift", level));
            target.setVelocity(velocity);

            double damageAmount = getStat("damage", level) * falloff;
            if (damageAmount > 0.1) {
                damage(target, new CustomDamageTick(damageAmount, EntityDamageEvent.DamageCause.CUSTOM, getKey(), Instant.now(), player, null));
            }
        }
    }
}