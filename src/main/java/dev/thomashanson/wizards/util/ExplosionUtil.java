package dev.thomashanson.wizards.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * A utility class to create customizable, non-griefing block explosions.
 * This class handles the visual effect of blocks flying outwards from a central point
 * without using the standard Minecraft explosion, giving full control over the aesthetics
 * and performance.
 */
public final class ExplosionUtil {

    // Record to hold explosion configuration, preventing use of global static fields.
    public record ExplosionConfig(
        boolean regenerateBlocks,
        long regenerationDelayTicks,
        int debrisLifespanTicks,
        double debrisChance,
        double velocityStrength,
        double velocityYAward,
        double itemVelocityModifier
    ) {}

    private ExplosionUtil() {
        // Private constructor to prevent instantiation of this utility class.
    }

    /**
     * Creates a custom visual explosion affecting a spherical area of blocks.
     *
     * @param plugin   Your main plugin instance, required for scheduling tasks.
     * @param center   The center location of the explosion.
     * @param radius   The radius of the explosion sphere.
     * @param config   The configuration for the explosion's behavior.
     * @param playSound If true, plays an explosion sound at the center.
     */
    public static void createExplosion(JavaPlugin plugin, Location center, float radius, ExplosionConfig config, boolean playSound) {
        if (plugin == null || center == null || center.getWorld() == null) return;

        Map<Block, BlockData> affectedBlocks = getSphericalBlocks(center, radius);
        createExplosion(plugin, center, affectedBlocks, config, playSound);
    }

    /**
     * Creates a custom visual explosion affecting a specific collection of blocks.
     *
     * @param plugin   Your main plugin instance, required for scheduling tasks.
     * @param center   The center location (for effects and velocity calculation).
     * @param blocks   A collection of blocks to include in the explosion.
     * @param config   The configuration for the explosion's behavior.
     * @param playSound If true, plays an explosion sound at the center.
     */
    public static void createExplosion(JavaPlugin plugin, Location center, Collection<Block> blocks, ExplosionConfig config, boolean playSound) {
        if (plugin == null || center == null || center.getWorld() == null || blocks == null || blocks.isEmpty()) return;

        final Map<Block, BlockData> blockDataMap = new HashMap<>();
        for (Block block : blocks) {
            if (!block.getType().isAir() && !block.isLiquid()) {
                blockDataMap.put(block, block.getBlockData());
            }
        }
        createExplosion(plugin, center, blockDataMap, config, playSound);
    }

    private static void createExplosion(JavaPlugin plugin, Location center, Map<Block, BlockData> blocks, ExplosionConfig config, boolean playSound) {
        if (playSound) {
            center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0F, 0.8F);
        }
        center.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, center, 1);

        // Set all blocks to air first to create the crater instantly.
        for (Block block : blocks.keySet()) {
            // Handle containers: spill contents and don't turn them into flying blocks.
            if (block.getState() instanceof InventoryHolder holder) {
                Location itemSpawnLoc = block.getLocation().add(0.5, 0.5, 0.5);
                for (ItemStack itemStack : holder.getInventory().getContents()) {
                    if (itemStack == null || itemStack.getType().isAir()) continue;

                    Item droppedItem = block.getWorld().dropItem(itemSpawnLoc, itemStack);
                    Vector direction = droppedItem.getLocation().toVector().subtract(center.toVector()).normalize();
                    MathUtil.applyVelocity(droppedItem, direction, config.velocityStrength() * config.itemVelocityModifier(), 0, config.velocityYAward(), 10.0);
                }
                holder.getInventory().clear();
            }
            block.setType(Material.AIR, false); // No physics update for performance
        }

        // Schedule the debris effect for the next tick.
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<Block, BlockData> entry : blocks.entrySet()) {
                    if (ThreadLocalRandom.current().nextDouble() > config.debrisChance()) continue;
                    if (entry.getValue().getMaterial() == Material.TNT || entry.getKey().getState() instanceof InventoryHolder) continue;

                    Block block = entry.getKey();
                    Location blockLoc = block.getLocation().add(0.5, 0.5, 0.5);

                    FallingBlock fallingBlock = block.getWorld().spawnFallingBlock(blockLoc, entry.getValue());
                    fallingBlock.setDropItem(false);
                    fallingBlock.setHurtEntities(false);

                    Vector direction = blockLoc.toVector().subtract(center.toVector());
                    if (direction.getY() < 0) direction.setY(direction.getY() * -1);

                    MathUtil.applyVelocity(fallingBlock, direction, config.velocityStrength(), 0, config.velocityYAward(), 10.0);
                    scheduleFallingBlockRemoval(plugin, fallingBlock, config.debrisLifespanTicks());
                }

                if (config.regenerateBlocks()) {
                    scheduleBlockRegeneration(plugin, blocks, config.regenerationDelayTicks());
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    private static Map<Block, BlockData> getSphericalBlocks(Location center, float radius) {
        Map<Block, BlockData> blocks = new HashMap<>();
        int r = (int) Math.ceil(radius);
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (Math.sqrt((x * x) + (y * y) + (z * z)) <= radius) {
                        Block block = center.clone().add(x, y, z).getBlock();
                        if (!block.getType().isAir() && !block.isLiquid()) {
                            blocks.put(block, block.getBlockData());
                        }
                    }
                }
            }
        }
        return blocks;
    }

    private static void scheduleFallingBlockRemoval(JavaPlugin plugin, FallingBlock fallingBlock, int ticks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (fallingBlock.isValid()) {
                    fallingBlock.remove();
                }
            }
        }.runTaskLater(plugin, ticks);
    }

    private static void scheduleBlockRegeneration(JavaPlugin plugin, Map<Block, BlockData> blocksToRestore, long delay) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<Block, BlockData> entry : blocksToRestore.entrySet()) {
                    entry.getKey().setBlockData(entry.getValue(), true);
                }
            }
        }.runTaskLater(plugin, delay);
    }
}