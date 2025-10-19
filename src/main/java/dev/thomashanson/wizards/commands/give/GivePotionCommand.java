package dev.thomashanson.wizards.commands.give;

import java.util.Arrays;
import java.util.stream.Stream;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.commands.WizardsCommand;
import dev.thomashanson.wizards.game.potion.PotionType;

public class GivePotionCommand {

    private final WizardsCommand command;

    public GivePotionCommand(WizardsCommand command) {
        this.command = command;
    }

    public Argument<String> getCommand(WizardsPlugin plugin) {
        return new LiteralArgument("potion")
                    .then(new MultiLiteralArgument("potion_type", getAllPotions())
                    .then(new StringArgument("quantity")
                            .executes((sender, args) -> {

                                Player target = (Player) args.get("player");
                                String potionType = (String) args.get("potion_type");
                                int quantity = Integer.parseInt((String) args.get("quantity"));

                                command.executeAction(plugin, target, (wizard, game) -> {
                                    givePotion(potionType, quantity, target, sender);
                                });
                            })
                        )
                    );
    }

    private void givePotion(Player target, PotionType potionType, int quantity, CommandSender sender) {

        for (int i = 0; i < quantity; i++) {
            target.getInventory().addItem(potionType.createPotion());
        }
        
        sender.sendMessage("Gave " + quantity + " potions to player");
    }

    private void givePotion(String potionType, int quantity, Player target, CommandSender sender) {
        if (potionType.equalsIgnoreCase("all")) {
            // Iterate through all the spells in SpellType
            for (PotionType potion : PotionType.values()) {
                givePotion(target, potion, quantity, sender);
            }
        } else {
            // Handle specific spell
            PotionType potion = PotionType.valueOf(potionType.toUpperCase());

            try {
                givePotion(target, potion, quantity, sender);

            } catch (IllegalArgumentException e) {
                // If spellType is not found in the SpellType enum
                sender.sendMessage("Error: The potion '" + potionType + "' does not exist.");
            }
        }
    }

    private String[] getAllPotions() {
        return Stream.concat(
            Stream.of("all"), 
            Arrays.stream(PotionType.values())
                .map(Enum::name)
                .map(String::toLowerCase)
        ).toArray(String[]::new);
    }
}
