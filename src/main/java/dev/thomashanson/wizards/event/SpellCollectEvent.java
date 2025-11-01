package dev.thomashanson.wizards.event;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import dev.thomashanson.wizards.game.spell.Spell;

/**
 * Fired when a player picks up a spell item (from a chest or the ground).
 * This event allows for modification of the mana gain (for duplicates)
 * or cancellation of the spell collection.
 */
public class SpellCollectEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private Spell spell;
    private float manaGain;

    private boolean cancelled;

    /**
     * Creates a new SpellCollectEvent.
     *
     * @param who   The player collecting the spell.
     * @param spell The {@link Spell} being collected.
     */
    public SpellCollectEvent(Player who, Spell spell) {

        super(who);
        this.spell = spell;

        Bukkit.getLogger().info(player.getName() + " collected " + spell.getName() + " spell");
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * @return The {@link Spell} being collected.
     */
    public Spell getSpell() {
        return spell;
    }

    /**
     * Sets the {@link Spell} for this event.
     *
     * @param spell The new spell.
     */
    public void setSpell(Spell spell) {
        this.spell = spell;
    }

    /**
     * @return The amount of mana to be gained (used for duplicate spells).
     */
    public float getManaGain() {
        return manaGain;
    }

    /**
     * Sets the amount of mana to be gained.
     *
     * @param manaGain The new mana amount.
     */
    public void setManaGain(float manaGain) {
        this.manaGain = manaGain;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}