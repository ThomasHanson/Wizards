package dev.thomashanson.wizards.game.state.types;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.overtime.Disaster;
import dev.thomashanson.wizards.game.state.GameState;
import dev.thomashanson.wizards.game.state.listener.StateListenerProvider;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class OvertimeState extends GameState implements Listener {

    private BukkitTask updateTask;
    private Instant lastMessage;

    private Disaster disaster;

    @Override
    public void onEnable(WizardsPlugin plugin) {

        super.onEnable(plugin);
        this.disaster = getGame().getDisaster();

        List<String> messages = disaster.getMessages();
        messages.add("Fight to the death! Fight with your dying breath!");

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        AtomicInteger messageIndex = new AtomicInteger();

        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {

            if (Duration.between(getStartTime(), Instant.now()).toMinutes() >= 10)
                setState(new ThanosState());

            while (messageIndex.get() < messages.size()) {

                Bukkit.broadcastMessage("" + messageIndex.get());

                if (lastMessage == null || Duration.between(lastMessage, Instant.now()).toSeconds() >= 2) {
                    lastMessage = Instant.now();
                    plugin.getGameManager().announce(messages.get(messageIndex.get()), true);
                }

                messageIndex.incrementAndGet();
            }

            getGame().getActiveMap().getWorld().setTime(15000L);
            disaster.update();

        }, 0L, 1L);
    }

    @Override
    public void onDisable() {

        super.onDisable();

        if (updateTask != null)
            updateTask.cancel();

        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onServerPing(ServerListPingEvent event) {

        Wizards game = getGame();

        event.setMaxPlayers(game.getCurrentMode().getMaxPlayers());

        String extra = game.getCurrentMode().toString();

        event.setMotd (
                ChatColor.RED + extra + " - Overtime\n" +
                        ChatColor.RED + "Disaster: " + disaster.getName()
        );
    }

    @Override
    protected StateListenerProvider getListenerProvider() {
        return null;
    }
}
