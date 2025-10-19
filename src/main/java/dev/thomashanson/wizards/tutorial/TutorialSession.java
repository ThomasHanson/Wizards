package dev.thomashanson.wizards.tutorial;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import net.kyori.adventure.text.Component;

/**
 * Manages the state and progress of a single player's interactive tutorial session.
 * This includes inventory management, step tracking, and player state changes.
 */
public class TutorialSession {

    // Using final fields ensures these are set once and are central to the session's identity.
    private final UUID playerUUID;
    private final TutorialRoom room;

    // This field holds the player's inventory as it was before the tutorial started.
    private ItemStack[] savedInventory;

    // Tracks the player's current stage in the tutorial.
    private TutorialStep currentStep;

    /**
     * Represents the distinct stages of the interactive tutorial.
     */
    public enum TutorialStep {
        LOOT_CHEST,
        EQUIP_SPELL,
        HIT_DUMMY,
        COMPLETED
    }

    /**
     * Constructs a new tutorial session for a player.
     *
     * @param playerUUID The unique ID of the player starting the session.
     * @param room       The specific TutorialRoom instance assigned to this player.
     */
    public TutorialSession(UUID playerUUID, TutorialRoom room) {
        this.playerUUID = playerUUID;
        this.room = room;
        this.currentStep = TutorialStep.LOOT_CHEST; // The tutorial always starts at the first step.
    }

    /**
     * Initiates the tutorial session for the player.
     * This method handles saving their state, teleporting them, and setting up the initial scene.
     */
    public void begin() {
        Player player = getPlayer();
        if (player == null) return; // Safety check in case the player logs off.

        // 1. Save the player's current inventory and clear it.
        this.savedInventory = player.getInventory().getContents().clone();
        player.getInventory().clear();

        // Give the player the exit item in their last hotbar slot
        ItemStack exitItem = ItemBuilder.from(Material.BARRIER)
            .name(Component.text("Exit Tutorial")) // Replace with LanguageManager if needed
            .build();
        player.getInventory().setItem(8, exitItem);
        
        // Spawn the dummy armor stand
        ArmorStand dummy = room.getDummySpawn().getWorld().spawn(room.getDummySpawn(), ArmorStand.class);
        dummy.setGravity(false);
        dummy.setInvulnerable(true);
        // Set armor, head, etc. on the dummy here

        // 2. Set the player's gamemode and teleport them into the tutorial room.
        player.setGameMode(GameMode.ADVENTURE);
        player.teleport(room.getPlayerSpawn());

        // 3. Set up the initial state of the room (e.g., place the chest).
        // The chest should contain one Mana Bolt spell.
        room.getChestLocation().getBlock().setType(Material.CHEST);
        // NOTE: You would add the Mana Bolt item to the chest's inventory here.

        // 4. Give the player their starting items for the tutorial (one wand).
        // NOTE: You would add your custom "blank wand" item here.
        // player.getInventory().addItem(yourWandItem);

        // 5. Provide the first instruction to the player.
        player.sendTitle("§aWelcome to the Tutorial!", "§eLoot the chest to get your first spell.", 10, 70, 20);
    }

    /**
     * Concludes the tutorial session for the player.
     * This method handles restoring their state and teleporting them back to the lobby.
     *
     * @param spawnLocation The location to teleport the player back to in the main lobby.
     */
    public void end(Location spawnLocation) {
        Player player = getPlayer();
        if (player == null) return;

        // 1. Clear their tutorial inventory.
        player.getInventory().clear();

        // 2. Restore their original inventory.
        if (this.savedInventory != null) {
            player.getInventory().setContents(this.savedInventory);
        }

        // 3. Teleport them back to the main lobby spawn.
        player.teleport(spawnLocation);
    }

    /**
     * A helper method to safely get the Player object from the stored UUID.
     *
     * @return The Player object, or null if they are not online.
     */
    public Player getPlayer() {
        return Bukkit.getPlayer(playerUUID);
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }


    public TutorialRoom getRoom() {
        return room;
    }

    public TutorialStep getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(TutorialStep currentStep) {
        this.currentStep = currentStep;
    }
}