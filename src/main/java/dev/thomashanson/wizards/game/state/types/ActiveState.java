package dev.thomashanson.wizards.game.state.types;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.state.GameState;
import dev.thomashanson.wizards.game.state.listener.StateListenerProvider;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents the state when the game is in an
 * active and there is no winner declared.
 */
public class ActiveState extends GameState implements Listener {

    static BukkitTask UPDATE_TASK;

    @Override
    public void onEnable(WizardsPlugin plugin) {

        super.onEnable(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        getGame().setupGame();
        getGame().setLastSurge(Instant.now());

        AtomicInteger atomicInteger = new AtomicInteger();
        UPDATE_TASK = Bukkit.getScheduler().runTaskTimer(plugin, () -> getGame().updateGame(atomicInteger), 0L, 1L);
    }

    @Override
    public void onDisable() {

        super.onDisable();
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onServerPing(ServerListPingEvent event) {

        Wizards game = getGame();

        event.setMaxPlayers(game.getCurrentMode().getMaxPlayers());

        event.setMotd (
                ChatColor.GOLD + game.getCurrentMode().toString() + " - In Progress\n" +
                        ChatColor.YELLOW + "Wizards Remaining: " + ChatColor.GOLD + game.getWizards().size()
        );
    }

    @Override
    public List<String> getScoreboardLines() {

        Wizards game = getGame();

        return Arrays.asList (

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
        return null;
    }
}