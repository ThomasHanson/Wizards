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

public class KitManager implements Listener {

    private final WizardsPlugin plugin;
    private final DatabaseManager databaseManager;

    // --- Global Kit Storage ---
    private WizardsKit defaultKit = null;
    private final Map<String, WizardsKit> kitsByKey = new HashMap<>();
    private final Map<Integer, WizardsKit> kitsById = new HashMap<>();
    private final Map<Integer, Map<Integer, Integer>> kitUpgradeCosts = new HashMap<>();

    // --- Player-Specific Kit Data Cache ---
    private final Map<UUID, Map<Integer, Integer>> playerKitLevelsCache = new ConcurrentHashMap<>();
    private final Map<UUID, WizardsKit> selectedPlayerKits = new HashMap<>();

    public KitManager(WizardsPlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
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

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearPlayerKitsFromCache(event.getPlayer());
    }

    /**
     * Loads all kit definitions from the database. Should be called on startup.
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

    public void updatePlayerKitLevelCache(UUID playerUuid, int kitId, int newLevel) {
        playerKitLevelsCache.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(kitId, newLevel);
    }
    
    public void clearPlayerKitsFromCache(Player player) {
        playerKitLevelsCache.remove(player.getUniqueId());
        selectedPlayerKits.remove(player.getUniqueId());
    }

    // --- Getters and Setters ---

    public WizardsKit getDefaultKit() {
        return defaultKit;
    }

    public WizardsKit getKit(Player player) {
        return selectedPlayerKits.get(player.getUniqueId());
    }

    public void setKit(Player player, WizardsKit kit) {
        selectedPlayerKits.put(player.getUniqueId(), kit);
    }
    
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
    
    public WizardsKit getKit(String key) {
        return kitsByKey.get(key);
    }

    public WizardsKit getKit(int id) {
        return kitsById.get(id);
    }

    public Collection<WizardsKit> getAllKits() {
        return new ArrayList<>(kitsByKey.values());
    }

    public Map<Integer, Map<Integer, Integer>> getKitUpgradeCosts() {
        return kitUpgradeCosts;
    }
}