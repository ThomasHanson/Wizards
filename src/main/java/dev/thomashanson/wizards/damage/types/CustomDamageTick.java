package dev.thomashanson.wizards.damage.types;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.Nullable;

import dev.thomashanson.wizards.damage.DamageConfig;
import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.game.manager.DamageManager;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class CustomDamageTick extends PlayerDamageTick {

    public CustomDamageTick(double damage, EntityDamageEvent.DamageCause cause, String reason, Instant timestamp, Player damager, @Nullable Double distanceVal) {
        super(damage, cause, reason, timestamp, damager, distanceVal);
    }

    @Override
    public boolean matches(DamageTick tick) {
        return tick instanceof CustomDamageTick other && super.matches(other) && this.getReason().equals(other.getReason());
    }

    @Override
    public Component getDeathMessage(Player victim, LanguageManager lang, DamageManager damageManager) {
        Player attacker = getPlayer();
        Component victimName = victim.displayName().color(NamedTextColor.RED);
        Component attackerName = attacker.displayName().color(NamedTextColor.RED);
        Component spellName = lang.getTranslated(victim, getReason());
        
        DamageConfig.BySpellConfig spellMsgConfig = damageManager.getConfig().deathMessages().bySpell();

        if (victim.equals(attacker)) {
            return lang.getTranslated(victim, spellMsgConfig.suicideFormat(),
                Placeholder.component("victim_name", victimName),
                Placeholder.component("spell_name", spellName)
            );
        }

        // Check for a spell-specific override message first.
        String overrideKey = spellMsgConfig.overrides().get(getReason());
        if (overrideKey != null) {
            return lang.getTranslated(victim, overrideKey,
                Placeholder.component("victim_name", victimName),
                Placeholder.component("attacker_name", attackerName)
            );
        }
        
        // Fallback to generic spell death message with a random verb.
        List<String> verbs = spellMsgConfig.generic().verbs();
        if (verbs.isEmpty()) { // Safety check
             return lang.getTranslated(victim, damageManager.getConfig().deathMessages().defaultMessage(),
                Placeholder.component("victim_name", victimName),
                Placeholder.unparsed("reason", "a powerful spell")
            );
        }

        String verbKey = verbs.get(ThreadLocalRandom.current().nextInt(verbs.size()));
        Component verb = lang.getTranslated(victim, verbKey);
        
        return lang.getTranslated(victim, spellMsgConfig.generic().format(),
            Placeholder.component("victim_name", victimName),
            Placeholder.component("verb", verb),
            Placeholder.component("attacker_name", attackerName),
            Placeholder.component("spell_name", spellName)
        );
    }

    @Override
    public Component getSingleLineSummary(Player viewer, LanguageManager lang, DamageManager damageManager) {
        Component damageSource = lang.getTranslated(viewer, getReason());
        Component attackerName = getPlayer().displayName().color(NamedTextColor.RED);

        return lang.getTranslated(viewer, "wizards.damage.summary.by",
            Placeholder.component("damage_source", damageSource),
            Placeholder.component("attacker_name", attackerName)
        );
    }
}