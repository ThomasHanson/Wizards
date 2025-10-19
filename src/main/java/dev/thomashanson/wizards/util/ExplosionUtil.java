package dev.thomashanson.wizards.util;

import java.util.Collection; // Assuming this is the correct package for MathUtil
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.TNTPrimed;
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
 *
 * Refactored and modernized from original concept for Paper 1.20.4.
 */
public final class ExplosionUtil {

    // --- General Configuration --- //
    public static boolean REGENERATE_BLOCKS = false;
    public static long REGENERATION_DELAY_TICKS = 100;
    public static boolean TNT_SPREAD_ENABLED = true;
    public static int TNT_FUSE_TICKS = 80;

    // --- Special Block Handling ---
    public static boolean HANDLE_INVENTORY_HOLDERS = true; // If true, chests/hoppers etc. will spew their items
    public static double ITEM_VELOCITY_MODIFIER = 0.5; // Multiplier for the launch force of items from containers

    // --- Debris (FallingBlock) Configuration --- //
    public static double DEBRIS_BASE_CHANCE = 0.2;
    public static double DEBRIS_DENSITY_FACTOR = 80.0;
    public static double DEBRIS_CHANCE_CAP = 0.98;
    public static int DEBRIS_LIFESPAN_TICKS = 60;

    // --- Velocity Configuration (passed to MathUtil.setVelocity) --- //
    public static double VELOCITY_BASE_STRENGTH = 0.5;
    public static double VELOCITY_STRENGTH_RANDOMNESS = 0.25;
    public static double VELOCITY_Y_ADD = 0.4;
    public static double VELOCITY_Y_ADD_RANDOMNESS = 0.2;
    public static double VELOCITY_Y_MAX = 10.0;


    // Private constructor to prevent instantiation of this utility class.
    private ExplosionUtil() {}

    /**
     * Creates a custom visual explosion affecting a spherical area.
     *
     * @param plugin    Your main plugin instance, required for scheduling tasks.
     * @param center    The center location of the explosion.
     * @param radius    The radius of the explosion, affecting how many blocks are included.
     * @param playSound If true, plays an explosion sound at the center.
     */
    public static void createExplosion(JavaPlugin plugin, Location center, float radius, boolean useRayTrace, boolean playSound) {
        if (plugin == null || center == null || center.getWorld() == null) {
            return;
        }

        Map<Block, BlockData> affectedBlocks;
        // This is a placeholder for ray-tracing logic if you choose to implement it.
        // For now, it defaults to spherical.
        affectedBlocks = getSphericalBlocks(center, radius);

        createExplosion(plugin, center, affectedBlocks, playSound);
    }

    /**
     * Creates a custom visual explosion affecting a specific collection of blocks.
     * This is a convenience method that builds the BlockData map for you.
     *
     * @param plugin    Your main plugin instance, required for scheduling tasks.
     * @param center    The center location of the explosion (for effects and velocity calculation).
     * @param blocks    A collection of Blocks to be included in the explosion.
     * @param playSound If true, plays an explosion sound at the center.
     */
    public static void createExplosion(JavaPlugin plugin, Location center, Collection<Block> blocks, boolean playSound) {
        if (plugin == null || center == null || center.getWorld() == null || blocks == null || blocks.isEmpty()) {
            return;
        }

        final Map<Block, BlockData> blockDataMap = new HashMap<>();
        for (Block block : blocks) {
            if (!block.getType().isAir() && !block.isLiquid()) {
                blockDataMap.put(block, block.getBlockData());
            }
        }
        createExplosion(plugin, center, blockDataMap, playSound);
    }

    /**
     * Creates a custom visual explosion affecting a specific map of blocks and their data.
     * All other public createExplosion methods delegate to this one.
     *
     * @param plugin    Your main plugin instance, required for scheduling tasks.
     * @param center    The center location of the explosion (for effects and velocity calculation).
     * @param blocks    A map of Blocks and their original BlockData to be included in the explosion.
     * @param playSound If true, plays an explosion sound at the center.
     */
    public static void createExplosion(JavaPlugin plugin, Location center, Map<Block, BlockData> blocks, boolean playSound) {
        if (plugin == null || center == null || center.getWorld() == null || blocks == null || blocks.isEmpty()) {
            return;
        }

        if (playSound) {
            center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.0f);
        }
        center.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, center, 1);

        // --- Handle Inventory Holders (Chests, etc.) ---
        if (HANDLE_INVENTORY_HOLDERS) {
            // Use a copy to avoid ConcurrentModificationException while iterating and removing
            for (Block block : new HashSet<>(blocks.keySet())) {
                if (block.getState() instanceof InventoryHolder) {
                    InventoryHolder holder = (InventoryHolder) block.getState();
                    Location blockLoc = block.getLocation().add(0.5, 0.5, 0.5);

                    // Drop and launch items from the container
                    if (holder.getInventory().getContents() != null) {
                        for (ItemStack itemStack : holder.getInventory().getContents()) {
                            if (itemStack == null || itemStack.getType().isAir()) continue;

                            Item droppedItem = block.getWorld().dropItem(blockLoc, itemStack.clone());
                            Vector direction = droppedItem.getLocation().toVector().subtract(center.toVector()).normalize();
                            double strength = (VELOCITY_BASE_STRENGTH + (ThreadLocalRandom.current().nextDouble() * VELOCITY_STRENGTH_RANDOMNESS)) * ITEM_VELOCITY_MODIFIER;
                            double yAdd = VELOCITY_Y_ADD + (ThreadLocalRandom.current().nextDouble() * VELOCITY_Y_ADD_RANDOMNESS);
                            MathUtil.setVelocity(droppedItem, direction, strength, false, 0, yAdd, VELOCITY_Y_MAX, false);
                        }
                    }

                    // Clear inventory to prevent item duplication when the block regenerates
                    holder.getInventory().clear();

                    block.setType(Material.AIR);

                    // The container block itself will be removed, but not launched as a FallingBlock
                    blocks.remove(block);
                }
            }
        }

        // Set all remaining (non-container) blocks to air
        for (Block block : blocks.keySet()) {
            block.setType(Material.AIR, false);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                final AtomicInteger blocksSpawned = new AtomicInteger(0);

                for (Map.Entry<Block, BlockData> entry : blocks.entrySet()) {
                    double dynamicChance = DEBRIS_BASE_CHANCE + (blocksSpawned.get() / DEBRIS_DENSITY_FACTOR);
                    if (ThreadLocalRandom.current().nextDouble() > Math.min(DEBRIS_CHANCE_CAP, dynamicChance)) {
                        blocksSpawned.incrementAndGet();

                        Block block = entry.getKey();
                        BlockData blockData = entry.getValue();
                        Location blockLoc = block.getLocation().add(0.5, 0.5, 0.5);

                        if (TNT_SPREAD_ENABLED && blockData.getMaterial() == Material.TNT) {
                            TNTPrimed tnt = block.getWorld().spawn(blockLoc, TNTPrimed.class);
                            tnt.setFuseTicks(TNT_FUSE_TICKS);
                            tnt.setYield(0);
                        } else {
                            FallingBlock fallingBlock = block.getWorld().spawnFallingBlock(blockLoc, blockData);
                            fallingBlock.setDropItem(false);
                            fallingBlock.setHurtEntities(false);

                            Vector direction = blockLoc.toVector().subtract(center.toVector());
                            if (direction.getY() < 0) direction.setY(direction.getY() * -1);

                            double strength = VELOCITY_BASE_STRENGTH + (ThreadLocalRandom.current().nextDouble() * VELOCITY_STRENGTH_RANDOMNESS);
                            double yAdd = VELOCITY_Y_ADD + (ThreadLocalRandom.current().nextDouble() * VELOCITY_Y_ADD_RANDOMNESS);
                            MathUtil.setVelocity(fallingBlock, direction, strength, false, 0, yAdd, VELOCITY_Y_MAX, false);

                            scheduleFallingBlockRemoval(plugin, fallingBlock, DEBRIS_LIFESPAN_TICKS);
                        }
                    }
                }

                if (REGENERATE_BLOCKS) {
                    scheduleBlockRegeneration(plugin, blocks);
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
                if (fallingBlock.isValid() && !fallingBlock.isDead()) {
                    fallingBlock.getWorld().spawnParticle(Particle.BLOCK_CRACK, fallingBlock.getLocation(), 10, fallingBlock.getBlockData());
                    fallingBlock.remove();
                }
            }
        }.runTaskLater(plugin, ticks);
    }

    private static void scheduleBlockRegeneration(JavaPlugin plugin, Map<Block, BlockData> blocksToRestore) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<Block, BlockData> entry : blocksToRestore.entrySet()) {
                    entry.getKey().setBlockData(entry.getValue(), true);
                }
            }
        }.runTaskLater(plugin, REGENERATION_DELAY_TICKS);
    }
}
