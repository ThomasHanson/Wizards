package dev.thomashanson.wizards.commands.attributes;

import org.bukkit.entity.Player;

import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.commands.WizardsCommand;

public class GetAttributesCommand {

    private final WizardsCommand command;
    private final AttributeRegistry attributeRegistry;

    public GetAttributesCommand(WizardsCommand command, AttributeRegistry attributeRegistry) {
        this.command = command;
        this.attributeRegistry = attributeRegistry;
    }

    public Argument<String> getCommand(WizardsPlugin plugin) {
        return new MultiLiteralArgument("attribute", attributeRegistry.getAttributeNames())
            .executes((sender, args) -> {
                Player player = (Player) args.get("player");
                String attribute = (String) args.get("attribute");

                // Perform the get action for the wizard's attribute
                command.executeAction(plugin, player, (wizard, game) -> {
                    Object value = attributeRegistry.getAttribute(wizard, attribute);
                    if (value != null) {
                        sender.sendMessage(attribute + ": " + value.toString());
                    } else {
                        sender.sendMessage("Invalid attribute");
                    }
                });
            });
    }
}
