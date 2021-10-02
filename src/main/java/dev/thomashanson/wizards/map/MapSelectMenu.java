package dev.thomashanson.wizards.map;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.util.menu.InventoryMenuBuilder;
import dev.thomashanson.wizards.util.menu.ItemBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

public class MapSelectMenu {

    private final WizardsPlugin plugin;

    public MapSelectMenu(WizardsPlugin plugin) {
        this.plugin = plugin;
    }

    public void showMenu(Player player) {

        InventoryMenuBuilder menuBuilder = new InventoryMenuBuilder(plugin, "Map Selection", 54);

        List<LocalGameMap> allMaps = plugin.getMapManager().getAllMaps();

        for (int i = 0; i < allMaps.size(); i++) {

            LocalGameMap currentMap = allMaps.get(i);

            menuBuilder.withItem(10 + i,

                    new ItemBuilder(Material.MAP)
                            .withName(ChatColor.GREEN.toString() + ChatColor.BOLD + currentMap.getName())
                            .withLore("", ChatColor.GRAY + "Click to set " + currentMap.getName() + " as active map!")
                            .get(),

                    (p, action, item) -> {

                if (plugin.getMapManager().getActiveMap() != null)
                    plugin.getMapManager().getActiveMap().unload();

                plugin.getMapManager().setActiveMap(currentMap);
                p.closeInventory();

            }, ClickType.LEFT);
        }

        menuBuilder.show(player);
    }
}