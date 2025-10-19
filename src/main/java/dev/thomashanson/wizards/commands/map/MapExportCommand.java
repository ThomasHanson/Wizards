package dev.thomashanson.wizards.commands.map;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BoundingBox;

import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.map.LocalGameMap;
import dev.thomashanson.wizards.util.LocationUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class MapExportCommand {

    // The unused 'command' field and the constructor have been removed.

    public Argument<String> getCommand(WizardsPlugin plugin) {
        return new LiteralArgument("export")
            .withPermission("wizards.command.map.export")
            .then(new StringArgument("name")
                .then(new GreedyStringArgument("authors")
                    .executesPlayer((player, args) -> {
                        LocalGameMap gameMap = plugin.getMapManager().getActiveMap();
                        
                        // Added a safety check for the active map itself
                        if (gameMap == null) {
                            player.sendMessage(Component.text("Cannot export: No map is currently active.", NamedTextColor.RED));
                            return;
                        }

                        String mapName = (String) args.get("name");
                        String authorsRaw = (String) args.get("authors");

                        BoundingBox bounds = gameMap.getBounds();
                        if (bounds == null || bounds.getVolume() == 0 || gameMap.getSpectatorLocation() == null || gameMap.getSpawnLocations().isEmpty()) {
                            player.sendMessage(Component.text("Cannot export: Boundaries, spectator, and spawn points must be set.", NamedTextColor.RED));
                            return;
                        }

                        File mapFolder = new File(plugin.getDataFolder().getParentFile().getParentFile() + "/maps", mapName);
                        if (mapFolder.exists()) {
                            player.sendMessage(Component.text("A map with this name already exists!", NamedTextColor.RED));
                            return;
                        }
                        mapFolder.mkdirs();

                        File dataFile = new File(mapFolder, "data.yml");
                        YamlConfiguration config = new YamlConfiguration();

                        config.set("core.name", mapName);
                        
                        // This now safely handles cases where authorsRaw might be null
                        config.set("core.authors", authorsRaw == null ? Collections.emptyList() : Arrays.asList(authorsRaw.split("\\s+")));
                        config.set("core.type", "NORMAL");

                        config.set("locations.min.x", bounds.getMinX());
                        config.set("locations.min.y", bounds.getMinY());
                        config.set("locations.min.z", bounds.getMinZ());

                        config.set("locations.max.x", bounds.getMaxX());
                        config.set("locations.max.y", bounds.getMaxY());
                        config.set("locations.max.z", bounds.getMaxZ());

                        config.set("locations.spectator", LocationUtil.locationToString(gameMap.getSpectatorLocation()));
                        config.set("locations.spawns", gameMap.getSpawnLocations().stream().map(LocationUtil::locationToString).collect(Collectors.toList()));

                        try {
                            config.save(dataFile);
                            player.sendMessage(Component.text("Successfully exported map '" + mapName + "'!", NamedTextColor.GREEN));
                        } catch (IOException e) {
                            player.sendMessage(Component.text("An error occurred while saving the map file.", NamedTextColor.RED));
                            e.printStackTrace();
                        }
                    })));
    }
}