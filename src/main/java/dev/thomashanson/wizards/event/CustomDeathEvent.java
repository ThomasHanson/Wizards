package dev.thomashanson.wizards.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.game.manager.DamageManager;

/**
 * Fired when a {@link LivingEntity} is killed by the custom {@link DamageManager}.
 * This event is fired *after* the death message has been determined and broadcast,
 * and serves as a notification that a custom-tracked death has occurred.
 *
 * @see dev.thomashanson.wizards.game.listener.DeathListener
 */
public class CustomDeathEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity victim;
    private final DamageTick damageTick;

    private boolean cancelled;

    /**
     * Creates a new CustomDeathEvent.
     *
     * @param victim     The {@link LivingEntity} that died.
     * @param damageTick The final {@link DamageTick} that caused the death.
     */
    public CustomDeathEvent(LivingEntity victim, DamageTick damageTick) {
        this.victim = victim;
        this.damageTick = damageTick;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * @return The {@link LivingEntity} that died.
     */
    public LivingEntity getVictim() {
        return victim;
    }

    /**
     * @return The final {@link DamageTick} that caused the death.
     */
    public DamageTick getDamageTick() {
        return damageTick;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}