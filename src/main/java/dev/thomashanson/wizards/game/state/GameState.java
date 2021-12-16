package dev.thomashanson.wizards.game.state;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.state.listener.StateListenerProvider;

import java.time.Instant;
import java.util.List;

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

    public abstract List<String> getScoreboardLines();

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