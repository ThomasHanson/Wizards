package dev.thomashanson.wizards.event;


import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;

import com.comphenix.protocol.wrappers.EnumWrappers;
import com.google.common.base.Preconditions;

/**
 * Fired when the server is about to send an equipment packet (armor or held item)
 * for any {@link LivingEntity} to a specific {@link Player}.
 * <p>
 * This event allows for modifying the {@link ItemStack} that a player sees
 * on another entity, enabling effects like "invisibility" armor or custom
 * cosmetic appearances without modifying the underlying entity's actual equipment.
 * <p>
 * This event is fired via ProtocolLib and is asynchronous by nature.
 */
public class EquipmentSendingEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity visibleEntity;
    private EnumWrappers.ItemSlot slot;
    private ItemStack equipment;

    private boolean cancelled;

    /**
     * Creates a new EquipmentSendingEvent.
     *
     * @param client        The player who will *receive* the packet and see the equipment.
     * @param visibleEntity The entity whose equipment is being displayed.
     * @param slot          The {@link EnumWrappers.ItemSlot} being updated.
     * @param equipment     The {@link ItemStack} that is about to be sent.
     */
    public EquipmentSendingEvent(Player client, LivingEntity visibleEntity, EnumWrappers.ItemSlot slot, ItemStack equipment) {

        super(client);

        this.visibleEntity = visibleEntity;
        this.slot = slot;
        this.equipment = equipment;
    }

    /**
     * Retrieve the entity whose armor or held item is being updated for the client.
     *
     * @return The entity being rendered.
     */
    public LivingEntity getVisibleEntity() {
        return visibleEntity;
    }

    /**
     * Gets the {@link ItemStack} that will be sent to the client.
     *
     * @return The equipment ItemStack.
     */
    public ItemStack getEquipment() {
        return equipment;
    }

    /**
     * Sets the {@link ItemStack} that will be sent to the client.
     *
     * @param equipment The new equipment to send, or null to send air.
     */
    public void setEquipment(ItemStack equipment) {
        this.equipment = equipment;
    }

    /**
     * Retrieve the slot of this equipment.
     * @return The slot.
     */
    public EnumWrappers.ItemSlot getSlot() {
        return slot;
    }

    /**
     * Set the slot of this equipment.
     * @param slot - the slot.
     */
    public void setSlot(EnumWrappers.ItemSlot slot) {
        this.slot = Preconditions.checkNotNull(slot, "slot cannot be null");
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
}
