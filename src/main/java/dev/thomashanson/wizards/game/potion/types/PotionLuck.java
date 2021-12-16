package dev.thomashanson.wizards.game.potion.types;

import dev.thomashanson.wizards.event.SpellCollectEvent;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.potion.Potion;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

import java.util.concurrent.ThreadLocalRandom;

public class PotionLuck extends Potion {

    @Override
    public void activate(Wizard wizard) {}

    @EventHandler
    public void onSpellCollect(SpellCollectEvent event) {

        Player player = event.getPlayer();
        Wizard wizard = getGame().getWizard(player);

        if (wizard == null || wizard.getActivePotion() != getPotion())
            return;

        double random = Math.random();

        if (random < 0.2) {

            if (ThreadLocalRandom.current().nextBoolean()) {
                event.setManaGain(event.getManaGain() * 2);
                player.sendMessage("Mana doubled!");

            } else {
                getGame().learnSpell(player, event.getSpell());
                player.sendMessage("Spell bonus doubled!");
            }

        } else if (random < 0.03) {
            event.setCancelled(true);
        }
    }
}
