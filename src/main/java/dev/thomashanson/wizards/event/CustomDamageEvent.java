package dev.thomashanson.wizards.event;

import dev.thomashanson.wizards.damage.DamageTick;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

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
        return damageTick.getDamage();
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