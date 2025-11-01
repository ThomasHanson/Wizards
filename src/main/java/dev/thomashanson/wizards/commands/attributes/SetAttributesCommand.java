package dev.thomashanson.wizards.commands.attributes;

import org.bukkit.entity.Player;

import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.commands.WizardsCommand;
import dev.thomashanson.wizards.game.Wizard;

/**
 * Handles the logic for the `/wizards attributes <player> set <attribute> <value>` sub-command.
 */
public class SetAttributesCommand {

    private final WizardsCommand command;
    private final AttributeRegistry attributeRegistry;

    /**
     * Creates a new instance of the set-attributes command.
     *
     * @param command           The parent {@link WizardsCommand} helper.
     * @param attributeRegistry The registry containing all available attributes.
     */
    public SetAttributesCommand(WizardsCommand command, AttributeRegistry attributeRegistry) {
        this.command = command;
        this.attributeRegistry = attributeRegistry;
    }

    /**
     * Builds the CommandAPI argument tree for the "set" sub-command.
     *
     * @param plugin The main plugin instance.
     * @return The configured {@link Argument} for this command branch.
     */
    public Argument<String> getCommand(WizardsPlugin plugin) {
        return new MultiLiteralArgument("attribute", attributeRegistry.getAttributeNames())
            .then(new StringArgument("value")
                .executes((sender, args) -> {
                    Player player = (Player) args.get("player");
                    String attribute = (String) args.get("attribute");
                    String valueStr = (String) args.get("value");

                    command.executeAction(plugin, player, (wizard, game) -> {

                        // 1. Get the Attribute object itself from the registry
                        Wizard.Attribute<?> attr = attributeRegistry.getAttributeObject(wizard, attribute); // You'll need to add this getter to AttributeRegistry

                        if (attr == null) {
                            sender.sendMessage("Invalid attribute");
                            return;
                        }

                        try {
                            // 2. Parse the string based on the *expected* type
                            Object parsedValue = parseValue(valueStr, attr.getType()); 

                            // 3. Set it. The registry's setAttribute will handle the type-safe cast.
                            attributeRegistry.setAttribute(wizard, attribute, parsedValue);
                            sender.sendMessage("Set " + attribute + " to " + valueStr);

                        } catch (NumberFormatException e) {
                            sender.sendMessage("Failed to set attribute: Invalid format. Expected a " + attr.getType().getSimpleName());
                        } catch (Exception e) {
                            sender.sendMessage("Failed to set attribute: " + e.getMessage());
                        }
                    });
                })
            );
    }

    /**
     * Parses a string input value into the target class type (e.g., Integer, Float).
     *
     * @param value        The raw string value from the command argument.
     * @param expectedType The target class to parse the value into.
     * @return The parsed object (e.g., an {@link Integer} or {@link Float}).
     * @throws NumberFormatException      If the string cannot be parsed into the expected number type.
     * @throws IllegalArgumentException If the {@code expectedType} is not supported.
     */
    private Object parseValue(String value, Class<?> expectedType) throws NumberFormatException {
        
        if (expectedType == Integer.class) {
            return Integer.valueOf(value);
        } else if (expectedType == Float.class) {
            return Float.valueOf(value);
        } else if (expectedType == String.class) {
            return value;
        }

        throw new IllegalArgumentException("Unsupported attribute type: " + expectedType.getSimpleName());
    }
}
