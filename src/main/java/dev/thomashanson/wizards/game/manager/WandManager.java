package dev.thomashanson.wizards.game.manager;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Manages all logic related to player wands, including item creation,
 * inventory interactions, and visual updates for spells and cooldowns.
 */
public class WandManager {

    private final WizardsPlugin plugin;
    private final WizardManager wizardManager;

    public WandManager(WizardsPlugin plugin, Wizards game) {
        this.plugin = plugin;
        this.wizardManager = game.getWizardManager();
    }

    /**
     * Gives a player their initial set of wands and placeholders when they join a game.
     */
    public void issueInitialWands(Player player) {
        Wizard wizard = wizardManager.getWizard(player);
        if (wizard == null) return;

        for (int i = 0; i < wizard.getMaxWands(); i++) {
            if (i < wizard.getWandsOwned()) {
                player.getInventory().setItem(i, createWandItem(wizard, i));
            } else {
                player.getInventory().setItem(i, createLockedWandItem(i));
            }
        }
    }

    /**
     * Handles the logic for when a player finds and "gains" a new wand,
     * unlocking a new slot.
     */
    public void gainWand(Player player) {
        Wizard wizard = wizardManager.getWizard(player);
        if (wizard == null) return;

        int nextSlot = wizard.getWandsOwned();
        if (nextSlot < wizard.getMaxWands()) {
            wizard.setWandsOwned(wizard.getWandsOwned() + 1);
            player.getInventory().setItem(nextSlot, createWandItem(wizard, nextSlot));
            player.sendMessage(plugin.getLanguageManager().getTranslated(player, "wizards.gainedWand"));
        } else {
            // Player already has max wands, give mana instead.
            wizard.addMana(100F);
            player.sendMessage(plugin.getLanguageManager().getTranslated(player, "wizards.duplicateWand"));
        }
    }

    /**
     * Updates the display of all wands for a player. Call this after a spell is learned,
     * a cooldown changes, or mana updates.
     */
    public void updateAllWandDisplays(Player player) {
        Wizard wizard = wizardManager.getWizard(player);
        if (wizard == null) return;
        for (int i = 0; i < wizard.getMaxWands(); i++) {
            updateWandDisplay(player, i);
        }
    }

    /**
     * Handles a PlayerItemHeldEvent to update the appearance of the previously
     * held and newly held wand items.
     */
    public void handleItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();

        Wizard wizard = wizardManager.getWizard(player);

        if (wizard == null)
            return;

        // Update the appearance of the wand they are switching FROM
        updateWandDisplay(player, event.getPreviousSlot());
        // Update the appearance of the wand they are switching TO
        updateWandDisplay(player, event.getNewSlot());
    }

    /**
     * Handles all inventory click logic related to wand slots.
     */
    public void handleInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Wizard wizard = wizardManager.getWizard(player);
        if (wizard == null) return;

        int slot = event.getSlot();
        
        // Clicks outside the main inventory are not our concern
        if (event.getClickedInventory() != player.getInventory()) {
            return;
        }

        // Check if the clicked slot is a potential wand slot
        if (slot >= 0 && slot < wizard.getMaxWands()) {
            event.setCancelled(true); // Always cancel clicks in wand slots

            // Is the slot locked?
            if (slot >= wizard.getWandsOwned()) {
                player.sendMessage(plugin.getLanguageManager().getTranslated(player, "wizards.game.wand.slotNotOwned"));
                return;
            }

            // Is it a number key swap? (e.g., pressing '2' while hovering slot 1)
            if (event.getClick() == ClickType.NUMBER_KEY) {
                int hotbarSlot = event.getHotbarButton();
                handleWandSwap(player, slot, hotbarSlot);
            } else {
                player.sendMessage(plugin.getLanguageManager().getTranslated(player, "wizards.game.wand.cannotMoveItem"));
            }
        }
    }

    /**
     * The core logic for updating the visual appearance of a single wand item.
     * This now handles icon swapping and dynamic name/lore generation.
     */
    public void updateWandDisplay(Player player, int slot) {
        Wizard wizard = wizardManager.getWizard(player);

        // --- 1. VALIDATION ---
        // Exit if the player isn't a wizard or the slot is not an owned wand slot.
        if (wizard == null || slot < 0 || slot >= wizard.getWandsOwned()) {
            return;
        }

        Wizards activeGame = plugin.getGameManager().getActiveGame();

        ItemStack item = player.getInventory().getItem(slot);

        // --- 2. GATHER DATA ---
        boolean isHeld = player.getInventory().getHeldItemSlot() == slot;
        Spell spell = wizard.getSpell(slot);

        if (item == null || spell == null || activeGame == null) return;

        double usableTime = activeGame.getUsableTime(wizard, spell).getKey();

        // --- 3. ICON SWAPPING ---
        Material displayMaterial;
        if (isHeld) {
            // If HELD: Show the wand's elemental material.
            displayMaterial = spell.getWandElement().getMaterial();
        } else {
            // If NOT HELD: Show the spell's icon.
            displayMaterial = spell.getIcon();
        }
        item.setType(displayMaterial);

        // --- 4. BUKKIT COOLDOWN GRAPHIC ---
        // This graphic should ONLY appear on the currently held item.
        int bukkitCooldownTicks = (int) Math.ceil(usableTime * 20.0);
        if (isHeld) {
            player.setCooldown(item.getType(), bukkitCooldownTicks);
        } else {
            // Crucially, clear the cooldown graphic if this is NOT the held item.
            if (player.hasCooldown(item.getType())) {
                player.setCooldown(item.getType(), 0);
            }
        }

        // --- 5. ITEM AMOUNT (for non-held cooldown) ---
        // If held, amount is always 1. If not held, it shows the cooldown in seconds.
        item.setAmount(isHeld ? 1 : Math.max(1, (int) Math.ceil(usableTime)));

        // --- 6. DYNAMIC NAME & LORE ---
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        if (isHeld) {
            // If HELD: The name is dynamic and includes mana/cooldown info.
            meta.displayName(getWandName(wizard, spell));
            meta.lore(getWandLore(spell));
        } else {
            // If NOT HELD: The name is just the simple spell name.
            Component displayName = (spell != null) ? Component.text(spell.getName()) : Component.text("Empty", NamedTextColor.GRAY);
            meta.displayName(displayName.decoration(TextDecoration.ITALIC, false));
            meta.lore(null);
        }
        item.setItemMeta(meta);
    }

    public void handleWandSwap(Player player, int oldSlot, int newSlot) {
        Wizard wizard = wizardManager.getWizard(player);
        if (wizard == null) return;
        PlayerInventory inv = player.getInventory();

        // Update the OLD slot (change from wand to spell icon)
        if (oldSlot >= 0 && oldSlot < wizard.getWandsOwned()) {
            Spell spell = wizard.getSpell(oldSlot);
            ItemStack item = inv.getItem(oldSlot);
            if (item != null) {
                item.setType(spell != null ? spell.getIcon() : Material.BARRIER);
                // The item's amount (cooldown) will be updated by the tick loop.
            }
        }

        // Update the NEW slot (change from spell icon to wand)
        if (newSlot >= 0 && newSlot < wizard.getWandsOwned()) {
            Spell spell = wizard.getSpell(newSlot);
            ItemStack item = inv.getItem(newSlot);
            if (item != null) {
                Material wandMaterial = (spell != null && spell.getWandElement() != null) ? spell.getWandElement().getMaterial() : Material.BLAZE_ROD;
                item.setType(wandMaterial);
                item.setAmount(1); // Held item is always amount 1.
            }
        }
        
        // Force an update of the wand titles after the swap.
        updateAllWandTitles(player);
    }

    public void updateAllWandTitles(Player player) {
        Wizard wizard = wizardManager.getWizard(player);
        if (wizard == null) return;

        int heldSlot = player.getInventory().getHeldItemSlot();

        for (int i = 0; i < wizard.getWandsOwned(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null) continue;
            
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            Spell spell = wizard.getSpell(i);
            Component displayName;

            if (i == heldSlot) {
                // HELD WAND: Name includes mana and cooldown.
                if (spell != null) {
                    String manaCost = String.valueOf((int) wizard.getManaCost(spell));
                    String cooldown = new DecimalFormat("0.#").format(wizard.getSpellCooldown(spell));
                    
                    displayName = Component.text()
                        .append(Component.text(spell.getName()).color(NamedTextColor.WHITE))
                        .append(Component.text(" ｜ ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(manaCost, NamedTextColor.AQUA)).append(Component.text(" Mana ｜ ", NamedTextColor.BLUE))
                        .append(Component.text(cooldown + "s", NamedTextColor.GREEN)).append(Component.text(" Cooldown", NamedTextColor.GREEN))
                        .build().decoration(TextDecoration.ITALIC, false);
                } else {
                    displayName = plugin.getLanguageManager().getTranslated(player, "wizards.item.wand.name.inInventory");
                }

            } else {
                // NOT HELD WAND: Name is just the spell name.
                displayName = (spell != null) ? Component.text(spell.getName()) : Component.text("Empty", NamedTextColor.GRAY);
            }
            
            meta.displayName(displayName.decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
    }
    
    // --- Item Factory Methods ---

    private ItemStack createWandItem(Wizard wizard, int slot) {
        Spell spell = wizard.getSpell(slot);
        ItemStack item = ItemBuilder.from(Material.BLAZE_ROD)
                .model(slot + 1) // CustomModelData for wand 1, 2, 3...
                .name(getWandName(wizard, spell))
                .lore(getWandLore(spell))
                .unbreakable(true)
                .build();
        return item;
    }

    private Component getWandName(Wizard wizard, Spell spell) {
        if (spell == null) {
            return plugin.getLanguageManager().getTranslated(wizard.getPlayer(), "wizards.item.wand.name.inInventory");
        }
        return Component.text(spell.getName());
    }

    private List<Component> getWandLore(Spell spell) {
        List<Component> lore = new ArrayList<>();
        if (spell != null) {
            // You can add spell stats to the lore here if you want
        }
        lore.add(Component.empty());
        lore.add(plugin.getLanguageManager().getTranslated(null, "wizards.item.wand.lore.bind"));
        lore.add(plugin.getLanguageManager().getTranslated(null, "wizards.item.wand.lore.quickcast"));
        return lore;
    }

    private ItemStack createLockedWandItem(int slot) {
        return ItemBuilder.from(Material.GRAY_DYE)
                .model(slot + 1)
                .name(plugin.getLanguageManager().getTranslated(null, "wizards.item.wand.emptySlot.name"))
                .lore(plugin.getLanguageManager().getTranslated(null, "wizards.item.wand.emptySlot.lore"))
                .build();
    }

    /**
     * Creates a new, un-bound wand ItemStack specifically for use in loot chests.
     * This item is visually distinct (glowing) from wands in a player's inventory.
     *
     * @return A new ItemStack representing a lootable wand.
     */
    public ItemStack createLootableWandItem() {
        
        // This is the translated name for a wand found in a chest
        Component wandName = plugin.getLanguageManager().getTranslated(null, "wizards.item.wand.name.inChest");

        ItemStack wand = ItemBuilder.from(Material.BLAZE_ROD)
                .name(wandName.decoration(TextDecoration.ITALIC, false))
                .glow(true)
                .unbreakable(true)
                .build();
        
        // We can add a PDC tag to identify it as a special item, if needed later.
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            // Using a generic "wizards_item" key can be useful
            NamespacedKey key = new NamespacedKey(plugin, "wizards_item");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "loot_wand");
            wand.setItemMeta(meta);
        }

        return wand;
    }

    public boolean isWandSlot(Wizard wizard, int slot) {
        return wizard != null && slot >= 0 && slot < wizard.getWandsOwned();
    }
}