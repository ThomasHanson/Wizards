package dev.thomashanson.wizards.game.potion.types;

import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.potion.Potion;

import java.util.concurrent.ThreadLocalRandom;

public class PotionWisdom extends Potion {

    @Override
    public void activate(Wizard wizard) {

        wizard.setManaRate(wizard.getManaRate() * 2.5F, true);

        double cooldownIncrease = 1 + ThreadLocalRandom.current().nextDouble(0.15, 0.20);
        wizard.setCooldownModifier(wizard.getCooldownModifier() * (float) cooldownIncrease, true);
    }

    @Override
    public void deactivate(Wizard wizard) {
        wizard.revert();
    }
}