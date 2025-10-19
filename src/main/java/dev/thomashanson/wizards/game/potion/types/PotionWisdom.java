package dev.thomashanson.wizards.game.potion.types;

import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.potion.Potion;

import java.util.concurrent.ThreadLocalRandom;

public class PotionWisdom extends Potion {

    @Override
    public void onActivate(Wizard wizard) {

        wizard.setManaMultiplier(2.5F, true);

        float cooldownMultiplier = 1 + ThreadLocalRandom.current().nextFloat(
            1.15F, 1.20F
        );

        wizard.setCooldownMultiplier(cooldownMultiplier, true);
    }

    @Override
    public void onDeactivate(Wizard wizard) {
        wizard.revert();
    }
}