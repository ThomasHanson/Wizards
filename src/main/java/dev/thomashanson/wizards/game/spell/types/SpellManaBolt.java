package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;
import dev.thomashanson.wizards.util.effects.ParticleConfig;
import dev.thomashanson.wizards.util.effects.ParticleUtil;

public class SpellManaBolt extends Spell {

    public SpellManaBolt(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        StatContext context = StatContext.of(level);

        double damage = getStat("damage", level);
        double range = getStat("range", level);
        double homingStrength = getStat("homing-strength", level);
        double speedBps = getStat("speed-bps", level);

        new ManaBoltProjectile(
                player, level, damage, range, homingStrength, speedBps
        ).runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    private class ManaBoltProjectile extends BukkitRunnable {

        private static final ParticleConfig MANA_BOLT_TRAIL_CONFIG = new ParticleConfig(
                Particle.REDSTONE, 1, 0, 0, 0, 0,
                new Particle.DustOptions(Color.AQUA, 0.8F)
        );

        private final Player caster;
        private final int level;
        private final double damage;
        private final double maxRangeSquared;
        private final double homingStrength;
        private final double speedPerTick;

        private final Location location;
        private final Vector direction;
        private final World world;
        private Location lastLocation;
        private double distanceTraveled = 0;

        ManaBoltProjectile(Player caster, int level, double damage, double maxRange, double homing, double speedBps) {
            this.caster = caster;
            this.level = level;
            this.damage = damage;
            this.maxRangeSquared = maxRange * maxRange;
            this.homingStrength = homing;
            this.speedPerTick = speedBps / 20.0; 

            this.location = caster.getEyeLocation();
            this.lastLocation = location.clone();
            this.direction = caster.getLocation().getDirection().normalize().multiply(speedPerTick);
            this.world = caster.getWorld();
        }

        @Override
        public void run() {
            if (!caster.isOnline()) {
                cancel();
                return;
            }

            LivingEntity target = findClosestTarget();
            if (target != null) {
                Vector toTarget = target.getLocation().add(0, 1, 0).toVector()
                                       .subtract(location.toVector());
                direction.add(toTarget.normalize().multiply(homingStrength));
                direction.normalize().multiply(speedPerTick);
            }

            lastLocation = location.clone();
            location.add(direction);
            distanceTraveled += speedPerTick;

            ParticleUtil.drawParticleLine(lastLocation, location, MANA_BOLT_TRAIL_CONFIG);
            new BukkitRunnable() {
                final Location p1 = lastLocation.clone();
                final Location p2 = location.clone();
                @Override
                public void run() {
                    ParticleUtil.drawParticleLine(p1, p2, MANA_BOLT_TRAIL_CONFIG);
                }
            }.runTaskLater(plugin, 1L);

            if (distanceTraveled * distanceTraveled >= maxRangeSquared) {
                explode(location, null);
                cancel();
                return;
            }

            if (location.getBlock().getType().isSolid()) {
                explode(location, null);
                cancel();
                return;
            }

            for (LivingEntity entity : world.getNearbyLivingEntities(location, 1.0, this::isTargetable)) {
                if (entity.equals(caster)) continue;
                explode(entity.getLocation(), entity);
                cancel();
                return;
            }
            
            // --- SOUND UPDATED ---
            // This is the original Mineplex sound: (Sound.ORB_PICKUP, 0.7F, 0)
            world.playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 0.5F);
        }

        private LivingEntity findClosestTarget() {
            LivingEntity closest = null;
            double closestDistSq = Double.MAX_VALUE;

            for (LivingEntity entity : world.getNearbyLivingEntities(location, 15, this::isTargetable)) {
                if (entity.equals(caster)) continue;

                double distSq = entity.getLocation().distanceSquared(location);
                if (distSq < closestDistSq) {
                    Vector toTarget = entity.getLocation().toVector().subtract(location.toVector());
                    if (toTarget.normalize().dot(direction.clone().normalize()) > 0.5) { 
                        closest = entity;
                        closestDistSq = distSq;
                    }
                }
            }
            return closest;
        }

        private void explode(Location hitLocation, LivingEntity hitEntity) {
            // This is the original Mineplex sound: (Sound.BAT_TAKEOFF, 1.2F, 1)
            world.playSound(hitLocation, Sound.ENTITY_BAT_TAKEOFF, 1.2F, 1.0F);

            world.spawnParticle(
                    Particle.REDSTONE,
                    hitLocation,
                    100, 1.0, 1.0, 1.0, 0.5,
                    new Particle.DustOptions(Color.AQUA, 1.2F)
            );

            if (hitEntity != null) {
                damage(hitEntity, new CustomDamageTick(
                        this.damage,
                        EntityDamageEvent.DamageCause.MAGIC,
                        "Mana Bolt",
                        Instant.now(),
                        caster,
                        null
                ));
            }
        }

        private boolean isTargetable(LivingEntity entity) {
            return !entity.equals(caster) && (entity instanceof Player);
        }
    }
}