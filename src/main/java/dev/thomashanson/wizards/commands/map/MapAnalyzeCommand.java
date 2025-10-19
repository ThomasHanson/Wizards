package dev.thomashanson.wizards.commands.map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.thomashanson.wizards.WizardsPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class MapAnalyzeCommand {

    public Argument<String> getCommand(WizardsPlugin plugin) {
        return new LiteralArgument("analyze")
                .withPermission("wizards.command.map.analyze")
                .executesPlayer((player, args) -> {

                    player.sendMessage(MiniMessage.miniMessage().deserialize("<gold>[Debug] <white>Analyze command initiated by <player_name>.</white>", Placeholder.unparsed("player_name", player.getName())));

                    final World world = player.getWorld();
                    final Location playerLocation = player.getLocation();

                    player.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Starting map analysis... This may take a moment."));
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<gold>[Debug] <white>Running analysis in world: <world_name>", Placeholder.unparsed("world_name", world.getName())));

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            Location min = null;
                            Location max = null;

                            int searchRadius = 512;
                            int scanCenterX = playerLocation.getBlockX();
                            int scanCenterZ = playerLocation.getBlockZ();

                            player.sendMessage(MiniMessage.miniMessage().deserialize("<gold>[Debug] <white>Search radius set to <radius> blocks.", Placeholder.unparsed("radius", String.valueOf(searchRadius))));
                            player.sendMessage(MiniMessage.miniMessage().deserialize("<gold>[Debug] <white>Scanning around center point: <x>, <z>", Placeholder.unparsed("x", String.valueOf(scanCenterX)), Placeholder.unparsed("z", String.valueOf(scanCenterZ))));

                            // Create a temporary world border for the player
                            final WorldBorder playerBorder = Bukkit.createWorldBorder();
                            Bukkit.getScheduler().runTask(plugin, () -> player.setWorldBorder(playerBorder));

                            long lastUpdateTime = System.currentTimeMillis();

                            for (int x = scanCenterX - searchRadius; x <= scanCenterX + searchRadius; x++) {

                                // Send a progress update every 2 seconds to show it's not stuck
                                if (System.currentTimeMillis() - lastUpdateTime > 2000) {
                                    final int currentX = x;
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        player.sendMessage(MiniMessage.miniMessage().deserialize("<gold>[Debug] <gray>Scanning... (X=<x>)", Placeholder.unparsed("x", String.valueOf(currentX))));
                                    });
                                    lastUpdateTime = System.currentTimeMillis();
                                }

                                for (int z = scanCenterZ - searchRadius; z <= scanCenterZ + searchRadius; z++) {
                                    for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                                        if (!world.getBlockAt(x, y, z).getType().isAir()) {

                                            boolean needsUpdate = false;

                                            if (min == null) {
                                                min = new Location(world, x, y, z);
                                                max = new Location(world, x, y, z);
                                                needsUpdate = true;

                                                final Location firstBlockLoc = min.clone();
                                                Bukkit.getScheduler().runTask(plugin, () -> {
                                                    player.sendMessage(MiniMessage.miniMessage().deserialize("<gold>[Debug] <white>Found first solid block at: <loc>", Placeholder.unparsed("loc", formatLocation(firstBlockLoc))));
                                                });

                                            } else {
                                                // This explicit check will satisfy the static analyzer.
                                                if (max == null) continue;

                                                if (x < min.getX()) { min.setX(x); needsUpdate = true; }
                                                if (y < min.getY()) { min.setY(y); needsUpdate = true; }
                                                if (z < min.getZ()) { min.setZ(z); needsUpdate = true; }
                                                
                                                if (x > max.getX()) { max.setX(x); needsUpdate = true; }
                                                if (y > max.getY()) { max.setY(y); needsUpdate = true; }
                                                if (z > max.getZ()) { max.setZ(z); needsUpdate = true; }
                                            }

                                            if (needsUpdate) {
                                                final Location currentMin = min.clone();
                                                final Location currentMax = max.clone();

                                                // Schedule the world border update back on the main server thread
                                                Bukkit.getScheduler().runTask(plugin, () -> {
                                                    updatePlayerBorder(player, playerBorder, currentMin, currentMax);
                                                });
                                            }
                                        }
                                    }
                                }
                            }

                            if (min != null && max != null) {
                                final Location finalMin = min.clone();
                                final Location finalMax = max.clone();

                                // Final update and messages must be on the main thread
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    player.sendMessage(MiniMessage.miniMessage().deserialize("<green>✔ Analysis complete!"));
                                    player.sendMessage(MiniMessage.miniMessage().deserialize("<gray>Final Min corner: <loc>", Placeholder.unparsed("loc", formatLocation(finalMin))));
                                    player.sendMessage(MiniMessage.miniMessage().deserialize("<gray>Final Max corner: <loc>", Placeholder.unparsed("loc", formatLocation(finalMax))));
                                    player.sendMessage(MiniMessage.miniMessage().deserialize("<aqua>You can now save these values. Run the command again to hide the border."));
                                });

                            } else {
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    player.sendMessage(MiniMessage.miniMessage().deserialize("<red>No blocks found within a <radius>-block radius of your location.", Placeholder.unparsed("radius", String.valueOf(searchRadius))));
                                    player.setWorldBorder(world.getWorldBorder()); // Reset border
                                });
                            }
                        }
                    }.runTaskAsynchronously(plugin);
                });
    }

    private void updatePlayerBorder(Player player, WorldBorder border, Location min, Location max) {
        if (player == null || !player.isOnline()) return; // Safety check

        Location center = min.clone().add(max).multiply(0.5);
        double sizeX = max.getX() - min.getX();
        double sizeZ = max.getZ() - min.getZ();

        border.setCenter(center);
        border.setSize(Math.max(sizeX, sizeZ) + 1); // Add a little padding
        border.setWarningDistance(0);
        border.setDamageAmount(0);
    }

    private String formatLocation(Location loc) {
        return String.format("%d, %d, %d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}