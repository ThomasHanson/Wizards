package dev.thomashanson.wizards.commands;

import java.util.function.BiConsumer;

import org.bukkit.entity.Player;

import dev.jorel.commandapi.CommandTree;
import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.commands.attributes.AttributesCommand;
import dev.thomashanson.wizards.commands.game.OvertimeCommand;
import dev.thomashanson.wizards.commands.give.GiveCommand;
import dev.thomashanson.wizards.commands.map.MapCommand;
import dev.thomashanson.wizards.commands.stats.StatsCommand;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.Wizards;

/**
 * The main command registrar for the `/wizards` command.
 * This class builds the {@link CommandTree} and provides shared helper methods
 * for all sub-commands, such as {@link #executeAction(WizardsPlugin, Player, BiConsumer)}.
 */
public class WizardsCommand {

    public void register(WizardsPlugin plugin) {
        new CommandTree("wizards")
            .then(new GiveCommand(this).getCommand(plugin))
            .then(new AttributesCommand(this).getCommand(plugin))
            .then(new MapCommand(this).getCommand(plugin))
            .then(new OvertimeCommand(this).getCommand(plugin))
            .then(new StatsCommand(this).getCommand(plugin))
        .register();
    }

    /**
     * A shared helper method to safely execute an action on a {@link Wizard}.
     * This method handles all necessary null checks for the active game
     * and the target player's wizard status.
     *
     * @param plugin The main plugin instance.
     * @param player The target player to execute the action on.
     * @param action A lambda or method reference to be executed if the player
     * is a valid, active wizard in the current game.
     */
    public void executeAction(WizardsPlugin plugin, Player player, BiConsumer<Wizard, Wizards> action) {

        Wizards activeGame = plugin.getGameManager().getActiveGame();

        if (activeGame != null) {

            Wizard wizard = plugin.getGameManager().getActiveGame().getWizard(player);

            if (wizard != null) {
                action.accept(wizard, activeGame);
            } else {
                player.sendMessage("Target player is not a wizard");
            }
        } else {
            player.sendMessage("Game is not initialized.");
        }
    }
}
