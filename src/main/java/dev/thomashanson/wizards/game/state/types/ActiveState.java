package dev.thomashanson.wizards.game.state.types;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.thomashanson.wizards.game.manager.PlayerStatsManager;
import dev.thomashanson.wizards.game.state.GameState;
import dev.thomashanson.wizards.game.state.listener.StateListenerProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/**
 * Represents the state when the game is in an
 * active and there is no winner declared.
 */
public class ActiveState extends GameState implements Listener {

    @Override
    public void onEnable(WizardsPlugin plugin) {

        super.onEnable(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        Wizards game = getGame();
        
        game.setupGame();
        game.setGameStartTime(Instant.now());
        
        PlayerStatsManager statsManager = plugin.getStatsManager();

        for (Player player : game.getPlayers(true)) {
            statsManager.incrementStat(player, PlayerStatsManager.StatType.GAMES_PLAYED, 1);
        }

        AtomicInteger atomicInteger = new AtomicInteger();
        game.getGameManager().startGameLoop();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        HandlerList.unregisterAll(this);
    }

    @Override
    public List<Component> getScoreboardComponents(Player player) {
        return createDefaultGameScoreboard(player);
    }

    @EventHandler
    public void onServerPing(ServerListPingEvent event) {

        Wizards game = getGame();
        LanguageManager lang = getPlugin().getLanguageManager();

        event.setMaxPlayers(game.getCurrentMode().getMaxPlayers());

        String modeName = game.getCurrentMode().toString().replaceAll("_", " ");
        boolean isTeamMode = game.getCurrentMode().isTeamMode();
        String key = isTeamMode ? "wizards.motd.inProgress.team" : "wizards.motd.inProgress.solo";
        int count = isTeamMode ? game.getActiveTeams().size() : game.getPlayers(true).size();

        Component motd = lang.getTranslated(
                null,
                key,
                Placeholder.unparsed("mode", modeName),
                Placeholder.unparsed("map", game.getActiveMap().getName()),
                Placeholder.unparsed("count", String.valueOf(count))
        );

        event.motd(motd);
    }

    @Override
    protected StateListenerProvider getListenerProvider() {
        return null;
    }
}