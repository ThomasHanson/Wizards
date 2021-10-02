package dev.thomashanson.wizards.game.potion.types;

import dev.thomashanson.wizards.event.CustomDamageEvent;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.potion.Potion;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

public class PotionRegeneration extends Potion {

    @Override
    public void activate(Wizard wizard) {

    }

    @Override
    public void deactivate(Wizard wizard) {

    }

    @EventHandler
    public void onDamage(CustomDamageEvent event) {

        LivingEntity victim = event.getVictim();
        Player victimPlayer = null;

        if (victim instanceof Player)
            victimPlayer = (Player) victim;

        Wizard wizard = getGame().getWizard(victimPlayer);

        if (victimPlayer != null && wizard != null)
            if (wizard.getActivePotion() == getPotion())
                event.setDamage(event.getDamage() * 2);
    }
}
