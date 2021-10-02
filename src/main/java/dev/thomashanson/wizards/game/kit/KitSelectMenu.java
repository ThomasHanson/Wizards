package dev.thomashanson.wizards.game.kit;

import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.manager.GameManager;
import dev.thomashanson.wizards.util.menu.InventoryMenuBuilder;
import dev.thomashanson.wizards.util.menu.ItemBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

public class KitSelectMenu {

    private final Wizards game;
    private final GameManager gameManager;

    public KitSelectMenu(Wizards game) {
        this.game = game;
        this.gameManager = game.getPlugin().getGameManager();
    }

    public void showMenu(Player player) {

        InventoryMenuBuilder menuBuilder = new InventoryMenuBuilder(game.getPlugin(), "Kit Selection", 45);

        ItemBuilder itemBuilder;

        for (int i = 0; i < gameManager.getWizardsKits().size(); i++) {

            WizardsKit kit = gameManager.getWizardsKits().get(i);
            KitDetailMenu detailMenu = new KitDetailMenu(game, kit, this);

            itemBuilder = new ItemBuilder(kit.getMenuItem().getType())
                    .withName(kit.getFormattedName())
                    .withLore(kit.getDescription());

            menuBuilder.withItem(19 + i, itemBuilder.get(), (p, action, item) -> {

                if (action.isLeftClick()) {

                    game.getPlugin().getGameManager().setKit(p, kit);

                    player.sendMessage(ChatColor.GREEN + "Set selected kit to " + ChatColor.GOLD + kit.getName());
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 10F, 1F);

                    player.closeInventory();

                } else {
                    detailMenu.showMenu(player);
                }

            }, ClickType.LEFT, ClickType.RIGHT);
        }

        menuBuilder.show(player);
    }
}