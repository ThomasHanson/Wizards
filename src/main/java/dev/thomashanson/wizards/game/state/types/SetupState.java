package dev.thomashanson.wizards.game.state.types;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.mode.GameTeam;
import dev.thomashanson.wizards.game.mode.WizardsMode;
import dev.thomashanson.wizards.game.state.GameState;
import dev.thomashanson.wizards.game.state.listener.StateListenerProvider;
import dev.thomashanson.wizards.map.LocalGameMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.server.ServerListPingEvent;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents the server preparation state. This will
 * set the proper game mode from config and select a map
 * at random upon server startup.
 */
public class SetupState extends GameState implements Listener {

    @Override
    public void onEnable(WizardsPlugin plugin) {

        //String serverMode = getPlugin().getConfig().getString("mode");
        //WizardsMode wizardsMode = WizardsMode.valueOf(serverMode);

        File gameMapsFolder = new File(plugin.getDataFolder(), "maps");

        if (!gameMapsFolder.exists())
            gameMapsFolder.mkdirs();

        File[] directories = new File(gameMapsFolder.getPath()).listFiles(File::isDirectory);

        if (directories == null)
            return;

        int numMaps = directories.length;

        if (numMaps == 0) {
            Bukkit.getLogger().severe("Could not locate any game maps!");
            return;
        }

        Bukkit.getLogger().info("Loading " + numMaps + " map" + (numMaps > 1 ? "s" : "") + " from directory.");

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        for (File mapFile : directories) {
            LocalGameMap currentMap = new LocalGameMap(gameMapsFolder, mapFile.getName(), false);
            plugin.getMapManager().addMap(currentMap);
        }

        plugin.getMapManager().getAllMaps().forEach(map -> Bukkit.getLogger().info(map.getName()));

        LocalGameMap randomMap = plugin.getMapManager().getAllMaps().get(ThreadLocalRandom.current().nextInt(numMaps)); // NULL WHEN WE ENABLE THE REST;

        for (LocalGameMap map : plugin.getMapManager().getAllMaps())
            if (map.getName().equalsIgnoreCase("Hogwarts"))
                randomMap = map;

        /*
         * Make sure that the map we select is for the right mode
         */
        /*
        while (
                randomMap == null ||
                randomMap.getCoreSection().getStringList("modes").contains(wizardsMode.toString())
        ) {

            randomMap = plugin.getMapManager().getAllMaps().get(ThreadLocalRandom.current().nextInt(numMaps));
        }
         */

        // Load the map manually
        if (randomMap.load()) {
            plugin.getMapManager().setActiveMap(randomMap);
            Bukkit.getLogger().info(randomMap.getName() + " selected as randomized map.");
        }

        plugin.getGameManager().setState(new LobbyState());

        plugin.getGameManager().setActiveGame(new Wizards(plugin));
        plugin.getServer().getPluginManager().registerEvents(plugin.getGameManager().getActiveGame(), plugin);

        Wizards game = plugin.getGameManager().getActiveGame();
        //game.setCurrentMode(wizardsMode);

        WizardsMode mode = game.getCurrentMode();

        if (mode.isTeamMode()) {

            for (int i = 0; i < mode.getNumTeams(); i++) {

                List<Location> spawnLocations = randomMap.getSpawnLocations();

                GameTeam newTeam = new GameTeam(game, "" + (i + 1), spawnLocations);
                game.getTeams().add(newTeam);

                Bukkit.getLogger().info("Team " + newTeam.getTeamName() + " created.");
            }

        } else {

            GameTeam newTeam = new GameTeam(game, "Wizards", randomMap.getSpawnLocations());
            game.getTeams().add(newTeam);

            Bukkit.getLogger().info("Solo team created.");
        }

        plugin.getMapManager().registerListeners();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onJoin(AsyncPlayerPreLoginEvent event) {
        event.setKickMessage(ChatColor.RED + "Server is still preparing! Please try again soon!");
        event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
    }

    @EventHandler
    public void onServerPing(ServerListPingEvent event) {

        Wizards game = getGame();

        if (game == null)
            return;

        event.setMaxPlayers(game.getCurrentMode().getMaxPlayers());

        LocalGameMap selectedMap = game.getActiveMap();

        if (selectedMap != null)
            event.setMotd(ChatColor.YELLOW + "Map Selected: " + ChatColor.GOLD + selectedMap.getName());
    }

    @Override
    public List<String> getScoreboardLines() {

        return Arrays.asList (
                ChatColor.YELLOW + "Preparing Server",
                ChatColor.RED + "Please wait!"
        );
    }

    @Override
    protected StateListenerProvider getListenerProvider() {
        return null;
    }
}