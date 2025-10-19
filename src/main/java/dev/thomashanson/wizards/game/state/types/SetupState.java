package dev.thomashanson.wizards.game.state.types;

import java.io.File;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.server.ServerListPingEvent;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.manager.GameManager;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.thomashanson.wizards.game.mode.WizardsMode;
import dev.thomashanson.wizards.game.state.GameState;
import dev.thomashanson.wizards.game.state.listener.StateListenerProvider;
import dev.thomashanson.wizards.map.LocalGameMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class SetupState extends GameState implements Listener {

    @Override
    public void onEnable(WizardsPlugin plugin) {
        super.onEnable(plugin);

        WizardsMode wizardsMode = WizardsMode.valueOf(plugin.getConfig().getString("mode"));
        Bukkit.getLogger().info(String.format("Game configured as: %s.", wizardsMode.name()));

        File gameMapsFolder = new File(plugin.getDataFolder(), "maps");
        if (!gameMapsFolder.exists()) {
            gameMapsFolder.mkdirs();
        }

        File[] directories = gameMapsFolder.listFiles(File::isDirectory);
        if (directories == null || directories.length == 0) {
            plugin.getLogger().severe(String.format("Could not locate any game maps within '%s'!", gameMapsFolder.getAbsolutePath()));
            return;
        }
        
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        for (File mapFile : directories) {
            plugin.getMapManager().addMap(new LocalGameMap(plugin, gameMapsFolder, mapFile.getName()));
        }

        List<LocalGameMap> modeSpecificMaps = plugin.getMapManager().getAllMaps(wizardsMode);
        if (modeSpecificMaps.isEmpty()) {
            plugin.getLogger().severe(String.format("No maps found for the configured game mode '%s'.", wizardsMode.name()));
            return;
        }

        LocalGameMap randomMap = modeSpecificMaps.get(ThreadLocalRandom.current().nextInt(modeSpecificMaps.size()));
        
        if (randomMap.load()) {
            plugin.getMapManager().setActiveMap(randomMap);
            plugin.getLogger().info(String.format("Successfully loaded and set active map: %s", randomMap.getName()));
        } else {
            plugin.getLogger().severe(String.format("Failed to load random map: %s. Server setup cannot continue.", randomMap.getName()));
            return;
        }

        // *** BEGIN FIX ***
        // We must load the kits into the KitManager BEFORE the lobby starts.
        // We create a temporary game instance here solely to satisfy the method signature
        // for loading kits. This instance is not the one that will be used for the actual game.
        plugin.getLogger().info("Loading kits into KitManager...");
        GameManager gameManager = plugin.getGameManager();
        Wizards tempGameForLoading = new Wizards(plugin);
        gameManager.getKitManager().loadKitsFromDatabase(tempGameForLoading);
        gameManager.getKitManager().loadKitUpgradeCosts();
        plugin.getLogger().info("Kits loaded successfully.");
        // *** END FIX ***

        // Now that kits are loaded, it's safe to switch to the LobbyState.
        plugin.getGameManager().setState(new LobbyState());
    }

    @Override
    public void onDisable() {
        super.onDisable();
        HandlerList.unregisterAll(this);
    }
    
    @Override
    public List<Component> getScoreboardComponents(Player player) {
        LanguageManager lang = getPlugin().getLanguageManager();
        return List.of(
            lang.getTranslated(player, "wizards.scoreboard.setup.settingUp")
        );
    }

    @EventHandler
    public void onJoin(AsyncPlayerPreLoginEvent event) {
        LanguageManager lang = getPlugin().getLanguageManager();
        event.kickMessage(lang.getTranslated(null, "wizards.system.kick.preparing"));
        event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
    }

    @EventHandler
    public void onServerPing(ServerListPingEvent event) {
        Wizards game = getGame();
        if (game == null) return;

        event.setMaxPlayers(game.getCurrentMode().getMaxPlayers());
        LocalGameMap selectedMap = game.getActiveMap();

        if (selectedMap != null) {
            LanguageManager lang = getPlugin().getLanguageManager();
            Component motd = lang.getTranslated(
                    null,
                    "wizards.motd.mapSelected",
                    Placeholder.unparsed("map_name", selectedMap.getName())
            );
            event.motd(motd);
        }
    }

    @Override
    protected StateListenerProvider getListenerProvider() {
        return null;
    }
}