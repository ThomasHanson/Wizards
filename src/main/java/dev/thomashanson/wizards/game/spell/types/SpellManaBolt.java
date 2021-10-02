package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.mode.GameTeam;
import dev.thomashanson.wizards.game.overtime.types.DisasterManaStorm;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.util.BlockUtil;
import org.apache.commons.lang.Validate;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class SpellManaBolt extends Spell implements Spell.Deflectable {

    public void castSpell(final Player player, int level) {

        final Location missileLocation = player.getEyeLocation();
        final Location shotFrom = missileLocation.clone();

        final Vector direction = missileLocation.getDirection().normalize().multiply(0.3);

        final int maxRange = 20 + (10 * level);
        final int maxDings = maxRange * 3;

        final int damage = 4 + (level * 2);
        final int multiplier = getGame().isOvertime() && getGame().getDisaster() instanceof DisasterManaStorm ? 2 : 1;

        new BukkitRunnable() {

            private int numDings;
            private Location prevLocation = missileLocation;

            private void burst(boolean hitEntity) {

                for (Entity worldEntity : Objects.requireNonNull(missileLocation.getWorld()).getEntities()) {

                    if (
                            worldEntity.equals(player) || !(worldEntity instanceof LivingEntity) ||
                                    (worldEntity instanceof Player && getWizard((Player) worldEntity) == null)
                    )
                        continue;

                    LivingEntity entity = (LivingEntity) worldEntity;

                    Location entityLocation = entity.getLocation();
                            //entity instanceof Player ?
                            //getGame().getTargetLocation((Player) entity) :
                            //entity.getLocation();

                    // If they are less than 0.5 blocks away
                    if (entityLocation.clone().add(0, missileLocation.getY() - entityLocation.getY(), 0).distance(missileLocation) <= 0.7) {

                        // If it is in their body height
                        if (Math.abs((entityLocation.getY() + (entity.getEyeHeight() / 1.5)) - missileLocation.getY()) <= entity.getEyeHeight() / 2) {

                            getWizard(player).addAccuracy(true, false);

                            if (!(entity instanceof Player) || getWizard((Player) entity) != null) {

                                CustomDamageTick damageTick = new CustomDamageTick (
                                        damage,
                                        EntityDamageEvent.DamageCause.MAGIC,
                                        getSpell().getSpellName(),
                                        Instant.now(),
                                        player
                                );

                                damage(entity, damageTick);
                            }
                        }
                    }
                }

                playParticle(missileLocation, prevLocation);

                for (int i = 0; i < 120; i++) {

                    Vector vector = new Vector(
                            ThreadLocalRandom.current().nextFloat() - 0.5F,
                            ThreadLocalRandom.current().nextFloat() - 0.5F,
                            ThreadLocalRandom.current().nextFloat() - 0.5F
                    );

                    if (vector.length() >= 1) {
                        i--;
                        continue;
                    }

                    Location location = missileLocation.clone();
                    location.add(vector.multiply(2));

                    Validate.notNull(location.getWorld());

                    location.getWorld().spawnParticle (
                                    Particle.REDSTONE, location, 0, 0.001, 1, 0, 1,
                                    new Particle.DustOptions(Color.fromRGB(104, 171, 177), 1)
                            );
                }

                missileLocation.getWorld().playSound(missileLocation, Sound.ENTITY_BAT_TAKEOFF, 1.2F, 1F);
                cancel();

                if (!hitEntity)
                    getWizard(player).addAccuracy(false);
            }

            @Override
            public void run() {

                if (numDings >= maxDings || !player.isOnline() || getWizard(player) == null) {
                    burst(false);

                } else {

                    for (int i = 0; i < 2; i++) {

                        Player closestPlayer = null;
                        double distance = 0;

                        for (Player closest : Bukkit.getOnlinePlayers()) {

                            if (getWizard(closest) == null)
                                continue;

                            GameTeam.TeamRelation relation = getGame().getRelation(closest, player);

                            Location location = closest.getLocation();

                            if (closest != player) {

                                double distToClosest = location.distance(shotFrom);

                                // If the player is a valid target
                                if (distToClosest < maxRange + 10) {

                                    double distFromMissile = missileLocation.distance(location);

                                    // If the player is closer to the magic missile than the other dist
                                    if (closestPlayer == null || distFromMissile < distance) {

                                        double distToLocation = missileLocation.clone().add(direction).distance(location);

                                        if (distToLocation < distFromMissile) {
                                            closestPlayer = closest;
                                            distance = distFromMissile;
                                        }
                                    }
                                }
                            }
                        }

                        if (closestPlayer != null) {

                            Vector newDirection = closestPlayer.getLocation()
                                    .add(0, 1, 0).toVector()
                                    .subtract(missileLocation.toVector());

                            direction.add(newDirection.normalize().multiply(0.01)).normalize().multiply(0.3);
                            direction.multiply(multiplier);
                        }

                        missileLocation.add(direction);

                        /*
                        NPC npcAtLoc = getGame().npcFromLocation(missileLocation);

                        if (npcAtLoc != null) {

                            Location npcLocation = npcAtLoc.getLocation();

                            // If they are less than 0.5 blocks away
                            if (npcLocation.clone().add(0, missileLocation.getY() - npcLocation.getY(), 0).distance(missileLocation) <= 0.7) {

                                // If it is in their body height
                                if (Math.abs((npcLocation.getY() + (entity.getEyeHeight() / 1.5)) - missileLocation.getY()) <= entity.getEyeHeight() / 2) {

                                    burst(true);
                                    return;
                                }
                            }
                        }
                         */

                        for (Entity worldEntity : Objects.requireNonNull(missileLocation.getWorld()).getEntities()) {

                            if (
                                    worldEntity == player ||
                                    !(worldEntity instanceof LivingEntity)
                                    || (worldEntity instanceof Player && getWizard(player) == null)
                            )
                                continue;

                            LivingEntity entity = (LivingEntity) worldEntity;
                            Location entityLocation = entity.getLocation();

                            // If they are less than 0.5 blocks away
                            if (entityLocation.clone().add(0, missileLocation.getY() - entityLocation.getY(), 0).distance(missileLocation) <= 0.7) {

                                // If it is in their body height
                                if (Math.abs((entityLocation.getY() + (entity.getEyeHeight() / 1.5)) - missileLocation.getY()) <= entity.getEyeHeight() / 2) {

                                    burst(true);

                                    if (!isShield(entity))
                                        getWizard(player).addAccuracy(true, false);
                                    else
                                        deflectSpell(player, level, direction.clone().multiply(-1));

                                    return;
                                }
                            }
                        }

                        if (missileLocation.getBlock().getType().isSolid()) {
                            burst(false);
                            return;
                        }

                        playParticle(missileLocation, prevLocation);
                        prevLocation = missileLocation.clone();

                        numDings++;
                    }

                    missileLocation.getWorld().playSound(missileLocation, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 0F);
                }
            }

        }.runTaskTimer(getGame().getPlugin(), 0L, 0L);
    }

    @Override
    public void deflectSpell(Player player, int level, Vector direction) {

    }

    private void playParticle(Location start, Location end) {

        final List<Location> locations = BlockUtil.getLinesDistancedPoints(start, end, 0.1);

        new BukkitRunnable() {

            int timesRan;

            @Override
            public void run() {

                for (Location location : locations) {

                    Objects.requireNonNull(location.getWorld())
                            .spawnParticle (
                                    Particle.REDSTONE, location, 0, 0.001, 1, 0, 1,
                                    new Particle.DustOptions(Color.fromRGB(104, 171, 177), 1)
                            );
                }

                if (timesRan++ > 1)
                    cancel();
            }

        }.runTaskTimer(getGame().getPlugin(), 0L, 0L);
    }
}