package dev.thomashanson.wizards.game.potion.types;

import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.potion.Potion;

public class PotionMana extends Potion {

    @Override
    public void activate(Wizard wizard) {
        wizard.addMana(100F);
    }

    @Override
    public void deactivate(Wizard wizard) {
        super.deactivate(wizard);
    }
}
