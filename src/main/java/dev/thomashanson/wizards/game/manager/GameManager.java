package dev.thomashanson.wizards.game.manager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.manager.PlayerStatsManager.StatType;
import dev.thomashanson.wizards.game.mode.WizardsMode;
import dev.thomashanson.wizards.game.state.GameState;
import dev.thomashanson.wizards.game.state.listener.GameListener;
import dev.thomashanson.wizards.scoreboard.ScoreboardOptions;
import dev.thomashanson.wizards.scoreboard.WizardsScoreboard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;


/**
 * Manages the high-level state of the Wizards server, including game transitions,
 * player sessions, and the master game loop.
 * <p>
 * This class is a singleton for the plugin, responsible for:
 * <ul>
 * <li>Transitioning between {@link GameState}s (e.g., Lobby, Prepare, Active).</li>
 * <li>Creating, managing, and destroying {@link Wizards} game instances.</li>
 * <li>Running the master {@link Tickable} loop that drives all active game components.</li>
 * <li>Managing global managers that persist between games, like {@link KitManager}.</li>
 * <li>Handling player connections and disconnections, and managing scoreboards.</li>
 * </ul>
 * It acts as the "kernel" for the minigame, coordinating all other managers.
 */
public class GameManager implements Listener {

    private final WizardsPlugin plugin;
    private final KitManager kitManager;
    private Wizards activeGame;
    private Listener activeGameListener;
    
    private BukkitTask masterTickTask;
    private long gameTickCounter = 0;

    /**
     * A thread-safe list of all components that need to be updated by the master loop.
     * This includes the active game instance itself, disasters, and any ticking spells.
     */
    private final List<Tickable> tickableComponents = new CopyOnWriteArrayList<>();

    private GameState state;
    private WizardsMode nextGameMode;
    private List<GameState> previousStates = new ArrayList<>();

    private boolean forceStart = false;
    public static final String SPECTATING_KEY = "spectating";

    private WizardsScoreboard wizardsScoreboard;

    public GameManager(WizardsPlugin plugin) {
        this.plugin = plugin;
        this.kitManager = new KitManager(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        String defaultModeStr = plugin.getConfig().getString("mode", "SOLO_NORMAL");
        try {
            this.nextGameMode = WizardsMode.valueOf(defaultModeStr.toUpperCase());
            Bukkit.getLogger().info(String.format("Default game mode loaded from config: %s", this.nextGameMode));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning(String.format("Invalid mode '%s' in config.yml. Defaulting to SOLO_NORMAL.", defaultModeStr));
            this.nextGameMode = WizardsMode.SOLO_NORMAL;
        }

        this.wizardsScoreboard = new WizardsScoreboard(plugin, this, plugin.getLanguageManager(), ScoreboardOptions.DEFAULT_OPTIONS);

        wizardsScoreboard.setTitleGenerator(
            player -> plugin.getLanguageManager().getTranslated(player, "wizards.scoreboard.title")
        );

        wizardsScoreboard.setLineGenerator(player -> {
            LanguageManager lang = plugin.getLanguageManager();
            List<Component> components = new ArrayList<>();

            // --- HEADER ---
            // The date format can now be defined in the language file if needed
            SimpleDateFormat format = new SimpleDateFormat("MM/dd/yy");
            format.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles")); // Using a specific ZoneId is safer
            String dateStr = format.format(new Date());
            
            components.add(lang.getTranslated(player, "wizards.scoreboard.date",
                Placeholder.unparsed("date", dateStr)
            ));
            components.add(Component.text("")); // This is now the ONLY blank line after the date

            // --- STATE-SPECIFIC CONTENT ---
            // The GameState methods (updated below) no longer add their own spacing
            List<Component> stateComponents = state.getScoreboardComponents(player);
            if (stateComponents != null) {
                components.addAll(stateComponents);
            }

            // --- FOOTER ---
            if (activeGame != null) {
                components.add(Component.text(""));
                components.add(lang.getTranslated(player, "wizards.scoreboard.map",
                    Placeholder.unparsed("map", activeGame.getActiveMap().getName())
                ));
                components.add(lang.getTranslated(player, "wizards.scoreboard.mode",
                    Placeholder.unparsed("mode", activeGame.getCurrentMode().toString())
                ));

                // Example: Displaying teammates or enemies
                // This uses the [PLAYER:name] convention handled by WizardsScoreboard.processLineForPlayer
                // Optional<ScoreboardTeam> playerTeam = wizardsScoreboard.getGameTeamForPlayer(player.getUniqueId());
                // if (playerTeam.isPresent()) {
                //     lines.add(""); // Spacer
                //     lines.add(playerTeam.get().getTeamColor() + playerTeam.get().getDisplayName() + " Team:");
                //     playerTeam.get().getMembers().stream()
                //               .map(Bukkit::getPlayer)
                //               .filter(Objects::nonNull)
                //               .filter(p -> !p.equals(player)) // Don't list self here
                //               .limit(3) // Limit number of teammates shown
                //               .forEach(tm -> lines.add("  " + ChatColor.GRAY + "- " + "[PLAYER:" + tm.getName() + "]"));
                // }
            }

            return components;
        });
    }

    /**
     * Starts the single, unified game loop for the active game.
     * This is the heart of the "best of the best" architecture.
     */
    public void startGameLoop() {
        if (masterTickTask != null) {
            masterTickTask.cancel();
        }

        if (activeGame == null) {
            plugin.getLogger().severe("Attempted to start game loop with no active game!");
            return;
        }
      
        // The active game instance itself is the first tickable component.
        // We will move the logic from Wizards#updateGame into Wizards#tick.
        registerTickable(activeGame);

        // Now, find and register all spells that are tickable
        for (dev.thomashanson.wizards.game.spell.Spell spell : plugin.getSpellManager().getAllSpells().values()) {
            if (spell instanceof Tickable) {
                registerTickable((Tickable) spell);
                plugin.getLogger().info(String.format("Registered tickable spell: ", spell.getKey()));
            }
        }

        masterTickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Iterate through all registered components and tick them.
            for (Tickable component : tickableComponents) {
                if (gameTickCounter % component.getTickInterval() == 0) {
                    try {
                        component.tick(gameTickCounter);
                    } catch (Exception e) {
                        // Catching exceptions here prevents one faulty component
                        // (e.g., a broken spell) from crashing the entire game loop.
                        plugin.getLogger().severe(String.format("Error while ticking component: %s", component.getClass().getSimpleName()));
                        e.printStackTrace();
                    }
                }
            }
            gameTickCounter++;
        }, 0L, 1L);
    }

    public void stopGameLoop() {
        if (masterTickTask != null) {
            masterTickTask.cancel();
            masterTickTask = null;
        }
        // Clear all tickable components to prepare for the next game
        tickableComponents.clear();
        gameTickCounter = 0;
    }

    /**
     * Registers a component to be updated by the master game loop.
     * @param component The Tickable component to add.
     */
    public void registerTickable(Tickable component) {
        Objects.requireNonNull(component, "Tickable component cannot be null");
        tickableComponents.add(component);
    }

    /**
     * Unregisters a component from the master game loop.
     * @param component The Tickable component to remove.
     */
    public void unregisterTickable(Tickable component) {
        tickableComponents.remove(component);
    }

    public void handleListeners() {
        HandlerList.unregisterAll(this);
    }

    public void setState(GameState state) {
        setState(state, this.state);
    }

    private void setState(GameState newState, GameState oldState) {

        previousStates.add(oldState);

        if (oldState != null)
            oldState.onDisable();

        state = newState;
        state.onEnable(plugin);

        if (wizardsScoreboard != null)
            wizardsScoreboard.updateAllScoreboards();
    }

    public double getStat(Player player, StatType stat) {
        return plugin.getStatsManager().getStat(player, stat);
    }

    public void incrementStat(Player player, PlayerStatsManager.StatType stat, double amount) {
        plugin.getStatsManager().incrementStat(player, stat, amount);
    }

    public void gameAnnounce(Player player, boolean start, List<Component> messages) {

        LanguageManager lang = plugin.getLanguageManager();

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 2F, 1F);

        // Send blank lines using empty components
        for (int i = 0; i < 6 - messages.size(); i++)
            player.sendMessage(Component.empty());

        // Use the translated line break
        player.sendMessage(lang.getTranslated(player, "wizards.game.announcement.lineBreak"));

        // Use the translated header
        player.sendMessage(lang.getTranslated(player, "wizards.game.announcement.header"));
        player.sendMessage(Component.empty());

        // Loop through the Component messages
        for (Component message : messages) {
            if (start) {
                // Apply styling directly to the component
                player.sendMessage(message.colorIfAbsent(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
            } else {
                player.sendMessage(message);
            }
        }
        
        player.sendMessage(Component.empty());

        // Use the translated map line with placeholders
        player.sendMessage(lang.getTranslated(player, "wizards.game.announcement.mapLine",
                Placeholder.unparsed("map_name", activeGame.getActiveMap().getName()),
                Placeholder.unparsed("authors", activeGame.getActiveMap().getAuthors())
        ));

        player.sendMessage(lang.getTranslated(player, "wizards.game.announcement.lineBreak"));

        for (int i = 0; i < 6 - messages.size(); i++)
            player.sendMessage(Component.empty());
    }

    public boolean canStart() {
        boolean canStart = false;

        WizardsMode mode = this.getNextGameMode();

        int participatingPlayers = Bukkit.getOnlinePlayers().size();
        int maxPlayers = mode.getMaxPlayers();
        
        if (maxPlayers == 0) {
            return false;
        }

        double minRequirement = 0.75;

        // Note the cast to double on participatingPlayers to ensure floating-point division.
        if (((double) participatingPlayers / maxPlayers) >= minRequirement) {
            canStart = true;
        }

        return canStart || forceStart;
    }

    public void announce(String key, boolean withSound, TagResolver... placeholders) {
        if (key == null || key.isEmpty()) {
            // To send a blank line, just broadcast an empty component.
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(Component.empty()));
            return;
        }

        LanguageManager lang = plugin.getLanguageManager();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (withSound) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1F, 1F);
            }
            
            // Translate the message for each player and send it
            player.sendMessage(lang.getTranslated(player, "wizards.game.announcement.prefix",
                Placeholder.component("message", lang.getTranslated(player, key, placeholders))
            ));
        }

        // For logging, we can get a default translation
        Component logComponent = lang.getTranslated(null, key, placeholders);
        String logMessage = LegacyComponentSerializer.legacySection().serialize(logComponent);
        Bukkit.getLogger().info(String.format("[Announcement] %s", logMessage));
    }

    public void announce(String key, boolean withSound) {
        announce(key, withSound, TagResolver.empty());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        kitManager.loadPlayerKitsIntoCache(event.getPlayer());
        // Delay slightly to ensure player is fully initialized on server
        new BukkitRunnable() {
            @Override
            public void run() {
                wizardsScoreboard.addPlayer(player);
            }
        }.runTaskLater(plugin, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        wizardsScoreboard.removePlayer(event.getPlayer());
        kitManager.clearPlayerKitsFromCache(event.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) { // Also handle kicks
        wizardsScoreboard.removePlayer(event.getPlayer());
    }

    public void setForceStart(boolean forceStart) {
        this.forceStart = forceStart;
    }

    public WizardsScoreboard getScoreboard() {
        return wizardsScoreboard;
    }

    public void setScoreboard(WizardsScoreboard wizardsScoreboard) {
        this.wizardsScoreboard = wizardsScoreboard;
    }

    public Wizards getActiveGame() {
        return activeGame;
    }

    public void setActiveGame(Wizards game) {
        // --- DEBUG MESSAGES START ---
        Bukkit.getLogger().info("========================================");
        Bukkit.getLogger().info("[DEBUG] setActiveGame called.");

        if (activeGameListener != null) {
            Bukkit.getLogger().info("[DEBUG] An old GameListener exists. UNREGISTERING it now.");
            HandlerList.unregisterAll(activeGameListener);
            activeGameListener = null;
        }

        this.activeGame = game;

        if (game != null) {
            Bukkit.getLogger().info("[DEBUG] A new game instance was provided. REGISTERING a new GameListener.");
            this.activeGameListener = new GameListener(plugin, game);
            plugin.getServer().getPluginManager().registerEvents(activeGameListener, plugin);
        } else {
            Bukkit.getLogger().info("[DEBUG] Game instance was set to NULL.");
        }
        Bukkit.getLogger().info("========================================");
        // --- DEBUG MESSAGES END ---
    }

    public GameState getState() {
        return state;
    }

    public GameState getPreviousState() {
        return previousStates.get(previousStates.size() - 1);
    }

    public List<GameState> getPreviousStates() {
        return previousStates;
    }

    public WizardsMode getNextGameMode() {
        return this.nextGameMode;
    }

    public void setNextGameMode(WizardsMode mode) {
        if (this.nextGameMode == mode) return;

        this.nextGameMode = mode;
        Bukkit.getLogger().info(String.format("Next game mode has been changed to: %s", mode.name()));
        
        // You can add logic here to announce the change to players
        // announce("wizards.game.modeChanged", true, Placeholder.unparsed("mode", mode.toString()));
    }

    public KitManager getKitManager() {
        return this.kitManager;
    }
}