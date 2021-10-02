package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.game.spell.Spell;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class SpellSpeedBoost extends Spell {

    @Override
    public void castSpell(Player player, int level) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 20, level, false));
    }
}