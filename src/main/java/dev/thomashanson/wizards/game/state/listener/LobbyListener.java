package dev.thomashanson.wizards.game.state.listener;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.manager.DatabaseManager;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.thomashanson.wizards.game.state.types.LobbyState;
import dev.thomashanson.wizards.game.state.types.PrepareState;
import dev.thomashanson.wizards.map.LocalGameMap;
import dev.thomashanson.wizards.map.MapSelectMenu;
import dev.thomashanson.wizards.util.EntityUtil;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.md_5.bungee.api.ChatColor;

public class LobbyListener extends StateListenerProvider {

    private final WizardsPlugin plugin;
    private final LanguageManager lang;
    private final MapSelectMenu mapSelectMenu;

    public LobbyListener(WizardsPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.mapSelectMenu = new MapSelectMenu(plugin);
    }

    @Override
    public void onEnable(WizardsPlugin plugin) {
        super.onEnable(plugin);
    }

    public void setupLobbyPlayer(Player player) {
        // 1. Reset the player's state completely
        EntityUtil.resetPlayer(player, org.bukkit.GameMode.ADVENTURE);

        // 2. Set lobby-specific attributes
        AttributeInstance instance = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if (instance != null) {
            instance.setBaseValue(16.0);
        }

        // 3. Give ALL lobby items in one place
        new BukkitRunnable() {
            @Override
            public void run() {
                Wizards activeGame = plugin.getGameManager().getActiveGame();
                player.getInventory().setItem(2, activeGame.getSpellMenuBook(player));
                player.getInventory().setItem(4, getKitSelectIcon(player));
                player.getInventory().setItem(6, getControlPanelIcon(player));
            }
        }.runTaskLater(plugin, 1L);
    }

    public ItemStack getKitSelectIcon(Player player) {
        return ItemBuilder.from(Material.NETHER_STAR)
                .name(
                    lang.getTranslated(player, "wizards.item.kitSelect.name")
                        .decoration(TextDecoration.ITALIC, false)
                )
                .lore(lang.getTranslated(player, "wizards.item.kitSelect.lore"))
                .build();
    }
    
    private ItemStack getControlPanelIcon(Player player) {
        return ItemBuilder.from(Material.BLAZE_ROD)
            .name(
                lang.getTranslated(player, "wizards.lobby.item.controlPanel.name")
                   .decoration(TextDecoration.ITALIC, false)
            )
            .lore(Component.empty(), lang.getTranslated(player, "wizards.lobby.item.controlPanel.lore"))
            .build();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.setJoinMessage(null);
        Player player = event.getPlayer();
        Location spawnPoint = plugin.getLobbySpawnLocation();

        if (spawnPoint != null) {
            player.teleport(spawnPoint);
        } else {
            plugin.getLogger().warning(String.format("Could not teleport %s because lobby spawn is null.", player.getName()));
        }

        DatabaseManager db = plugin.getDatabaseManager();
        String playerUUID = player.getUniqueId().toString();
        String playerName = player.getName();
        String sql = "INSERT INTO players (uuid, username) VALUES (?, ?) ON DUPLICATE KEY UPDATE username = ?";
        db.executeUpdateAsync(sql, playerUUID, playerName, playerName);

        // The onJoin event now simply calls our new centralized method
        setupLobbyPlayer(player);
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // We only care about clicks in the player's own inventory
        if (event.getClickedInventory() == null || event.getClickedInventory().getType() != InventoryType.PLAYER) {
            return;
        }

        // Allow operators in creative mode to bypass this
        if (event.getWhoClicked().getGameMode() == GameMode.CREATIVE && event.getWhoClicked().isOp()) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onHandSwap(PlayerSwapHandItemsEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        // The pattern creates the 'player' variable, removing the need for the next line
        if (event.getEntity() instanceof Player player) {
            if (player.getGameMode() != GameMode.CREATIVE) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {

        Action action = event.getAction();

        if (action == Action.RIGHT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock == null) {
            } else {
                Material type = clickedBlock.getType();
                String typeName = type.name();

                if (typeName.contains("DOOR") || typeName.contains("TRAPDOOR") || typeName.contains("GATE")) {
                    event.setCancelled(true);
                }
            }
        }

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)
            return;

        ItemStack item = event.getItem();
        Player player = event.getPlayer();
        if (item == null) return;

        if (item.isSimilar(getControlPanelIcon(player))) {
            showControlPanel(player);
        }
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        // Again, you might want to allow creative OPs to interact
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        // Cancel the event if the new state has weather (is not clear)
        if (event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onLeavesDecay(LeavesDecayEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockFade(BlockFadeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockSpread(BlockSpreadEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockForm(BlockFormEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // We only want to cancel "natural" spawns
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        // This clears the list of blocks that would be destroyed,
        // effectively preventing any damage to the terrain.
        event.blockList().clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // 1. Hide the default quit message
        event.setQuitMessage(null);

        // 2. Check if the game is in the lobby state
        if (!(plugin.getGameManager().getState() instanceof LobbyState)) {
            return;
        }

        LobbyState lobbyState = (LobbyState) plugin.getGameManager().getState();
        Wizards game = plugin.getGameManager().getActiveGame();

        // 3. Check if a countdown is active
        if (lobbyState.isStarting()) {
            // Check player count (-1 because the player is about to disconnect)
            int playersRemaining = Bukkit.getOnlinePlayers().size() - 1;

            if (playersRemaining < game.getCurrentMode().getMinPlayers()) {
                // Cancel the countdown and reset the state
                lobbyState.cancelCountdown();
            }
        }
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

        Gui menuBuilder = Gui.gui()
            .title(Component.text("Control Panel"))
            .rows(3)
            .create();

            menuBuilder.setItem(11,

                ItemBuilder
                    .from(Material.GREEN_TERRACOTTA)
                    .name(Component.text(ChatColor.GREEN.toString() + ChatColor.BOLD + "Start Game"))
                    .asGuiItem(event -> {

                        ClickType clickType = event.getClick();

                        if (!clickType.isLeftClick())
                            return;

                        event.getWhoClicked().closeInventory();

                        if (clickType == ClickType.LEFT)
                            plugin.getGameManager().setForceStart(true);
                        else if (clickType == ClickType.SHIFT_LEFT)
                            plugin.getGameManager().setState(new PrepareState());
                    })
        );

        menuBuilder.setItem(13,

            ItemBuilder
                .from(Material.MAP)
                .name(Component.text(ChatColor.GREEN.toString() + ChatColor.BOLD + "Select Map"))
                .lore(Component.text(""), Component.text(ChatColor.WHITE + "Currently Selected: " + plugin.getMapManager().getActiveMap().getName()))
                .asGuiItem(event -> {

                    if (!(event.getWhoClicked() instanceof Player))
                        return;

                    if (event.getClick().isLeftClick())
                        mapSelectMenu.showMenu((Player) event.getWhoClicked());
                })
        );

        menuBuilder.setItem(15,

            ItemBuilder
                .from(Material.TNT)
                .name(Component.text(ChatColor.GREEN.toString() + ChatColor.BOLD + "Shutdown Server"))
                .asGuiItem(event -> {
                    
                    if (event.getClick().isLeftClick()) {        
                        event.getWhoClicked().closeInventory();
                        Bukkit.getServer().shutdown();
                    }
                })
        );

        if (!player.isOp())
            player.sendMessage(ChatColor.RED + "You must be an operator to open this menu!");
        else
            menuBuilder.open(player);
    }
}