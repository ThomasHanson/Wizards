package dev.thomashanson.wizards.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.*;

public class BlockUtil {

    public static final BlockFace[] AXIS = {
            BlockFace.NORTH,
            BlockFace.EAST,
            BlockFace.SOUTH,
            BlockFace.WEST
    };

    public static final BlockFace[] RADIAL = {
            BlockFace.NORTH, BlockFace.NORTH_EAST,
            BlockFace.EAST, BlockFace.SOUTH_EAST,
            BlockFace.SOUTH, BlockFace.SOUTH_WEST,
            BlockFace.WEST, BlockFace.NORTH_WEST
    };

    public static BlockFace getFace(float yaw) {
        return getFace(yaw, true);
    }

    public static BlockFace getFace(float yaw, boolean useSubCardinalDirections) {

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

    public static List<Location> getLinesLimitedPoints(Location startingPoint, Location endingPoint, int amountOfPoints) {

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

    public static Set<Material> getNonSolidBlocks() {

        Set<Material> nonSolids = new HashSet<>(Arrays.asList(Material.values()));
        nonSolids.removeIf(material -> getSolidBlocks().contains(material));

        return nonSolids;
    }

    public static Set<Material> getSolidBlocks() {

        Set<Material> solids = new HashSet<>(Arrays.asList(Material.values()));
        solids.removeIf(material -> (material.isBlock() && !material.isSolid()));

        return solids;
    }
}