package dev.thomashanson.wizards.game.potion;

import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.Wizards;
import org.bukkit.event.Listener;

public abstract class Potion implements Listener {

    private Wizards game;
    private PotionType potion;

    public abstract void onActivate(Wizard wizard);
    public abstract void onDeactivate(Wizard wizard);

    public void deactivate(Wizard wizard) {
        onDeactivate(wizard);
        wizard.setActivePotion(null);
    }

    public void cleanup() {}

    public boolean hasActivePotion(Wizard wizard, PotionType potion) {
        return wizard.getActivePotion().equals(potion);
    }

    protected Wizards getGame() {
        return game;
    }

    public void setGame(Wizards game) {
        this.game = game;
    }

    protected PotionType getPotion() {
        return potion;
    }

    public void setPotion(PotionType potion) {
        this.potion = potion;
    }
}