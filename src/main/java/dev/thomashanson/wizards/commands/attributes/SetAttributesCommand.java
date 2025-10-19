package dev.thomashanson.wizards.commands.attributes;

import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.commands.WizardsCommand;
import org.bukkit.entity.Player;

public class SetAttributesCommand {

    private WizardsCommand command;
    private AttributeRegistry attributeRegistry;

    public SetAttributesCommand(WizardsCommand command, AttributeRegistry attributeRegistry) {
        this.command = command;
        this.attributeRegistry = attributeRegistry;
    }

    public Argument<String> getCommand(WizardsPlugin plugin) {
        return new MultiLiteralArgument("attribute", attributeRegistry.getAttributeNames())
            .then(new StringArgument("value")
                .executes((sender, args) -> {
                    Player player = (Player) args.get("player");
                    String attribute = (String) args.get("attribute");
                    String value = (String) args.get("value");

                    // Perform the set action for the wizard's attribute
                    command.executeAction(plugin, player, (wizard, game) -> {
                        try {
                            Object parsedValue = parseValue(value, attributeRegistry.getAttribute(wizard, attribute));
                            attributeRegistry.setAttribute(wizard, attribute, parsedValue);
                            sender.sendMessage("Set " + attribute + " to " + value);
                        } catch (Exception e) {
                            sender.sendMessage("Failed to set attribute: " + e.getMessage());
                        }
                    });
                })
            );
    }

    private Object parseValue(String value, Object currentValue) {
        if (currentValue instanceof Integer) {
            return Integer.parseInt(value);
        } else if (currentValue instanceof Float) {
            return Float.parseFloat(value);
        }
        return value;
    }
}
