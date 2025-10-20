package dev.thomashanson.wizards.map;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.mode.WizardsMode;
import dev.thomashanson.wizards.util.FileUtil;
import dev.thomashanson.wizards.util.LocationUtil;

/**
 * Manages a game map by copying a source world folder to a temporary, active folder
 * that can be safely modified and deleted after a match.
 *
 * Implements {@link Comparable} to allow sorting maps by name.
 */
public class LocalGameMap implements GameMap, Comparable<LocalGameMap> {

    // Prevent typos by using constants for YAML keys
    private static final String KEY_CORE = "core";
    private static final String KEY_LOCATIONS = "locations";
    private static final String KEY_NAME = "name";
    private static final String KEY_AUTHORS = "authors";
    private static final String KEY_MODES = "modes";
    private static final String KEY_MIN = "min";
    private static final String KEY_MAX = "max";
    private static final String KEY_SPAWNS = "spawns";
    private static final String KEY_SPECTATOR = "spectator";

    private final WizardsPlugin plugin;
    private final File srcWorldFolder;
    private final YamlConfiguration dataFile;
    private final Set<WizardsMode> modes = new HashSet<>();

    private World world;
    private File activeWorldFolder;

    public LocalGameMap(WizardsPlugin plugin, File worldFolder, String worldName) {
        this.plugin = plugin;
        this.srcWorldFolder = new File(worldFolder, worldName);
        File dataYmlFile = new File(srcWorldFolder, "data.yml");

        if (!dataYmlFile.exists()) {
            plugin.getLogger().severe("Map data file not found for '" + worldName + "' at: " + dataYmlFile.getAbsolutePath());
            this.dataFile = new YamlConfiguration(); // Create empty config to prevent NPEs
            return;
        }

        this.dataFile = YamlConfiguration.loadConfiguration(dataYmlFile);
        parseModes();
    }

    private void parseModes() {
        String modesString = getCoreSection().getString(KEY_MODES);
        if (modesString == null || modesString.isEmpty()) {
            return;
        }

        for (String mode : modesString.split(",")) {
            try {
                WizardsMode parsedMode = WizardsMode.valueOf(mode.trim().toUpperCase());
                this.modes.add(parsedMode);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid mode '" + mode + "' in data.yml for map '" + getName() + "'.");
            }
        }
    }

    /**
     * Loads the map into a playable state.
     * <p>
     * <b>Threading Note:</b> This method performs heavy file I/O and should be
     * called from an asynchronous thread. It will handle dispatching Bukkit API
     * calls back to the main server thread where required.
     *
     * @return {@code true} if the map was loaded successfully.
     */
    @Override
    public boolean load() {
        if (isLoaded()) {
            return true;
        }

        // --- ASYNC --- Heavy file I/O should not be on the main thread
        this.activeWorldFolder = new File(
            Bukkit.getWorldContainer().getParentFile(),
            srcWorldFolder.getName() + "_active_" + System.currentTimeMillis()
        );

        try {
            // Updated to use Path-based FileUtil
            FileUtil.copy(srcWorldFolder.toPath(), activeWorldFolder.toPath());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to copy map files for '" + getName() + "'!", e);
            return false;
        }
        // --- END ASYNC ---

        // --- SYNC --- Bukkit API calls must be on the main thread
        WorldCreator worldCreator = new WorldCreator(activeWorldFolder.getName());
        this.world = Bukkit.createWorld(worldCreator);

        if (world == null) {
            plugin.getLogger().severe("Failed to create Bukkit World for '" + getName() + "'.");
            // Cleanup the copied folder on failure
            try {
                FileUtil.delete(activeWorldFolder.toPath());
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to clean up active world folder after load failure!", e);
            }
            return false;
        }

        // Configure world properties
        configureWorld();
        return true;
    }

    private void configureWorld() {
        world.setAutoSave(false);
        world.setDifficulty(Difficulty.NORMAL);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setTime(6000L); // Noon
        world.setStorm(false);
        world.setThundering(false);
    }

    /**
     * Unloads the map, teleporting players out and deleting the temporary world files.
     * <p>
     * <b>Threading Note:</b> This method dispatches file deletion to an async thread
     * after handling player teleports on the main thread.
     */
    @Override
    public void unload() {
        if (!isLoaded()) {
            return;
        }

        World lobby = Bukkit.getWorld(plugin.getConfig().getString("lobby-world", "world"));
        if (lobby == null) {
            plugin.getLogger().severe("Cannot unload map: Fallback lobby world is not loaded!");
            return;
        }

        // Create a copy to avoid ConcurrentModificationException while teleporting
        for (Player player : new ArrayList<>(world.getPlayers())) {
            player.teleport(lobby.getSpawnLocation());
        }

        if (!Bukkit.unloadWorld(world, false)) {
            plugin.getLogger().warning("Failed to unload Bukkit world: " + world.getName());
        }
        // --- END SYNC ---

        // --- ASYNC --- Deleting files should be off the main thread
        try {
            // Updated to use Path-based FileUtil and handle IOException
            FileUtil.delete(activeWorldFolder.toPath());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete active world folder for " + getName(), e);
        }
        // --- END ASYNC ---

        this.world = null;
        this.activeWorldFolder = null;
    }

    @Override
    public boolean isLoaded() {
        return world != null && Bukkit.getWorld(world.getUID()) != null;
    }

    @Override
    public World getWorld() {
        return world;
    }

    public String getName() {
        return getCoreSection().getString(KEY_NAME, "Unknown Map");
    }

    public String getAuthors() {
        return String.join(", ", getCoreSection().getStringList(KEY_AUTHORS));
    }

    public Set<WizardsMode> getModes() {
        return modes;
    }

    public Location getSpectatorLocation() {
        String spectatorString = getLocations().getString(KEY_SPECTATOR);
        // Updated to use new LocationUtil method name
        return spectatorString != null ? LocationUtil.fromString(world, spectatorString) : world.getSpawnLocation();
    }

    public void setSpectatorLocation(Location location) {
        // Updated to use constant and new LocationUtil method name
        getLocations().set(KEY_SPECTATOR, LocationUtil.toString(location));
    }

    public List<Location> getSpawnLocations() {
        List<Location> locations = new ArrayList<>();
        if (!isLoaded()) {
            return locations;
        }

        List<String> spawnStrings = getLocations().getStringList(KEY_SPAWNS);
        for (String locationString : spawnStrings) {
            Location loc = LocationUtil.fromString(getWorld(), locationString);
            if (loc != null) {
                locations.add(loc);
            }
        }
        return locations;
    }

    public void addSpawnLocation(Location location) {
        List<String> spawns = getLocations().getStringList(KEY_SPAWNS);
        // Updated to use constant and new LocationUtil method name
        spawns.add(LocationUtil.toString(location));
        getLocations().set(KEY_SPAWNS, spawns);
    }

    private ConfigurationSection getCoreSection() {
        return dataFile.getConfigurationSection(KEY_CORE) != null ? dataFile.getConfigurationSection(KEY_CORE) : dataFile.createSection(KEY_CORE);
    }

    private ConfigurationSection getLocations() {
        return dataFile.getConfigurationSection(KEY_LOCATIONS) != null ? dataFile.getConfigurationSection(KEY_LOCATIONS) : dataFile.createSection(KEY_LOCATIONS);
    }

    public BoundingBox getBounds() {
        ConfigurationSection min = getLocations().getConfigurationSection(KEY_MIN);
        ConfigurationSection max = getLocations().getConfigurationSection(KEY_MAX);

        if (min == null || max == null) {
            return new BoundingBox(0, 0, 0, 0, 0, 0); // Return empty box on error
        }
        return new BoundingBox(
            min.getDouble("x"), min.getDouble("y"), min.getDouble("z"),
            max.getDouble("x"), max.getDouble("y"), max.getDouble("z")
        );
    }

    public void setBounds(BoundingBox bounds) {
        ConfigurationSection locations = getLocations();
        locations.set("min.x", bounds.getMinX());
        locations.set("min.y", bounds.getMinY());
        locations.set("min.z", bounds.getMinZ());
        locations.set("max.x", bounds.getMaxX());
        locations.set("max.y", bounds.getMaxY());
        locations.set("max.z", bounds.getMaxZ());
    }

    public void saveDataFile() {
        File targetFile = new File(srcWorldFolder, "data.yml");
        try {
            dataFile.save(targetFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save data.yml for map '" + getName() + "'!", e);
        }
    }

    @Override
    public int compareTo(@NotNull LocalGameMap other) {
        return this.getName().compareTo(other.getName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalGameMap that = (LocalGameMap) o;
        return srcWorldFolder.equals(that.srcWorldFolder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(srcWorldFolder);
    }
}