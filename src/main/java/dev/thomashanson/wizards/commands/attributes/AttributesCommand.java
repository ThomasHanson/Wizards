package dev.thomashanson.wizards.commands.attributes;

import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.PlayerArgument;
import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.commands.WizardsCommand;

public class AttributesCommand {

    private final WizardsCommand command;
    
    private final AttributeRegistry attributeRegistry;

    public AttributesCommand(WizardsCommand command) {
        this.command = command;
        this.attributeRegistry = new AttributeRegistry();
    }

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
