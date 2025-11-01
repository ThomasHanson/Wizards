package dev.thomashanson.wizards;

import java.io.File;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIBukkitConfig;
import dev.thomashanson.wizards.commands.WizardsCommand;
import dev.thomashanson.wizards.game.loot.LootManager;
import dev.thomashanson.wizards.game.manager.DamageManager;
import dev.thomashanson.wizards.game.manager.DatabaseManager;
import dev.thomashanson.wizards.game.manager.GameManager;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.thomashanson.wizards.game.manager.MapManager;
import dev.thomashanson.wizards.game.manager.PlayerStatsManager;
import dev.thomashanson.wizards.game.manager.WandManager;
import dev.thomashanson.wizards.game.potion.PotionManager;
import dev.thomashanson.wizards.game.spell.SpellManager;
import dev.thomashanson.wizards.game.state.types.SetupState;
import dev.thomashanson.wizards.hologram.HologramManager;
import dev.thomashanson.wizards.map.LocalGameMap;
import dev.thomashanson.wizards.projectile.ProjectileManager;
import dev.thomashanson.wizards.tutorial.TutorialManager;

/**
 * The main entry point for the Wizards Bukkit plugin.
 * This class handles the enabling and disabling of the plugin,
 * initialization of all core managers, and loading of configuration files.
 */
public class WizardsPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private LanguageManager languageManager;
    private MapManager mapManager;
    private GameManager gameManager;
    private WandManager wandManager;
    private LootManager lootManager;
    private DamageManager damageManager;
    private SpellManager spellManager;
    private PotionManager potionManager;
    private ProjectileManager projectileManager;
    private HologramManager hologramManager;
    private PlayerStatsManager statsManager;

    private Location lobbySpawnLocation;

    private static WizardsPlugin INSTANCE;

    @Override
    public void onEnable() {
        INSTANCE = this;

        saveDefaultConfig();
        getDataFolder().mkdirs();

        loadLobbyLocation();

        // --- Initialize All Managers ---
        String host = getConfig().getString("database.host");
        int port = getConfig().getInt("database.port");
        String database = getConfig().getString("database.database");
        String username = getConfig().getString("database.username");
        String password = getConfig().getString("database.password");

        this.databaseManager = new DatabaseManager(this, host, port, database, username, password);
        this.languageManager = new LanguageManager(this);
        this.mapManager = new MapManager(this);
        this.damageManager = new DamageManager(this);
        this.projectileManager = new ProjectileManager(this);

        this.hologramManager = new HologramManager(this);
        this.hologramManager.initialize();

        this.spellManager = new SpellManager(this);
        this.spellManager.loadSpells();

        this.statsManager = new PlayerStatsManager(this);
        new TutorialManager(this);

        this.wandManager = new WandManager(this); 

        this.lootManager = new LootManager(this);
        loadLootTables();

        this.gameManager = new GameManager(this);
        
        CommandAPI.onLoad(new CommandAPIBukkitConfig(this).verboseOutput(false));
        new WizardsCommand().register(this);

        gameManager.setState(new SetupState());
    }

    @Override
    public void onDisable() {
        if (mapManager != null) {
            mapManager.handleListeners();
            LocalGameMap activeMap = mapManager.getActiveMap();
            if (activeMap != null && activeMap.isLoaded()) {
                List<Player> players = activeMap.getWorld().getPlayers();
                for (Player player : players) {
                    player.kick();
                }
                activeMap.unload();
            }
        }
        if (gameManager != null) gameManager.handleListeners();
        if (projectileManager != null) projectileManager.stopUpdates();

        if (hologramManager != null) {
            hologramManager.shutdown();
        }
    }

    /**
     * Loads loot tables from the {@code loot.yml} file.
     * If the file does not exist, it is created from the plugin's resources.
     */
    private void loadLootTables() {
        getLogger().info("Loading loot tables...");
        File lootFile = new File(getDataFolder(), "loot.yml");
        if (!lootFile.exists()) {
            saveResource("loot.yml", false);
        }
        FileConfiguration lootConfig = YamlConfiguration.loadConfiguration(lootFile);

        this.lootManager.load(lootConfig, this.wandManager);
        getLogger().info("Loot tables loaded successfully.");
    }
    
    /**
     * Loads the lobby world and sets the lobby spawn point from {@code config.yml}.
     * If the lobby world is not loaded, it attempts to load it.
     */
    private void loadLobbyLocation() {
        String worldName = getConfig().getString("lobby.world");
        if (worldName == null || worldName.isEmpty()) {
            getLogger().severe("Lobby world name is not defined in config.yml!");
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            getLogger().info(String.format("Lobby world '%s' not found, attempting to load it now...", worldName));
            try {
                world = new WorldCreator(worldName).createWorld();
                getLogger().info(String.format("Successfully loaded world '%s'.", worldName));
            } catch (Exception e) {
                getLogger().severe(String.format("An error occurred while trying to load world '%s'!", worldName));
                e.printStackTrace();
                return;
            }
        }
        if (world == null) {
            getLogger().severe(String.format("Failed to load lobby world '%s'. Is the folder name correct?", worldName));
            return;
        }
        configureLobbyWorld(world);
        double x = getConfig().getDouble("lobby.spawn-point.x");
        double y = getConfig().getDouble("lobby.spawn-point.y");
        double z = getConfig().getDouble("lobby.spawn-point.z");
        this.lobbySpawnLocation = new Location(world, x, y, z);
        getLogger().info("Lobby spawn location set successfully!");
    }

    /**
     * Applies specific, non-griefing gamerules to the lobby world.
     *
     * @param world The lobby world to configure.
     */
    private void configureLobbyWorld(World world) {
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setTime(6000L);
        world.setStorm(false);
        world.setThundering(false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
        world.setKeepSpawnInMemory(true);
        getLogger().info("Applied custom gamerules to lobby world '" + world.getName() + "'.");
    }

    /**
     * @return The pre-configured spawn location for the lobby.
     */
    public Location getLobbySpawnLocation() { return this.lobbySpawnLocation; }
    
    public LanguageManager getLanguageManager() { return languageManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public MapManager getMapManager() { return mapManager; }
    public GameManager getGameManager() { return gameManager; }
    public DamageManager getDamageManager() { return damageManager; }
    public ProjectileManager getProjectileManager() { return projectileManager; }
    public HologramManager getHologramManager() { return hologramManager; }
    public PlayerStatsManager getStatsManager() { return this.statsManager; }
    public SpellManager getSpellManager() { return spellManager; }
    public PotionManager getPotionManager() { return potionManager; }
    public LootManager getLootManager() { return lootManager; }
    public WandManager getWandManager() { return wandManager; }
    
    /**
     * @return The static singleton instance of the main plugin class.
     * @throws IllegalStateException if the plugin has not been enabled yet.
     */
    public static WizardsPlugin getInstance() { 
        if (INSTANCE == null) {
            throw new IllegalStateException("WizardsPlugin has not been enabled yet!");
        }
        return INSTANCE; 
    }
}