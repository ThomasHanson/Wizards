package dev.thomashanson.wizards.commands.map;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WorldBorder;
import org.bukkit.util.BoundingBox;

import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.commands.WizardsCommand;
import dev.thomashanson.wizards.map.LocalGameMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class MapVisualizeCommand {

    private final Map<UUID, Boolean> visualizing = new HashMap<>();

    public MapVisualizeCommand(WizardsCommand command) {}

    public Argument<String> getCommand(WizardsPlugin plugin) {
        return new LiteralArgument("visualize")
            .executesPlayer((player, args) -> {
                if (visualizing.getOrDefault(player.getUniqueId(), false)) {
                    player.setWorldBorder(null); // Reset to default
                    visualizing.put(player.getUniqueId(), false);
                    player.sendMessage(Component.text("Map visualization hidden.", NamedTextColor.YELLOW));
                    return;
                }

                LocalGameMap activeMap = plugin.getMapManager().getActiveMap();
                // FIXED: Use the BoundingBox to get map data
                BoundingBox bounds = activeMap.getBounds();

                if (bounds.getVolume() == 0) {
                    player.sendMessage(Component.text("Map boundaries have not been set! Use /setlocation corner1/corner2.", NamedTextColor.RED));
                    return;
                }

                WorldBorder playerBorder = Bukkit.createWorldBorder();
                
                // FIXED: Use BoundingBox methods for center and size
                Location center = bounds.getCenter().toLocation(player.getWorld());
                double size = Math.max(bounds.getWidthX(), bounds.getWidthZ());

                playerBorder.setCenter(center);
                playerBorder.setSize(size + 1); // Add padding
                playerBorder.setWarningDistance(0);
                playerBorder.setDamageAmount(0);

                player.setWorldBorder(playerBorder);
                visualizing.put(player.getUniqueId(), true);
                player.sendMessage(Component.text("Map visualization shown. Run the command again to hide it.", NamedTextColor.GREEN));
            });
    }
}