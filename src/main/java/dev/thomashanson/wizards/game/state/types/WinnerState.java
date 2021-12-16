package dev.thomashanson.wizards.game.state.types;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.potion.Potion;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.state.GameState;
import dev.thomashanson.wizards.game.state.listener.StateListenerProvider;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.List;

/**
 * Represents the brief state while the winner
 * gets announced and all effects will be played.
 * After this phase, it will switch to the reset
 * stage for the janitor process.
 */
public class WinnerState extends GameState {

    private final String[] messages;

    public WinnerState(String... messages) {
        this.messages = new String[messages.length];
        System.arraycopy(messages, 0, this.messages, 0, messages.length);
    }

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
        plugin.getGameManager().getWizardsKits().forEach(HandlerList::unregisterAll);
        plugin.getGameManager().getWizardsKits().clear();

        /*
         * Game
         */
        HandlerList.unregisterAll(activeGame);

        Bukkit.getOnlinePlayers().forEach(player -> {
            // TODO: 12/15/21 teleport and winner effects
            plugin.getGameManager().gameAnnounce(player, false, messages);
        });

        setState(new ResetState());
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @Override
    public List<String> getScoreboardLines() {

        return Collections.singletonList (
                ChatColor.GREEN + ChatColor.BOLD.toString() + "Winner declared!"
        );
    }

    @Override
    protected StateListenerProvider getListenerProvider() {
        return null;
    }
}