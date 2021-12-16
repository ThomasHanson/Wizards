package dev.thomashanson.wizards.game.state.types;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.manager.GameManager;
import dev.thomashanson.wizards.game.mode.GameTeam;
import dev.thomashanson.wizards.game.mode.WizardsMode;
import dev.thomashanson.wizards.game.state.GameState;
import dev.thomashanson.wizards.game.state.listener.PrepareListener;
import dev.thomashanson.wizards.game.state.listener.StateListenerProvider;
import dev.thomashanson.wizards.util.EntityUtil;
import dev.thomashanson.wizards.util.MathUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Represents the preparation state when players
 * are being teleported to the map. Players will
 * be frozen for a few seconds until the active
 * state takes over.
 */
public class PrepareState extends GameState {

    private PrepareListener listener;
    private BukkitTask actionBarTask;

    private static final String[] INTRO_MESSAGES = new String[] {
            "Find loot and spells in chests.",
            "Right click wands to assign spells.",
            "Left click with wands to cast magic.",
            "The last wizard alive wins!"
    };

    private static final int LINE_LENGTH = 35;
    private final String LINE_BREAK = ChatColor.GREEN + ChatColor.STRIKETHROUGH.toString() + ChatColor.BOLD + "=".repeat(LINE_LENGTH);

    @Override
    public void onEnable(WizardsPlugin plugin) {

        this.listener = new PrepareListener(plugin);

        super.onEnable(plugin);

        WizardsMode mode = getGame().getCurrentMode();

        assignTeams(getGame());

        Bukkit.getOnlinePlayers().forEach(player -> {

            player.leaveVehicle();
            player.eject();

            GameTeam team = getGame().getTeam(player);
            player.teleport(team.getSpawn());

            plugin.getGameManager().gameAnnounce(player, true, INTRO_MESSAGES);

            if (!player.hasMetadata(GameManager.SPECTATING_KEY))
                getGame().setupWizard(player);
        });

        this.actionBarTask = new BukkitRunnable() {

            int numTicks = 0;

            @Override
            public void run() {

                double percentage = ((double) Duration.between(getStartTime(), Instant.now()).toSeconds()) / mode.getPreparationSecs();

                for (Player player : Bukkit.getOnlinePlayers()) {

                    getGame().getKit(player).playIntro(player, player.getLocation(), numTicks++);

                    EntityUtil.displayProgress(player, "Game Start", percentage,

                            MathUtil.formatTime (
                                    Math.max(0, mode.getPreparationSecs() - Duration.between(Instant.now(), getStartTime()).toMillis()),
                                    1
                            )
                    );
                }
            }

        }.runTaskTimer(plugin, 0L, 1L);

        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> setState(new ActiveState()), mode.getPreparationSecs() * 20L);
    }

    private void assignTeams(Wizards game) {

        if (game.getCurrentMode().isTeamMode())
            game.getPlayers(true).forEach(player -> game.getRandomTeam(game.getCurrentMode()).addPlayer(player));
    }

    @Override
    public void onDisable() {

        super.onDisable();

        actionBarTask.cancel();

        for (Player player : getGame().getActiveMap().getWorld().getPlayers())
            if (player.getGameMode() != GameMode.SPECTATOR)
                player.setGameMode(GameMode.SURVIVAL);
    }

    @Override
    public List<String> getScoreboardLines() {

        Wizards game = getGame();

        return Arrays.asList (

                "",

                ChatColor.RESET + "Players left: " +
                        ChatColor.GREEN + game.getPlayers(true).size(),

                ChatColor.RESET + "Teams left: " +
                        ChatColor.GREEN + game.getTeams().size(),

                "",

                ChatColor.RESET + "Kills: " + ChatColor.GREEN + "0",
                ChatColor.RESET + "Assists: " + ChatColor.GREEN + "0"
        );
    }

    @Override
    protected StateListenerProvider getListenerProvider() {
        return listener;
    }
}
