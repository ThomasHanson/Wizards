package dev.thomashanson.wizards.commands.map;

import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.commands.WizardsCommand;

public class MapCommand {

    private final WizardsCommand command;

    public MapCommand(WizardsCommand command) {
        this.command = command;
    }

    public Argument<String> getCommand(WizardsPlugin plugin) {
        return new LiteralArgument("map")
                .withPermission("wizards.command.map")
                // .then(new MapCreateCommand(command).getCommand(plugin))
                // .then(new MapEditCommand(command).getCommand(plugin))
                .then(new MapExportCommand().getCommand(plugin))
                .then(new MapAnalyzeCommand().getCommand(plugin))
                .then(new MapVisualizeCommand(command).getCommand(plugin));
                // .then(new MapSetCommand(command).getCommand(plugin))
                // .then(new MapSpawnCommand(command).getCommand(plugin));
    }
}