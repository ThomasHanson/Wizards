package dev.thomashanson.wizards.game.spell.types;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.mode.GameTeam;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.hologram.Hologram;
import dev.thomashanson.wizards.hologram.HologramManager;
import dev.thomashanson.wizards.hologram.HologramProperties;

public class SpellWizardsCompass extends Spell implements Tickable {

    private final WizardsPlugin plugin;
    private final List<CompassInstance> activeCompasses = new ArrayList<>();

    public SpellWizardsCompass(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
        this.plugin = plugin;
    }

    @Override
    public boolean cast(Player player, int level) {
        activeCompasses.add(new CompassInstance(this, player, level));
        player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.5F, 1F);
        return true;
    }

    @Override
    public void tick(long gameTick) {
        if (activeCompasses.isEmpty()) return;
        activeCompasses.removeIf(CompassInstance::tick);
    }

    @Override
    public void cleanup() {
        activeCompasses.forEach(CompassInstance::cleanup);
        activeCompasses.clear();
    }

    public WizardsPlugin getPlugin() {
        return this.plugin;
    }

    private static class CompassInstance {
        final SpellWizardsCompass parent;
        final Player caster;
        final int level;
        final List<ParticleStrand> strands = new ArrayList<>();

        final int particleLifespan;

        CompassInstance(SpellWizardsCompass parent, Player caster, int level) {
            this.parent = parent;
            this.caster = caster;
            this.level = level;
            this.particleLifespan = (int) parent.getStat("particle-lifespan-ticks", level, 50.0);
            initializeStrands();
        }

        void initializeStrands() {
            parent.getGame().ifPresent(game -> game.getPlayers(true).stream()
                .filter(target -> game.getRelation(caster, target) == GameTeam.TeamRelation.ENEMY)
                .forEach(target -> {
                    Color color = Color.fromBGR(
                        ThreadLocalRandom.current().nextInt(256),
                        ThreadLocalRandom.current().nextInt(256),
                        ThreadLocalRandom.current().nextInt(256)
                    );
                    strands.add(new ParticleStrand(parent, caster, target, color, level));
                }));
        }

        boolean tick() {
            if (!caster.isOnline() || strands.isEmpty()) {
                cleanup(); // Ensure holograms are cleaned up if caster logs off
                return true;
            }
            strands.forEach(strand -> strand.tick(particleLifespan));
            strands.removeIf(strand -> {
                if (strand.isDone()) {
                    strand.cleanup(); // Important: Delete the hologram
                    return true;
                }
                return false;
            });
            return strands.isEmpty();
        }

        void cleanup() {
            strands.forEach(ParticleStrand::cleanup);
        }
    }

    private static class ParticleStrand {
        final SpellWizardsCompass parent;
        final Player caster;
        final Player target;
        final Color color;
        final int level;
        final List<ParticlePoint> points = new ArrayList<>();
        boolean done = false;

        // The hologram that displays the target's name
        final Hologram hologram;

        // --- NEW FIELDS ---
        /** The current location of the "head" of the particle strand. */
        private final Location currentHeadLocation;
        /** How far the head has traveled so far. */
        private double distanceTraveled = 0.0;
        /** Has the head reached the target? If true, we stop spawning new particles. */
        private boolean reachedTarget = false;
        // --- END NEW FIELDS ---

        ParticleStrand(SpellWizardsCompass parent, Player caster, Player target, Color color, int level) {
            this.parent = parent;
            this.caster = caster;
            this.target = target;
            this.color = color;
            this.level = level;

            // Start the particle strand at the caster's eyes.
            this.currentHeadLocation = caster.getEyeLocation().clone();

            HologramManager hologramManager = parent.getPlugin().getHologramManager();

            // 1. Define properties for a private, see-through hologram
            HologramProperties props = HologramProperties.builder()
                .visibility(HologramProperties.Visibility.PRIVATE) // Only visible when shown
                .seeThrough(true) // Can be seen through walls
                .build();

            // 2. Create the hologram at a temporary location with the target's name
            this.hologram = hologramManager.createHologram(
                caster.getEyeLocation(),
                List.of(target.displayName()), // The name to display
                props
            );

            // 3. Make it visible ONLY to the caster
            this.hologram.showTo(caster);
        }

        void tick(int maxAge) {
            // --- Target/World Check ---
            if (!target.isOnline() || !target.getWorld().equals(caster.getWorld())) {
                done = true;
                return;
            }

            // --- Hologram Position Update ---
            // Calculate the direction from caster to target.
            Vector direction = target.getEyeLocation().toVector()
                .subtract(caster.getEyeLocation().toVector()).normalize();

            // Calculate distance to the target and cap it at 7 blocks
            double hologramDist = Math.min(7.0, caster.getLocation().distance(target.getLocation()));
            Location hologramLocation = caster.getEyeLocation().add(direction.clone().multiply(hologramDist));
            hologram.teleport(hologramLocation);

            // --- Particle Logic ---
            
            // 1. Tick and spawn all existing particles in the trail
            points.forEach(point -> point.tick(color));
            // 2. Remove any particles that have expired
            points.removeIf(point -> point.isExpired(maxAge));

            // 3. Logic for the "head" of the strand
            if (!reachedTarget) {
                
                double speed = parent.getStat("particle-speed", level, 0.2);
                double targetDistance = caster.getEyeLocation().distance(target.getEyeLocation());

                // 4. Check if we've arrived at the target
                if (distanceTraveled >= targetDistance) {
                    reachedTarget = true; // Stop spawning new particles

                } else {
                    // 5. We haven't arrived. Advance the head, add a new particle, and update distance.
                    Vector trajectory = direction.clone().multiply(speed); // Use a clone!
                    
                    // Add a new particle point at the *current* head location
                    points.add(new ParticlePoint(currentHeadLocation.clone()));
                    
                    // Advance the head for the *next* tick
                    currentHeadLocation.add(trajectory);
                    distanceTraveled += speed;
                }
            }
            
            // 6. The spell is "done" (can be cleaned up) only when...
            //    - The head has reached the target (reachedTarget == true)
            //    - AND all particles in the trail have faded (points.isEmpty())
            if (reachedTarget && points.isEmpty()) {
                done = true;
            }
        }

        /**
         * Permanently deletes the hologram to prevent memory leaks.
         */
        void cleanup() {
            if (hologram != null) {
                hologram.delete();
            }
            done = true;
        }

        boolean isDone() {
            return done;
        }
    }

    private static class ParticlePoint {
        final Location location;
        int age = 0;
        ParticlePoint(Location location) { this.location = location; }
        void tick(Color color) {
            age++;
            location.getWorld().spawnParticle(Particle.REDSTONE, location, 1, 0, 0, 0, 0, new Particle.DustOptions(color, 1.0F));
        }
        boolean isExpired(int maxAge) { return age > maxAge; }
    }
}