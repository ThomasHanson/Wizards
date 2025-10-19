package dev.thomashanson.wizards.game.state.types;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.thomashanson.wizards.game.state.GameState;
import dev.thomashanson.wizards.game.state.listener.StateListenerProvider;
import dev.thomashanson.wizards.map.LocalGameMap;
import net.kyori.adventure.text.Component;

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

        // 1. Get the map before we reset the game's state
        LocalGameMap gameMap = game.getActiveMap();

        // 2. Teleport players to a safe location (e.g., the lobby spawn)
        // You should create a method in your plugin's main class or a manager to get the lobby location.
        Location lobbySpawn = plugin.getLobbySpawnLocation(); 
        for (Player player : Bukkit.getOnlinePlayers()) {
            // You should also have a method to reset a player's inventory, gamemode, health, etc.
            // plugin.getPlayerManager().resetPlayerState(player);
            player.teleport(lobbySpawn);
        }

        // 3. Call the new, centralized reset method in your Wizards game class
        game.reset();

        // 4. Unload the map after the game is fully reset
        if (gameMap != null && gameMap.isLoaded()) {
            gameMap.unload();
        }

        // 5. Nullify the active game and transition to the lobby
        plugin.getGameManager().setActiveGame(null);
        plugin.getGameManager().setState(new LobbyState()); // Transition to the next state
        
        // Bukkit.getServer().spigot().restart();
    }

    @Override
    public List<Component> getScoreboardComponents(Player player) {
        LanguageManager lang = getPlugin().getLanguageManager();
        return List.of(
            Component.text(""),
            lang.getTranslated(player, "wizards.scoreboard.reset.restarting"),
            Component.text("")
        );
    }

    @Override
    protected StateListenerProvider getListenerProvider() {
        return null;
    }
}