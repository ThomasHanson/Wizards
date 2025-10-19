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

public class BlockDamageTick extends DamageTick {

    private final Material type;

    public BlockDamageTick(double damage, String reason, Instant timestamp, Material type, @Nullable Location location) {
        super(damage, EntityDamageEvent.DamageCause.CONTACT, reason, timestamp);
        this.type = type;
        setKnockbackOrigin(location);
    }

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