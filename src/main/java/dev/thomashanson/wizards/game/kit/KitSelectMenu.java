package dev.thomashanson.wizards.game.kit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.manager.GameManager;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class KitSelectMenu {

    private final WizardsPlugin plugin;
    private final GameManager gameManager;
    private final LanguageManager lang;

    public KitSelectMenu(WizardsPlugin plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
        this.lang = plugin.getLanguageManager();
    }

    public void showMenu(Player player) {
        String sql = "SELECT kit_id, level FROM player_kits WHERE player_uuid = ?";
        plugin.getDatabaseManager().executeQueryAsync(sql, (List<Map<String, Object>> results) -> {

            Map<Integer, Integer> playerKits = new HashMap<>();
            
            for (Map<String, Object> row : results) {
                playerKits.put((Integer) row.get("kit_id"), (Integer) row.get("level"));
            }
            
            Gui menuBuilder = Gui.gui()
                .title(lang.getTranslated(player, "wizards.gui.kitSelect.title"))
                .rows(4)
                .create();
        
            menuBuilder.setDefaultClickAction(event -> event.setCancelled(true));

            List<WizardsKit> sortedKits = new ArrayList<>(gameManager.getKitManager().getAllKits());
            sortedKits.sort(Comparator.comparingInt(WizardsKit::getId));

            ItemStack selectedIndicatorItem = ItemBuilder.from(Material.LIME_DYE)
                    .name(lang.getTranslated(player, "wizards.gui.kitSelect.indicator.selected"))
                    .glow(true)
                    .build();
            
            ItemStack deselectedIndicatorItem = ItemBuilder.from(Material.GRAY_DYE)
            .name(lang.getTranslated(player, "wizards.gui.kitSelect.indicator.select"))
            .build();
    
            int slotIndex = 0;

            for (WizardsKit kit : sortedKits) {
                int currentLevel = playerKits.getOrDefault(kit.getId(), 0);
                boolean isUnlocked = currentLevel > 0 || kit.getUnlockType() == WizardsKit.UnlockType.DEFAULT;
                if (kit.getUnlockType() == WizardsKit.UnlockType.DEFAULT && currentLevel == 0) {
                    currentLevel = 1;
                }
                
                Component kitName = lang.getTranslated(player, kit.getNameKey());
                Component kitDescription = lang.getTranslated(player, kit.getDescriptionKey());
                List<Component> lore = new ArrayList<>();
                lore.addAll(wrapText(kitDescription, 30, Style.style(NamedTextColor.GRAY)));
                lore.add(Component.empty());
                if (isUnlocked) {
                    lore.add(lang.getTranslated(player, "wizards.gui.kitSelect.level", Placeholder.unparsed("level", String.valueOf(currentLevel))));
                    lore.add(Component.empty());
                    lore.add(lang.getTranslated(player, "wizards.gui.kitSelect.action.select"));
                    lore.add(lang.getTranslated(player, "wizards.gui.kitSelect.action.upgrade"));
                    
                } else {
                    lore.add(lang.getTranslated(player, "wizards.gui.generic.locked"));
                    lore.add(Component.empty());
                    
                    switch (kit.getUnlockType()) {
                        case COINS -> {
                            lore.add(lang.getTranslated(player, "wizards.gui.generic.cost", Placeholder.unparsed("cost", String.valueOf(kit.getUnlockCost()))));
                            lore.add(Component.empty());
                            lore.add(lang.getTranslated(player, "wizards.gui.kitSelect.action.purchase"));
                        }
                        case ACHIEVEMENTS -> lore.add(lang.getTranslated(player, "wizards.gui.kitSelect.unlock.achievements"));
                        case DEFAULT -> lore.add(lang.getTranslated(player, "wizards.gui.kitSelect.unlock.achievements")); // TODO: Update these to different key
                        default -> throw new IllegalArgumentException("Unexpected value: " + kit.getUnlockType());
                    }
                }   // REFACTORED: Style the existing kitName component instead of using ChatColor
                ItemStack kitIcon = ItemBuilder.from(kit.getIcon())
                        .name(kitName.colorIfAbsent(NamedTextColor.AQUA).decorate(TextDecoration.BOLD))
                        .lore(lore)
                        .glow(gameManager.getKitManager().getKit(player) != null && gameManager.getKitManager().getKit(player).getId() == kit.getId())
                        .amount(Math.max(1, currentLevel))
                        .build();
                int finalCurrentLevel = currentLevel;
                int currentSlot = 10 + slotIndex;
                menuBuilder.setItem(currentSlot, ItemBuilder.from(kitIcon).asGuiItem(event -> {
                    Player p = (Player) event.getWhoClicked();
                    if (event.isLeftClick()) {
                        if (isUnlocked) {
                            gameManager.getKitManager().setKit(p, kit);
                            // REFACTORED: Send a translated message
                            p.sendMessage(lang.getTranslated(p, "wizards.game.kit.selected", Placeholder.component("kit_name", kitName)));
                            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1F, 1.5F);
                            showMenu(p);
                        } else {
                            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1F, 1F);
                        }
                    } else if (event.isRightClick()) {
                        KitDetailMenu detailMenu = new KitDetailMenu(plugin, kit, finalCurrentLevel, KitSelectMenu.this);
                        detailMenu.showMenu(p);
                    }
                }));
                WizardsKit selectedKit = gameManager.getKitManager().getKit(player);
                int indicatorSlot = currentSlot + 9;
                if (selectedKit != null && selectedKit.getId() == kit.getId()) {
                    menuBuilder.setItem(indicatorSlot, ItemBuilder.from(selectedIndicatorItem).asGuiItem());
                } else {
                    menuBuilder.setItem(indicatorSlot, ItemBuilder.from(deselectedIndicatorItem).asGuiItem(event -> {
                        Player p = (Player) event.getWhoClicked();
                        if (isUnlocked) {
                            gameManager.getKitManager().setKit(p, kit);
                            // REFACTORED: Send a translated message
                            p.sendMessage(lang.getTranslated(p, "wizards.game.kit.selected", Placeholder.component("kit_name", kitName)));
                            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1F, 1.5F);
                            showMenu(p);
                        } else {
                            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1F, 1F);
                        }
                    }));
                }   slotIndex++;
            }
            menuBuilder.open(player);
        }, player.getUniqueId().toString());
    }

    /**
     * Wraps a component's plain text content to a specific line length.
     * @param component The component to wrap.
     * @param maxLength The maximum length of each line.
     * @param baseStyle The base style to apply to each new line.
     * @return A list of components, each representing a wrapped line.
     */
    public static List<Component> wrapText(Component component, int maxLength, Style baseStyle) {
        List<Component> lines = new ArrayList<>();
        String plainText = PlainTextComponentSerializer.plainText().serialize(component);
        
        if (plainText.isEmpty()) {
            lines.add(Component.empty());
            return lines;
        }

        String[] words = plainText.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > maxLength && currentLine.length() > 0) {
                lines.add(Component.text(currentLine.toString(), baseStyle));
                currentLine = new StringBuilder();
            }
            if (currentLine.length() > 0) {
                currentLine.append(" ");
            }
            currentLine.append(word);
        }
        lines.add(Component.text(currentLine.toString(), baseStyle));
        return lines;
    }
}