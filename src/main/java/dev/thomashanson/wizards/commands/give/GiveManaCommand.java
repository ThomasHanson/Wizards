package dev.thomashanson.wizards.commands.give;

import org.bukkit.entity.Player;

import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.commands.WizardsCommand;
import dev.thomashanson.wizards.game.Wizard;

public class GiveManaCommand {

    private WizardsCommand command;

    public GiveManaCommand(WizardsCommand command) {
        this.command = command;
    }

    public Argument<String> getCommand(WizardsPlugin plugin) {
        return new LiteralArgument("mana")
            .then(new StringArgument("amount")
                    .executes((sender, args) -> {

                        Player target = (Player) args.get("player");
                        String amount = (String) args.get("amount");

                        command.executeAction(plugin, target, (wizard, game) -> {
                            int amountNum = parseManaAmount(amount, wizard);
                            wizard.addMana(amountNum);
                            sender.sendMessage("Added " + amount + " mana");
                        });
                    })
                );
    }

    private int parseManaAmount(String amount, Wizard wizard) {
        if (amount.equalsIgnoreCase("max")) {
            return (int) wizard.getMaxMana();
        } else {
            try {
                return Integer.parseInt(amount); // Return the parsed number if it's not "max"
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid amount: " + amount); // Handle invalid input
            }
        }
    }
}
