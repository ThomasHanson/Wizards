package dev.thomashanson.wizards.game.state;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;

import com.comphenix.protocol.wrappers.Pair;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.thomashanson.wizards.game.manager.PlayerStatsManager.StatType;
import dev.thomashanson.wizards.game.mode.GameTeam;
import dev.thomashanson.wizards.game.state.listener.StateListenerProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public abstract class GameState {

    private WizardsPlugin plugin;
    private Instant startTime;

    public void onEnable(WizardsPlugin plugin) {

        this.plugin = plugin;
        this.startTime = Instant.now();

        if (getListenerProvider() != null)
            getListenerProvider().onEnable(plugin);
    }

    public void onDisable() {

        if (getListenerProvider() != null)
            getListenerProvider().onDisable();
    }

    public String getMotd(Wizards game) {
        return "";
    }

    /**
     * Creates the default scoreboard display for active game states.
     * @param player The player to create the scoreboard for.
     * @return A list of components for the scoreboard.
     */
    protected List<Component> createDefaultGameScoreboard(Player player) {
        LanguageManager lang = getPlugin().getLanguageManager();
        List<Component> components = new ArrayList<>();
        Wizards game = getGame();

        Pair<String, Instant> nextEvent = game.getNextEvent();
        String eventKey = nextEvent.getFirst();
        Instant eventTime = nextEvent.getSecond();

        Duration remaining = Duration.between(Instant.now(), eventTime);
        long seconds = remaining.isNegative() ? 0 : remaining.toSeconds();
        String formattedTime = String.format("%02d:%02d", seconds / 60, seconds % 60);

        Component translatedEventName = lang.getTranslated(player, eventKey);

        // CORRECTED: The placeholder key is now lowercase "event_name".
        components.add(lang.getTranslated(player, "wizards.scoreboard.event.line",
            Placeholder.component("event_name", translatedEventName),
            Placeholder.unparsed("time", formattedTime)
        ));
        
        components.add(Component.text("")); // Spacer

        if (game.getCurrentMode().isTeamMode()) {
            components.add(lang.getTranslated(player, "wizards.scoreboard.game.teammate"));
            GameTeam team = game.getTeamManager().getTeam(player);
            components.add(Component.text("  " + team.getTeamName()));
            components.add(Component.text(""));
        }

        components.add(lang.getTranslated(player, "wizards.scoreboard.game.wizardsLeft",
            Placeholder.unparsed("count", String.valueOf(game.getPlayers(true).size()))
        ));
        components.add(Component.text(""));

        String kills = String.valueOf((int) getPlugin().getStatsManager().getStat(player, StatType.KILLS));
        String assists = String.valueOf((int) getPlugin().getStatsManager().getStat(player, StatType.ASSISTS));

        components.add(lang.getTranslated(player, "wizards.scoreboard.game.kills", Placeholder.unparsed("kills", kills)));
        components.add(lang.getTranslated(player, "wizards.scoreboard.game.assists", Placeholder.unparsed("assists", assists)));

        return components;
    }

    public List<Component> getScoreboardComponents(Player player) {
        List<GameState> previousStates = plugin.getGameManager().getPreviousStates();

        // Iterate backwards through previous states to find the most recent one with a scoreboard
        for (int i = previousStates.size() - 1; i >= 0; i--) {
            GameState previousState = previousStates.get(i);

            if (previousState != null) {
                // Call the method just ONCE and store the result in a variable
                List<Component> components = previousState.getScoreboardComponents(player);
                
                // If the result is not null, we've found our scoreboard
                if (components != null) {
                    return components;
                }
            }
        }

        // If no previous state had a scoreboard to show, return null
        return null;
    }

    protected abstract StateListenerProvider getListenerProvider();

    protected void setState(GameState state) {
        plugin.getGameManager().setState(state);
    }

    public Instant getStartTime() {
        return startTime;
    }

    protected Wizards getGame() {
        return plugin.getGameManager().getActiveGame();
    }

    protected WizardsPlugin getPlugin() {
        return plugin;
    }
}