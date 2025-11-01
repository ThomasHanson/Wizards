package dev.thomashanson.wizards.game.state.types;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.kit.WizardsKit;
import dev.thomashanson.wizards.game.loot.LootManager;
import dev.thomashanson.wizards.game.manager.GameManager;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.thomashanson.wizards.game.mode.WizardsMode;
import dev.thomashanson.wizards.game.state.GameState;
import dev.thomashanson.wizards.game.state.listener.LobbyListener;
import dev.thomashanson.wizards.game.state.listener.StateListenerProvider;
import dev.thomashanson.wizards.map.LocalGameMap;
import dev.thomashanson.wizards.util.EntityUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class LobbyState extends GameState {

    private LobbyListener listener;
    private LanguageManager lang;
    private BukkitTask updateLobbyTask;

    private Instant lastTip;
    private int tipIndex = 0;
    private NamedTextColor tipColor = NamedTextColor.YELLOW;

    private boolean starting = false;
    private int countdownTime;
    private int timeUntilStart;

    @Override
    public void onEnable(WizardsPlugin plugin) {

        this.listener = new LobbyListener(plugin);
        this.lang = plugin.getLanguageManager();
        super.onEnable(plugin);

        for (Player player : Bukkit.getOnlinePlayers()) {
            listener.setupLobbyPlayer(player);
        }

        GameManager gameManager = getPlugin().getGameManager();
        GameState previousState = gameManager.getPreviousState();

        // Check if we arrived here from a completed game
        if (previousState instanceof ResetState) {
            Bukkit.getLogger().info("Previous game has ended! Returning to the lobby.");
        }

        gameManager.getPreviousStates().clear();

        ConfigurationSection lobbyConfig = plugin.getConfig().getConfigurationSection("lobby");
        this.countdownTime = lobbyConfig.getInt("countdown-seconds", 30);
        final int tipInterval = lobbyConfig.getInt("tip-interval-seconds", 20);
        final List<String> gameTipKeys = lobbyConfig.getStringList("game-tips");

        updateLobbyTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {

            plugin.getGameManager().getScoreboard().updateAllScoreboards();

            if (lastTip == null || Duration.between(lastTip, Instant.now()).toSeconds() >= tipInterval) {

                String tipKey = gameTipKeys.get(tipIndex);
                tipIndex = (tipIndex + 1) % gameTipKeys.size(); // Move this up

                // Alternate color based on the index
                tipColor = (tipIndex % 2 == 0) ? NamedTextColor.YELLOW : NamedTextColor.GOLD;

                Component prefix = Component.text("TIP: ", NamedTextColor.WHITE, TextDecoration.BOLD);

                for (Player player : Bukkit.getOnlinePlayers()) {
                    Component translatedTip = lang.getTranslated(player, tipKey);
                    Component finalMessage = prefix.append(translatedTip.color(tipColor));

                    player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1F, 1F);
                    player.sendMessage(finalMessage);
                }

                // Move to the next tip for the next broadcast
                tipIndex = (tipIndex + 1) % gameTipKeys.size();
            }
            
            // If the countdown is starting AND there is no active game, create one!
            if (getGame() == null) {
                
                Wizards newGame = new Wizards(plugin);
                gameManager.setActiveGame(newGame);
                newGame.setCurrentMode(gameManager.getNextGameMode());

                // Select a new random map from the manager
                List<LocalGameMap> maps = plugin.getMapManager().getAllMaps(newGame.getCurrentMode());

                if (maps.isEmpty()) {
                    Bukkit.getLogger().severe(String.format("No maps found for mode %s! Aborting game start.", newGame.getCurrentMode()));
                    gameManager.setActiveGame(null); // Abort
                    return;
                }

                LocalGameMap randomMap = maps.get(ThreadLocalRandom.current().nextInt(maps.size()));
                plugin.getMapManager().setActiveMap(randomMap);

                // Verify that the map actually loaded before continuing
                if (plugin.getMapManager().getActiveMap() == null || !plugin.getMapManager().getActiveMap().isLoaded()) {
                    Bukkit.getLogger().severe(String.format("Failed to load map %s! Aborting game start.", randomMap.getName()));
                    gameManager.setActiveGame(null); // Abort
                    return;
                }

                newGame.getTeamManager().setupTeams();
                plugin.getServer().getPluginManager().registerEvents(newGame, plugin);
                Bukkit.getLogger().info(String.format("New game instance created. Map is '%s'.", randomMap.getName()));
            }

            if (!gameManager.canStart()) {
                if (starting) { // If countdown was running but players left
                    starting = false;
                    Bukkit.broadcast(lang.getTranslated(null, "wizards.game.startCancelled"));
                }
                timeUntilStart = countdownTime; // Reset timer
                return;
            }

            if (timeUntilStart <= 0) {
                gameManager.setState(new PrepareState());

            } else {
                if (timeUntilStart == 10 || timeUntilStart <= 5) {
                    Bukkit.broadcast(Component.text("Starting in " + timeUntilStart + "...", NamedTextColor.GREEN));
                    for (Player player : Bukkit.getOnlinePlayers())
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1F, 1F);
                }
            }

            starting = true;
            timeUntilStart--;

        }, 0L, 20L);
    }

    @Override
    public void onDisable() {

        super.onDisable();

        if (updateLobbyTask != null && !updateLobbyTask.isCancelled())
            updateLobbyTask.cancel();

        for (Player player : Bukkit.getOnlinePlayers())
            EntityUtil.resetPlayer(player, GameMode.ADVENTURE);

        Wizards game = getGame();

        if (game == null) return;

        LootManager lootManager = game.getLootManager();
        LocalGameMap selectedMap = game.getActiveMap();
        WizardsMode wizardsMode = game.getCurrentMode();

        lootManager.populateMapWithLoot(selectedMap, wizardsMode);
        //createRandomChests(getGame().getActiveMap());
    }

    public void cancelCountdown() {
        this.countdownTime = 0;
    }

    @Override
    public List<Component> getScoreboardComponents(Player player) {
        LanguageManager lang = getPlugin().getLanguageManager();
        List<Component> components = new ArrayList<>();

        Wizards game = getGame();

        // If no game has been created yet, show a default lobby board.
        if (game == null) {
            int playerCount = Bukkit.getOnlinePlayers().size();
            // A default max players, or you can get it from config
            // TODO: Change this later (set the mode again)
            int maxPlayers = getPlugin().getConfig().getInt("defaultMaxPlayers", 24); 

            components.add(lang.getTranslated(player, "wizards.scoreboard.lobby.players",
                Placeholder.unparsed("players", playerCount + "/" + maxPlayers)
            ));
            components.add(Component.text(""));
            components.add(lang.getTranslated(player, "wizards.scoreboard.lobby.waiting"));
            return components;
        }

        WizardsMode mode = game.getCurrentMode();
        int playerCount = Bukkit.getOnlinePlayers().size();
        String playersText = playerCount + "/" + mode.getMaxPlayers();

        WizardsKit selectedKit = getPlugin().getGameManager().getKitManager().getKit(player);

        components.add(lang.getTranslated(player, "wizards.scoreboard.lobby.players",
            Placeholder.unparsed("players", playersText)
        ));
        components.add(Component.text("")); // Spacer

        if (starting) {
            components.add(lang.getTranslated(player, "wizards.scoreboard.lobby.startingIn",
                Placeholder.unparsed("time", String.valueOf(timeUntilStart))
            ));
        } else {
            components.add(lang.getTranslated(player, "wizards.scoreboard.lobby.waiting"));
        }
        components.add(Component.text("")); // Spacer

        components.add(lang.getTranslated(player, "wizards.scoreboard.lobby.kit",
            Placeholder.component("kit", lang.getTranslated(player, selectedKit.getNameKey()))
        ));
        
        return components;
    }

    @Override
    protected StateListenerProvider getListenerProvider() {
        return listener;
    }

    public boolean isStarting() {
        return starting;
    }

    public int getTimeUntilStart() {
        return timeUntilStart;
    }
}
