package dev.thomashanson.wizards.tutorial;

import org.bukkit.Location;

/**
 * Represents a single, pre-built tutorial instance room. This class holds the
 * key locations within that room and tracks its availability for players.
 */
public class TutorialRoom {

    // Final fields are set once in the constructor and never change.
    private final Location playerSpawn;
    private final Location dummySpawn;
    private final Location chestLocation;

    // This tracks the state of the room. 'true' means a player can use it.
    private boolean isAvailable;

    /**
     * Constructs a new TutorialRoom instance.
     *
     * @param playerSpawn   The location where the player will be teleported upon starting.
     * @param dummySpawn    The location where the training dummy will spawn.
     * @param chestLocation The location where the tutorial chest will be placed.
     */
    public TutorialRoom(Location playerSpawn, Location dummySpawn, Location chestLocation) {
        this.playerSpawn = playerSpawn;
        this.dummySpawn = dummySpawn;
        this.chestLocation = chestLocation;
        this.isAvailable = true; // Rooms are available by default when the server starts.
    }

    /**
     * Resets the room to its original state so it can be used again.
     * In a more complex system, this method would also contain logic to
     * respawn the dummy and replace the chest. For now, it just marks it as available.
     */
    public void reset() {
        // The TutorialManager will handle the actual entity/block resetting logic.
        // This method's primary job is to flip the availability flag.
        this.isAvailable = true;
    }

    /**
     * Checks if the tutorial room is currently available for a player to use.
     *
     * @return true if the room is available, false otherwise.
     */
    public boolean isAvailable() {
        return isAvailable;
    }

    /**
     * Sets the availability of the room.
     *
     * @param available true to mark the room as available, false to mark it as in-use.
     */
    public void setAvailable(boolean available) {
        isAvailable = available;
    }

    /**
     * Gets the spawn location for the player inside this tutorial room.
     *
     * @return The player spawn location.
     */
    public Location getPlayerSpawn() {
        return playerSpawn;
    }

    /**
     * Gets the spawn location for the training dummy inside this tutorial room.
     *
     * @return The dummy's spawn location.
     */
    public Location getDummySpawn() {
        return dummySpawn;
    }

    /**
     * Gets the location for the tutorial chest inside this tutorial room.
     *
     * @return The chest's location.
     */
    public Location getChestLocation() {
        return chestLocation;
    }
}