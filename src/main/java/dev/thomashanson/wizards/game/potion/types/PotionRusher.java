package dev.thomashanson.wizards.game.potion.types;

import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.potion.Potion;

public class PotionRusher extends Potion {

    @Override
    public void onActivate(Wizard wizard) {
        wizard.setCooldownMultiplier(0.8F, true);
        wizard.setManaMultiplier(1.25F, true);
    }

    @Override
    public void onDeactivate(Wizard wizard) {
        wizard.revert();
    }
}