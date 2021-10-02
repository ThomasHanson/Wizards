package dev.thomashanson.wizards.game.state.listener;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.manager.GameManager;
import dev.thomashanson.wizards.game.state.types.LobbyState;
import dev.thomashanson.wizards.game.state.types.PrepareState;
import dev.thomashanson.wizards.map.LocalGameMap;
import dev.thomashanson.wizards.map.MapSelectMenu;
import dev.thomashanson.wizards.util.menu.InventoryMenuBuilder;
import dev.thomashanson.wizards.util.menu.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

public class LobbyListener extends StateListenerProvider {

    private final WizardsPlugin plugin;

    private final ItemStack spectatorIcon = new ItemBuilder(Material.ENDER_EYE)
            .withName(ChatColor.GOLD.toString() + ChatColor.BOLD + "Toggle Spectator")
            .withLore("", ChatColor.GRAY + "Right-Click with this to toggle spectator")
            .get();

    private final ItemStack controlPanelIcon = new ItemBuilder(Material.BLAZE_ROD)
            .withName(ChatColor.GOLD.toString() + ChatColor.BOLD + "Control Panel")
            .withLore("", ChatColor.GRAY + "Right-Click with this to control the game")
            .get();

    private final MapSelectMenu mapSelectMenu;

    public LobbyListener(WizardsPlugin plugin) {
        this.plugin = plugin;
        this.mapSelectMenu = new MapSelectMenu(plugin);
    }

    @Override
    public void onEnable(WizardsPlugin plugin) {
        super.onEnable(plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {

        event.setJoinMessage(null);

        Player player = event.getPlayer();

        // player.teleport(WizardsPlugin.SPAWN);

        AttributeInstance instance = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);

        if (instance != null)
            instance.setBaseValue(16.0); // Base value is 4

        new BukkitRunnable() {

            @Override
            public void run() {

                player.getInventory().setItem(5, spectatorIcon);
                player.getInventory().setItem(7, controlPanelIcon);
            }
        }.runTaskLater(plugin, 1L);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {

        Player player = event.getPlayer();

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        if (event.getItem() == null)
            return;

        ItemStack item = event.getItem();

        if (item.isSimilar(spectatorIcon)) {

            if (player.hasMetadata(GameManager.SPECTATING_KEY))
                player.removeMetadata(GameManager.SPECTATING_KEY, plugin);
            else
                player.setMetadata(GameManager.SPECTATING_KEY, new FixedMetadataValue(plugin, true));

            player.sendMessage(player.hasMetadata(GameManager.SPECTATING_KEY) ?
                    ChatColor.RED + "You will be a spectator next round!" :
                    ChatColor.GREEN + "You will no longer be spectating!"
            );

            event.setCancelled(true); // disable eye of ender use
        }

        if (item.isSimilar(controlPanelIcon))
            showControlPanel(player);
    }

    @EventHandler
    public void onServerPing(ServerListPingEvent event) {

        Wizards game = plugin.getGameManager().getActiveGame();

        event.setMaxPlayers(game.getCurrentMode().getMaxPlayers());

        LocalGameMap selectedMap = game.getActiveMap();

        if (selectedMap == null)
            return;

        LobbyState state = (LobbyState) plugin.getGameManager().getState();

        event.setMotd (

                (state.isStarting() ?

                        ChatColor.GREEN + "Starting in " + ChatColor.DARK_GREEN + state.getTimeUntilStart() + "\n" :
                        ChatColor.GREEN + "Recruiting Players\n") +

                        ChatColor.YELLOW + "Map Selected: " + ChatColor.GOLD + selectedMap.getName()
        );
    }

    private void showControlPanel(Player player) {

        InventoryMenuBuilder menuBuilder = new InventoryMenuBuilder(plugin, "Control Panel", 36);

        menuBuilder.withItem(11,

                new ItemBuilder(Material.GREEN_TERRACOTTA)
                        .withName(ChatColor.GREEN.toString() + ChatColor.BOLD + "Start Game")
                        .get(),

                (p, action, item) -> {

                    if (action == ClickType.LEFT)
                        plugin.getGameManager().setForceStart(true);
                    else
                        plugin.getGameManager().setState(new PrepareState());

                    p.closeInventory();

                }, ClickType.LEFT, ClickType.SHIFT_LEFT
        );

        menuBuilder.withItem(13,

                new ItemBuilder(Material.MAP)
                        .withName(ChatColor.GREEN.toString() + ChatColor.BOLD + "Select Map")
                        .withLore("", ChatColor.RESET + "Currently Selected: " + plugin.getMapManager().getActiveMap().getName())
                        .get(),

                (p, action, item) -> mapSelectMenu.showMenu(player), ClickType.LEFT
        );


        menuBuilder.withItem(24,

                new ItemBuilder(Material.TNT)
                        .withName(ChatColor.GREEN.toString() + ChatColor.BOLD + "Shutdown Server")
                        .get(),

                (p, action, item) -> {

                    p.closeInventory();
                    Bukkit.getServer().shutdown();

                }, ClickType.LEFT
        );

        if (!player.isOp())
            player.sendMessage(ChatColor.RED + "You must be an operator to open this menu!");
        else
            player.openInventory(menuBuilder.build());
    }
}