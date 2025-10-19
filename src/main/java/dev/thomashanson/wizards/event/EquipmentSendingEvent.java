package dev.thomashanson.wizards.event;


import com.comphenix.protocol.wrappers.EnumWrappers;
import com.google.common.base.Preconditions;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;

public class EquipmentSendingEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity visibleEntity;
    private EnumWrappers.ItemSlot slot;
    private ItemStack equipment;

    private boolean cancelled;

    public EquipmentSendingEvent(Player client, LivingEntity visibleEntity, EnumWrappers.ItemSlot slot, ItemStack equipment) {

        super(client);

        this.visibleEntity = visibleEntity;
        this.slot = slot;
        this.equipment = equipment;
    }

    /**
     * Retrieve the entity whose armor or held item we are updating.
     * @return The visible entity.
     */
    public LivingEntity getVisibleEntity() {
        return visibleEntity;
    }

    /**
     * @return The equipment
     */
    public ItemStack getEquipment() {
        return equipment;
    }

    /**
     * Set the equipment we will send to the player.
     * @param equipment - the equipment, or NULL to sent air.
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
