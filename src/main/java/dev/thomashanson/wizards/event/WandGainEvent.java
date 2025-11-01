package dev.thomashanson.wizards.event;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired when a player picks up a new wand item (from a chest or the ground),
 * unlocking a new wand slot.
 * <p>
 * This event can be cancelled to prevent the player from gaining the new wand.
 */
public class WandGainEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private boolean cancelled;

    /**
     * Creates a new WandGainEvent.
     *
     * @param who The player gaining the wand.
     */
    public WandGainEvent(Player who) {
        super(who);
        Bukkit.getLogger().info(player.getName() + " picked up a new wand");
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