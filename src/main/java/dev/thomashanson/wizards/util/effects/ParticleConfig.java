package dev.thomashanson.wizards.util.effects;

import org.bukkit.Particle;

/**
 * A flexible record to store configuration for spawning particles.
 *
 * @param particle The Bukkit Particle type to spawn.
 * @param count    The number of particles to spawn at each point.
 * @param speed    The speed/spread of the particle.
 * @param offsetX  The random offset on the X-axis.
 * @param offsetY  The random offset on the Y-axis.
 * @param offsetZ  The random offset on the Z-axis.
 * @param data     Optional data, such as DustOptions for REDSTONE.
 */
public record ParticleConfig(
        Particle particle,
        int count,
        double speed,
        double offsetX,
        double offsetY,
        double offsetZ,
        Object data
) {
    /**
     * Convenience constructor for simple particles with no offset or data.
     */
    public ParticleConfig(Particle particle, int count, double speed) {
        this(particle, count, speed, 0, 0, 0, null);
    }
}