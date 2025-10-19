package dev.thomashanson.wizards.game.spell.types;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;

public class SpellSpeedBoost extends Spell {

    public SpellSpeedBoost(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        StatContext context = StatContext.of(level);
        int durationTicks = (int) (getStat("duration-seconds", level) * 20);
        int amplifier = (int) getStat("speed-amplifier", level) - 1; // Potion effects are 0-indexed

        if (durationTicks <= 0) {
            return false;
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, amplifier, false, true, true));
        return true;
    }
}