package dev.thomashanson.wizards.event;

import dev.thomashanson.wizards.game.potion.PotionType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import java.time.Instant;

public class PotionConsumeEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private PotionType potion;
    private Instant instant;

    private boolean cancelled;

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

    public PotionType getPotion() {
        return potion;
    }

    public void setPotion(PotionType potion) {
        this.potion = potion;
    }

    public Instant getInstant() {
        return instant;
    }

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