package dev.thomashanson.wizards.game.overtime;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.spell.SpellType;

public abstract class Disaster {

    // --- State Variables ---
    private final String name;
    private final Set<SpellType> buffedSpells;
    private final List<String> messages;
    private final Wizards game;
    private Instant lastStrike;
    private Duration strikeInterval; // Initialized in constructor using abstract method
    private Instant nextSound;
    private double accuracyFactor = 0;
    private float size = 1.5F;

    protected Disaster(Wizards game, String name, Set<SpellType> buffedSpells, List<String> messages) {
        this.game = game;
        this.name = name;
        this.buffedSpells = buffedSpells;
        this.messages = messages;

        // Initialize strike interval with the value provided by the subclass
        this.strikeInterval = Duration.ofMillis(getInitialStrikeIntervalMs());
    }

    // --- Abstract Configuration Getters ---
    // Subclasses must implement these to define their own timing.

    /**
     * @return The initial time between strikes in milliseconds when the disaster starts.
     */
    protected abstract long getInitialStrikeIntervalMs();

    /**
     * @return The absolute minimum time between strikes in milliseconds.
     */
    protected abstract long getMinimumStrikeIntervalMs();

    /**
     * @return The amount of time in milliseconds to reduce the strike interval by each tick.
     */
    protected abstract long getStrikeIntervalReductionPerTickMs();

    // --- Abstract Strike Logic ---
    protected abstract void strikeAt(Location location);

    // --- Core Methods ---
    protected void strike() {
        Location targetLocation = getNextLocation();
        if (targetLocation != null) {
            strikeAt(targetLocation);
            this.lastStrike = Instant.now();
        }
    }

    public void update() {
        // --- Strike Interval Logic ---
        final Duration minimumStrikeInterval = Duration.ofMillis(getMinimumStrikeIntervalMs());

        // Only reduce the interval if it's currently above the minimum.
        if (strikeInterval.compareTo(minimumStrikeInterval) > 0) {
            strikeInterval = strikeInterval.minusMillis(getStrikeIntervalReductionPerTickMs());

            // After reducing, ensure it hasn't dropped below the minimum.
            if (strikeInterval.compareTo(minimumStrikeInterval) < 0) {
                strikeInterval = minimumStrikeInterval;
            }
        }

        // Check if it's time to strike based on the dynamically shrinking interval
        if (lastStrike == null || Duration.between(lastStrike, Instant.now()).compareTo(strikeInterval) >= 0) {
            strike();
        }

        // --- Sound Effects Logic (Unchanged) ---
        if (nextSound == null || Instant.now().isAfter(nextSound)) {
            Sound sound = switch (ThreadLocalRandom.current().nextInt(3)) {
                case 0 -> Sound.ENTITY_GHAST_AMBIENT;
                case 1 -> Sound.ENTITY_ENDER_DRAGON_GROWL;
                default -> Sound.ENTITY_GHAST_SCREAM;
            };

            for (Player player : game.getPlayers(false)) {
                player.playSound(player.getLocation(), sound, 0.7F, 0.5F + (ThreadLocalRandom.current().nextFloat() * 0.5F));
            }
            nextSound = Instant.now().plusSeconds(5 + ThreadLocalRandom.current().nextInt(8));
        }
    }

    protected Location getNextLocation() {
        World world = getGame().getActiveMap().getWorld();
        if (world == null) return null;

        double minX = getGame().getCurrentMinX();
        double maxX = getGame().getCurrentMaxX();
        double minZ = getGame().getCurrentMinZ();
        double maxZ = getGame().getCurrentMaxZ();

        // Fallback for a very small or collapsed world border
        if (minX >= maxX - 0.5 || minZ >= maxZ - 0.5) {
            Location centerFallback = new Location(world, (getGame().getActiveMap().getBounds().getMinX() + getGame().getActiveMap().getBounds().getMaxX()) / 2.0, game.getInitialMapMaxY(), (getGame().getActiveMap().getBounds().getMinZ() + getGame().getActiveMap().getBounds().getMaxZ()) / 2.0);
            return world.getHighestBlockAt(centerFallback).getLocation().add(0.5, 0.5, 0.5);
        }

        List<Player> alivePlayersInBounds = getGame().getPlayers(true).stream()
                .filter(p -> {
                    Location loc = p.getLocation();
                    return loc.getX() >= minX && loc.getX() < maxX && loc.getZ() >= minZ && loc.getZ() < maxZ;
                })
                .collect(Collectors.toList());

        // --- ACCURACY BEHAVIOR CHANGE ---
        // 1. Slower Accuracy Increase: The increment is reduced by 5x to match the reference behavior.
        this.accuracyFactor += 0.0001;
        if (this.accuracyFactor > 1.0) this.accuracyFactor = 1.0;

        if (!alivePlayersInBounds.isEmpty()) {
            for (int attempt = 0; attempt < 15; attempt++) {
                Player targetPlayer = alivePlayersInBounds.get(ThreadLocalRandom.current().nextInt(alivePlayersInBounds.size()));
                Location playerLoc = targetPlayer.getLocation();

                // --- ACCURACY BEHAVIOR CHANGE ---
                // 2. Replaced static spread with the randomized accuracy logic from the reference.
                
                // First, calculate a randomized base spread ('chance' from the example).
                int baseSpread = ThreadLocalRandom.current().nextInt(50) + 3; // Equivalent to UtilMath.r(50) + 3

                // Next, calculate the final spread for this specific strike. As accuracyFactor -> 1.0, this value -> 1.
                int finalSpread = (int) Math.max(1.0, baseSpread - (this.accuracyFactor * baseSpread));

                // Finally, calculate the random offset. The range is [-finalSpread, finalSpread-1].
                // This precisely mimics the `UtilMath.r(accuracy * 2) - accuracy` logic.
                int offsetX = (finalSpread > 0) ? ThreadLocalRandom.current().nextInt(finalSpread * 2) - finalSpread : 0;
                int offsetZ = (finalSpread > 0) ? ThreadLocalRandom.current().nextInt(finalSpread * 2) - finalSpread : 0;
                // --- END OF CHANGES ---

                // Your existing, robust clamping and validation logic is preserved.
                double targetX = Math.max(minX + 0.5, Math.min(playerLoc.getX() + offsetX, maxX - 0.5));
                double targetZ = Math.max(minZ + 0.5, Math.min(playerLoc.getZ() + offsetZ, maxZ - 0.5));

                Location potentialLoc = new Location(world, targetX, playerLoc.getY() + 10, targetZ);
                Location finalLoc = world.getHighestBlockAt(potentialLoc).getLocation().add(0.5, 0, 0.5);

                if (finalLoc.getY() >= game.getInitialMapMinY() && finalLoc.getY() <= game.getInitialMapMaxY()) {
                    Block block = finalLoc.getBlock();
                    if (!block.isEmpty() && !block.isLiquid() && block.getType() != Material.BEDROCK) {
                        if (finalLoc.getX() >= minX && finalLoc.getX() < maxX && finalLoc.getZ() >= minZ && finalLoc.getZ() < maxZ) {
                            return finalLoc;
                        }
                    }
                }
            }
        }

        // --- Fallback Logic (Unchanged) ---
        // This section remains the same, providing a random location if no player-based spot was found.
        for (int attempt = 0; attempt < 10; attempt++) {
            double randomX = minX + ThreadLocalRandom.current().nextDouble(maxX - minX);
            double randomZ = minZ + ThreadLocalRandom.current().nextDouble(maxZ - minZ);

            Location potentialLoc = new Location(world, randomX, game.getInitialMapMaxY(), randomZ);
            Location finalLoc = world.getHighestBlockAt(potentialLoc).getLocation().add(0.5, 0, 0.5);

            if (finalLoc.getY() >= game.getInitialMapMinY() && finalLoc.getY() <= game.getInitialMapMaxY()) {
                Block block = finalLoc.getBlock();
                if (!block.isEmpty() && !block.isLiquid() && block.getType() != Material.BEDROCK) {
                    if (finalLoc.getX() >= minX && finalLoc.getX() < maxX && finalLoc.getZ() >= minZ && finalLoc.getZ() < maxZ) {
                        return finalLoc;
                    }
                }
            }
        }

        // Final fallback to the center of the map.
        Location centerLoc = new Location(world, (minX + maxX) / 2.0, game.getInitialMapMaxY(), (minZ + maxZ) / 2.0);
        Location highestCenter = world.getHighestBlockAt(centerLoc).getLocation().add(0.5, 0.5, 0.5);
        if (highestCenter.getX() >= minX && highestCenter.getX() < maxX && highestCenter.getZ() >= minZ && highestCenter.getZ() < maxZ &&
                highestCenter.getY() >= game.getInitialMapMinY() && highestCenter.getY() <= game.getInitialMapMaxY() &&
                !highestCenter.getBlock().isEmpty() && !highestCenter.getBlock().isLiquid()) {
            return highestCenter;
        }

        return null;
    }

    protected void damage(LivingEntity entity, DamageTick tick) {
        getGame().getPlugin().getDamageManager().damage(entity, tick);
    }

    public String getName() {
        return name;
    }

    public Set<SpellType> getBuffedSpells() {
        return buffedSpells;
    }

    public List<String> getMessages() {
        return messages;
    }

    protected Instant getLastStrike() {
        return lastStrike;
    }

    protected float getSize() {
        return size;
    }

    public void setSize(float size) {
        this.size = size;
    }

    protected Wizards getGame() {
        return game;
    }
}