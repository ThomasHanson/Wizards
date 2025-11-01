package dev.thomashanson.wizards.damage.types;

import java.text.DecimalFormat;
import java.time.Instant;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.game.manager.DamageManager;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/**
 * A {@link DamageTick} implementation for damage caused by falling.
 * It specifically stores the distance fallen to include in the death message.
 */
public class FallDamageTick extends DamageTick {

    private static final DecimalFormat DISTANCE_FORMAT = new DecimalFormat("#");

    /**
     * The distance, in blocks, that the player fell.
     */
    private final double distance;

    /**
     * Creates a new damage tick caused by falling.
     *
     * @param damage       The amount of damage dealt.
     * @param timestamp    The time the damage occurred.
     * @param fallDistance The distance the player fell.
     */
    public FallDamageTick(double damage, Instant timestamp, float fallDistance) {
        super(damage, EntityDamageEvent.DamageCause.FALL, "Fall", timestamp);
        this.distance = fallDistance;
    }

    @Override
    public boolean matches(DamageTick tick) {
        return false; // Each fall is a unique event
    }

    @Override
    public Component getDeathMessage(Player victim, LanguageManager lang, DamageManager damageManager) {
        Component victimName = victim.displayName().color(NamedTextColor.RED);
        String distanceStr = DISTANCE_FORMAT.format(this.distance);
        String messageKey = damageManager.getConfig().deathMessages().fall();

        return lang.getTranslated(victim, messageKey,
            Placeholder.component("victim_name", victimName),
            Placeholder.unparsed("distance", distanceStr)
        );
    }

    @Override
    public Component getSingleLineSummary(Player viewer, LanguageManager lang, DamageManager damageManager) {
        String distanceStr = DISTANCE_FORMAT.format(this.distance);
        return lang.getTranslated(viewer, "wizards.damage.summary.fall",
            Placeholder.unparsed("distance", distanceStr)
        );
    }
}