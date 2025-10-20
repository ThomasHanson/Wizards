package dev.thomashanson.wizards.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

/**
 * Utility class for mathematical operations, including vector math and formatting.
 */
public final class MathUtil {

    // Cache DecimalFormat instances for performance.
    private static final Map<Integer, DecimalFormat> DECIMAL_FORMAT_CACHE = new ConcurrentHashMap<>();

    private MathUtil() {
        // Private constructor to prevent instantiation
    }

    /**
     * Formats a duration in milliseconds into a human-readable string like "1.5 minutes".
     *
     * @param millis The duration in milliseconds.
     * @return A formatted string.
     */
    public static String formatTime(long millis) {
        if (millis < 0) return "0 seconds";

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        if (days > 0) return days + (days == 1 ? " day" : " days");

        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        if (hours > 0) return hours + (hours == 1 ? " hour" : " hours");

        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        if (minutes > 0) return minutes + (minutes == 1 ? " minute" : " minutes");

        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        if (seconds > 0) return seconds + (seconds == 1 ? " second" : " seconds");

        return millis + " ms";
    }

    /**
     * Trims a double to a specified number of decimal places.
     *
     * @param degree The number of decimal places.
     * @param value The double value to trim.
     * @return The trimmed double.
     */
    public static double trim(int degree, double value) {
        DecimalFormat format = DECIMAL_FORMAT_CACHE.computeIfAbsent(degree, k -> {
            String pattern = "#." + "#".repeat(Math.max(0, k));
            return new DecimalFormat(pattern, new DecimalFormatSymbols(Locale.US));
        });
        return Double.parseDouble(format.format(value));
    }

    /**
     * Gets a normalized direction vector pointing from one location to another.
     *
     * @param from The starting location.
     * @param to The target location.
     * @return A normalized {@link Vector}.
     */
    public static Vector getDirection(Location from, Location to) {
        return to.toVector().subtract(from.toVector()).normalize();
    }

    /**
     * Gets a normalized 2D direction vector (ignoring Y-axis) from one entity to another.
     *
     * @param from The starting entity.
     * @param to The target entity.
     * @return A normalized 2D {@link Vector}.
     */
    public static Vector getDirection2D(Entity from, Entity to) {
        // Create new location objects to avoid modifying originals if they are mutable
        Location fromLoc = from.getLocation();
        Location toLoc = to.getLocation();
        return toLoc.toVector().subtract(fromLoc.toVector()).setY(0).normalize();
    }

    /**
     * Calculates the 2D distance between two vectors, ignoring the Y-axis.
     * This method is pure and does not modify the input vectors.
     *
     * @param a The first vector.
     * @param b The second vector.
     * @return The 2D distance.
     */
    public static double getOffset2D(Vector a, Vector b) {
        Vector a2d = a.clone().setY(0);
        Vector b2d = b.clone().setY(0);
        return a2d.distance(b2d);
    }

    /**
     * Applies a velocity to an entity.
     *
     * @param entity       The entity to apply velocity to.
     * @param direction    The direction of the velocity.
     * @param strength     The magnitude of the velocity.
     * @param yBase        The base Y value to set.
     * @param yAdd         An additional value to add to the Y velocity.
     * @param yMax         The maximum value for the Y velocity.
     */
    public static void applyVelocity(Entity entity, Vector direction, double strength, double yBase, double yAdd, double yMax) {
        Vector velocity = direction.clone(); // Clone to avoid mutating the original direction vector
        if (Double.isNaN(velocity.getX()) || Double.isNaN(velocity.getY()) || Double.isNaN(velocity.getZ()) || velocity.length() == 0) {
            return;
        }

        velocity.setY(yBase).normalize().multiply(strength);
        velocity.setY(Math.min(velocity.getY() + yAdd, yMax));

        if (entity.isOnGround()) {
            velocity.setY(velocity.getY() + 0.2); // Small ground boost
        }

        entity.setFallDistance(0F);
        entity.setVelocity(velocity);
    }
}