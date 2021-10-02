package dev.thomashanson.wizards.game.state.types;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.state.GameState;
import dev.thomashanson.wizards.game.state.listener.StateListenerProvider;
import dev.thomashanson.wizards.map.LocalGameMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Represents the state at the end of the game,
 * after winner has been declared and announced.
 * This is the janitor phase, while the map is
 * in the process of getting cleaned up / deleted.
 */
public class ResetState extends GameState {

    @Override
    public void onEnable(WizardsPlugin plugin) {

        super.onEnable(plugin);

        Wizards game = getGame();

        if (game.getActiveMap() == null)
            return;

        LocalGameMap gameMap = game.getActiveMap();

        for (Player player : gameMap.getWorld().getPlayers())
            player.teleport(new Location(Bukkit.getWorld("world"), 0.5, 3, 0.5));

        if (gameMap.isLoaded())
            gameMap.unload();

        plugin.getGameManager().setActiveGame(null);
    }

    @Override
    protected StateListenerProvider getListenerProvider() {
        return null;
    }
}