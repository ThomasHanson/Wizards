package dev.thomashanson.wizards.game.spell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.thomashanson.wizards.game.manager.WizardManager;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;


/**
 * Manages the "Spell Book" GUI, allowing players to view, bind,
 * and quick-cast their known spells.
 * <p>
 * This class is responsible for:
 * <ul>
 * <li>Building the {@link Gui} with all spell categories and elements.</li>
 * <li>Populating the GUI with the spells the player has learned.</li>
 * <li>Handling player sort preferences (Default, Rarity, etc.).</li>
 * <li>Processing {@link InventoryClickEvent}s to bind spells to wands
 * or to quick-cast them.</li>
 * </ul>
 */
public class SpellBook {

    /**
     * Defines the sorting methods available to the player in the spell book.
     */
    public enum SortType {
        DEFAULT("Default"),
        RARITY("Rarity"),
        ALPHABETICAL("Alphabetical"),
        CUSTOM("My Layout");

        private final String displayName;
        SortType(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    private final Wizards game;
    private final LanguageManager lang;
    private final WizardManager wizardManager;
    private final SpellManager spellManager;
    private Gui menu;

    private final Map<UUID, SortType> playerSortPreferences = new HashMap<>();

    /**
     * Creates a new SpellBook manager for a specific game instance.
     *
     * @param game           The active {@link Wizards} game.
     * @param spellManager   The global {@link SpellManager}.
     */
    public SpellBook(Wizards game, SpellManager spellManager) {
        this.game = game;
        this.lang = game.getPlugin().getLanguageManager();
        this.wizardManager = game.getWizardManager();
        this.spellManager = spellManager;
    }

    /**
     * Builds and displays the spell book GUI for a specific player.
     *
     * @param player The player to show the GUI to.
     */
    public void showBook(Player player) {
        Wizard wizard = game.getWizard(player);

        if (wizard != null) {
            wizard.setSpellBookOpen(true);
        }

        // Get the player's current sort preference, defaulting to DEFAULT
        SortType currentSort = playerSortPreferences.getOrDefault(player.getUniqueId(), SortType.DEFAULT);

        this.menu = Gui.gui()
                .title(lang.getTranslated(player, "wizards.gui.spellbook.title"))
                .rows(6)
                .disableAllInteractions()
                .create();
        
        switch (currentSort) {
            case DEFAULT:
                populateGuiByDefault(menu, player, wizard);
                break;
            
            // Stubs for other sorting methods you would implement
            case RARITY:
                // populateGuiByRarity(menu, player, wizard);
                break;
            case ALPHABETICAL:
                // populateGuiAlphabetically(menu, player, wizard);
                break;
            case CUSTOM:
                // populateGuiFromCustomLayout(menu, player, wizard);
                break;
            default:
                populateGuiByDefault(menu, player, wizard);
        }

        Map<Integer, Spell> spellSlotMap = spellManager.getAllSpells().values().stream()
                .filter(s -> s.getGuiSlot() >= 0)
                .collect(Collectors.toMap(Spell::getGuiSlot, Function.identity()));

        Set<Integer> spellColumns = new HashSet<>();

        for (SpellElement element : SpellElement.values()) {
            Component descriptionComponent = lang.getTranslated(player, element.getDescriptionKey());
            List<Component> wrappedLore = Spell.wrapText(descriptionComponent, 30, Style.style(NamedTextColor.GRAY));

            menu.setItem(element.getSlot(), ItemBuilder.from(element.getIcon())
                    .model(0x1)
                    .name(lang.getTranslated(player, element.getNameKey()))
                    .lore(wrappedLore) // Use the wrapped lore
                    .asGuiItem());

            for (int i = element.getFirstSlot(); i <= element.getSecondSlot(); i++) {
                spellColumns.add(i);
            }
        }

        for (int i = 0; i < menu.getRows() * 9; i++) {
            Spell spell = spellSlotMap.get(i);

            if (spellColumns.contains(i % 9)) {
                if (spell != null) {
                    if (wizard != null) {
                        int spellLevel = wizard.getLevel(spell.getKey());
                        if (spellLevel > 0) {
                            ItemStack spellItem = spell.createItemStack(player, spellLevel, 1);
                            List<Component> finalLore = new ArrayList<>(spellItem.lore());
                            finalLore.addAll(buildActionLore(player));

                            menu.setItem(i, ItemBuilder.from(spellItem)
                                .lore(finalLore)
                                .asGuiItem(event -> handleSpellClick(event, spell, wizard)));
                        } else {
                            showUnknownSpellItem(i, player);
                        }
                    } else {
                        // REFACTORED: Use the correct createItemStack method from the Spell object
                        ItemStack spellItem = spell.createItemStack(player, 0, 1);
                        menu.setItem(i, ItemBuilder.from(spellItem).asGuiItem());
                    }
                }
            } else if (menu.getInventory().getItem(i) == null) {
                menu.setItem(i, ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).asGuiItem());
            }
        }
        menu.open(player);
    }
    
    /**
     * Fills a GUI slot with a grayed-out "Unknown Spell" item.
     *
     * @param slot   The GUI slot to fill.
     * @param player The player viewing the GUI (for localization).
     */
    private void showUnknownSpellItem(int slot, Player player) {
        menu.setItem(slot, ItemBuilder.from(Material.GRAY_DYE)
                .name(lang.getTranslated(player, "wizards.gui.spellbook.unknown.name"))
                .lore(lang.getTranslated(player, "wizards.gui.spellbook.unknown.lore"))
                .asGuiItem());
    }

    /**
     * Populates the spell book GUI using the default sorting method (by GUI slot).
     *
     * @param menu   The {@link Gui} to populate.
     * @param player The player viewing the GUI.
     * @param wizard The wizard data for the player.
     */
    private void populateGuiByDefault(Gui menu, Player player, Wizard wizard) {
        Map<Integer, Spell> spellSlotMap = spellManager.getAllSpells().values().stream()
                .filter(s -> s.getGuiSlot() >= 0)
                .collect(Collectors.toMap(Spell::getGuiSlot, Function.identity()));
        
        // ... (The entire rest of your showBook logic from before goes here) ...
        // ... from "Set<Integer> spellColumns = new HashSet<>();" to the end of the main for-loop ...
    }

    /**
     * Handles a click on a known spell icon in the spell book.
     *
     * @param event  The {@link InventoryClickEvent}.
     * @param spell  The {@link Spell} that was clicked.
     * @param wizard The {@link Wizard} who clicked.
     */
    private void handleSpellClick(InventoryClickEvent event, Spell spell, Wizard wizard) {
        Player player = (Player) event.getWhoClicked();

        if (event.getClick().isLeftClick()) {
            int wandSlot = player.getInventory().getHeldItemSlot();
            wizard.setSpell(wandSlot, spell.getKey());
            
            player.sendMessage(lang.getTranslated(player, "wizards.boundSpell",
                Placeholder.unparsed("spell_name", spell.getName())
            ));

            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1F, 1.2F);
            player.closeInventory();

        } else if (event.getClick().isRightClick()) {
            game.castSpell(player, wizard, spell, null, true);
            player.closeInventory();
        }

        wizardManager.updateActionBar(wizard);
        game.getWandManager().updateAllWandDisplays(player);
    }

    /**
     * Builds the action lore (Left-Click to Bind, Right-Click to Quick-Cast)
     * for a spell item in the GUI.
     *
     * @param player The player viewing the GUI (for localization).
     * @return A list of action {@link Component}s.
     */
    private List<Component> buildActionLore(Player player) {
        return List.of(
            Component.empty(),
            lang.getTranslated(player, "wizards.gui.spellbook.action.bind"),
            lang.getTranslated(player, "wizards.gui.spellbook.action.quickcast")
        );
    }
}