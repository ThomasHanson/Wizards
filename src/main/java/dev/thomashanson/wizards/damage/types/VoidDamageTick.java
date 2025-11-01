package dev.thomashanson.wizards.damage.types;

import java.time.Instant;

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

/**
 * A specialized {@link DamageTick} for void damage.
 * This class stores a reference to the {@link DamageTick} that occurred *just before*
 * the void damage, allowing for contextual death messages (e.g., "knocked into the void").
 */
public class VoidDamageTick extends DamageTick {

    /**
     * The last damage event that happened before the void, potentially the
     * knockback source.
     */
    private final DamageTick previousTick;

    /**
     * Creates a new damage tick for void damage.
     *
     * @param damage       The amount of damage (usually very high).
     * @param timestamp    The time the damage occurred.
     * @param previousTick The last {@link DamageTick} recorded for the player, or null.
     */
    public VoidDamageTick(double damage, Instant timestamp, @Nullable DamageTick previousTick) {
        super(damage, EntityDamageEvent.DamageCause.VOID, "Void", timestamp);
        this.previousTick = previousTick;
    }

    @Override
    public boolean matches(DamageTick tick) {
        return false; // Void damage is a terminal event.
    }

    @Override
    public Component getDeathMessage(Player victim, LanguageManager lang, DamageManager damageManager) {
        Component victimName = victim.displayName().color(NamedTextColor.RED);
        DamageConfig.VoidConfig msgConfig = damageManager.getConfig().deathMessages().voidMessages();

        if (previousTick instanceof PlayerDamageTick pdt && pdt.getPlayer() != null) {
            Component attackerName = pdt.getPlayer().displayName().color(NamedTextColor.RED);
            return lang.getTranslated(victim, msgConfig.knockedByPlayer(),
                Placeholder.component("victim_name", victimName),
                Placeholder.component("attacker_name", attackerName)
            );
        } else if (previousTick instanceof MonsterDamageTick mdt) {
            return lang.getTranslated(victim, msgConfig.knockedByMonster(),
                Placeholder.component("victim_name", victimName),
                Placeholder.unparsed("monster_name", mdt.getAttackerName())
            );
        }

        return lang.getTranslated(victim, msgConfig.fell(),
            Placeholder.component("victim_name", victimName)
        );
    }

    @Override
    public Component getSingleLineSummary(Player viewer, LanguageManager lang, DamageManager damageManager) {
        if (previousTick instanceof PlayerDamageTick pdt && pdt.getPlayer() != null) {
            Component attackerName = pdt.getPlayer().displayName().color(NamedTextColor.RED);
            return lang.getTranslated(viewer, "wizards.damage.summary.void.knocked_by_player",
                Placeholder.component("attacker_name", attackerName)
            );
        }
        return lang.getTranslated(viewer, "wizards.damage.summary.void.fell");
    }
}