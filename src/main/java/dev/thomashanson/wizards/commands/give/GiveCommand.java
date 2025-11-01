package dev.thomashanson.wizards.commands.give;

import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.PlayerArgument;
import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.commands.WizardsCommand;

/**
 * Registers the main `/wizards give` command branch, which
 * delegates to sub-commands for giving spells, potions, mana, and wands.
 */
public class GiveCommand {

    private WizardsCommand command;

    public GiveCommand(WizardsCommand command) {
        this.command = command;
    }

    public Argument<String> getCommand(WizardsPlugin plugin) {
        return new LiteralArgument("give")
                .then(new PlayerArgument("player")
                    .then(new GiveSpellCommand(command).getCommand(plugin))
                    .then(new GivePotionCommand(command).getCommand(plugin))
                    .then(new GiveManaCommand(command).getCommand(plugin))
                    .then(new GiveWandCommand(command).getCommand(plugin))
        );
    }
}
