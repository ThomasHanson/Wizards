package dev.thomashanson.wizards.damage.types;

import java.text.DecimalFormat;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.Nullable;

import dev.thomashanson.wizards.damage.DamageConfig;
import dev.thomashanson.wizards.game.manager.DamageManager;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class PlayerDamageTick extends MonsterDamageTick {

    protected static final DecimalFormat DISTANCE_FORMAT = new DecimalFormat("#.#");

    public PlayerDamageTick(double damage, EntityDamageEvent.DamageCause cause, String reason, Instant timestamp, Player attacker, @Nullable Double distanceVal) {
        super(damage, cause, reason, timestamp, attacker, distanceVal);
    }

    public Player getPlayer() {
        return (Player) getEntity();
    }

    @Override
    public Component getDeathMessage(Player victim, LanguageManager lang, DamageManager damageManager) {
        Component victimName = victim.displayName().color(NamedTextColor.RED);
        Component attackerName = getPlayer().displayName().color(NamedTextColor.RED);
        DamageConfig.PvpConfig pvpConfig = damageManager.getConfig().deathMessages().pvp();

        if (isRanged()) {
            String distanceStr = DISTANCE_FORMAT.format(getDistance());
            return lang.getTranslated(victim, pvpConfig.rangedFormat(),
                Placeholder.component("victim_name", victimName),
                Placeholder.component("attacker_name", attackerName),
                Placeholder.unparsed("distance", distanceStr)
            );

        } else {
            List<String> verbs = pvpConfig.melee().verbs();
            if (verbs.isEmpty()) { // Safety fallback
                 return lang.getTranslated(victim, damageManager.getConfig().deathMessages().defaultMessage(),
                    Placeholder.component("victim_name", victimName),
                    Placeholder.unparsed("reason", "melee")
                );
            }
            
            String verbKey = verbs.get(ThreadLocalRandom.current().nextInt(verbs.size()));
            Component verb = lang.getTranslated(victim, verbKey);

            return lang.getTranslated(victim, pvpConfig.melee().format(),
                Placeholder.component("victim_name", victimName),
                Placeholder.component("verb", verb),
                Placeholder.component("attacker_name", attackerName)
            );
        }
    }

    @Override
    public Component getSingleLineSummary(Player viewer, LanguageManager lang, DamageManager damageManager) {
        Component attackerName = getPlayer().displayName().color(NamedTextColor.RED);
        String healthStr = DISTANCE_FORMAT.format(getPlayer().getHealth());

        if (isRanged()) {
            String distanceStr = DISTANCE_FORMAT.format(getDistance());
            return lang.getTranslated(viewer, "wizards.damage.summary.player.ranged",
                Placeholder.component("attacker_name", attackerName),
                Placeholder.unparsed("distance", distanceStr),
                Placeholder.unparsed("health", healthStr)
            );
        } else {
            return lang.getTranslated(viewer, "wizards.damage.summary.player.melee",
                Placeholder.component("attacker_name", attackerName),
                Placeholder.unparsed("health", healthStr)
            );
        }
    }
}