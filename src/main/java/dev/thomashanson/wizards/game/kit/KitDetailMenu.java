package dev.thomashanson.wizards.game.kit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.manager.GameManager;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class KitDetailMenu {

    private final WizardsPlugin plugin;
    private final GameManager gameManager;
    private final LanguageManager lang;
    private final WizardsKit kit;
    private int currentLevel;
    private final KitSelectMenu prevMenu;

    public KitDetailMenu(WizardsPlugin plugin, WizardsKit kit, int currentLevel, KitSelectMenu prevMenu) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
        this.lang = plugin.getLanguageManager();
        this.kit = kit;
        this.currentLevel = currentLevel;
        this.prevMenu = prevMenu;
    }

    public void showMenu(Player player) {
        // Get the translated kit name once at the beginning
        Component kitName = lang.getTranslated(player, kit.getNameKey());

        Gui gui = Gui.gui()
                .title(kitName) // Use the translated component directly
                .rows(5)
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));

        // --- Kit Info Item ---
        List<Component> infoLore = new ArrayList<>();
        // Get the translated description and add it
        infoLore.add(lang.getTranslated(player, kit.getDescriptionKey()));
        
        gui.setItem(13, ItemBuilder.from(kit.getIcon())
            .name(kitName.colorIfAbsent(NamedTextColor.AQUA)) // Apply color to the already-translated name
            .lore(infoLore)
            .asGuiItem());

        // --- Level Display ---
        for (int i = 1; i <= 5; i++) {
            boolean isLevelUnlocked = i <= currentLevel;
            Material material = isLevelUnlocked ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
            
            String statusKey = isLevelUnlocked ? "wizards.gui.generic.unlocked" : "wizards.gui.generic.locked";

            List<Component> levelLore = new ArrayList<>();
            levelLore.add(lang.getTranslated(player, statusKey));
            
            String descriptionKey = kit.getDescriptionKey();

            levelLore.add(Component.text(""));
            levelLore.add(lang.getTranslated(player, descriptionKey));

            gui.setItem(20 + (i - 1), ItemBuilder.from(material)
                .name(lang.getTranslated(player, "wizards.gui.generic.level", Placeholder.unparsed("level", String.valueOf(i))))
                .lore(levelLore)
                .asGuiItem());
        }

        // --- Purchase/Upgrade Button ---
        if (currentLevel == 0) { // Kit is not owned yet
            if (kit.getUnlockType() == WizardsKit.UnlockType.COINS) {
                gui.setItem(31, ItemBuilder.from(Material.GOLD_INGOT)
                        .name(lang.getTranslated(player, "wizards.gui.kitDetail.action.purchaseKit"))
                        .lore(
                            lang.getTranslated(player, "wizards.gui.generic.cost", Placeholder.unparsed("cost", String.valueOf(kit.getUnlockCost()))),
                            Component.text(""),
                            lang.getTranslated(player, "wizards.gui.generic.action.clickToPurchase")
                        ).asGuiItem(event -> handlePurchase(player)));

            } else if (kit.getUnlockType() == WizardsKit.UnlockType.ACHIEVEMENTS) {
                gui.setItem(31, ItemBuilder.from(Material.BARRIER)
                        .name(lang.getTranslated(player, "wizards.gui.generic.locked"))
                        .lore(lang.getTranslated(player, "wizards.gui.kitSelect.unlock.achievements"))
                        .asGuiItem());
            }

        } else if (currentLevel < 5) { // Kit is owned but not max level
            Map<Integer, Integer> upgradeTiers = gameManager.getKitManager().getKitUpgradeCosts().get(kit.getId());
            int nextLevel = currentLevel + 1;

            if (upgradeTiers == null || !upgradeTiers.containsKey(nextLevel)) {
                gui.setItem(31, ItemBuilder.from(Material.BARRIER)
                        .name(lang.getTranslated(player, "wizards.gui.kitDetail.status.upgradeNotAvailable"))
                        .lore(lang.getTranslated(player, "wizards.gui.kitDetail.lore.cannotUpgrade"))
                        .asGuiItem());
            } else {
                int upgradeCost = upgradeTiers.get(nextLevel);
                gui.setItem(31, ItemBuilder.from(Material.EMERALD)
                        .name(lang.getTranslated(player, "wizards.gui.kitDetail.action.upgradeToLevel", Placeholder.unparsed("level", String.valueOf(nextLevel))))
                        .lore(
                            lang.getTranslated(player, "wizards.gui.generic.cost", Placeholder.unparsed("cost", String.valueOf(upgradeCost))),
                            Component.text(""),
                            lang.getTranslated(player, "wizards.gui.generic.action.clickToUpgrade")
                        ).asGuiItem(event -> handleUpgrade(player, nextLevel, upgradeCost)));
            }

        } else { // Kit is max level
            gui.setItem(31, ItemBuilder.from(Material.DIAMOND)
                    .name(lang.getTranslated(player, "wizards.gui.kitDetail.status.maxLevel"))
                    .lore(lang.getTranslated(player, "wizards.gui.kitDetail.lore.fullyUpgraded"))
                    .glow(true)
                    .asGuiItem());
        }

        // --- Back Button ---
        if (prevMenu != null) {
            gui.setItem(40, ItemBuilder.from(Material.ARROW)
                .name(lang.getTranslated(player, "wizards.gui.generic.goBack"))
                .asGuiItem(event -> prevMenu.showMenu(player)));
        }

        gui.open(player);
    }

    private void handlePurchase(Player player) {
        KitConfirmationMenu confirmationMenu = new KitConfirmationMenu(plugin, this, kit, KitConfirmationMenu.ActionType.PURCHASE, kit.getUnlockCost(), 1);
        confirmationMenu.showMenu(player);
    }

    private void handleUpgrade(Player player, int nextLevel, int cost) {
        KitConfirmationMenu confirmationMenu = new KitConfirmationMenu(plugin, this, kit, KitConfirmationMenu.ActionType.UPGRADE, cost, nextLevel);
        confirmationMenu.showMenu(player);
    }

    public void setCurrentLevel(int level) {
        this.currentLevel = level;
    }
}