package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.spell.Spell;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class SpellSoulExchange extends Spell {

    @Override
    public void castSpell(Player player, int level) {

        AttributeInstance healthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);

        if (healthAttribute != null) {
            healthAttribute.setBaseValue(Math.max(healthAttribute.getBaseValue() - 2.0, 2.0));
            player.setHealth(Math.min(player.getHealth(), healthAttribute.getBaseValue()));
        }

        Wizard wizard = getWizard(player);
        wizard.setMaxMana(wizard.getMaxMana() + 15);

        player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 200, 0));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1F, 1F);
    }
}