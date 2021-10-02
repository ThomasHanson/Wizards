package dev.thomashanson.wizards.damage;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public abstract class DamageTick implements Comparable<DamageTick> {

    private double damage;
    private final EntityDamageEvent.DamageCause cause;
    private final String reason;
    private Instant timestamp;

    private Map<String, Double> damageMods = new HashMap<>();

    private Location knockbackOrigin;
    private Map<String, Double> knockbackMods = new HashMap<>();

    protected DamageTick(double damage, EntityDamageEvent.DamageCause cause, String reason, Instant timestamp) {
        this.damage = damage;
        this.cause = cause;
        this.reason = reason;
        this.timestamp = timestamp;
    }

    @Override
    public int compareTo(DamageTick tick) {
        return timestamp.compareTo(tick.getTimestamp());
    }

    public abstract boolean matches(DamageTick tick);

    public abstract String getDeathMessage(Player player);

    public abstract String getSingleLineSummary();

    public String timeDiff() {

        Instant now = Instant.now();
        Instant then = getTimestamp();

        long difference = Duration.between(then, now).toMillis();

        if (difference < 1500) {
            return "just now";

        } else {
            difference = difference / 1000;
            return difference + "s prior";
        }
    }

    public double getDamage() {

        double damage = this.damage;

        for (double multiplier : damageMods.values())
            damage *= multiplier;

        return damage;
    }

    public void setDamage(double damage) {
        this.damage = damage;
    }

    public void addDamage(String reason, double amount) {
        damageMods.put(reason, amount);
    }

    public EntityDamageEvent.DamageCause getCause() {
        return cause;
    }

    public String getReason() {

        /*
        StringBuilder reason = new StringBuilder(damageMods.isEmpty() ? this.reason : "");

        for (String changeReason : damageMods.keySet())
            reason.append(ChatColor.GREEN).append(changeReason).append(ChatColor.GRAY).append(", ");

        if (reason.length() > 0) {
            reason = new StringBuilder(reason.substring(0, reason.length() - 2));
            return reason.toString();
        }

        return reason.toString();
         */

        return reason;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Location getKnockbackOrigin() {
        return knockbackOrigin;
    }

    public void setKnockbackOrigin(Location knockbackOrigin) {
        this.knockbackOrigin = knockbackOrigin;
    }

    public void addKnockback(String reason, double amount) {
        knockbackMods.put(reason, amount);
    }

    public Map<String, Double> getKnockbackMods() {
        return knockbackMods;
    }
}