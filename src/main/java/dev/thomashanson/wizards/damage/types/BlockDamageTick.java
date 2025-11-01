package dev.thomashanson.wizards.damage.types;

import java.time.Instant;

import org.bukkit.Location;
import org.bukkit.Material;
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
 * A {@link DamageTick} implementation for damage caused by contact with a specific block.
 * This is used for hazards like Cacti or Magma Blocks.
 *
 * @see dev.thomashanson.wizards.game.listener.DamageListener
 */
public class BlockDamageTick extends DamageTick {

    private final Material type;

    /**
     * Creates a new damage tick caused by a block.
     *
     * @param damage    The amount of damage dealt.
     * @param reason    The internal reason for the damage.
     * @param timestamp The time the damage occurred.
     * @param type      The {@link Material} of the block that dealt the damage.
     * @param location  The location of the damaging block, used as the knockback origin.
     */
    public BlockDamageTick(double damage, String reason, Instant timestamp, Material type, @Nullable Location location) {
        super(damage, EntityDamageEvent.DamageCause.CONTACT, reason, timestamp);
        this.type = type;
        setKnockbackOrigin(location);
    }

    /**
     * @return The {@link Material} of the block that caused the damage.
     */
    public Material getType() {
        return type;
    }

    @Override
    public boolean matches(DamageTick tick) {
        return tick instanceof BlockDamageTick other && getReason().equals(other.getReason()) && getType() == other.getType();
    }

    @Override
    public Component getDeathMessage(Player victim, LanguageManager lang, DamageManager damageManager) {
        Component victimName = victim.displayName().color(NamedTextColor.RED);
        DamageConfig.DeathMessageConfig msgConfig = damageManager.getConfig().deathMessages();
        
        String messageKey = msgConfig.byBlock().getOrDefault(getType().name(), msgConfig.defaultBlock());

        Component blockName = Component.translatable(getType());
        return lang.getTranslated(victim, messageKey,
            Placeholder.component("victim_name", victimName),
            Placeholder.component("block_name", blockName)
        );
    }

    @Override
    public Component getSingleLineSummary(Player viewer, LanguageManager lang, DamageManager damageManager) {
        Component blockName = Component.translatable(getType());
        return lang.getTranslated(viewer, "wizards.damage.summary.block",
            Placeholder.component("block_name", blockName)
        );
    }
}