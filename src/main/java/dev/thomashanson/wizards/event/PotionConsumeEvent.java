package dev.thomashanson.wizards.event;

import java.time.Instant;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import dev.thomashanson.wizards.game.potion.PotionType;

/**
 * Fired when a player successfully consumes a custom Wizards potion.
 * This event is fired *after* the {@link org.bukkit.event.player.PlayerItemConsumeEvent}
 * and allows other systems to react to the application of a custom potion effect.
 */
public class PotionConsumeEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private PotionType potion;
    private Instant instant;

    private boolean cancelled;

    /**
     * Creates a new PotionConsumeEvent.
     *
     * @param who    The player who consumed the potion.
     * @param potion The {@link PotionType} that was consumed.
     */
    public PotionConsumeEvent(Player who, PotionType potion) {

        super(who);

        this.potion = potion;
        this.instant = Instant.now();

        Bukkit.getLogger().info(player.getName() + " drank " + potion.getPotionName());
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * @return The {@link PotionType} that was consumed.
     */
    public PotionType getPotion() {
        return potion;
    }

    /**
     * Sets the {@link PotionType} for this event.
     *
     * @param potion The new potion type.
     */
    public void setPotion(PotionType potion) {
        this.potion = potion;
    }

    /**
     * @return The {@link Instant} the potion was consumed.
     */
    public Instant getInstant() {
        return instant;
    }

    /**
     * Sets the timestamp for this event.
     *
     * @param instant The new {@link Instant}.
     */
    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}