package dev.thomashanson.wizards.event;

import dev.thomashanson.wizards.game.spell.SpellType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public class SpellCastEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private SpellType spell;
    private float manaMultiplier = 1;

    private boolean cancelled;

    public SpellCastEvent(Player who, SpellType spell) {

        super(who);
        this.spell = spell;

        Bukkit.getLogger().info(player.getName() + " cast " + spell.getSpellName() + " spell");
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

    public float getManaMultiplier() {
        return manaMultiplier;
    }

    public void setManaMultiplier(float manaMultiplier) {
        this.manaMultiplier = manaMultiplier;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}