package dev.thomashanson.wizards.damage.types;

import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.game.manager.DamageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.text.DecimalFormat;
import java.time.Instant;

public class FallDamageTick extends DamageTick {

    private final double distance;

    public FallDamageTick(double damage, String name, Instant timestamp, double distance) {
        super(damage, EntityDamageEvent.DamageCause.FALL, name, timestamp);
        this.distance = distance;
    }

    private double getDistance() {
        return distance;
    }

    @Override
    public boolean matches(DamageTick tick) {
        return false;
    }

    @Override
    public String getDeathMessage(Player player) {
        DecimalFormat decimalFormat = new DecimalFormat("#");
        return DamageManager.ACCENT_COLOR + player.getDisplayName() + DamageManager.BASE_COLOR + " was killed by " + DamageManager.ACCENT_COLOR + decimalFormat.format(getDistance()) + DamageManager.BASE_COLOR + " block fall";
    }

    @Override
    public String getSingleLineSummary() {
        DecimalFormat decimalFormat = new DecimalFormat("#");
        return DamageManager.BASE_COLOR + "Fell " + DamageManager.ACCENT_COLOR + decimalFormat.format(getDistance()) + DamageManager.BASE_COLOR + " blocks";
    }
}