package dev.thomashanson.wizards.game.manager;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.listener.WorldListener;
import dev.thomashanson.wizards.map.LocalGameMap;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.List;

public class MapManager {

    private final WizardsPlugin plugin;

    private LocalGameMap activeMap;
    private final List<LocalGameMap> allMaps = new ArrayList<>();

    private WorldListener worldListener;

    public MapManager(WizardsPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerListeners() {
        this.worldListener = new WorldListener(this);
        plugin.getServer().getPluginManager().registerEvents(worldListener, plugin);
    }

    public void handleListeners() {
        HandlerList.unregisterAll(worldListener);
    }

    public void addMap(LocalGameMap gameMap) {
        allMaps.add(gameMap);
    }

    public WizardsPlugin getPlugin() {
        return plugin;
    }

    public LocalGameMap getActiveMap() {
        return activeMap;
    }

    public List<LocalGameMap> getAllMaps() {
        return allMaps;
    }

    public void setActiveMap(LocalGameMap activeMap) {
        this.activeMap = activeMap;
        Bukkit.getLogger().info(activeMap.getName() + " selected as active map.");
    }
}