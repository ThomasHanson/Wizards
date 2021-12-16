package dev.thomashanson.wizards.game.state.types;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.state.GameState;
import dev.thomashanson.wizards.game.state.listener.StateListenerProvider;
import dev.thomashanson.wizards.map.LocalGameMap;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

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
            player.kickPlayer(ChatColor.RED + "Server is in the process of resetting!");

        if (gameMap.isLoaded())
            gameMap.unload();

        plugin.getGameManager().setActiveGame(null);
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