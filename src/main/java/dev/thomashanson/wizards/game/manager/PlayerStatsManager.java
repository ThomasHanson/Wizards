package dev.thomashanson.wizards.game.manager;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.CoinSummary;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.mode.GameTeam;
import dev.thomashanson.wizards.game.mode.WizardsMode;

/**
 * Manages player statistics for the Wizards minigame.
 * This class can be instantiated and used to add, retrieve, and manage player stats.
 * It can also optionally implement Listener to handle common Bukkit events for stat tracking.
 */
public class PlayerStatsManager {

    private final WizardsPlugin plugin;

    // --- Coin Economy Constants ---
    private static final int COINS_PER_KILL = 40;
    private static final int COINS_PER_ASSIST = 20;
    // --- Placement Coin Bonuses ---
    private static final int COINS_WINNER = 150;
    private static final int COINS_RUNNER_UP = 100;
    private static final int COINS_PODIUM = 50;

    // --- Participation Requirements ---
    private static final double MIN_DAMAGE_DEALT = 15.0;
    private static final int MIN_ASSISTS = 2;

    public PlayerStatsManager(WizardsPlugin plugin) {
        this.plugin = plugin;
    }

    // Main storage for player stats: UUID -> (StatName -> StatValue)
    // Using ConcurrentHashMap for thread safety if stats might be accessed/modified asynchronously.
    private final Map<UUID, Map<String, Double>> playerStats = new ConcurrentHashMap<>();

    // Enum for stat keys for better type safety, readability, and maintainability
    public enum StatType {
        // --- Core Combat Stats ---
        KILLS("kills"),
        ASSISTS("assists"),
        DEATHS("deaths"),
        DAMAGE_DEALT("damage_dealt"),
        DAMAGE_TAKEN("damage_taken"),

        // --- Gameplay & World Interaction ---
        CHESTS_LOOTED("chests_looted"),
        SPELLS_COLLECTED("spells_collected"),
        WANDS_COLLECTED("wands_collected"),
        POTIONS_DRANK("potions_drank"),

        // --- Spell & Resource Management ---
        SPELLS_CAST("spells_cast"),
        MANA_GAINED("mana_gained"),

        // --- Accuracy Tracking ---
        AIMED_SPELLS_CAST("aimed_spells_cast"),
        AIMED_SPELLS_HIT("aimed_spells_hit"),

        // --- Session & Progression Stats ---
        GAMES_PLAYED("games_played"),
        GAMES_WON("games_won"),
        WIN_STREAK("win_streak"),
        TIME_PLAYED_SECONDS("time_played_seconds"),
        PLAYERS_OUTLASTED("players_outlasted"),
        HIGHEST_KILL_STREAK("highest_kill_streak");

        private final String key;

        StatType(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        // Optional: a method to get StatType from its key string
        private static final Map<String, StatType> BY_KEY = new HashMap<>();
        static {
            for (StatType st : values()) {
                BY_KEY.put(st.key, st);
            }
        }
        public static StatType fromKey(String key) {
            return BY_KEY.get(key);
        }
    }

    /**
     * Adds a specified value to a player's stat.
     * If the player or stat does not exist, they will be created.
     *
     * @param player The player whose stat is to be updated.
     * @param stat   The type of stat to update.
     * @param value  The value to add to the stat (can be negative to subtract).
     */
    public void incrementStat(Player player, StatType stat, double value) {
        if (player == null || stat == null) {
            // Consider logging a warning if this occurs unexpectedly
            return;
        }
        incrementStat(player.getUniqueId(), stat, value);
    }

    /**
     * Adds a specified value to a player's stat by their UUID.
     *
     * @param playerUuid The UUID of the player.
     * @param stat       The type of stat to update.
     * @param value      The value to add.
     */
    public void incrementStat(UUID playerUuid, StatType stat, double value) {
        if (playerUuid == null || stat == null) {
            return;
        }
        // computeIfAbsent ensures the player has an entry in the outer map.
        // merge updates the specific stat, summing the new value with any existing value.
        playerStats.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                   .merge(stat.getKey(), value, Double::sum);
    }

    /**
     * Sets a player's stat to a specific value, overwriting any previous value.
     *
     * @param player The player whose stat is to be set.
     * @param stat   The type of stat to set.
     * @param value  The new value for the stat.
     */
    public void setStat(Player player, StatType stat, double value) {
        if (player == null || stat == null) {
            return;
        }
        setStat(player.getUniqueId(), stat, value);
    }

    /**
     * Sets a player's stat to a specific value by their UUID.
     *
     * @param playerUuid The UUID of the player.
     * @param stat       The type of stat to set.
     * @param value      The new value for the stat.
     */
    public void setStat(UUID playerUuid, StatType stat, double value) {
        if (playerUuid == null || stat == null) {
            return;
        }
        playerStats.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                   .put(stat.getKey(), value);
    }


    /**
     * Retrieves a specific stat for a player.
     *
     * @param player The player whose stat is to be retrieved.
     * @param stat   The type of stat to retrieve.
     * @return The value of the stat, or 0.0 if the player or stat is not found.
     */
    public double getStat(Player player, StatType stat) {
        if (player == null || stat == null) {
            return 0.0;
        }
        return getStat(player.getUniqueId(), stat);
    }

    /**
     * Retrieves a specific stat for a player by their UUID.
     *
     * @param playerUuid The UUID of the player.
     * @param stat       The type of stat to retrieve.
     * @return The value of the stat, or 0.0 if not found.
     */
    public double getStat(UUID playerUuid, StatType stat) {
        if (playerUuid == null || stat == null) {
            return 0.0;
        }
        return playerStats.getOrDefault(playerUuid, Collections.emptyMap())
                          .getOrDefault(stat.getKey(), 0.0);
    }

    /**
     * Retrieves all stats for a specific player.
     *
     * @param player The player whose stats are to be retrieved.
     * @return A new Map containing all stats for the player (StatName -> Value).
     * Returns an empty map if the player has no stats recorded.
     * Modifying this returned map will not affect the stored stats.
     */
    public Map<String, Double> getAllStats(Player player) {
        if (player == null) {
            return Collections.emptyMap();
        }
        return getAllStats(player.getUniqueId());
    }

    /**
     * Retrieves all stats for a specific player by UUID.
     *
     * @param playerUuid The UUID of the player.
     * @return A new Map containing all stats for the player. Empty if none.
     */
    public Map<String, Double> getAllStats(UUID playerUuid) {
        if (playerUuid == null) {
            return Collections.emptyMap();
        }
        Map<String, Double> stats = playerStats.get(playerUuid);
        if (stats == null) {
            return Collections.emptyMap();
        }
        // Return a copy to prevent external modification of the internal map
        return new HashMap<>(stats);
    }

    private CoinSummary.Builder calculateCoinSummaryBuilder(UUID playerUuid) {
        double damageDealt = getStat(playerUuid, StatType.DAMAGE_DEALT);
        int assists = (int) getStat(playerUuid, StatType.ASSISTS);

        CoinSummary.Builder builder = new CoinSummary.Builder();

        // If the player didn't participate, they don't get performance coins.
        // They WILL still get their placement bonus, which is added later.
        if (damageDealt < MIN_DAMAGE_DEALT && assists < MIN_ASSISTS) {
            return builder;
        }

        int kills = (int) getStat(playerUuid, StatType.KILLS);
        double minutes = getStat(playerUuid, StatType.TIME_PLAYED_SECONDS) / 60;
        
        // Build the summary with performance stats
        return builder
            .withKills(kills, COINS_PER_KILL)
            .withAssists(assists, COINS_PER_ASSIST);
    }

    /**
     * Saves all accumulated in-memory stats for ALL players to the database.
     * After saving, it clears all session stats to prepare for the next game.
     */
    public void saveAllPlayerStats(WizardsMode mode) {
        String gameModeName = mode.name();

        // Loop through each player's UUID in the session stats map
        playerStats.forEach((uuid, sessionStats) -> {
            
            String playerUUID = uuid.toString();

            // Loop through this player's individual stats for the session
            for (Map.Entry<String, Double> entry : sessionStats.entrySet()) {
                
                String statKey = entry.getKey();
                double statValue = entry.getValue();

                if (statValue == 0) continue;

                String sql = "INSERT INTO player_stats (player_uuid, game_mode, stat_key, stat_value) " +
                             "VALUES (?, ?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE " +
                             "stat_value = stat_value + VALUES(stat_value);";

                plugin.getDatabaseManager().executeUpdateAsync(sql, playerUUID, gameModeName, statKey, statValue);
            }
        });

        // After sending all save requests to the database, clear the in-memory map.
        clearAllGameStats();
        plugin.getLogger().info("All player session stats have been sent to the database and the cache has been cleared.");
    }

    /**
     * Processes all end-game rewards, including performance and placement bonuses.
     * This now iterates through the final rankings to assign coins and win stats.
     *
     * @param game The game instance that just ended.
     * @param finalRankings The ordered list of teams, from 1st place down.
     */
    public void processEndGameRewards(Wizards game, List<GameTeam> finalRankings) {
        for (int i = 0; i < finalRankings.size(); i++) {
            GameTeam team = finalRankings.get(i);
            int placement = i + 1;
            boolean isWinner = (placement == 1);

            for (UUID uuid : team.getTeamMembers()) {
            
                // Increment for winners, reset for everyone else.
                if (isWinner) {
                    String sql = "INSERT INTO player_stats (player_uuid, game_mode, stat_key, stat_value) " +
                                "VALUES (?, ?, 'win_streak', 1) " +
                                "ON DUPLICATE KEY UPDATE stat_value = stat_value + 1;";
                    plugin.getDatabaseManager().executeUpdateAsync(sql, uuid.toString(), game.getCurrentMode().name());

                } else {
                    String sql = "INSERT INTO player_stats (player_uuid, game_mode, stat_key, stat_value) " +
                                "VALUES (?, ?, 'win_streak', 0) " +
                                "ON DUPLICATE KEY UPDATE stat_value = 0;";
                    plugin.getDatabaseManager().executeUpdateAsync(sql, uuid.toString(), game.getCurrentMode().name());
                }

                incrementStat(uuid, StatType.GAMES_WON, isWinner ? 1 : 0);
            }

            int placementBonus = 0;
            String placementTitleKey = null;

            // Determine coin bonus and title based on placement
            switch (placement) {
                case 1:
                    placementBonus = COINS_WINNER;
                    placementTitleKey = "wizards.coins.placement.winner";
                    for (UUID uuid : team.getTeamMembers()) {
                        incrementStat(uuid, StatType.GAMES_WON, 1);
                    }
                    break;
                case 2:
                    placementBonus = COINS_RUNNER_UP;
                    placementTitleKey = "wizards.coins.placement.second";
                    break;
                case 3:
                    placementBonus = COINS_PODIUM;
                    placementTitleKey = "wizards.coins.placement.third";
                    break;
            }

            // Now, calculate and award coins for each player on this team
            for (UUID uuid : team.getTeamMembers()) {
                CoinSummary.Builder summaryBuilder = calculateCoinSummaryBuilder(uuid);

                if (placementTitleKey != null) {
                    // REFACTORED: Pass the key to the builder
                    summaryBuilder.withPlacementBonus(placementTitleKey, placementBonus);
                }

                CoinSummary summary = summaryBuilder.build();

                if (summary.hasEarnedCoins()) {
                    String sql = "UPDATE players SET coins = coins + ? WHERE uuid = ?";
                    plugin.getDatabaseManager().executeUpdateAsync(sql, summary.getTotalCoins(), uuid.toString());

                    Player teamMember = Bukkit.getPlayer(uuid);

                    if (teamMember != null && teamMember.isOnline()) {
                        summary.sendBreakdownMessage(teamMember, plugin.getLanguageManager());
                    }
                }
            }
        }

        // After all rewards are processed, save stats and clear the session cache.
        saveAllPlayerStats(game.getCurrentMode());
    }

    /**
     * Exports all stats for all players.
     * Useful for saving data to a database or for leaderboard calculations.
     *
     * @return A new Map where the key is the player's UUID and the value is another
     * Map of their stats (StatName -> StatValue).
     * This is a deep copy; modifying it will not affect stored stats.
     */
    public Map<UUID, Map<String, Double>> exportAllPlayerStats() {
        Map<UUID, Map<String, Double>> defensiveCopy = new HashMap<>();
        playerStats.forEach((uuid, statsMap) -> defensiveCopy.put(uuid, new HashMap<>(statsMap)));
        return defensiveCopy;
    }

    /**
     * Clears all recorded stats for a specific player.
     *
     * @param player The player whose stats should be cleared.
     */
    public void clearPlayerStats(Player player) {
        if (player == null) {
            return;
        }
        clearPlayerStats(player.getUniqueId());
    }

    /**
     * Clears all recorded stats for a specific player by UUID.
     * @param playerUuid The UUID of the player.
     */
    public void clearPlayerStats(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        playerStats.remove(playerUuid);
    }

    /**
     * Clears all stats for all players. Typically used at the end of a game
     * if stats are not persisted across games, or for a server-wide reset.
     */
    public void clearAllGameStats() {
        playerStats.clear();
    }
}