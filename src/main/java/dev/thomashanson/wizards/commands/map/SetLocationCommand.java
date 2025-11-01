package dev.thomashanson.wizards.commands.map;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.map.LocalGameMap;

/**
 * Handles the legacy Bukkit `/setlocation` command for map administration.
 * This command allows setting spawn points, spectator points, and map corners
 * for the currently active map.
 * <p>
 * Note: This uses the Bukkit {@link CommandExecutor} interface and is registered
 * separately from the CommandAPI-based `/wizards` command.
 */
public class SetLocationCommand implements CommandExecutor {

    private final WizardsPlugin plugin;

    /**
     * Stores the first corner selection for a player defining map boundaries.
     * This is not robust for multiple simultaneous editors but works for a single admin.
     */
    private static final Map<UUID, Location> corner1 = new HashMap<>();

    /**
     * Stores the second corner selection for a player defining map boundaries.
     * @deprecated This field is unused, as the second corner is used immediately
     * with the first corner and then discarded. Only `corner1` is needed for state.
     */
    private static final Map<UUID, Location> corner2 = new HashMap<>();

    public SetLocationCommand(WizardsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage("Usage: /setlocation <spawn|spectator|corner1|corner2>");
            return true;
        }

        LocalGameMap activeMap = plugin.getMapManager().getActiveMap();
        if (activeMap == null) {
            player.sendMessage("No map is currently active.");
            return true;
        }

        String locationType = args[0].toLowerCase();
        Location playerLocation = player.getLocation();

        switch (locationType) {
            case "spawn" -> {
                activeMap.addSpawnLocation(playerLocation);
                player.sendMessage("Spawn location added.");
            }
            case "spectator" -> {
                activeMap.setSpectatorLocation(playerLocation);
                player.sendMessage("Spectator location set.");
            }
            case "corner1" -> {
                corner1.put(player.getUniqueId(), playerLocation);
                player.sendMessage("Corner 1 set. Now go to the opposite corner and run /setlocation corner2");
                // Don't save yet, wait for corner2
                return true;
            }
            case "corner2" -> {
                Location c1 = corner1.get(player.getUniqueId());
                if (c1 == null) {
                    player.sendMessage("You must set corner1 first!");
                    return true;
                }
                Location c2 = playerLocation;
                // Create a BoundingBox from the two corners and set it
                activeMap.setBounds(BoundingBox.of(c1, c2));
                player.sendMessage("Corner 2 set. Map boundaries have been calculated and saved.");
                // Clean up entries
                corner1.remove(player.getUniqueId());
            }
            default -> {
                player.sendMessage("Invalid location type. Use: spawn, spectator, corner1, corner2");
                return true;
            }
        }

        // Save the changes to data.yml
        activeMap.saveDataFile();
        return true;
    }
}