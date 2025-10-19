package dev.thomashanson.wizards.game.overtime.types;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.overtime.Disaster;

public class DisasterHail extends Disaster {

    // --- Configuration for Hail Timing ---
    private static final long INITIAL_STRIKE_INTERVAL_MS = 8000;
    private static final long MINIMUM_STRIKE_INTERVAL_MS = 750;
    private static final long STRIKE_INTERVAL_REDUCTION_PER_TICK_MS = 2;

    // --- Configuration for Hail Effects ---
    private static final double IMPACT_RADIUS = 5.0;
    private static final double ICE_CONVERSION_RADIUS = 4.0;
    private static final float EXPLOSION_POWER = 1.5F;
    private static final double PLAYER_DAMAGE_ON_ICE = 4.0;
    private static final int HAILSTONES_PER_STRIKE = 3;

    public DisasterHail(Wizards game) {
        super(game,
            "wizards.disaster.hail.name", // Key for name
            Collections.emptySet(),
            Arrays.asList( // List of keys for announcements
                "wizards.disaster.hail.announce.1",
                "wizards.disaster.hail.announce.2",
                "wizards.disaster.hail.announce.3",
                "wizards.disaster.final_announce"
            )
        );
    }

    // --- Abstract Method Implementations ---
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
        if (strikeLocation == null || strikeLocation.getWorld() == null) {
            return;
        }

        for (int i = 0; i < HAILSTONES_PER_STRIKE; i++) {
            // Slightly randomize the exact impact spot for each hailstone
            Location individualHailImpactLoc = strikeLocation.clone().add(
                (ThreadLocalRandom.current().nextDouble() - 0.5) * IMPACT_RADIUS * 0.8,
                0,
                (ThreadLocalRandom.current().nextDouble() - 0.5) * IMPACT_RADIUS * 0.8
            );
            // Ensure it's within game bounds
            individualHailImpactLoc.setX(Math.max(getGame().getCurrentMinX() + 0.5, Math.min(individualHailImpactLoc.getX(), getGame().getCurrentMaxX() - 0.5)));
            individualHailImpactLoc.setZ(Math.max(getGame().getCurrentMinZ() + 0.5, Math.min(individualHailImpactLoc.getZ(), getGame().getCurrentMaxZ() - 0.5)));

            spawnHailstone(individualHailImpactLoc);
        }
    }

    private void spawnHailstone(Location impactLocation) {
        World world = impactLocation.getWorld();
        if (world == null) return;

        Location spawnHeightLocation = impactLocation.clone().add(0, 15 + ThreadLocalRandom.current().nextInt(10), 0);

        // Use PACKED_ICE to prevent melting
        FallingBlock hailstone = world.spawnFallingBlock(spawnHeightLocation, Material.PACKED_ICE.createBlockData());
        hailstone.setDropItem(false);
        hailstone.setHurtEntities(true);

        world.playSound(spawnHeightLocation, Sound.WEATHER_RAIN, 1.0F, 0.5F);

        new BukkitRunnable() {
            int ticksLived = 0;

            @Override
            public void run() {
                // Max 5 seconds fall time, landed, or invalid
                if (!hailstone.isValid() || hailstone.isOnGround() || ticksLived > 100) {
                    this.cancel();
                    handleHailImpact(hailstone.getLocation());
                    if (hailstone.isValid()) hailstone.remove();
                    return;
                }
                // Visual trail for the hailstone
                world.spawnParticle(Particle.SNOWFLAKE, hailstone.getLocation(), 5, 0.2, 0.2, 0.2, 0);
                ticksLived++;
            }
        }.runTaskTimer(getGame().getPlugin(), 0L, 1L);
    }

    private void handleHailImpact(Location impactLocation) {
        World world = impactLocation.getWorld();
        if (world == null) return;

        // Ensure impact is within current game bounds before creating effects
        if (!(impactLocation.getX() >= getGame().getCurrentMinX() && impactLocation.getX() < getGame().getCurrentMaxX() &&
            impactLocation.getZ() >= getGame().getCurrentMinZ() && impactLocation.getZ() < getGame().getCurrentMaxZ())) {
            return; // Impact out of bounds
        }

        // --- Visuals and Sound for Impact ---
        world.playSound(impactLocation, Sound.BLOCK_GLASS_BREAK, 2F, 0.8F);
        world.playSound(impactLocation, Sound.ENTITY_GENERIC_EXPLODE, 1F, 1.2F);
        // Use PACKED_ICE for the particle effect to match
        world.spawnParticle(Particle.ITEM_CRACK, impactLocation, 50, 0.5, 0.5, 0.5, 0.1, new ItemStack(Material.PACKED_ICE));
        world.spawnParticle(Particle.EXPLOSION_NORMAL, impactLocation, 20, 1, 1, 1, 0.05);

        // --- Controlled Block Destruction ---
        for (int dx = -(int) EXPLOSION_POWER; dx <= (int) EXPLOSION_POWER; dx++) {
            for (int dy = -(int) EXPLOSION_POWER; dy <= (int) EXPLOSION_POWER; dy++) {
                for (int dz = -(int) EXPLOSION_POWER; dz <= (int) EXPLOSION_POWER; dz++) {
                    if (dx * dx + dy * dy + dz * dz > EXPLOSION_POWER * EXPLOSION_POWER) continue;

                    Block block = impactLocation.clone().add(dx, dy, dz).getBlock();
                    Material type = block.getType();

                    // **FIX**: Do not break liquids or unbreakable blocks.
                    if (type.isAir() || type == Material.BEDROCK || block.isLiquid()) {
                        continue;
                    }

                    if (type.getHardness() < 2.0f) {
                        if (ThreadLocalRandom.current().nextFloat() < 0.6) { // 60% chance
                            // Play effect but set to AIR cleanly instead of breakNaturally()
                            world.spawnParticle(Particle.BLOCK_DUST, block.getLocation().add(0.5, 0.5, 0.5), 10, type.createBlockData());
                            block.setType(Material.AIR);
                        }
                    }
                }
            }
        }

        // --- Turn Nearby Blocks to Packed Ice ---
        for (int x = -(int) ICE_CONVERSION_RADIUS; x <= (int) ICE_CONVERSION_RADIUS; x++) {
            for (int y = -(int) ICE_CONVERSION_RADIUS; y <= (int) ICE_CONVERSION_RADIUS; y++) {
                for (int z = -(int) ICE_CONVERSION_RADIUS; z <= (int) ICE_CONVERSION_RADIUS; z++) {
                    if (x * x + y * y + z * z > ICE_CONVERSION_RADIUS * ICE_CONVERSION_RADIUS) continue;

                    Block currentBlock = impactLocation.clone().add(x, y, z).getBlock();

                    // Check if the block is within game bounds
                    if (!(currentBlock.getX() >= getGame().getCurrentMinX() && currentBlock.getX() < getGame().getCurrentMaxX() &&
                        currentBlock.getZ() >= getGame().getCurrentMinZ() && currentBlock.getZ() < getGame().getCurrentMaxZ() &&
                        currentBlock.getY() >= getGame().getInitialMapMinY() && currentBlock.getY() <= getGame().getInitialMapMaxY())) {
                        continue;
                    }

                    // **FIX**: Only convert solid, non-bedrock blocks to PACKED_ICE to prevent melting and water creation.
                    if (currentBlock.getType().isSolid() && currentBlock.getType() != Material.BEDROCK && !currentBlock.isLiquid()) {
                        currentBlock.setType(Material.PACKED_ICE);
                    }
                }
            }
        }

        // --- Damage Players on Ice/Packed Ice in the Area ---
        world.getNearbyEntities(impactLocation, ICE_CONVERSION_RADIUS, ICE_CONVERSION_RADIUS, ICE_CONVERSION_RADIUS).forEach(entity -> {
            if (entity instanceof Player player && getGame().getPlayers(true).contains(entity)) {
                // Ensure player is within current game bounds
                Location playerLoc = player.getLocation();
                if (!(playerLoc.getX() >= getGame().getCurrentMinX() && playerLoc.getX() < getGame().getCurrentMaxX() &&
                    playerLoc.getZ() >= getGame().getCurrentMinZ() && playerLoc.getZ() < getGame().getCurrentMaxZ())) {
                    return;
                }

                Block blockUnder = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
                if (blockUnder.getType() == Material.ICE || blockUnder.getType() == Material.PACKED_ICE) {
                    // Your damage logic here...
                    // getGame().getPlugin().getDamageManager().damage(player, ...);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1F, 1F);
                    player.spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.01);
                }
            }
        });
    }
}