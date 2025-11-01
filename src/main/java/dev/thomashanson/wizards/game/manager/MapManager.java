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

/**
 * Manages the loading, selection, and lifecycle of all {@link LocalGameMap}s.
 * <p>
 * This manager is responsible for:
 * <ul>
 * <li>Loading all map configurations from the {@code /maps/} directory on startup.</li>
 * <li>Tracking the currently active {@link LocalGameMap} for the game.</li>
 * <li>Handling the loading (copying) and unloading (deleting) of active map worlds.</li>
 * <li>Registering and unregistering global {@link WorldListener}s.</li>
 * </ul>
 */
public class MapManager {

    private final WizardsPlugin plugin;
    private final MapEditingManager mapEditingManager;

    /** The currently loaded and active map for the game. */
    private LocalGameMap activeMap;
    
    /** A list of all available maps found in the /maps/ directory. */
    private final List<LocalGameMap> allMaps = new ArrayList<>();
    
    /** A cache of maps available for each specific {@link WizardsMode}. */
    private final Map<WizardsMode, List<LocalGameMap>> modeMapsCache = new EnumMap<>(WizardsMode.class);

    /** The listener for global, non-game-specific world events (e.g., block burn, leaf decay). */
    private WorldListener worldListener;

    /**
     * Creates a new MapManager.
     *
     * @param plugin The main plugin instance.
     */
    public MapManager(WizardsPlugin plugin) {
        this.plugin = plugin;
        this.mapEditingManager = new MapEditingManager();
    }

    /**
     * Registers the global {@link WorldListener} to prevent unwanted environmental changes.
     */
    public void registerListeners() {
        this.worldListener = new WorldListener();
        plugin.getServer().getPluginManager().registerEvents(worldListener, plugin);
    }

    /**
     * Unregisters all listeners associated with this manager.
     */
    public void handleListeners() {
        HandlerList.unregisterAll(worldListener);
    }

    /**
     * Adds a newly discovered {@link LocalGameMap} to the manager's list
     * and sorts it into the mode-specific cache.
     *
     * @param gameMap The map to add.
     */
    public void addMap(LocalGameMap gameMap) {
        
        allMaps.add(gameMap);

        for (WizardsMode mode : gameMap.getModes()) {
            modeMapsCache.computeIfAbsent(mode, k -> new ArrayList<>()).add(gameMap);
        }
    }

    /**
     * @return The main plugin instance.
     */
    public WizardsPlugin getPlugin() {
        return plugin;
    }

    /**
     * @return The {@link LocalGameMap} that is currently loaded and in use, or null.
     */
    public LocalGameMap getActiveMap() {
        return this.activeMap;
    }

    /**
     * @return An unmodifiable list of all available game maps.
     */
    public List<LocalGameMap> getAllMaps() {
        return allMaps;
    }

    /**
     * Gets a list of all maps that are compatible with a specific {@link WizardsMode}.
     *
     * @param mode The game mode to filter by.
     * @return A list of compatible maps.
     */
    public List<LocalGameMap> getAllMaps(WizardsMode mode) {

        List<LocalGameMap> modeSpecific = new ArrayList<>();

        for (LocalGameMap map : allMaps) {

            if (map.getModes().contains(mode))
                modeSpecific.add(map);
        }

        return modeSpecific;
    }

    /**
     * Sets the active game map.
     * This method will automatically handle unloading any previously active map
     * and loading the new one.
     *
     * @param newActiveMap The {@link LocalGameMap} to make active.
     */
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

    /**
     * @return The {@link MapEditingManager} instance for map setup commands.
     */
    public MapEditingManager getMapEditingManager() {
        return this.mapEditingManager;
    }
}