package dev.thomashanson.wizards.util;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

import com.comphenix.protocol.reflect.FuzzyReflection;
import com.comphenix.protocol.reflect.fuzzy.FuzzyMethodContract;
import com.comphenix.protocol.utility.MinecraftReflection;

/**
 * Utility class for block-related operations, such as spatial queries and direction calculations.
 */
public final class BlockUtil {

    private static Method m_craftBlockData_getState = null;
    private static Method m_nmsBlock_getId = null;

    private static final Set<Material> SOLID_BLOCKS;
    private static final Set<Material> NON_SOLID_BLOCKS;

    private static final BlockFace[] AXIS = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };
    private static final BlockFace[] RADIAL = { BlockFace.NORTH, BlockFace.NORTH_EAST, BlockFace.EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH, BlockFace.SOUTH_WEST, BlockFace.WEST, BlockFace.NORTH_WEST };

    static {
        // Pre-compute and cache solid and non-solid block materials for performance.
        Set<Material> solid = EnumSet.noneOf(Material.class);
        for (Material material : Material.values()) {
            if (material.isBlock() && material.isSolid()) {
                solid.add(material);
            }
        }
        SOLID_BLOCKS = Collections.unmodifiableSet(solid);

        // Derive non-solid blocks from the solid set.
        NON_SOLID_BLOCKS = Collections.unmodifiableSet(
            Arrays.stream(Material.values())
                .filter(Material::isBlock)
                .filter(m -> !solid.contains(m))
                .collect(Collectors.toSet())
        );
    }

    private BlockUtil() {
        // Private constructor to prevent instantiation
    }

    /**
     * Gets the cardinal or sub-cardinal direction based on a given yaw.
     *
     * @param yaw                      The yaw angle.
     * @param useSubCardinalDirections If true, includes diagonal directions (e.g., NORTH_EAST).
     * @return The corresponding {@link BlockFace}.
     */
    public static BlockFace getFace(float yaw, boolean useSubCardinalDirections) {
        if (useSubCardinalDirections) {
            // Each of the 8 directions covers 45 degrees.
            return RADIAL[Math.round(yaw / 45f) & 7].getOppositeFace();
        } else {
            // Each of the 4 cardinal directions covers 90 degrees.
            return AXIS[Math.round(yaw / 90f) & 3].getOppositeFace();
        }
    }

    /**
     * Gathers all blocks within a specified radius of a location.
     *
     * @param location The center location.
     * @param radius   The radius to check within.
     * @return A map of blocks within the radius to their distance-based offset (1.0 = closest, 0.0 = furthest).
     */
    public static Map<Block, Double> getBlocksInRadius(Location location, double radius) {
        if (location.getWorld() == null) {
            return Collections.emptyMap();
        }

        Map<Block, Double> blocks = new HashMap<>();
        int searchRadius = (int) Math.ceil(radius);

        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -searchRadius; y <= searchRadius; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {

                    Location blockLocation = location.clone().add(x, y, z);
                    double distance = location.distance(blockLocation);

                    if (distance <= radius) {
                        blocks.put(blockLocation.getBlock(), 1.0 - (distance / radius));
                    }
                }
            }
        }
        return blocks;
    }

    /**
     * Gets a cached, unmodifiable set of all solid block materials.
     *
     * @return The set of solid materials.
     */
    public static Set<Material> getSolidBlocks() {
        return SOLID_BLOCKS;
    }

    /**
     * Gets a cached, unmodifiable set of all non-solid block materials.
     *
     * @return The set of non-solid materials.
     */
    public static Set<Material> getNonSolidBlocks() {
        return NON_SOLID_BLOCKS;
    }

    /**
     * Gets the internal NMS registry state ID for the given block data.
     * <p>
     * <b>Warning:</b> This method uses reflection and is highly dependent on internal
     * server code. It is fragile and may break on any server version update. Use with caution
     * and only when there is no alternative through the public API.
     *
     * @param data The block data.
     * @return The NMS registry state ID, or -1 if an error occurs.
     */
    public static int getBlockStateId(BlockData data) {
        try {
            if (m_craftBlockData_getState == null || m_nmsBlock_getId == null) {
                // Lazily initialize reflective handles
                Class<?> craftBlockDataClazz = MinecraftReflection.getCraftBukkitClass("block.data.CraftBlockData");
                m_craftBlockData_getState = craftBlockDataClazz.getDeclaredMethod("getState");
                m_craftBlockData_getState.setAccessible(true);

                FuzzyReflection blockReflector = FuzzyReflection.fromClass(MinecraftReflection.getBlockClass());
                m_nmsBlock_getId = blockReflector.getMethod(FuzzyMethodContract.newBuilder()
                    .banModifier(Modifier.PRIVATE)
                    .requireModifier(Modifier.STATIC)
                    .parameterExactArray(MinecraftReflection.getIBlockDataClass())
                    .returnTypeExact(int.class)
                    .build());
            }

            Object nmsState = m_craftBlockData_getState.invoke(data);
            return (int) m_nmsBlock_getId.invoke(null, nmsState);

        } catch (ReflectiveOperationException e) {
            // Instead of crashing, log the error and return a sentinel value.
            System.err.println("Failed to get block state ID via reflection. This may be due to a server update.");
            e.printStackTrace();
            return -1;
        }
    }
}