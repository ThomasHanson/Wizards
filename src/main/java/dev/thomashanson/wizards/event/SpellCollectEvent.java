package dev.thomashanson.wizards.event;

import dev.thomashanson.wizards.game.spell.SpellType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public class SpellCollectEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private SpellType spell;
    private float manaGain;

    private boolean cancelled;

    public SpellCollectEvent(Player who, SpellType spell) {

        super(who);
        this.spell = spell;

        Bukkit.getLogger().info(player.getName() + " collected " + spell.getSpellName() + " spell");
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public SpellType getSpell() {
        return spell;
    }

    public void setSpell(SpellType spell) {
        this.spell = spell;
    }

    public float getManaGain() {
        return manaGain;
    }

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