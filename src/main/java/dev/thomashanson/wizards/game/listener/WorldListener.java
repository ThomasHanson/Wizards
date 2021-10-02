package dev.thomashanson.wizards.game.listener;

import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.manager.MapManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.LeavesDecayEvent;

public class WorldListener implements Listener {

    private final Wizards game;

    public WorldListener(MapManager mapManager) {
        this.game = mapManager.getPlugin().getGameManager().getActiveGame();
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {

        if (game != null)
            event.setCancelled(true);
    }

    @EventHandler
    public void onIgnite(BlockIgniteEvent event) {

        if (game != null)
            event.setCancelled(true);
    }

    @EventHandler
    public void onDecay(LeavesDecayEvent event) {

        if (game != null)
            event.setCancelled(true);
    }
}