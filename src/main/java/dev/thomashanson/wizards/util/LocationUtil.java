package dev.thomashanson.wizards.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;

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

    public static Location getLocationAroundCircle(Location center, double radius, double angle) {

        double x = center.getX() + radius * Math.cos(angle);
        double z = center.getZ() + radius * Math.sin(angle);
        double y = center.getY();

        Location location = new Location(center.getWorld(), x, y, z);
        Vector difference = center.toVector().clone().subtract(location.toVector());
        location.setDirection(difference);

        return location;
    }

    public static String locationToString(Location location) {

        String locationString;

        locationString = location.getBlockX() + ",";
        locationString += location.getBlockY() + ",";
        locationString += String.valueOf(location.getBlockZ());

        return locationString;
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