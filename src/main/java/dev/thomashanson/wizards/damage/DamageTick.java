package dev.thomashanson.wizards.damage;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import dev.thomashanson.wizards.game.manager.DamageManager;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public abstract class DamageTick implements Comparable<DamageTick> {

    private double damage;
    private final EntityDamageEvent.DamageCause cause;
    private final String reason;
    private Instant timestamp;

    private final Map<String, Double> damageModifiers = new HashMap<>();
    private Location knockbackOrigin;
    private final Map<String, Double> knockbackModifiers = new HashMap<>();

    protected DamageTick(double damage, EntityDamageEvent.DamageCause cause, String reason, Instant timestamp) {
        this.damage = damage;
        this.cause = cause;
        this.reason = reason;
        this.timestamp = timestamp;
    }

    public abstract boolean matches(DamageTick other);

    public abstract Component getDeathMessage(Player victim, LanguageManager lang, DamageManager damageManager);

    public abstract Component getSingleLineSummary(Player viewer, LanguageManager lang, DamageManager damageManager);

    public Component getTimeDifferenceComponent(Player viewer, LanguageManager lang, DamageManager damageManager) {
        long differenceMillis = Duration.between(getTimestamp(), Instant.now()).toMillis();
        long justNowThreshold = damageManager.getConfig().justNowThresholdMillis();

        if (differenceMillis < justNowThreshold) {
            return lang.getTranslated(viewer, "wizards.time.just_now");
        }

        long seconds = differenceMillis / 1000;
        return lang.getTranslated(viewer, "wizards.time.seconds_prior",
            Placeholder.unparsed("seconds", String.valueOf(seconds))
        );
    }

    @Override
    public int compareTo(DamageTick o) {
        return this.timestamp.compareTo(o.getTimestamp());
    }

    public double getFinalDamage() {
        return damage * damageModifiers.values().stream().reduce(1.0, (a, b) -> a * b);
    }

    public void setDamage(double damage) {
        this.damage = damage;
    }

    public void addDamageModifier(String reason, double multiplier) {
        damageModifiers.put(reason, multiplier);
    }

    public EntityDamageEvent.DamageCause getCause() {
        return cause;
    }

    public String getReason() {
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

    public void addKnockbackModifier(String reason, double multiplier) {
        knockbackModifiers.put(reason, multiplier);
    }

    public Map<String, Double> getKnockbackModifiers() {
        return knockbackModifiers;
    }
}