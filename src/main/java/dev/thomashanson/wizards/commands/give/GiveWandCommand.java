package dev.thomashanson.wizards.commands.give;

import org.bukkit.entity.Player;

import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.commands.WizardsCommand;
import dev.thomashanson.wizards.game.Wizard;

public class GiveWandCommand {
    
    private WizardsCommand command;

    public GiveWandCommand(WizardsCommand command) {
        this.command = command;
    }

    public Argument<String> getCommand(WizardsPlugin plugin) {

        return new LiteralArgument("wand")
            .then(new StringArgument("quantity")
                .executes((sender, args) -> {

                    Player target = (Player) args.get("player");
                    String quantity = (String) args.get("quantity");

                    command.executeAction(plugin, target, (wizard, game) -> {
                        int quantityNum = parseWandAmount(quantity, wizard);

                        for (int i = 0; i < quantityNum; i++)
                            game.getWandManager().gainWand(target);

                        sender.sendMessage("Gave " + quantity + "wands to player");
                    });
                })
            );
    }

    private int parseWandAmount(String quantity, Wizard wizard) {
        if (quantity.equalsIgnoreCase("max")) {
            return wizard.getMaxWands() - wizard.getWandsOwned();
        } else {
            try {
                return Integer.parseInt(quantity); // Return the parsed number if it's not "max"
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid quantity: " + quantity); // Handle invalid input
            }
        }
    }
}
