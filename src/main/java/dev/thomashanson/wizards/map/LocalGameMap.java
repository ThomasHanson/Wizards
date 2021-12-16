package dev.thomashanson.wizards.map;

import dev.thomashanson.wizards.util.FileUtil;
import dev.thomashanson.wizards.util.LocationUtil;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class LocalGameMap implements GameMap {

    private World world;
    private final YamlConfiguration dataFile;

    private final File srcWorldFolder;
    private File activeWorldFolder;

    private final Set<Location> chestLocations = new HashSet<>();

    public LocalGameMap(File worldFolder, String worldName, boolean loadOnInit) {

        this.srcWorldFolder = new File(worldFolder, worldName);
        this.dataFile = YamlConfiguration.loadConfiguration(new File(srcWorldFolder, "/data.yml"));

        if (loadOnInit)
            load();
    }

    @Override
    public boolean load() {

        if (isLoaded())
            return true;

        this.activeWorldFolder = new File (
                Bukkit.getWorldContainer().getParentFile(),
                srcWorldFolder.getName() + "_active_" + System.currentTimeMillis()
        );

        try {
            FileUtil.copy(srcWorldFolder, activeWorldFolder);

        } catch (IOException e) {
            Bukkit.getLogger().severe("Failed to load map from source folder!");
            e.printStackTrace();
            return false;
        }

        this.world = Bukkit.createWorld (
                new WorldCreator(activeWorldFolder.getName())
                        //.environment(World.Environment.valueOf(getCoreSection().getString("type")))
        );

        if (world != null) {

            world.setAutoSave(false);

            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);

            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);

            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);

            world.setTime(12000L);
            world.setStorm(false);
            world.setThundering(false);
        }

        ConfigurationSection locationsSection = dataFile.getConfigurationSection("locations");

        if (locationsSection != null)
            for (String location : locationsSection.getStringList("chests"))
                addChestLocation(LocationUtil.locationFromConfig(world, location));

        return isLoaded();
    }

    @Override
    public void unload() {

        Bukkit.getLogger().info("Unloading map: " + activeWorldFolder.getName());

        if (world != null)
            Bukkit.unloadWorld(world, false);

        if (activeWorldFolder != null)
            FileUtil.delete(activeWorldFolder);

        world = null;
        activeWorldFolder = null;
    }

    @Override
    public boolean isLoaded() {
        return getWorld() != null;
    }

    @Override
    public World getWorld() {
        return world;
    }

    public String getName() {
        return getCoreSection().getString("name");
    }

    public String getAuthors() {
        return String.join(", ", getCoreSection().getStringList("authors"));
    }

    public double getMinX() {
        return Objects.requireNonNull(getLocations().getConfigurationSection("min")).getDouble("x");
    }

    public double getMinY() {
        return Objects.requireNonNull(getLocations().getConfigurationSection("min")).getDouble("y");
    }

    public double getMinZ() {
        return Objects.requireNonNull(getLocations().getConfigurationSection("min")).getDouble("z");
    }

    public double getMaxX() {
        return Objects.requireNonNull(getLocations().getConfigurationSection("max")).getDouble("x");
    }

    public double getMaxY() {
        return Objects.requireNonNull(getLocations().getConfigurationSection("max")).getDouble("y");
    }

    public double getMaxZ() {
        return Objects.requireNonNull(getLocations().getConfigurationSection("max")).getDouble("z");
    }

    public List<Location> getSpawnLocations() {

        List<Location> locations = new ArrayList<>();

        for (String location : getLocations().getStringList("spawns"))
            locations.add(LocationUtil.locationFromConfig(world, location));

        return locations;
    }

    public Location getSpectatorLocation() {

        return LocationUtil.locationFromConfig (

                world,

                Objects.requireNonNull(
                        Objects.requireNonNull(getLocations()).getString("spectator")
                )
        );
    }

    private void addChestLocation(Location location) {
        chestLocations.add(location);
    }

    public Set<Location> getChestLocations() {
        return chestLocations;
    }

    private ConfigurationSection getCoreSection() {
        return dataFile.getConfigurationSection("core");
    }

    private ConfigurationSection getLocations() {
        return dataFile.getConfigurationSection("locations");
    }

    public YamlConfiguration getDataFile() {
        return dataFile;
    }

    public File getActiveWorldFolder() {
        return activeWorldFolder;
    }
}