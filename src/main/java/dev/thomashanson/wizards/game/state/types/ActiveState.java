package dev.thomashanson.wizards.game.state.types;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.state.GameState;
import dev.thomashanson.wizards.game.state.listener.StateListenerProvider;
import dev.thomashanson.wizards.map.LocalGameMap;
import dev.thomashanson.wizards.util.LocationUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents the state when the game is in an
 * active and there is no winner declared.
 */
public class ActiveState extends GameState implements Listener {

    static BukkitTask UPDATE_TASK;

    @Override
    public void onEnable(WizardsPlugin plugin) {

        super.onEnable(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        getGame().setLastSurge(Instant.now());

        getGame().setupGame();
        //findAllChests();

        AtomicInteger atomicInteger = new AtomicInteger();
        UPDATE_TASK = Bukkit.getScheduler().runTaskTimer(plugin, () -> getGame().updateGame(atomicInteger), 0L, 1L);
    }

    @Override
    public void onDisable() {

        super.onDisable();
        HandlerList.unregisterAll(this);
    }

    private void findAllChests() {

        LocalGameMap gameMap = getGame().getActiveMap();
        World world = gameMap.getWorld();

        Block blockMin = world.getBlockAt((int) gameMap.getMinX(), (int) gameMap.getMinY(), (int) gameMap.getMinZ());
        Chunk chunkMin = blockMin.getChunk();

        Block blockMax = world.getBlockAt((int) gameMap.getMaxX(), (int) gameMap.getMaxY(), (int) gameMap.getMaxZ());
        Chunk chunkMax = blockMax.getChunk();

        for (int cx = chunkMin.getX(); cx < chunkMax.getX(); cx++) {
            for (int cz = chunkMin.getZ(); cz < chunkMax.getZ(); cz++) {

                Chunk currentChunk = world.getChunkAt(cx, cz);

                for (BlockState tileEntity : currentChunk.getTileEntities()) {

                    if (tileEntity instanceof Chest || tileEntity instanceof DoubleChest) {

                        Location location = tileEntity.getLocation();
                        world.strikeLightningEffect(location);

                        String locationString = LocationUtil.locationToString(location);

                        if (!gameMap.getLocations().getStringList("chests").contains(locationString))
                            Bukkit.broadcastMessage("    - \"" + LocationUtil.locationToString(location) + "\"");
                    }
                }
            }
        }
    }

    @EventHandler
    public void onServerPing(ServerListPingEvent event) {

        Wizards game = getGame();

        event.setMaxPlayers(game.getCurrentMode().getMaxPlayers());

        event.setMotd (
                ChatColor.GOLD + game.getCurrentMode().toString() + " - In Progress\n" +
                        ChatColor.YELLOW + "Wizards Remaining: " + ChatColor.GOLD + game.getWizards().size()
        );
    }

    @Override
    protected StateListenerProvider getListenerProvider() {
        return null;
    }
}