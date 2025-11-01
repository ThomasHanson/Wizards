package dev.thomashanson.wizards.game.potion;

import org.bukkit.event.Listener;

import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.manager.WizardManager;

/**
 * Represents the abstract base for all custom potion effects in Wizards.
 * <p>
 * This class provides a stateful framework for effects that are applied
 * to a {@link Wizard} when they consume a {@link PotionType}. Implementations
 * must define {@link #onActivate(Wizard)} and {@link #onDeactivate(Wizard)}
 * to manage the application and removal of their unique effects.
 * <p>
 * If the potion has passive logic (e.g., reacting to damage events), it can
 * implement {@link Listener} methods directly within the subclass.
 *
 * @see PotionType
 * @see WizardManager
 */
public abstract class Potion implements Listener {

    /** The main {@link Wizards} game instance. */
    private Wizards game;

    /** The {@link PotionType} enum constant that this instance represents. */
    private PotionType potion;

    /**
     * Called when a player consumes this potion and its effects should be applied.
     *
     * @param wizard The Wizard who consumed the potion.
     */
    public abstract void onActivate(Wizard wizard);
    
    /**
     * Called when this potion's duration expires or it is overridden by another potion.
     * This method is responsible for reverting any temporary stat changes.
     *
     * @param wizard The Wizard whose effects are ending.
     */
    public abstract void onDeactivate(Wizard wizard);

    /**
     * A wrapper for {@link #onDeactivate(Wizard)} that also clears the
     * active potion state from the Wizard object.
     *
     * @param wizard The Wizard whose effects are ending.
     */
    public void deactivate(Wizard wizard) {
        onDeactivate(wizard);
        wizard.setActivePotion(null);
    }

    /**
     * Called when the game ends to clean up any persistent state (e.g.,
     * cancelling tasks, clearing maps) to prevent memory leaks.
     */
    public void cleanup() {}

    /**
     * Checks if a Wizard is currently under the effect of a specific potion.
     *
     * @param wizard The wizard to check.
     * @param potion The potion type to look for.
     * @return True if the wizard's active potion matches, false otherwise.
     */
    public boolean hasActivePotion(Wizard wizard, PotionType potion) {
        return wizard.getActivePotion().equals(potion);
    }

    protected Wizards getGame() {
        return game;
    }

    /**
     * Sets the game instance for this potion. This is called by the manager
     * upon creation.
     *
     * @param game The active {@link Wizards} game.
     */
    public void setGame(Wizards game) {
        this.game = game;
    }

    protected PotionType getPotion() {
        return potion;
    }

    /**
     * Sets the enum type for this potion. This is called by the manager
     * upon creation.
     *
     * @param potion The {@link PotionType} this instance represents.
     */
    public void setPotion(PotionType potion) {
        this.potion = potion;
    }
}