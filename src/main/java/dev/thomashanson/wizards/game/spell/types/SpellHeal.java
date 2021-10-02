package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.game.spell.Spell;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class SpellHeal extends Spell {

    @Override
    public void castSpell(Player player, int level) {

        int regenLength = (level + 5) * 20; //(int) getValue(player, "Regeneration Length (seconds)") * 20;

        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenLength, 0, false));

        // Special effects
        player.getWorld().spawnParticle(Particle.HEART, player.getEyeLocation(), 6, 0.8F, 0.4F, 0.8F, 0);
    }
}