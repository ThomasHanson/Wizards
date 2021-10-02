package dev.thomashanson.wizards.game.state.types;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.manager.GameManager;
import dev.thomashanson.wizards.game.mode.WizardsMode;
import dev.thomashanson.wizards.game.state.GameState;
import dev.thomashanson.wizards.game.state.listener.PrepareListener;
import dev.thomashanson.wizards.game.state.listener.StateListenerProvider;
import dev.thomashanson.wizards.util.EntityUtil;
import dev.thomashanson.wizards.util.MathUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

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

        Bukkit.getOnlinePlayers().forEach(player -> {

            player.teleport(plugin.getMapManager().getActiveMap().getSpectatorLocation());

            announce(player);

            if (!player.hasMetadata(GameManager.SPECTATING_KEY))
                getGame().setupWizard(player);
        });

        this.actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {

            double percentage = (Duration.between(getStartTime(), Instant.now()).toSeconds()) / mode.getPreparationSecs();

            for (Player player : Bukkit.getOnlinePlayers()) {

                EntityUtil.displayProgress(player, "Game Start", percentage,

                        MathUtil.formatTime (
                                Math.max(0, mode.getPreparationSecs() - Duration.between(Instant.now(), getStartTime()).toMillis()),
                                1
                        )
                );
            }

        }, 0L, 1L);

        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> setState(new ActiveState()), mode.getPreparationSecs() * 20L);
    }

    @Override
    public void onDisable() {

        super.onDisable();

        actionBarTask.cancel();

        for (Player player : getGame().getActiveMap().getWorld().getPlayers())
            if (player.getGameMode() != GameMode.SPECTATOR)
                player.setGameMode(GameMode.SURVIVAL);
    }

    private void announce(Player player) {

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 2F, 1F);

        for (int i = 0; i < 6 - INTRO_MESSAGES.length; i++)
            player.sendMessage("");

        player.sendMessage(LINE_BREAK);

        player.sendMessage(ChatColor.WHITE.toString() + ChatColor.BOLD + "Wizards");

        player.sendMessage("");
        Arrays.stream(INTRO_MESSAGES).forEach(message -> player.sendMessage(ChatColor.YELLOW.toString() + ChatColor.BOLD + message));
        player.sendMessage("");

        player.sendMessage (

                ChatColor.GREEN.toString() + ChatColor.BOLD + "Map: " +
                        ChatColor.YELLOW.toString() + ChatColor.BOLD + getGame().getActiveMap().getName() +

                        ChatColor.GRAY + " created by " +
                        ChatColor.YELLOW.toString() + ChatColor.BOLD + getGame().getActiveMap().getAuthors()
        );

        player.sendMessage(LINE_BREAK);

        for (int i = 0; i < 6 - INTRO_MESSAGES.length; i++)
            player.sendMessage("");
    }

    @Override
    protected StateListenerProvider getListenerProvider() {
        return listener;
    }
}
