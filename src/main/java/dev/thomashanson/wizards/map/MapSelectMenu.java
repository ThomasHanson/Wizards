package dev.thomashanson.wizards.map;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class MapSelectMenu {

    private final WizardsPlugin plugin;
    private final LanguageManager lang; // Assume LanguageManager is accessible

    public MapSelectMenu(WizardsPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    public void showMenu(Player player) {
        Gui menu = Gui.gui()
                .title(lang.getTranslated(player, "wizards.menu.map_select.title"))
                .rows(3) // Adjusted rows for a cleaner look
                .create();

        List<LocalGameMap> allMaps = plugin.getMapManager().getAllMaps();
        Collections.sort(allMaps);

        for (int i = 0; i < allMaps.size(); i++) {
            LocalGameMap map = allMaps.get(i);

            // Using MiniMessage placeholders for dynamic values in lore
            String modes = map.getModes().stream().map(Enum::name).collect(Collectors.joining(", "));
            
            menu.setItem(10 + i, ItemBuilder.from(Material.FILLED_MAP)
                    .name(Component.text(map.getName(), NamedTextColor.GREEN, TextDecoration.BOLD))
                    .lore(
                        lang.getTranslated(player, "wizards.menu.map_select.lore.modes", Placeholder.unparsed("modes", modes)),
                        lang.getTranslated(player, "wizards.menu.map_select.lore.authors", Placeholder.unparsed("authors", map.getAuthors())),
                        Component.empty(),
                        lang.getTranslated(player, "wizards.menu.map_select.lore.click_prompt")
                    )
                    .asGuiItem(event -> {
                        player.closeInventory();
                        plugin.getMapManager().setActiveMap(map);
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
                        lang.getTranslated(player, "wizards.misc.map_changed", Placeholder.unparsed("map_name", map.getName()));
                    }));
        }
        menu.open(player);
    }
}