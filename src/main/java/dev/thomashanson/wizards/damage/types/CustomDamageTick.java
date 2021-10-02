package dev.thomashanson.wizards.damage.types;

import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.game.manager.DamageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.time.Instant;

public class CustomDamageTick extends PlayerDamageTick {

    private final EntityDamageEvent.DamageCause cause;

    public CustomDamageTick(double damage, EntityDamageEvent.DamageCause cause, String name, Instant timestamp, Player damager) {
        super(damage, name, timestamp, damager);
        this.cause = cause;
    }

    @Override
    public boolean matches(DamageTick tick) {
        return (tick instanceof CustomDamageTick) && tick.getReason().equals(getReason());
    }

    @Override
    public String getDeathMessage(Player player) {
        return DamageManager.ACCENT_COLOR + player.getDisplayName() + DamageManager.BASE_COLOR + " was killed by " + DamageManager.ACCENT_COLOR + getReason();
    }

    @Override
    public String getSingleLineSummary() {
        return DamageManager.ACCENT_COLOR + getReason() + DamageManager.BASE_COLOR + " damage";
    }

    @Override
    public EntityDamageEvent.DamageCause getCause() {
        return cause;
    }
}