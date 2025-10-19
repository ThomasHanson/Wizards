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

public class WizardsCommand {

    // Method to register the command
    public void register(WizardsPlugin plugin) {
        // Main "wizards" command
        new CommandTree("wizards")
            .then(new GiveCommand(this).getCommand(plugin))
            .then(new AttributesCommand(this).getCommand(plugin))
            .then(new MapCommand(this).getCommand(plugin))
            .then(new OvertimeCommand(this).getCommand(plugin))
            .then(new StatsCommand(this).getCommand(plugin))
        .register();
    }

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
