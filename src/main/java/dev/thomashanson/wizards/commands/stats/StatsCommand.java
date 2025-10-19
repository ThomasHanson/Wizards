package dev.thomashanson.wizards.commands.stats;

import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.PlayerArgument;
import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.commands.WizardsCommand;

public class StatsCommand {

    private final WizardsCommand command;

    public StatsCommand(WizardsCommand command) {
        this.command = command;
    }

    public Argument<String> getCommand(WizardsPlugin plugin) {
        return new LiteralArgument("stats")
            .then(new PlayerArgument("player")
                .then(new LiteralArgument("get")
                    .then(new GetStatsCommand(command).getCommand(plugin)) // "get" attribute
                )
            );
    }

    public WizardsCommand getCommand() {
        return command;
    }
}
