package dev.thomashanson.wizards.util.menu.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/**
 * Listener for menu interactions
 */
public interface InventoryMenuListener {

    /**
     * Called when a player clicks the inventory
     *
     * @param player {@link Player} who clicked
     * @param action the {@link ClickType} performed
     * @param slot   the clicked slot
     */
    void onInteract(Player player, ClickType action, int slot);
}