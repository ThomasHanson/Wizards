package dev.thomashanson.wizards.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import dev.thomashanson.wizards.game.spell.Spell;

/**
 * Called just before a player successfully casts a spell and the mana cost is deducted.
 * <p>
 * This event allows for the modification of the spell being cast or its mana cost.
 * Cancelling this event will prevent the spell from activating and the mana from being spent.
 */
public class SpellCastEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private Spell spell;
    private float manaMultiplier = 1.0F;
    private boolean cancelled;

    public SpellCastEvent(Player who, Spell spell) {
        super(who);
        this.spell = spell;
        // DO NOT add logging or other logic to an event's constructor.
        // Listeners should handle all actions based on the event.
    }

    /**
     * @return The spell that is being cast.
     */
    public Spell getSpell() {
        return spell;
    }

    /**
     * Sets the spell to be cast. This can be used to upgrade, downgrade,
     * or otherwise change the spell that will activate.
     *
     * @param spell The new spell.
     */
    public void setSpell(Spell spell) {
        this.spell = spell;
    }

    /**
     * Gets the mana cost multiplier for this spell cast.
     * Default is 1.0.
     *
     * @return The mana cost multiplier.
     */
    public float getManaMultiplier() {
        return manaMultiplier;
    }

    /**
     * Sets the mana cost multiplier. For example, a value of 0.5 would
     * halve the mana cost, while a value of 2.0 would double it.
     *
     * @param manaMultiplier The new mana cost multiplier.
     */
    public void setManaMultiplier(float manaMultiplier) {
        this.manaMultiplier = manaMultiplier;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}