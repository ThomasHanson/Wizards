package dev.thomashanson.wizards.commands.game;

import java.util.stream.Collectors;

import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.commands.WizardsCommand;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.manager.GameManager;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.thomashanson.wizards.game.overtime.Disaster;
import dev.thomashanson.wizards.game.state.types.ActiveState;
import dev.thomashanson.wizards.game.state.types.OvertimeState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class OvertimeCommand {

    private final WizardsPlugin plugin = WizardsPlugin.getInstance();
    private final GameManager gameManager;
    private final LanguageManager lang;

    public OvertimeCommand(WizardsCommand command) {
        this.gameManager = plugin.getGameManager();
        this.lang = plugin.getLanguageManager();
    }

    public Argument<String> getCommand(WizardsPlugin plugin) {
        return new LiteralArgument("overtime")
            .withPermission("wizards.admin.overtime")

            // Branch 1: /wizards overtime start
            .then(new LiteralArgument("start")
                .executesPlayer((player, args) -> { // Changed to executesPlayer

                    if (!(gameManager.getState() instanceof ActiveState)) {
                        player.sendMessage(lang.getTranslated(player, "wizards.command.overtime.noActiveGame"));
                        return;
                    }

                    if (gameManager.getActiveGame().isOvertime()) {
                        player.sendMessage(lang.getTranslated(player, "wizards.command.overtime.alreadyOvertime"));
                        return;
                    }

                    gameManager.getActiveGame().pickRandomDisaster();
                    player.sendMessage(lang.getTranslated(player, "wizards.command.overtime.forcing"));
                    gameManager.setState(new OvertimeState());
                })
            )

            // Branch 2: /wizards overtime set disaster <disaster>
            .then(new LiteralArgument("set")
                .then(new LiteralArgument("disaster")
                    .then(new StringArgument("disaster_name")
                        .replaceSuggestions(ArgumentSuggestions.strings(info -> {
                            Wizards activeGame = gameManager.getActiveGame();
                            if (activeGame == null) {
                                return new String[0];
                            }
                            return activeGame.getDisasters().stream()
                                .map(this::formatDisasterNameForCommand)
                                .toArray(String[]::new);
                        }))
                        .executesPlayer((player, args) -> { // Changed to executesPlayer
                            Wizards activeGame = gameManager.getActiveGame();
                            String inputDisasterName = (String) args.get("disaster_name");

                            if (activeGame == null) {
                                player.sendMessage(lang.getTranslated(player, "wizards.command.overtime.set.noActiveGame"));
                                return;
                            }

                            Disaster selectedDisaster = activeGame.getDisasters().stream()
                                .filter(disaster -> formatDisasterNameForCommand(disaster).equals(inputDisasterName))
                                .findFirst()
                                .orElse(null);

                            if (selectedDisaster == null) {
                                player.sendMessage(lang.getTranslated(player, "wizards.command.overtime.set.unknownDisaster",
                                    Placeholder.unparsed("disaster_name", inputDisasterName)
                                ));

                                String validDisasters = activeGame.getDisasters().stream()
                                    .map(this::formatDisasterNameForCommand)
                                    .collect(Collectors.joining(", "));

                                player.sendMessage(lang.getTranslated(player, "wizards.command.overtime.set.validDisasters",
                                    Placeholder.unparsed("disasters", validDisasters)
                                ));
                                return;
                            }

                            activeGame.setDisaster(selectedDisaster);

                            Component translatedDisasterName = lang.getTranslated(player, selectedDisaster.getName());
                            player.sendMessage(lang.getTranslated(player, "wizards.command.overtime.set.success",
                                Placeholder.component("disaster_name", translatedDisasterName)
                            ));
                            player.sendMessage(lang.getTranslated(player, "wizards.command.overtime.set.successNote"));
                        })
                    )
                )
            );
    }

    /**
     * Converts a Disaster object's name KEY into the command-line format.
     * Example: "wizards.disaster.mana_storm.name" -> "mana_storm"
     */
    private String formatDisasterNameForCommand(Disaster disaster) {
        String[] parts = disaster.getName().split("\\.");
        // The desired part is typically the third from the end (disaster.*name*)
        if (parts.length >= 3) {
            return parts[parts.length - 2];
        }
        // Fallback for unexpected key formats
        return disaster.getName().toLowerCase().replace(' ', '_');
    }
}