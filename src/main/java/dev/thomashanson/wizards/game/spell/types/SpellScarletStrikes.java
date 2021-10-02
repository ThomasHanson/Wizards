package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.game.spell.Spell;
import org.bukkit.EntityEffect;
import org.bukkit.entity.Player;

public class SpellScarletStrikes extends Spell {

    @Override
    public void castSpell(Player player, int level) {

        player.getNearbyEntities(5, 5, 5).forEach(entity -> {

            for (int i = 0; i < 5; i++)
                entity.playEffect(EntityEffect.HURT);
        });
    }
}