package dev.thomashanson.wizards.game.manager;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.listener.WorldListener;
import dev.thomashanson.wizards.game.mode.WizardsMode;
import dev.thomashanson.wizards.map.LocalGameMap;

public class MapManager {

    private final WizardsPlugin plugin;
    private final MapEditingManager mapEditingManager;

    private LocalGameMap activeMap;
    private final List<LocalGameMap> allMaps = new ArrayList<>();
    private final Map<WizardsMode, List<LocalGameMap>> modeMapsCache = new EnumMap<>(WizardsMode.class);

    private WorldListener worldListener;

    public MapManager(WizardsPlugin plugin) {
        this.plugin = plugin;
        this.mapEditingManager = new MapEditingManager();
    }

    public void registerListeners() {
        this.worldListener = new WorldListener();
        plugin.getServer().getPluginManager().registerEvents(worldListener, plugin);
    }

    public void handleListeners() {
        HandlerList.unregisterAll(worldListener);
    }

    public void addMap(LocalGameMap gameMap) {
        
        allMaps.add(gameMap);

        for (WizardsMode mode : gameMap.getModes()) {
            modeMapsCache.computeIfAbsent(mode, k -> new ArrayList<>()).add(gameMap);
        }
    }

    public WizardsPlugin getPlugin() {
        return plugin;
    }

    public LocalGameMap getActiveMap() {
        return this.activeMap;
    }

    public List<LocalGameMap> getAllMaps() {
        return allMaps;
    }

    public List<LocalGameMap> getAllMaps(WizardsMode mode) {

        List<LocalGameMap> modeSpecific = new ArrayList<>();

        for (LocalGameMap map : allMaps) {

            if (map.getModes().contains(mode))
                modeSpecific.add(map);
        }

        return modeSpecific;
    }

    public void setActiveMap(LocalGameMap newActiveMap) {
        // If the new map is the same as the current one, do nothing.
        if (this.activeMap != null && this.activeMap.equals(newActiveMap) && this.activeMap.isLoaded()) {
            return;
        }

        // Unload the old map first, if it exists.
        if (this.activeMap != null) {
            this.activeMap.unload();
        }

        // Set the new map and then load it.
        this.activeMap = newActiveMap;

        if (this.activeMap != null) {
            this.activeMap.load(); // The map is now loaded here.
            Bukkit.getLogger().info(String.format("%s selected as active map.", this.activeMap.getName()));
        }
    }

    public MapEditingManager getMapEditingManager() {
        return this.mapEditingManager;
    }
}