package dev.thomashanson.wizards.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Event called when a player equips or unequips a piece of armor.
 * This event is fired for various equip methods, including hotbar, drag-and-drop, and dispenser.
 */
public class ArmorEquipEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private boolean cancelled = false;
    private final EquipMethod equipType;
    private ItemStack oldArmorPiece, newArmorPiece;

    /**
     * Creates a new ArmorEquipEvent.
     *
     * @param player        The player equipping or unequipping armor.
     * @param equipType     The {@link EquipMethod} used.
     * @param oldArmorPiece The ItemStack that was in the slot (may be null or AIR).
     * @param newArmorPiece The ItemStack being placed in the slot (may be null or AIR).
     */
    public ArmorEquipEvent(final Player player, final EquipMethod equipType, final ItemStack oldArmorPiece, final ItemStack newArmorPiece) {

        super(player);

        this.equipType = equipType;
        this.oldArmorPiece = oldArmorPiece;
        this.newArmorPiece = newArmorPiece;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public final ItemStack getOldArmorPiece() {
        return oldArmorPiece;
    }

    public final void setOldArmorPiece(final ItemStack oldArmorPiece) {
        this.oldArmorPiece = oldArmorPiece;
    }

    public final ItemStack getNewArmorPiece() {
        return newArmorPiece;
    }

    public final void setNewArmorPiece(final ItemStack newArmorPiece) {
        this.newArmorPiece = newArmorPiece;
    }

    public EquipMethod getMethod() {
        return equipType;
    }

    /**
     * Describes the method used to equip or unequip the armor.
     */
    public enum EquipMethod {

        /**
         * When you shift click an armor piece to equip or un-equip.
         */
        SHIFT_CLICK,

        /**
         * When you drag and drop the item to equip or un-equip.
         */
        DRAG,

        /**
         * When you manually equip or unequip the item.
         */
        PICK_DROP,

        /**
         * When you right click an armor piece in the hotbar without the inventory open to equip.
         */
        HOTBAR,

        /**
         * When you press the hotbar slot number while hovering over the armor slot to equip or un-equip.
         */
        HOTBAR_SWAP,

        /**
         * When in range of a dispenser that shoots an armor piece to equip.
         */
        DISPENSER,

        /**
         * When an armor piece is removed due to it losing all durability.
         */
        BROKE,

        /**
         * When you die causing all armor to un-equip.
         */
        DEATH
    }
}