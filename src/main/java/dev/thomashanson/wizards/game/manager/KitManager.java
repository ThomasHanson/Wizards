package dev.thomashanson.wizards.game.manager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.kit.WizardsKit;
import dev.thomashanson.wizards.game.kit.types.KitEnchantress;
import dev.thomashanson.wizards.game.kit.types.KitLich;
import dev.thomashanson.wizards.game.kit.types.KitMage;
import dev.thomashanson.wizards.game.kit.types.KitMystic;
import dev.thomashanson.wizards.game.kit.types.KitScholar;
import dev.thomashanson.wizards.game.kit.types.KitSorcerer;
import dev.thomashanson.wizards.game.kit.types.KitWarlock;

/**
 * Manages the loading, caching, and selection of all {@link WizardsKit}s.
 * <p>
 * This manager is responsible for:
 * <ul>
 * <li>Loading all kit definitions and upgrade costs from the database on startup.</li>
 * <li>Caching which kits and levels each online player has unlocked.</li>
 * <li>Managing the player's currently selected kit for the next game.</li>
 * <li>Providing access to the default kit for new players.</li>
 * </ul>
 */
public class KitManager implements Listener {

    private final WizardsPlugin plugin;
    private final DatabaseManager databaseManager;

    // --- Global Kit Storage ---

    /** The kit assigned to players by default if they have none selected. */
    private WizardsKit defaultKit = null;

    /** Caches all loaded kits, keyed by their string identifier (e.g., "scholar"). */
    private final Map<String, WizardsKit> kitsByKey = new HashMap<>();
    
    /** Caches all loaded kits, keyed by their database ID. */
    private final Map<Integer, WizardsKit> kitsById = new HashMap<>();
    
    /** Caches the upgrade costs for all kits. Map<KitID, Map<Level, Cost>> */
    private final Map<Integer, Map<Integer, Integer>> kitUpgradeCosts = new HashMap<>();

    // --- Player-Specific Kit Data Cache ---
    
    /** Caches the unlocked levels for each online player. Map<PlayerUUID, Map<KitID, Level>> */
    private final Map<UUID, Map<Integer, Integer>> playerKitLevelsCache = new ConcurrentHashMap<>();
    
    /** Caches the kit each player has actively selected for the upcoming game. */
    private final Map<UUID, WizardsKit> selectedPlayerKits = new HashMap<>();

    /**
     * Creates a new KitManager.
     *
     * @param plugin The main plugin instance.
     */
    public KitManager(WizardsPlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * Loads a player's kit data into the cache upon them joining the server.
     * Also assigns them the default kit if they have none selected.
     *
     * @param event The player join event.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        loadPlayerKitsIntoCache(player);

        // --- NEW: Assign default kit ---
        // Use a small delay to ensure the player is fully loaded
        new BukkitRunnable() {
            @Override
            public void run() {
                // Check if the player DOES NOT have a kit selected for this session
                if (getKit(player) == null) {
                    if (defaultKit != null) {
                        // Silently set the kit without a chat message
                        setKit(player, defaultKit);
                    }
                }
            }
        }.runTaskLater(plugin, 5L);
    }

    /**
     * Clears a player's kit data from the cache upon them leaving the server.
     *
     * @param event The player quit event.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearPlayerKitsFromCache(event.getPlayer());
    }

    /**
     * Loads all kit definitions from the 'kits' table in the database.
     * This uses reflection to instantiate the correct {@link WizardsKit} subclass
     * based on the 'kit_key' column.
     *
     * @param game The main game instance, required for kit constructors.
     */
    public void loadKitsFromDatabase(Wizards game) {
        String sql = "SELECT * FROM kits;";
        databaseManager.executeQueryAsync(sql, results -> {
            kitsByKey.clear();
            kitsById.clear();

            for (Map<String, Object> kitData : results) {
                String key = (String) kitData.get("kit_key");
                WizardsKit kit = switch (key) {
                    case "scholar" -> new KitScholar(game, kitData);
                    case "mage" -> new KitMage(game, kitData);
                    case "sorcerer" -> new KitSorcerer(game, kitData);
                    case "mystic" -> new KitMystic(game, kitData);
                    case "warlock" -> new KitWarlock(game, kitData);
                    case "enchantress" -> new KitEnchantress(game, kitData);
                    case "lich" -> new KitLich(game, kitData);
                    default -> {
                        plugin.getLogger().warning("No matching Kit class found for kit_key: '" + key + "'");
                        yield null;
                    }
                };

                if (kit != null) {
                    kitsByKey.put(kit.getKey(), kit);
                    kitsById.put(kit.getId(), kit);

                    if (kit.getUnlockType() == WizardsKit.UnlockType.DEFAULT) {
                        this.defaultKit = kit;
                        plugin.getLogger().info("Found and set default kit: " + kit.getNameKey());
                    }
                }
            }
            plugin.getLogger().info("Successfully loaded " + kitsByKey.size() + " kits from the database.");
        });
    }
    
    /**
     * Loads all kit upgrade costs from the 'kit_upgrade_costs' table in the database.
     */
    public void loadKitUpgradeCosts() {
        String sql = "SELECT kit_id, level, cost FROM kit_upgrade_costs";
        databaseManager.executeQueryAsync(sql, results -> {
            kitUpgradeCosts.clear(); // Clear before loading
            for (Map<String, Object> row : results) {
                int kitId = ((Number) row.get("kit_id")).intValue();
                int level = ((Number) row.get("level")).intValue();
                int cost = ((Number) row.get("cost")).intValue();

                kitUpgradeCosts.computeIfAbsent(kitId, k -> new HashMap<>()).put(level, cost);
            }
            plugin.getLogger().info("Successfully loaded and cached " + results.size() + " kit upgrade tiers.");
        });
    }

    /**
     * Fetches an individual player's unlocked kits and levels from the database
     * and stores them in the {@link #playerKitLevelsCache}.
     *
     * @param player The player whose kits to load.
     */
    public void loadPlayerKitsIntoCache(Player player) {
        UUID playerUuid = player.getUniqueId();
        String sql = "SELECT kit_id, level FROM player_kits WHERE player_uuid = ?";

        databaseManager.executeQueryAsync(sql, results -> {
            Map<Integer, Integer> kitLevels = new ConcurrentHashMap<>();
            for (Map<String, Object> row : results) {
                int kitId = ((Number) row.get("kit_id")).intValue();
                int level = ((Number) row.get("level")).intValue();
                kitLevels.put(kitId, level);
            }
            playerKitLevelsCache.put(playerUuid, kitLevels);
            plugin.getLogger().info("Loaded " + kitLevels.size() + " kits into cache for " + player.getName());
        }, playerUuid.toString());
    }

    /**
     * Updates a player's cached kit level. This is called after a player
     * purchases or upgrades a kit, to avoid needing another database query.
     *
     * @param playerUuid The player's UUID.
     * @param kitId      The ID of the kit that was changed.
     * @param newLevel   The new level for the kit.
     */
    public void updatePlayerKitLevelCache(UUID playerUuid, int kitId, int newLevel) {
        playerKitLevelsCache.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(kitId, newLevel);
    }
    
    /**
     * Removes a player's data from all caches. Called on player quit.
     *
     * @param player The player to remove.
     */
    public void clearPlayerKitsFromCache(Player player) {
        playerKitLevelsCache.remove(player.getUniqueId());
        selectedPlayerKits.remove(player.getUniqueId());
    }

    // --- Getters and Setters ---

    /**
     * @return The default {@link WizardsKit} assigned to new players.
     */
    public WizardsKit getDefaultKit() {
        return defaultKit;
    }

    /**
     * @param player The player.
     * @return The {@link WizardsKit} the player has selected for the next game.
     */
    public WizardsKit getKit(Player player) {
        return selectedPlayerKits.get(player.getUniqueId());
    }

    /**
     * Sets a player's selected kit for the next game.
     *
     * @param player The player.
     * @param kit    The {@link WizardsKit} to select.
     */
    public void setKit(Player player, WizardsKit kit) {
        selectedPlayerKits.put(player.getUniqueId(), kit);
    }
    
    /**
     * Gets the purchased level for the player's currently selected kit.
     *
     * @param player The player.
     * @return The level (1-5) of their selected kit. Defaults to 1.
     */
    public int getSelectedKitLevel(Player player) {
        UUID playerUuid = player.getUniqueId();
        WizardsKit selectedKit = selectedPlayerKits.get(playerUuid);

        if (selectedKit == null) {
            return 1;
        }
        
        return playerKitLevelsCache
                .getOrDefault(playerUuid, Collections.emptyMap())
                .getOrDefault(selectedKit.getId(), 1);
    }
    
    /**
     * @param key The string key (e.g., "scholar").
     * @return The {@link WizardsKit} associated with that key, or null.
     */
    public WizardsKit getKit(String key) {
        return kitsByKey.get(key);
    }

    /**
     * @param id The database ID.
     * @return The {@link WizardsKit} associated with that ID, or null.
     */
    public WizardsKit getKit(int id) {
        return kitsById.get(id);
    }

    /**
     * @return An unmodifiable collection of all loaded {@link WizardsKit} definitions.
     */
    public Collection<WizardsKit> getAllKits() {
        return new ArrayList<>(kitsByKey.values());
    }

    /**
     * @return The cached map of all kit upgrade costs (KitID -> Level -> Cost).
     */
    public Map<Integer, Map<Integer, Integer>> getKitUpgradeCosts() {
        return kitUpgradeCosts;
    }
}