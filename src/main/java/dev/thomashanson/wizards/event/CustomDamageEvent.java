package dev.thomashanson.wizards.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import dev.thomashanson.wizards.damage.DamageTick;

/**
 * Fired immediately before a {@link DamageTick} is applied to a {@link LivingEntity}.
 * <p>
 * This event is the primary API endpoint for modifying or canceling damage dealt
 * by the custom {@link DamageManager}. It allows other systems (spells, potions,
 * kits) to intercept a damage event and apply modifiers, such as:
 * <ul>
 * <li>Increasing damage (e.g., Focus spell).</li>
 * <li>Decreasing damage (e.g., Iron potion).</li>
 * <li>Canceling the damage entirely.</li>
 * </ul>
 * This event is fired *after* initial armor/enchantment calculations but *before*
 * Bukkit/potion resistance effects.
 *
 * @see DamageManager#damage(LivingEntity, DamageTick)
 */
public class CustomDamageEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity victim;
    private final DamageTick damageTick;

    private boolean cancelled;

    public CustomDamageEvent(LivingEntity victim, DamageTick damageTick) {
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

    public LivingEntity getVictim() {
        return victim;
    }

    public DamageTick getDamageTick() {
        return damageTick;
    }

    public double getDamage() {
        return damageTick.getFinalDamage();
    }

    public void setDamage(double damage) {
        damageTick.setDamage(damage);
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}