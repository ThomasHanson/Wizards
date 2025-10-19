package dev.thomashanson.wizards.game.spell.types;

import org.bukkit.Sound;
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

public class SpellSoulExchange extends Spell {

    public SpellSoulExchange(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        AttributeInstance healthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (healthAttribute == null) return false;

        StatContext context = StatContext.of(level);
        double healthSacrifice = getStat("health-sacrifice", level);
        double minHealth = getStat("min-health", level);

        double newMaxHealth = Math.max(minHealth, healthAttribute.getBaseValue() - healthSacrifice);
        
        // Prevent casting if already at minimum health
        if (newMaxHealth == healthAttribute.getBaseValue()) {
            // Optional: send a feedback message to the player
            return false;
        }
        
        healthAttribute.setBaseValue(newMaxHealth);
        
        // Adjust current health if it's now higher than the new max
        if (player.getHealth() > newMaxHealth) {
            player.setHealth(newMaxHealth);
        }

        getWizard(player).ifPresent(wizard -> {
            double manaGain = getStat("mana-gain", level);
            double maxManaIncrease = getStat("max-mana-increase", level);

            wizard.addMana((float) manaGain);
            wizard.setMaxMana(wizard.getMaxMana() + (float) maxManaIncrease);
        });

        int witherDuration = (int) getStat("wither-duration-ticks", level);
        int witherAmplifier = (int) getStat("wither-amplifier", level) - 1;
        player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, witherDuration, witherAmplifier));
        
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1F, 1F);
        return true;
    }
}
