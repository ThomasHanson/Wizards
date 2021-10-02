package dev.thomashanson.wizards.game.state.types;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.state.GameState;
import dev.thomashanson.wizards.game.state.listener.StateListenerProvider;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ThanosState extends GameState {

    @Override
    public void onEnable(WizardsPlugin plugin) {

        Wizards activeGame = getGame();

        List<Player> players = new ArrayList<>(activeGame.getPlayers(true));
        players.sort((o1, o2) -> Double.compare(o2.getHealth(), o1.getHealth()));

        Iterator<Player> iterator = players.iterator();

        while (iterator.hasNext()) {

            Player player = iterator.next();

            if (iterator.hasNext()) {

                CustomDamageTick damageTick = new CustomDamageTick (
                        Integer.MAX_VALUE,
                        EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
                        "Magic",
                        Instant.now(),
                        null
                );

                plugin.getDamageManager().damage(player, damageTick);
            }
        }

        setState(new WinnerState());
    }

    @Override
    protected StateListenerProvider getListenerProvider() {
        return null;
    }
}
