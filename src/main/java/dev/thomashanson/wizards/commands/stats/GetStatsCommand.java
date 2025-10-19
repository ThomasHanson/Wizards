package dev.thomashanson.wizards.commands.stats;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import org.bukkit.entity.Player;

import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.commands.WizardsCommand;
import dev.thomashanson.wizards.game.manager.PlayerStatsManager;
import dev.thomashanson.wizards.game.manager.PlayerStatsManager.StatType;

public class GetStatsCommand {

    private final WizardsCommand command;

    public GetStatsCommand(WizardsCommand command) {
        this.command = command;
    }

    public Argument<String> getCommand(WizardsPlugin plugin) {
        PlayerStatsManager statsManager = plugin.getStatsManager();
        return new MultiLiteralArgument("stat_type", getAllStatTypes())
            .executes((sender, args) -> {
                Player target = (Player) args.get("player");
                String statType = (String) args.get("stat_type");

                // Perform the get action for the wizard's attribute
                command.executeAction(plugin, target, (wizard, game) -> {
                    
                    if (statType != null) {
                        
                        if (statType.equalsIgnoreCase("all")) {

                            Map<String, Double> allStats = statsManager.getAllStats(target);

                            for (String key : allStats.keySet()) {
                                sender.sendMessage(key + ": " + allStats.get(key));
                            }

                        } else {
                            sender.sendMessage(statType + ": " + statsManager.getStat(target, StatType.valueOf(statType.toUpperCase())));
                        }
                    }
                });
            });
    }

    private String[] getAllStatTypes() {
        return Stream.concat(
            Stream.of("all"), 
            Arrays.stream(StatType.values())
                .map(Enum::name)
                .map(String::toLowerCase)
        ).toArray(String[]::new);
    }
}
