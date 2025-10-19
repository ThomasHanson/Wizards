package dev.thomashanson.wizards.game.spell.types;

import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;

public class SpellHeal extends Spell {

    public SpellHeal(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        AttributeInstance healthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);

        // Fail the cast if the player is already at full health
        if (healthAttribute != null && player.getHealth() >= healthAttribute.getValue()) {
            return false;
        }

        StatContext context = StatContext.of(level);
        int durationTicks = (int) (getStat("duration-seconds", level) * 20);
        int amplifier = (int) getStat("amplifier", level) - 1; // Potion effects are 0-indexed

        if (durationTicks <= 0) {
            return false; // Don't apply a zero-duration effect
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, durationTicks, amplifier, false, true, true));

        // Also make particle effect configurable
        int particleCount = (int) getStat("particle-count", level);
        player.getWorld().spawnParticle(Particle.HEART, player.getEyeLocation(), particleCount, 0.8F, 0.4F, 0.8F, 0);

        return true;
    }
}
