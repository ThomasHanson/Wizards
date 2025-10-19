package dev.thomashanson.wizards.util;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class LocationUtil {

    /**
     * Returns a location with a specified distance away from the right side of
     * a location.
     * @param location The origin location
     * @param distance The distance to the right
     * @return the location of the distance to the right
     */
    public static Location getRightSide(Location location, double distance) {
        float angle = location.getYaw() / 60;
        return location.clone().subtract(new Vector(Math.cos(angle), 0, Math.sin(angle)).normalize().multiply(distance));
    }

    /**
     * Gets a location with a specified distance away from the left side of a
     * location.
     * @param location The origin location
     * @param distance The distance to the left
     * @return the location of the distance to the left
     */
    public static Location getLeftSide(Location location, double distance) {
        float angle = location.getYaw() / 60;
        return location.clone().add(new Vector(Math.cos(angle), 0, Math.sin(angle)).normalize().multiply(distance));
    }

    public static Location getLocationNearPlayers(List<Location> locations, List<Player> players, List<Player> noOverlap) {

        Location target = null;
        double bestDistance = 0;

        for (Location location : locations) {

            double closest = -1;
            boolean valid = true;

            // Don't spawn on other players
            for (Player player : noOverlap) {

                if (!player.getWorld().equals(location.getWorld()))
                    continue;

                double distance = player.getLocation().toVector().distanceSquared(location.toVector());

                if (distance < 0.8) {
                    valid = false;
                    break;
                }
            }

            if (!valid)
                continue;

            // Find closest player
            for (Player player : players) {

                if (!player.getWorld().equals(location.getWorld()))
                    continue;

                double distance = player.getLocation().distance(location);

                if (closest == -1 || distance < closest)
                    closest = distance;
            }

            if (closest == -1)
                continue;

            if (target == null || closest < bestDistance) {
                target = location;
                bestDistance = closest;
            }
        }

        return target;
    }

    public static Location getLocationAwayFromPlayers(List<Location> locations, List<Player> players) {

        Location bestLocation = null;
        double bestDistance = 0;

        for (Location location : locations) {

            double closest = -1;

            for (Player player : players) {

                // Different Worlds
                if (!player.getWorld().equals(location.getWorld())) {
                    continue;
                }

                double distance = player.getLocation().distanceSquared(location);

                if (closest == -1 || distance < closest) {
                    closest = distance;
                }
            }

            if (closest == -1) {
                continue;
            }

            if (bestLocation == null || closest > bestDistance) {
                bestLocation = location;
                bestDistance = closest;
            }
        }

        return bestLocation;
    }

    public static Location getAverageLocation(Collection<Location> locations) {

        if (locations.isEmpty())
            return null;

        Vector vector = new Vector(0, 0, 0);
        double count = 0;

        World world = null;

        for (Location spawn : locations) {

            count++;
            vector.add(spawn.toVector());

            world = spawn.getWorld();
        }

        vector.multiply(1.0 / count);
        return vector.toLocation(Objects.requireNonNull(world));
    }

    public static String locationToString(Location location) {

        StringBuilder locationString;

        locationString = new StringBuilder(location.getX() + ",");
        locationString.append(location.getY()).append(",");
        locationString.append(location.getZ());

        return locationString.toString();
    }

    public static Location locationFromConfig(World world, String input) {

        if (input.isEmpty())
            return null;

        String[] split = input.split(",");

        Location location = new Location (

                world,

                Double.parseDouble(split[0]),
                Double.parseDouble(split[1]),
                Double.parseDouble(split[2])
        );

        if (split.length > 3) {
            location.setYaw(Float.parseFloat(split[3]));
            location.setPitch(Float.parseFloat(split[4]));
        }

        return location;
    }
}