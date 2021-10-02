package dev.thomashanson.wizards.game.kit;

import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.util.menu.InventoryMenuBuilder;
import dev.thomashanson.wizards.util.menu.ItemBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

class KitDetailMenu {

    private final Wizards game;

    private final WizardsKit kit;
    private final KitSelectMenu prevMenu;

    KitDetailMenu(Wizards game, WizardsKit kit, KitSelectMenu prevMenu) {
        this.game = game;
        this.kit = kit;
        this.prevMenu = prevMenu;
    }

    void showMenu(Player player) {

        InventoryMenuBuilder menuBuilder = new InventoryMenuBuilder(game.getPlugin(), kit.getName(), prevMenu != null ? 54 : 45);

        menuBuilder.withItem(13,

                new ItemBuilder(kit.getMenuItem().getType())
                        .withName(kit.getFormattedName())
                        .withLore(kit.getDescription())
                        .get()
        );

        if (prevMenu != null) {

            menuBuilder.withItem(49,

                    new ItemBuilder(Material.ARROW)
                            .withName(ChatColor.GREEN + "Go Back")
                            .get(),

                    (p, action, item) -> game.getKitSelectMenu().showMenu(p), ClickType.LEFT
            );
        }

        menuBuilder.show(player);
    }
}