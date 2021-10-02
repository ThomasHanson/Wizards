package dev.thomashanson.wizards.game.state.types;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.kit.WizardsKit;
import dev.thomashanson.wizards.game.potion.Potion;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.state.GameState;
import dev.thomashanson.wizards.game.state.listener.StateListenerProvider;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;

/**
 * Represents the brief state while the winner
 * gets announced and all effects will be played.
 * After this phase, it will switch to the reset
 * stage for the janitor process.
 */
public class WinnerState extends GameState {

    @Override
    public void onEnable(WizardsPlugin plugin) {

        super.onEnable(plugin);

        BukkitTask updateTask = ActiveState.UPDATE_TASK;

        if (updateTask != null && !updateTask.isCancelled())
            updateTask.cancel();

        Wizards activeGame = getGame();

        /*
         * Spells
         */
        activeGame.getSpells().values().forEach(Spell::cleanup);
        activeGame.getSpells().values().forEach(HandlerList::unregisterAll);
        activeGame.getSpells().clear();

        /*
         * Potions
         */
        activeGame.getPotions().values().forEach(Potion::cleanup);
        activeGame.getPotions().values().forEach(HandlerList::unregisterAll);
        activeGame.getPotions().clear();

        /*
         * Kits
         */
        for (WizardsKit kit : plugin.getGameManager().getWizardsKits())
            HandlerList.unregisterAll(kit);

        plugin.getGameManager().getWizardsKits().clear();

        /*
         * Game
         */
        HandlerList.unregisterAll(activeGame);

        setState(new ResetState());
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @Override
    protected StateListenerProvider getListenerProvider() {
        return null;
    }
}