package dev.thomashanson.wizards.game.state.listener;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.inventory.Inventory;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.thomashanson.wizards.map.LocalGameMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class PrepareListener extends StateListenerProvider {

    private final WizardsPlugin plugin;

    public PrepareListener(WizardsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().setGameMode(GameMode.SPECTATOR);
    }

    @EventHandler
    public void onCropTrample(PlayerInteractEvent event) {

        if (event.getAction() != Action.PHYSICAL)
            return;

        Block block = event.getClickedBlock();

        if (block == null || block.getType() != Material.FARMLAND)
            return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onServerPing(ServerListPingEvent event) {

        Wizards game = plugin.getGameManager().getActiveGame();
        LanguageManager lang = plugin.getLanguageManager();

        event.setMaxPlayers(game.getCurrentMode().getMaxPlayers());

        LocalGameMap selectedMap = game.getActiveMap();

        if (selectedMap == null)
            return;

        String modeName = game.getCurrentMode().toString();

        Component motd = lang.getTranslated(
                null,
                "wizards.motd.preparing",
                Placeholder.unparsed("mode", modeName),
                Placeholder.unparsed("map_name", selectedMap.getName())
        );

        event.motd(motd);
    }

    @EventHandler
    public void onChestOpen(InventoryOpenEvent event) {

        Inventory inventory = event.getInventory();

        if (inventory.getType() == InventoryType.CHEST)
            event.setCancelled(true);
    }

    @EventHandler
    public void freezeCheck(PlayerMoveEvent event) {

        Wizards game = plugin.getGameManager().getActiveGame();

        if (game == null)
            return;

        if (event.getPlayer().getGameMode() == GameMode.SPECTATOR)
            return;

        Location
                from = event.getFrom(),
                to = event.getTo();

        if (from.toVector().distanceSquared(to.toVector()) <= 0)
            return;

        from.setPitch(to.getPitch());
        from.setYaw(from.getYaw());

        event.setTo(from);
    }



    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        event.setCancelled(true);
    }
}