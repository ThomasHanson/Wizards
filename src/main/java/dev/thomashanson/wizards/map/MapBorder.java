package dev.thomashanson.wizards.map;

import org.bukkit.Location;
import org.bukkit.WorldBorder;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.util.BoundingBox;

import dev.thomashanson.wizards.game.Wizards;

/**
 * Manages the world border for a Wizards game, dynamically resizing it
 * based on the number of players remaining.
 */
public class MapBorder implements Listener {

    // TODO: These values should be loaded from a configuration file for balancing.
    private static final double BORDER_SHRINK_RATE = 60.0;
    private static final double BORDER_PLAYER_SCALE_FACTOR = 61.0;

    private final Wizards game;
    private final int maxPlayers;
    private final double initialBorderSize;

    public MapBorder(Wizards game) {
        this.game = game;
        this.maxPlayers = game.getCurrentMode().getMaxPlayers();

        LocalGameMap map = game.getActiveMap();
        Location center = map.getSpectatorLocation();
        BoundingBox bounds = map.getBounds();

        // Find the largest distance from the center to any horizontal edge of the map.
        double maxDistX = Math.max(bounds.getMaxX() - center.getX(), center.getX() - bounds.getMinX());
        double maxDistZ = Math.max(bounds.getMaxZ() - center.getZ(), center.getZ() - bounds.getMinZ());
        double largestRadius = Math.max(maxDistX, maxDistZ);

        // The border size is a diameter, so it must be twice the largest radius to enclose the map.
        this.initialBorderSize = largestRadius * 2;

        WorldBorder border = map.getWorld().getWorldBorder();
        border.setCenter(center);
        border.setSize(initialBorderSize);
        border.setDamageAmount(0.2); // Increased slightly for more threat
        border.setDamageBuffer(0);
        border.setWarningDistance(10);
    }

    /**
     * Updates the border size based on the current number of players.
     * The border shrinks more slowly as fewer players remain.
     *
     * @param numPlayers The number of players currently alive.
     */
    public void updateBorderSize(int numPlayers) {
        WorldBorder border = game.getActiveMap().getWorld().getWorldBorder();

        // Formula calculates the time (in seconds) it should take to shrink to the new size.
        // It's based on the ratio of current border size to the total player scale,
        // multiplied by the current player count and a rate constant.
        long shrinkDurationSeconds = (long) (
            (border.getSize() / BORDER_PLAYER_SCALE_FACTOR) *
            (numPlayers * (maxPlayers / BORDER_PLAYER_SCALE_FACTOR)) *
            BORDER_SHRINK_RATE
        );

        if (shrinkDurationSeconds <= 0) {
            return; // Avoid issues with instant shrinking
        }

        // The target size is the initial size scaled by the fraction of players remaining.
        double targetSize = initialBorderSize * ((double) numPlayers / maxPlayers);
        
        border.setSize(targetSize, shrinkDurationSeconds);
    }

    /**
     * Resets the border and unregisters this listener when the game ends.
     */
    public void handleEnd() {
        if (game.getActiveMap() != null && game.getActiveMap().isLoaded()) {
            WorldBorder border = game.getActiveMap().getWorld().getWorldBorder();
            border.setSize(initialBorderSize * 2); // Expand border out of the way
        }
        HandlerList.unregisterAll(this);
    }
}