package dev.thomashanson.wizards.game.state.types;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.kit.WizardsKit;
import dev.thomashanson.wizards.game.loot.LootManager;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.thomashanson.wizards.game.mode.WizardsMode;
import dev.thomashanson.wizards.game.state.GameState;
import dev.thomashanson.wizards.game.state.listener.PrepareListener;
import dev.thomashanson.wizards.game.state.listener.StateListenerProvider;
import dev.thomashanson.wizards.map.LocalGameMap;
import dev.thomashanson.wizards.scoreboard.WizardsScoreboard;
import dev.thomashanson.wizards.util.EntityUtil;
import dev.thomashanson.wizards.util.MathUtil;
import net.kyori.adventure.text.Component;

/**
 * Represents the preparation state when players
 * are being teleported to the map. Players will
 * be frozen for a few seconds until the active
 * state takes over.
 */
public class PrepareState extends GameState {

    private LanguageManager lang;
    private PrepareListener listener;
    private BukkitTask actionBarTask;

    private static final List<String> INTRO_MESSAGE_KEYS = List.of(
            "wizards.game.intro.1",
            "wizards.game.intro.2",
            "wizards.game.intro.3",
            "wizards.game.intro.4"
    );

    @Override
    public void onEnable(WizardsPlugin plugin) {

        this.lang = plugin.getLanguageManager();
        this.listener = new PrepareListener(plugin);

        super.onEnable(plugin);

        Wizards game = getGame();
        WizardsMode mode = game.getCurrentMode();

        // Randomly assign teams
        game.getTeamManager().assignTeams(game.getPlayers(true));

        LootManager lootManager = game.getLootManager();
        lootManager.populateMapWithLoot(game.getActiveMap(), mode);

        this.actionBarTask = new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getGameManager().getScoreboard().updateAllScoreboards();

                double percentage = ((double) Duration.between(getStartTime(), Instant.now()).toSeconds()) / mode.getPreparationSecs();

                for (Player player : getGame().getPlayers(false)) {
                    double secsUntilStart = (double) ((mode.getPreparationSecs() * 1000) - Duration.between(getStartTime(), Instant.now()).toMillis()) / 1000;
                    secsUntilStart = MathUtil.trim(1, secsUntilStart);
                    
                    // UPDATED: displayProgress now needs Components, and formatTime's signature has changed.
                    Component prefix = Component.text("Game Start ");
                    String timeString = MathUtil.formatTime(Math.max(0, (long) (secsUntilStart * 1000)));
                    Component suffix = Component.text(" " + timeString);
                    
                    EntityUtil.displayProgress(player, prefix, percentage, suffix);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        if (mode.isBrawl()) {
            WizardsScoreboard scoreboard = plugin.getGameManager().getScoreboard();
            scoreboard.getOptions().setShowHealthUnderName(true);
        }

        Bukkit.getOnlinePlayers().forEach(player -> {

            LocalGameMap activeMap = getGame().getActiveMap();
            Location spectatorLocation = activeMap.getSpectatorLocation();

            if (spectatorLocation.getWorld() != null) {
                Bukkit.getLogger().info(String.format("Teleporting %s to %s", player.getName(), spectatorLocation.getWorld().getName()));
            }

            Location spawnLocation = getGame().getTeamManager().findSpawnForPlayer(player);
            player.teleport(spawnLocation);

            new BukkitRunnable() {

                @Override
                public void run() {

                    WizardsKit kit = getGame().getKit(player);

                    if (kit != null)
                        kit.playIntro(player);
                }

            }.runTaskLater(getPlugin(), 60L);

            List<Component> translatedIntro = INTRO_MESSAGE_KEYS.stream()
                    .map(key -> lang.getTranslated(player, key))
                    .collect(Collectors.toList());
            plugin.getGameManager().gameAnnounce(player, true, translatedIntro);

            getGame().getWizardManager().setupWizard(player);
        });

        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> setState(new ActiveState()), mode.getPreparationSecs() * 20L);
    }

    @Override
    public void onDisable() {

        super.onDisable();

        if (actionBarTask != null) {
            actionBarTask.cancel();
        }

        Wizards game = getGame();

        if (game != null && game.getActiveMap() != null && game.getActiveMap().getWorld() != null) {
            for (Player player : game.getActiveMap().getWorld().getPlayers()) {
                if (player.getGameMode() != GameMode.SPECTATOR) {
                    player.setGameMode(GameMode.SURVIVAL);
                }
            }
        }
    }

    @Override
    public List<Component> getScoreboardComponents(Player player) {
        return createDefaultGameScoreboard(player);
    }

    @Override
    protected StateListenerProvider getListenerProvider() {
        return listener;
    }
}