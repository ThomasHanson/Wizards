package dev.thomashanson.wizards.damage.types;

import java.time.Instant;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.game.manager.DamageManager;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class OtherDamageTick extends DamageTick {

    public OtherDamageTick(double damage, EntityDamageEvent.DamageCause cause, String reason, Instant timestamp) {
        super(damage, cause, reason, timestamp);
    }

    @Override
    public boolean matches(DamageTick tick) {
        return tick instanceof OtherDamageTick other &&
               getCause() == other.getCause() &&
               getReason().equals(other.getReason());
    }

    @Override
    public Component getDeathMessage(Player victim, LanguageManager lang, DamageManager damageManager) {
        Component victimName = victim.displayName().color(NamedTextColor.RED);

        // Prioritize a specific key from the config map, otherwise use the default.
        String messageKey = damageManager.getConfig().deathMessages().byCause()
            .getOrDefault(getCause().name(), damageManager.getConfig().deathMessages().defaultMessage());
        
        // The reason from the tick (e.g., "Drowned") is used as a fallback placeholder.
        return lang.getTranslated(victim, messageKey,
            Placeholder.component("victim_name", victimName),
            Placeholder.unparsed("reason", getReason().toLowerCase())
        );
    }

    @Override
    public Component getSingleLineSummary(Player viewer, LanguageManager lang, DamageManager damageManager) {
        // For the summary, we can simply show the reason text.
        return Component.text(getReason());
    }
}