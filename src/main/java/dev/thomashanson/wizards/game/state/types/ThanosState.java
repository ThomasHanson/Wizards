package dev.thomashanson.wizards.game.state.types;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.OtherDamageTick;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.thomashanson.wizards.game.state.GameState;
import dev.thomashanson.wizards.game.state.listener.StateListenerProvider;
import net.kyori.adventure.text.Component;

public class ThanosState extends GameState {

    @Override
    public void onEnable(WizardsPlugin plugin) {
        super.onEnable(plugin);
        Wizards activeGame = getGame();

        List<Player> players = new ArrayList<>(activeGame.getPlayers(true));
        players.sort((o1, o2) -> Double.compare(o2.getHealth(), o1.getHealth()));

        Iterator<Player> iterator = players.iterator();
        while (iterator.hasNext()) {
            Player player = iterator.next();

            // Only eliminate half the players, leaving the final winner
            if (iterator.hasNext()) {
                iterator.next(); // Skip the next player (the one who survives this round)

                // Use OtherDamageTick for non-player-sourced damage to avoid NullPointerExceptions.
                // This correctly represents an environmental or "act of god" kill.
                OtherDamageTick damageTick = new OtherDamageTick(
                    Integer.MAX_VALUE,
                    EntityDamageEvent.DamageCause.CUSTOM,
                    "Was snapped from existence",
                    Instant.now()
                );
                plugin.getDamageManager().damage(player, damageTick);
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                // The death events will trigger the end-game check automatically.
                // This is a fallback just in case.
                activeGame.checkEndGameCondition();
            }
        }.runTaskLater(plugin, 2L); // Slightly increased delay to ensure death events process
    }

    @Override
    public List<Component> getScoreboardComponents(Player player) {
        LanguageManager lang = getPlugin().getLanguageManager();
        return List.of(
            lang.getTranslated(player, "wizards.scoreboard.thanos.line1")
        );
    }

    @Override
    protected StateListenerProvider getListenerProvider() {
        return null;
    }
}
