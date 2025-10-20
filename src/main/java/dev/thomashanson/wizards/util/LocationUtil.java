package dev.thomashanson.wizards.util;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Utility class for location-based calculations and conversions.
 */
public final class LocationUtil {

    private static final double MIN_SPAWN_DISTANCE_SQUARED = 0.8 * 0.8;

    private LocationUtil() {
        // Private constructor to prevent instantiation
    }

    /**
     * Gets a location offset to the right of a given location's direction.
     *
     * @param location The origin location.
     * @param distance The distance to offset to the right.
     * @return The new location.
     */
    public static Location getRightSide(Location location, double distance) {
        // Get the direction vector and rotate it -90 degrees around the Y axis
        Vector direction = location.getDirection().setY(0).normalize();
        Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX());
        return location.clone().add(perpendicular.multiply(distance));
    }

    /**
     * Gets a location offset to the left of a given location's direction.
     *
     * @param location The origin location.
     * @param distance The distance to offset to the left.
     * @return The new location.
     */
    public static Location getLeftSide(Location location, double distance) {
        // Get the direction vector and rotate it +90 degrees around the Y axis
        Vector direction = location.getDirection().setY(0).normalize();
        Vector perpendicular = new Vector(direction.getZ(), 0, -direction.getX());
        return location.clone().add(perpendicular.multiply(distance));
    }

    /**
     * Finds the best spawn location from a list that is furthest from all players.
     *
     * @param locations A list of potential spawn locations.
     * @param players   A list of players to check against.
     * @return An {@link Optional} containing the best location, or empty if none are valid.
     */
    public static Optional<Location> getFurthestLocation(List<Location> locations, List<Player> players) {
        return locations.stream()
            .min(Comparator.comparingDouble(loc -> findClosestPlayerDistanceSq(loc, players)))
            .map(Location::clone);
    }

    /**
     * Finds the best spawn location from a list that is closest to any player,
     * while not being on top of an existing player.
     *
     * @param locations    A list of potential spawn locations.
     * @param targetPlayers The players to try to spawn near.
     * @param existingPlayers The players that cannot be spawned on top of.
     * @return An {@link Optional} containing the best location, or empty if none are valid.
     */
    public static Optional<Location> getClosestLocation(List<Location> locations, List<Player> targetPlayers, List<Player> existingPlayers) {
        return locations.stream()
            // Filter out locations that are too close to an existing player
            .filter(loc -> findClosestPlayerDistanceSq(loc, existingPlayers) > MIN_SPAWN_DISTANCE_SQUARED)
            // Find the location that has the minimum distance to the closest target player
            .min(Comparator.comparingDouble(loc -> findClosestPlayerDistanceSq(loc, targetPlayers)))
            .map(Location::clone);
    }

    private static double findClosestPlayerDistanceSq(Location location, Collection<Player> players) {
        return players.stream()
            .filter(p -> p.getWorld().equals(location.getWorld()))
            .mapToDouble(p -> p.getLocation().distanceSquared(location))
            .min()
            .orElse(Double.MAX_VALUE); // If no players, return max value
    }

    /**
     * Serializes a Location to a string format: "world,x,y,z,yaw,pitch".
     *
     * @param location The location to serialize.
     * @return The serialized string representation.
     */
    public static String toString(Location location) {
        if (location == null || location.getWorld() == null) {
            return "";
        }
        return location.getWorld().getName() + ","
            + location.getX() + "," + location.getY() + "," + location.getZ() + ","
            + location.getYaw() + "," + location.getPitch();
    }

    /**
     * Deserializes a location from a string.
     *
     * @param world The world the location is in.
     * @param input The string from config (format: "x,y,z" or "x,y,z,yaw,pitch").
     * @return The deserialized {@link Location}, or null if parsing fails.
     */
    public static Location fromString(World world, String input) {
        if (world == null || input == null || input.isEmpty()) {
            return null;
        }

        String[] parts = input.split(",");
        if (parts.length < 3) {
            return null;
        }

        try {
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);

            Location location = new Location(world, x, y, z);

            if (parts.length >= 5) {
                location.setYaw(Float.parseFloat(parts[3]));
                location.setPitch(Float.parseFloat(parts[4]));
            }
            return location;
        } catch (NumberFormatException e) {
            System.err.println("Failed to parse location from string: " + input);
            return null;
        }
    }
}