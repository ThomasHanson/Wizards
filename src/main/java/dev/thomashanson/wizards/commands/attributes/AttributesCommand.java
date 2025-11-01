package dev.thomashanson.wizards.commands.attributes;

import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.PlayerArgument;
import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.commands.WizardsCommand;
import dev.thomashanson.wizards.game.Wizard;

/**
 * Registers the main `/wizards attributes` command branch, which
 * delegates to sub-commands for getting and setting {@link Wizard.Attribute} values.
 */
public class AttributesCommand {

    private final WizardsCommand command;
    
    private final AttributeRegistry attributeRegistry;

    /**
     * Creates a new instance of the attributes command handler.
     *
     * @param command The parent {@link WizardsCommand} helper.
     */
    public AttributesCommand(WizardsCommand command) {
        this.command = command;
        this.attributeRegistry = new AttributeRegistry();
    }

    /**
     * Builds the CommandAPI argument tree for the `/wizards attributes <player> [get|set] ...` command.
     *
     * @param plugin The main plugin instance.
     * @return The configured {@link Argument} for this command branch.
     */
    public Argument<String> getCommand(WizardsPlugin plugin) {
        return new LiteralArgument("attributes")
            .then(new PlayerArgument("player")
                .then(new LiteralArgument("get")
                    .then(new GetAttributesCommand(command, attributeRegistry).getCommand(plugin)) // "get" attribute
                )
                .then(new LiteralArgument("set")
                    .then(new SetAttributesCommand(command, attributeRegistry).getCommand(plugin)) // "set" attribute
                )
            );
    }

    public WizardsCommand getCommand() {
        return command;
    }

    public AttributeRegistry getAttributeRegistry() {
        return attributeRegistry;
    }    
}
