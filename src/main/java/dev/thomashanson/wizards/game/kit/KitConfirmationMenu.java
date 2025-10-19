package dev.thomashanson.wizards.game.kit;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class KitConfirmationMenu {

    public enum ActionType { PURCHASE, UPGRADE }

    private final WizardsPlugin plugin;
    private final LanguageManager lang;
    private final KitDetailMenu prevMenu;
    private final WizardsKit kit;
    private final ActionType actionType;
    private final int cost;
    private final int nextLevel;

    public KitConfirmationMenu(WizardsPlugin plugin, KitDetailMenu prevMenu, WizardsKit kit, ActionType actionType, int cost, int nextLevel) {
        this.plugin = plugin;
        this.prevMenu = prevMenu;
        this.kit = kit;
        this.actionType = actionType;
        this.cost = cost;
        this.nextLevel = nextLevel;
        this.lang = plugin.getLanguageManager();
    }

    public void showMenu(Player player) {
        String titleKey = (actionType == ActionType.PURCHASE) ? "wizards.gui.kitConfirm.title.purchase" : "wizards.gui.kitConfirm.title.upgrade";
        Component title = lang.getTranslated(player, titleKey);

        Gui gui = Gui.gui()
                .title(title)
                .rows(3)
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));

        // --- Center Info Item ---
        Material infoMaterial = (actionType == ActionType.PURCHASE) ? Material.GOLD_INGOT : Material.EMERALD;

        String actionKey = (actionType == ActionType.PURCHASE) ? "wizards.gui.kitConfirm.action.purchase" : "wizards.gui.kitConfirm.action.upgrade";
        Component actionText = lang.getTranslated(player, actionKey, Placeholder.unparsed("level", String.valueOf(nextLevel)));
        Component kitName = lang.getTranslated(player, kit.getNameKey());

        gui.setItem(13, ItemBuilder.from(infoMaterial)
                .name(lang.getTranslated(player, "wizards.gui.kitConfirm.infoItem.name",
                        Placeholder.component("action", actionText),
                        Placeholder.component("kit_name", kitName)
                ))
                .lore(
                        Component.text(""),
                        lang.getTranslated(player, "wizards.gui.generic.cost", Placeholder.unparsed("cost", String.valueOf(cost)))
                ).asGuiItem());

        // --- Confirm Button ---
        gui.setItem(11, ItemBuilder.from(Material.LIME_WOOL)
                .name(lang.getTranslated(player, "wizards.gui.kitConfirm.button.confirm"))
                .lore(lang.getTranslated(player, "wizards.gui.kitConfirm.lore.confirm"))
                .asGuiItem(event -> performAction(player)));

        // --- Cancel Button ---
        gui.setItem(15, ItemBuilder.from(Material.RED_WOOL)
                .name(lang.getTranslated(player, "wizards.gui.kitConfirm.button.cancel"))
                .lore(lang.getTranslated(player, "wizards.gui.kitConfirm.lore.cancel"))
                .asGuiItem(event -> prevMenu.showMenu(player)));

        gui.open(player);
    }

    private void performAction(Player player) {
        String uuid = player.getUniqueId().toString();
        Component kitName = lang.getTranslated(player, kit.getNameKey());

        plugin.getDatabaseManager().executeQueryAsync("SELECT coins FROM players WHERE uuid = ? LIMIT 1", results -> {
            if (results.isEmpty()) {
                player.sendMessage(lang.getTranslated(player, "wizards.system.error.noPlayerData"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1F, 1F);
                return;
            }

            double playerCoins = ((Number) results.get(0).get("coins")).doubleValue();

            if (playerCoins >= cost) {
                double newBalance = playerCoins - cost;
                String updateCoinsSql = "UPDATE players SET coins = ? WHERE uuid = ?";
                
                Component successMessage;

                if (actionType == ActionType.PURCHASE) {
                    String purchaseSql = "INSERT INTO player_kits (player_uuid, kit_id, level) VALUES (?, ?, ?)";
                    plugin.getDatabaseManager().executeUpdateAsync(purchaseSql, uuid, kit.getId(), nextLevel);
                    successMessage = lang.getTranslated(player, "wizards.system.success.kitPurchased", Placeholder.component("kit_name", kitName));
                
                } else { // UPGRADE
                    String upgradeSql = "UPDATE player_kits SET level = ? WHERE player_uuid = ? AND kit_id = ?";
                    plugin.getDatabaseManager().executeUpdateAsync(upgradeSql, nextLevel, uuid, kit.getId());
                    successMessage = lang.getTranslated(player, "wizards.system.success.kitUpgraded",
                            Placeholder.component("kit_name", kitName),
                            Placeholder.unparsed("level", String.valueOf(nextLevel))
                    );
                }

                plugin.getDatabaseManager().executeUpdateAsync(updateCoinsSql, newBalance, uuid);
                plugin.getGameManager().getKitManager().updatePlayerKitLevelCache(player.getUniqueId(), kit.getId(), nextLevel);

                player.sendMessage(successMessage);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 1.2F);

                prevMenu.setCurrentLevel(nextLevel);
                prevMenu.showMenu(player);

            } else {
                player.sendMessage(lang.getTranslated(player, "wizards.system.error.notEnoughCoins",
                        Placeholder.unparsed("coins", String.valueOf(cost - playerCoins))
                ));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1F, 1F);
                prevMenu.showMenu(player);
            }

        }, uuid);
    }
}