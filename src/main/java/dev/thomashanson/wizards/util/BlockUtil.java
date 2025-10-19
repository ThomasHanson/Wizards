package dev.thomashanson.wizards.util;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;

import com.comphenix.protocol.reflect.FuzzyReflection;
import com.comphenix.protocol.reflect.fuzzy.FuzzyMethodContract;
import com.comphenix.protocol.utility.MinecraftReflection;

public class BlockUtil {

    // reflective handles
    // M method, F field
    private static Method m_craftBlockData_getState = null;
    private static Method m_nmsBlock_getId = null;

    // direct references
    private final static AtomicInteger nmsEntity_entityCounter = null;

    public static final BlockFace[] AXIS = {
            BlockFace.NORTH,
            BlockFace.EAST,
            BlockFace.SOUTH,
            BlockFace.WEST
    };

    private static final BlockFace[] RADIAL = {
            BlockFace.NORTH, BlockFace.NORTH_EAST,
            BlockFace.EAST, BlockFace.SOUTH_EAST,
            BlockFace.SOUTH, BlockFace.SOUTH_WEST,
            BlockFace.WEST, BlockFace.NORTH_WEST
    };

    public static BlockFace getFace(float yaw) {
        return getFace(yaw, true);
    }

    private static BlockFace getFace(float yaw, boolean useSubCardinalDirections) {

        return useSubCardinalDirections ?
                RADIAL[Math.round(yaw / 45F) & 0x7].getOppositeFace() :
                AXIS[Math.round(yaw / 90F) & 0x3].getOppositeFace();
    }

    public static BlockFace[] getSideBlockFaces(BlockFace facing) {
        return getSideBlockFaces(facing, true);
    }

    private static BlockFace[] getSideBlockFaces(BlockFace facing, boolean allowDiagonal) {

        int[][] facesXZ;
        allowDiagonal = !allowDiagonal && (facing.getModX() != 0 && facing.getModZ() != 0);

        facesXZ = new int[][] {
                        new int[] { allowDiagonal ? facing.getModX() : facing.getModZ(), allowDiagonal ? 0 : -facing.getModX() },
                        new int[] { allowDiagonal ? 0 : -facing.getModZ(), allowDiagonal ? facing.getModZ() : facing.getModX() }
                };

        BlockFace[] faces = new BlockFace[2];

        for (int i = 0; i < 2; i++) {

            int[] face = facesXZ[i];

            for (BlockFace blockFace : BlockFace.values()) {

                if (blockFace.getModY() == 0) {

                    if (face[0] == blockFace.getModX() && face[1] == blockFace.getModZ()) {
                        faces[i] = blockFace;
                        break;
                    }
                }
            }
        }

        if (allowDiagonal && (facing == BlockFace.NORTH_EAST || facing == BlockFace.SOUTH_WEST)) {
            faces = new BlockFace[] { faces[1], faces[0] };
        }

        return faces;
    }

    public static List<Block> getDiagonalBlocks(Block block, BlockFace facing, int blockWidth) {

        List<Block> blocks = new ArrayList<>();

        if (facing.getModX() == 0 || facing.getModZ() == 0)
            return blocks;

        BlockFace[] faces = getSideBlockFaces(facing);

        for (BlockFace face : faces) {

            Location location = block.getLocation().add(0.5 + (facing.getModX() / 2D), 0, 0.5 + (facing.getModZ() / 2D));
            blocks.add(location.add(face.getModX() / 2D, 0, face.getModZ() / 2D).getBlock());

            for (int i = 1; i < blockWidth; i++)
                blocks.add(location.add(face.getModX(), 0, face.getModZ()).getBlock());
        }

        return blocks;
    }

    public static BlockFace[] getCornerBlockFaces(BlockFace facing) {

        BlockFace left, right;

        for (int i = 0; i < RADIAL.length; i++) {

            if (RADIAL[i] != facing)
                continue;

            int high = i + 2;

            if (high >= RADIAL.length)
                high = high - RADIAL.length;

            int low = i - 2;

            if (low < 0)
                low = RADIAL.length + low;

            left = RADIAL[low];
            right = RADIAL[high];

            return new BlockFace[] { left, right };
        }

        return null;
    }

    public static Map<Block, Double> getInRadius(Location location, double radius, boolean ignoreY) {

        Map<Block, Double> blocks = new HashMap<>();
        int iR = (int) radius + 1;

        for (int x = -iR; x <= iR; x++) {
            for (int z = -iR; z <= iR; z++) {
                for (int y = (ignoreY ? 0 : -iR); y <= (ignoreY ? 0 : iR); y++) {

                    Block block = Objects.requireNonNull(location.getWorld()).getBlockAt (
                            (int) (location.getX() + x),
                            (int) (location.getY() + y),
                            (int) (location.getZ() + z)
                    );

                    double offset = location.distance(block.getLocation().add(0.5, 0.5, 0.5));

                    if (offset <= radius)
                        blocks.put(block, 1 - (offset / radius));
                }
            }
        }

        return blocks;
    }

    public static Map<Block, Double> getInRadius(Block block, double dR, boolean hollow) {

        Map<Block, Double> blocks = new HashMap<>();
        int iR = (int) dR + 1;

        for (int x = -iR; x <= iR; x++) {
            for (int z = -iR; z <= iR; z++) {
                for (int y = -iR; y <= iR; y++) {

                    Block relative = block.getRelative(x, y, z);
                    double offset = block.getLocation().distance(relative.getLocation());

                    if (offset <= dR && !(hollow && offset < dR - 1))
                        blocks.put(relative, 1 - (offset / dR));
                }
            }
        }

        return blocks;
    }

    public static List<Location> getLinesDistancedPoints(Location startingPoint, Location endingPoint, double distanceBetweenParticles) {
        return getLinesLimitedPoints(startingPoint, endingPoint, (int) Math.ceil(startingPoint.distance(endingPoint) / distanceBetweenParticles));
    }

    private static List<Location> getLinesLimitedPoints(Location startingPoint, Location endingPoint, int amountOfPoints) {

        startingPoint = startingPoint.clone();

        Vector vector = endingPoint.toVector().subtract(startingPoint.toVector());
        vector.normalize();
        vector.multiply(startingPoint.distance(endingPoint) / (amountOfPoints + 1D));

        List<Location> locations = new ArrayList<>();

        for (int i = 0; i < amountOfPoints; i++) {
            locations.add(startingPoint.add(vector).clone());
        }

        return locations;
    }

    /**
     * Gets the NMS registry state ID for the given block data.
     * @param data the block data
     * @return the NMS registry state ID.
     */
    public static int getBlockStateId(BlockData data) {
        try {
            if (m_craftBlockData_getState == null || m_nmsBlock_getId == null) {
                // use reflection to obtain the <nms.BlockState> getState method in CraftBlockData.
                Class<?> craftBlockDataClazz = MinecraftReflection.getCraftBukkitClass("block.data.CraftBlockData");
                Method M_craftBlockData_getState = craftBlockDataClazz.getMethod("getState");
                M_craftBlockData_getState.setAccessible(true);
                m_craftBlockData_getState = M_craftBlockData_getState;

                // use fuzzy reflection to find getId method in the nms.Block class.
                // this will lookup and return the registry state ID for the given nms.BlockState reference.
                // we'll just have to hope there isn't another public static method that returns int and accepts exactly nms.BlockState in the nms.Block class.
                FuzzyReflection blockReflector = FuzzyReflection.fromClass(MinecraftReflection.getBlockClass());
                m_nmsBlock_getId = blockReflector.getMethod(FuzzyMethodContract.newBuilder()
                        .banModifier(Modifier.PRIVATE)
                        .banModifier(Modifier.PROTECTED)
                        .requireModifier(Modifier.STATIC)
                        .parameterExactArray(MinecraftReflection.getIBlockDataClass())
                        .returnTypeExact(int.class)
                        .build());
            }

            // invoke getState to get the nms.BlockState of the CraftBlockData
            Object nmsState = m_craftBlockData_getState.invoke(data);
            // invoke getId to get the nms registry state ID of the nms.BlockState
            return (int) m_nmsBlock_getId.invoke(null, nmsState);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static Set<Material> getNonSolidBlocks() {

        Set<Material> nonSolids = new HashSet<>(Arrays.asList(Material.values()));
        nonSolids.removeIf(material -> getSolidBlocks().contains(material));

        return nonSolids;
    }

    private static Set<Material> getSolidBlocks() {

        Set<Material> solids = new HashSet<>(Arrays.asList(Material.values()));
        solids.removeIf(material -> (material.isBlock() && !material.isSolid()));

        return solids;
    }
}