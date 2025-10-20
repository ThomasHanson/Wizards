package dev.thomashanson.wizards.game.overtime.types;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.overtime.Disaster;
import dev.thomashanson.wizards.util.ExplosionUtil;

public class DisasterLightning extends Disaster {

    // --- Configuration for Hail Timing ---
    private static final long INITIAL_STRIKE_INTERVAL_MS = 9000;
    private static final long MINIMUM_STRIKE_INTERVAL_MS = 750;
    private static final long STRIKE_INTERVAL_REDUCTION_PER_TICK_MS = 2;

    public DisasterLightning(Wizards game) {
        super(game,
            "wizards.disaster.lightning.name",
            Collections.emptySet(),
            Arrays.asList(
                "wizards.disaster.lightning.announce.1",
                "wizards.disaster.lightning.announce.2",
                "wizards.disaster.lightning.announce.3",
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
    protected void strikeAt(Location location) {
        // --- Pre-Strike Warning Effects (copied from SpellLightningStrike) ---
        location.getWorld().spawnParticle(Particle.VILLAGER_ANGRY, location.clone().add(0, 1.3, 0), 7, 0.5, 0.3, 0.5, 0);
        location.getWorld().playSound(location, Sound.ENTITY_CAT_HISS, 1F, 1F);

        // Schedule the actual lightning strike with a delay to match the spell
        Bukkit.getScheduler().runTaskLater(getGame().getPlugin(), () -> {

            // Ensure strike is within current (possibly further shrunk) bounds
            if (location.getX() >= getGame().getCurrentMinX() && location.getX() < getGame().getCurrentMaxX() &&
                location.getZ() >= getGame().getCurrentMinZ() && location.getZ() < getGame().getCurrentMaxZ()) {

                // --- The Strike ---
                location.getWorld().strikeLightning(location);

                // --- Post-Strike Environmental Effects (copied from SpellLightningStrike) ---
                Block impactBlock = location.getWorld().getHighestBlockAt(location).getRelative(BlockFace.DOWN);

                List<Block> toExplode = new ArrayList<>();
                List<Block> toFire = new ArrayList<>();

                // Gather blocks in a 3x3x3 radius for the effects
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {
                            if (x == 0 || (Math.abs(x) != Math.abs(z) || ThreadLocalRandom.current().nextInt(3) == 0)) {
                                Block relativeBlock = impactBlock.getRelative(x, y, z);
                                Material material = relativeBlock.getType();

                                if ((y == 0 || (x == 0 && z == 0)) && material.isSolid() && material != Material.BEDROCK) {
                                    if (y == 0 || ThreadLocalRandom.current().nextBoolean()) {
                                        toExplode.add(relativeBlock);
                                        toFire.add(relativeBlock);
                                    }
                                } else if (relativeBlock.getType() == Material.AIR) {
                                    toFire.add(relativeBlock);
                                }
                            }
                        }
                    }
                }

                // Use the custom explosion utility for visuals
                ExplosionUtil.ExplosionConfig config = new ExplosionUtil.ExplosionConfig(
                        false,  // regenerateBlocks
                        100L,   // regenerationDelayTicks
                        60,     // debrisLifespanTicks
                        0.25,   // debrisChance
                        0.5,    // velocityStrength
                        0.4,    // velocityYAward
                        0.5     // itemVelocityModifier
                    );
                    ExplosionUtil.createExplosion(getGame().getPlugin(), impactBlock.getLocation(), toExplode, config, false);

                // Set blocks on fire randomly
                for (Block block : toFire) {
                    if (ThreadLocalRandom.current().nextBoolean()) {
                        block.setType(Material.FIRE);
                    }
                }
            }
        }, 20L); // 1.25 second delay (25 ticks), matching the spell
    }
}