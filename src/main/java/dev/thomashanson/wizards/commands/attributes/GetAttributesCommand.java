package dev.thomashanson.wizards.commands.attributes;

import org.bukkit.entity.Player;

import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.commands.WizardsCommand;

/**
 * Handles the logic for the `/wizards attributes <player> get <attribute>` sub-command.
 */
public class GetAttributesCommand {

    private final WizardsCommand command;
    private final AttributeRegistry attributeRegistry;

    /**
     * Creates a new instance of the get-attributes command.
     *
     * @param command           The parent {@link WizardsCommand} helper.
     * @param attributeRegistry The registry containing all available attributes.
     */
    public GetAttributesCommand(WizardsCommand command, AttributeRegistry attributeRegistry) {
        this.command = command;
        this.attributeRegistry = attributeRegistry;
    }

    /**
     * Builds the CommandAPI argument tree for the "get" sub-command.
     *
     * @param plugin The main plugin instance.
     * @return The configured {@link Argument} for this command branch.
     */
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
