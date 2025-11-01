package dev.thomashanson.wizards.util.effects;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * A utility class for drawing complex particle shapes.
 */
public final class ParticleUtil {

    private ParticleUtil() {
        // Private constructor for utility class
    }

    /**
     * Draws a line of particles interpolated between two locations.
     *
     * @param loc1   The start location.
     * @param loc2   The end location.
     * @param config The particle configuration.
     */
    public static void drawParticleLine(Location loc1, Location loc2, ParticleConfig config) {
        World world = loc1.getWorld();
        if (world == null || !world.equals(loc2.getWorld())) {
            return;
        }

        final double distance = loc1.distance(loc2);
        final double spacing = 0.25; // 4 particles per block
        Vector direction = loc2.toVector().subtract(loc1.toVector()).normalize();

        for (double d = 0; d < distance; d += spacing) {
            Location point = loc1.clone().add(direction.clone().multiply(d));
            world.spawnParticle(
                    config.particle(),
                    point,
                    config.count(),
                    config.offsetX(),
                    config.offsetY(),
                    config.offsetZ(),
                    config.speed(),
                    config.data()
            );
        }
    }

    /**
     * Draws the outline of a square on the XZ plane, centered at a location.
     *
     * @param center The center of the square.
     * @param size   The half-length of the square (radius).
     * @param config The particle configuration.
     */
    public static void drawParticleSquare(Location center, double size, ParticleConfig config) {
        Location c1 = center.clone().add(-size, 0, -size); // Bottom-left
        Location c2 = center.clone().add(size, 0, -size);  // Bottom-right
        Location c3 = center.clone().add(size, 0, size);   // Top-right
        Location c4 = center.clone().add(-size, 0, size);  // Top-left

        drawParticleLine(c1, c2, config);
        drawParticleLine(c2, c3, config);
        drawParticleLine(c3, c4, config);
        drawParticleLine(c4, c1, config);
    }

    /**
     * Creates an instant circular shockwave of particles on the XZ plane.
     *
     * @param center The center of the shockwave.
     * @param radius The radius of the circle.
     * @param config The particle configuration.
     */
    public static void createShockwave(Location center, double radius, ParticleConfig config) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        // High density: 6 particles per block of circumference
        double circumference = 2 * Math.PI * radius;
        int particleCount = (int) (circumference * 6);

        for (int i = 0; i < particleCount; i++) {
            double angle = 2 * Math.PI * i / particleCount;
            double x = center.getX() + (radius * Math.cos(angle));
            double z = center.getZ() + (radius * Math.sin(angle));
            Location point = new Location(world, x, center.getY(), z);

            world.spawnParticle(
                    config.particle(),
                    point,
                    config.count(),
                    config.offsetX(),
                    config.offsetY(),
                    config.offsetZ(),
                    config.speed(),
                    config.data()
            );
        }
    }
}