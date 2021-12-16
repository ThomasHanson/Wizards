package dev.thomashanson.wizards.game.potion.types;

import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.potion.Potion;

public class PotionRusher extends Potion {

    @Override
    public void activate(Wizard wizard) {
        wizard.setCooldownModifier(wizard.getCooldownModifier() * 0.8F, true);
        wizard.setManaModifier(wizard.getManaModifier() * 1.25F, true);
    }

    @Override
    public void deactivate(Wizard wizard) {

        super.deactivate(wizard);

        wizard.revert();
    }
}