package dev.thomashanson.wizards.game.overtime.types;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.Location; // Import your custom utility
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.overtime.Disaster;
import dev.thomashanson.wizards.game.spell.SpellType;
import dev.thomashanson.wizards.util.ExplosionUtil;

public class DisasterMeteors extends Disaster {

    // --- Configuration for Hail Timing ---
    private static final long INITIAL_STRIKE_INTERVAL_MS = 9000;
    private static final long MINIMUM_STRIKE_INTERVAL_MS = 750;
    private static final long STRIKE_INTERVAL_REDUCTION_PER_TICK_MS = 2;

    // --- General Configuration ---
    private static final double IMPACT_AREA_RADIUS = 10.0; // How spread out the meteors are from the center point
    private static final int SPAWN_HEIGHT = 40;         // How high above the target the meteor appears

    // --- Progressive Scaling Configuration ---
    private static final float BASE_METEOR_SIZE = 1.5F;
    private static final float MAX_METEOR_SIZE = 8.0F;
    private static final float SIZE_INCREMENT = 0.04F;

    private static final float BASE_METEOR_SPEED = 1.2F; // This is a multiplier for the fireball's direction vector
    private static final float SPEED_INCREMENT = 0.005F;

    // --- Instance Variables for Scaling ---
    private float currentMeteorSize = BASE_METEOR_SIZE;
    private float currentMeteorSpeed = BASE_METEOR_SPEED;

    public DisasterMeteors(Wizards game) {
        super(game,
            "wizards.disaster.meteors.name",
            Stream.of(
                SpellType.FIREBALL,
                SpellType.NAPALM,
                SpellType.SPECTRAL_ARROW
            ).collect(Collectors.toSet()),
            Arrays.asList(
                "wizards.disaster.meteors.announce.1",
                "wizards.disaster.meteors.announce.2",
                "wizards.disaster.meteors.announce.3",
                "wizards.disaster.final_announce"
            )
        );
    }

    @Override
    protected long getInitialStrikeIntervalMs() {
        return INITIAL_STRIKE_INTERVAL_MS;
    }

    @Override
    protected long getMinimumStrikeIntervalMs() {
        return MINIMUM_STRIKE_INTERVAL_MS;
    }

    @Override
    protected long getStrikeIntervalReductionPerTickMs() {
        return STRIKE_INTERVAL_REDUCTION_PER_TICK_MS;
    }

    @Override
    protected void strikeAt(Location strikeLocation) {
        // 1. Increment the size and speed for the next meteor, matching the reference code's behavior.
        if (this.currentMeteorSize < MAX_METEOR_SIZE) {
            this.currentMeteorSize += SIZE_INCREMENT;
        }
        this.currentMeteorSpeed += SPEED_INCREMENT;

        // 2. Find a random point on the ground to be the target for this single meteor.
        Location impactTarget = strikeLocation.clone().add(
            (ThreadLocalRandom.current().nextDouble() - 0.5) * IMPACT_AREA_RADIUS * 2,
            0,
            (ThreadLocalRandom.current().nextDouble() - 0.5) * IMPACT_AREA_RADIUS * 2
        );

        // Ensure it's within game bounds
        impactTarget.setX(Math.max(getGame().getCurrentMinX() + 1, Math.min(impactTarget.getX(), getGame().getCurrentMaxX() - 1)));
        impactTarget.setZ(Math.max(getGame().getCurrentMinZ() + 1, Math.min(impactTarget.getZ(), getGame().getCurrentMaxZ() - 1)));

        // 3. Summon the single, scaled meteor.
        spawnMeteor(impactTarget, this.currentMeteorSize, this.currentMeteorSpeed);
    }

    private void spawnMeteor(Location impactLocation, float size, float speed) {
        World world = impactLocation.getWorld();

        Location spawnLocation = impactLocation.clone().add(
            (ThreadLocalRandom.current().nextDouble() - 0.5) * 12,
            SPAWN_HEIGHT,
            (ThreadLocalRandom.current().nextDouble() - 0.5) * 12
        );

        Vector direction = impactLocation.toVector().subtract(spawnLocation.toVector()).normalize();
        Fireball meteor = (Fireball) world.spawnEntity(spawnLocation, EntityType.FIREBALL);

        meteor.setShooter(null);
        meteor.setDirection(direction.multiply(speed)); // Use the scaled speed
        meteor.setYield(0);
        meteor.setIsIncendiary(false);

        world.playSound(spawnLocation, Sound.ENTITY_GHAST_SHOOT, SoundCategory.HOSTILE, 1.5F, 0.5F);

        new BukkitRunnable() {
            int ticksLived = 0;
            final int maxTicks = 100;

            @Override
            public void run() {
                if (!meteor.isValid() || meteor.isDead() || ticksLived++ > maxTicks) {
                    this.cancel();
                    // On impact, remove the fireball entity and trigger our custom explosion.
                    handleMeteorImpact(meteor.getLocation(), size);
                    meteor.remove();
                    return;
                }

                world.spawnParticle(Particle.FLAME, meteor.getLocation(), 15, 0.2, 0.2, 0.2, 0.05);
                world.spawnParticle(Particle.SMOKE_LARGE, meteor.getLocation(), 5, 0.3, 0.3, 0.3, 0.01);

                if (ticksLived % 7 == 0) {
                    world.playSound(meteor.getLocation(), Sound.BLOCK_FIRE_AMBIENT, SoundCategory.HOSTILE, 2F, 0.8F);
                }
            }
        }.runTaskTimer(getGame().getPlugin(), 0L, 1L);
    }

    private void handleMeteorImpact(Location impactLocation, float explosionSize) {
        // Ensure impact is within current game bounds
        if (!(impactLocation.getX() >= getGame().getCurrentMinX() && impactLocation.getX() < getGame().getCurrentMaxX() &&
              impactLocation.getZ() >= getGame().getCurrentMinZ() && impactLocation.getZ() < getGame().getCurrentMaxZ())) {
            return;
        }

        // Trigger your custom explosion utility instead of the vanilla one.
        // The sound and huge explosion particle are handled by the utility.
        ExplosionUtil.createExplosion(getGame().getPlugin(), impactLocation, explosionSize, false, true);
    }
}