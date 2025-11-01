package dev.thomashanson.wizards.game.manager;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
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
 * <p>
 * This manager is instantiated once by {@link WizardsPlugin} and persists
 * for the life of the server.
 */
public class WandManager {

    private final WizardsPlugin plugin;
    private final NamespacedKey wandItemKey;

    /**
     * Constructs a new WandManager.
     *
     * @param plugin The main plugin instance, used to access other managers.
     */
    public WandManager(WizardsPlugin plugin) {
        this.plugin = plugin;
        this.wandItemKey = new NamespacedKey(plugin, "wizards_item");
    }

    /**
     * Issues the initial set of wand and placeholder items to a player
     * when they are initialized as a Wizard.
     *
     * @param player The player to issue wands to.
     */
    public void issueInitialWands(Player player) {
        Wizard wizard = getWizard(player);
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
     * Handles the logic for a player gaining a new wand, typically from a
     * loot chest or world drop.
     *
     * @param player The player gaining the wand.
     */
    public void gainWand(Player player) {
        Wizard wizard = getWizard(player);
        if (wizard == null) return;

        int nextSlot = wizard.getWandsOwned();
        if (nextSlot < wizard.getMaxWands()) {
            wizard.setWandsOwned(wizard.getWandsOwned() + 1);
            // Don't just set the item, call updateWandDisplay to fully initialize it
            updateWandDisplay(player, nextSlot);
            player.sendMessage(plugin.getLanguageManager().getTranslated(player, "wizards.gainedWand"));
        } else {
            // Player already has max wands, give mana instead.
            wizard.addMana(100F);
            player.sendMessage(plugin.getLanguageManager().getTranslated(player, "wizards.duplicateWand"));
        }
    }

    /**
     * Updates the display of all wands for a player.
     * Call this after a spell is learned, a cooldown changes, or mana updates.
     *
     * @param player The player whose wands should be updated.
     */
    public void updateAllWandDisplays(Player player) {
        Wizard wizard = getWizard(player);
        if (wizard == null) return;
        for (int i = 0; i < wizard.getMaxWands(); i++) {
            updateWandDisplay(player, i);
        }
    }

    /**
     * Handles a {@link PlayerItemHeldEvent} to update the appearance of the
     * previously held and newly held wand items.
     *
     * @param event The item held event.
     */
    public void handleItemHeld(PlayerItemHeldEvent event) {
        // Update the appearance of the wand they are switching FROM
        updateWandDisplay(event.getPlayer(), event.getPreviousSlot());
        // Update the appearance of the wand they are switching TO
        updateWandDisplay(event.getPlayer(), event.getNewSlot());
    }

    /**
     * Handles all inventory click logic related to wand slots.
     *
     * @param event The inventory click event.
     */
    public void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Wizard wizard = getWizard(player);
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
                handleWandSwap(player, wizard, slot, hotbarSlot);
            } else {
                player.sendMessage(plugin.getLanguageManager().getTranslated(player, "wizards.game.wand.cannotMoveItem"));
            }
        }
    }

    /**
     * Handles the logic for swapping two wand slots using a hotbar key.
     *
     * @param player     The player swapping wands.
     * @param wizard     The player's wizard object.
     * @param sourceSlot The slot the player's cursor was over (0-8).
     * @param targetSlot The hotbar key they pressed (0-8).
     */
    private void handleWandSwap(Player player, Wizard wizard, int sourceSlot, int targetSlot) {
        LanguageManager lang = plugin.getLanguageManager();

        // Check if the target slot is also an owned wand slot
        if (targetSlot < 0 || targetSlot >= wizard.getWandsOwned()) {
            player.sendMessage(lang.getTranslated(player, "wizards.game.wand.invalidSwapTarget"));
            return;
        }

        if (sourceSlot == targetSlot) {
            return; // Cannot swap with self
        }

        // Perform the swap
        Spell spellInSourceSlot = wizard.getSpell(sourceSlot);
        Spell spellInTargetSlot = wizard.getSpell(targetSlot);

        wizard.setSpell(sourceSlot, (spellInTargetSlot != null) ? spellInTargetSlot.getKey() : null);
        wizard.setSpell(targetSlot, (spellInSourceSlot != null) ? spellInSourceSlot.getKey() : null);

        player.sendMessage(lang.getTranslated(player, "wizards.game.wand.swapped",
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("slot_1", String.valueOf(sourceSlot + 1)),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("slot_2", String.valueOf(targetSlot + 1))
        ));

        // Visually update both slots
        updateWandDisplay(player, sourceSlot);
        updateWandDisplay(player, targetSlot);
    }

    /**
     * The authoritative method to update every visual aspect of a wand in a specific slot.
     * This handles icons, names, lore, amounts, and Bukkit cooldowns based on whether
     * the wand is held, on cooldown, or has a spell.
     *
     * @param player The player whose wand to update.
     * @param slot   The inventory slot of the wand (0-8).
     */
    public void updateWandDisplay(Player player, int slot) {
        Wizard wizard = getWizard(player);

        // --- 1. VALIDATION ---
        if (wizard == null || slot < 0 || slot >= wizard.getMaxWands()) {
            return;
        }

        // Handle locked (un-owned) slots
        if (slot >= wizard.getWandsOwned()) {
            player.getInventory().setItem(slot, createLockedWandItem(slot));
            return;
        }

        ItemStack item = player.getInventory().getItem(slot);
        if (item == null) {
            // This shouldn't happen, but as a fallback, issue a new item
            item = createWandItem(wizard, slot);
            player.getInventory().setItem(slot, item);
        }

        Wizards activeGame = getActiveGame();
        Spell spell = wizard.getSpell(slot);
        boolean isHeld = player.getInventory().getHeldItemSlot() == slot;

        // --- 2. GATHER DATA ---
        double usableTime = 0;
        if (spell != null && activeGame != null && activeGame.isLive()) {
            usableTime = activeGame.getUsableTime(wizard, spell).getKey();
        }

        // --- 3. ICON & BUKKIT COOLDOWN ---
        Material displayMaterial;
        if (isHeld) {
            displayMaterial = (spell != null) ? spell.getWandElement().getMaterial() : Material.BLAZE_ROD; // Default wand
            int bukkitCooldownTicks = (int) Math.ceil(usableTime * 20.0);
            player.setCooldown(displayMaterial, bukkitCooldownTicks);

        } else {
            displayMaterial = (spell != null) ? spell.getIcon() : Material.BLAZE_ROD; // Default wand
            // Clear cooldown graphic if this is NOT the held item.
            if (player.hasCooldown(item.getType())) {
                player.setCooldown(item.getType(), 0);
            }
        }
        item.setType(displayMaterial);

        // --- 4. ITEM AMOUNT (COOLDOWN) ---
        item.setAmount(isHeld ? 1 : Math.max(1, (int) Math.ceil(usableTime)));

        // --- 5. DYNAMIC NAME & LORE ---
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.displayName(getWandName(wizard, spell, isHeld));
        meta.lore(getWandLore(wizard, spell, isHeld));

        // Ensure CustomModelData is set (for wand 1, 2, 3...)
        meta.setCustomModelData(slot + 1);

        item.setItemMeta(meta);
    }

    /**
     * Generates the dynamic display name for a wand item.
     *
     * @param wizard The wizard who owns the wand.
     * @param spell  The spell bound to the wand, or null if empty.
     * @param isHeld Whether the player is currently holding this wand.
     * @return The formatted Component for the item's name.
     */
    private Component getWandName(Wizard wizard, Spell spell, boolean isHeld) {
        LanguageManager lang = plugin.getLanguageManager();
        Player player = wizard.getPlayer();

        if (spell == null) {
            return lang.getTranslated(player, "wizards.item.wand.name.inInventory")
                    .decoration(TextDecoration.ITALIC, false);
        }

        Wizards game = getActiveGame();
        boolean isLive = (game != null && game.isLive());

        if (isHeld && isLive) {
            // HELD WAND (In-Game): Name includes mana and cooldown.
            String manaCost = String.valueOf((int) wizard.getManaCost(spell));
            String cooldown = new DecimalFormat("0.#").format(wizard.getSpellCooldown(spell));

            return Component.text()
                    .append(Component.text(spell.getName()).color(NamedTextColor.WHITE))
                    .append(Component.text(" ｜ ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(manaCost, NamedTextColor.AQUA)).append(Component.text(" Mana ｜ ", NamedTextColor.BLUE))
                    .append(Component.text(cooldown + "s", NamedTextColor.GREEN)).append(Component.text(" Cooldown", NamedTextColor.GREEN))
                    .build().decoration(TextDecoration.ITALIC, false);
        } else {
            // NOT HELD WAND (or not in game): Name is just the spell name.
            return Component.text(spell.getName())
                    .color(NamedTextColor.YELLOW) // Use yellow for non-held spells
                    .decoration(TextDecoration.ITALIC, false);
        }
    }

    /**
     * Generates the dynamic lore for a wand item.
     *
     * @param wizard The wizard who owns the wand.
     * @param spell  The spell bound to the wand, or null if empty.
     * @param isHeld Whether the player is currently holding this wand.
     * @return The formatted List of Components for the item's lore.
     */
    private List<Component> getWandLore(Wizard wizard, Spell spell, boolean isHeld) {
        if (!isHeld || spell == null) {
            return null; // Only held wands with spells get lore
        }

        LanguageManager lang = plugin.getLanguageManager();
        Player player = wizard.getPlayer();
        List<Component> lore = new ArrayList<>();

        // You can re-add spell stats here if you want
        // lore.addAll(spell.buildLore(player, wizard.getLevel(spell.getKey())));

        lore.add(Component.empty());
        lore.add(lang.getTranslated(player, "wizards.item.wand.lore.bind"));
        lore.add(lang.getTranslated(player, "wizards.item.wand.lore.quickcast"));
        return lore;
    }

    /**
     * Creates the base ItemStack for an owned, unbound wand.
     *
     * @param wizard The wizard who owns the wand.
     * @param slot   The slot this wand is for.
     * @return A new ItemStack.
     */
    private ItemStack createWandItem(Wizard wizard, int slot) {
        Player player = wizard.getPlayer();
        LanguageManager lang = plugin.getLanguageManager();

        return ItemBuilder.from(Material.BLAZE_ROD)
                .model(slot + 1) // CustomModelData for wand 1, 2, 3...
                .name(lang.getTranslated(player, "wizards.item.wand.name.inInventory").decoration(TextDecoration.ITALIC, false))
                .unbreakable(true)
                .build();
    }

    /**
     * Creates the grayed-out placeholder item for a locked wand slot.
     *
     * @param slot The slot this placeholder is for.
     * @return A new ItemStack.
     */
    private ItemStack createLockedWandItem(int slot) {
        LanguageManager lang = plugin.getLanguageManager();
        return ItemBuilder.from(Material.GRAY_DYE)
                .model(slot + 1)
                .name(lang.getTranslated(null, "wizards.item.wand.emptySlot.name").decoration(TextDecoration.ITALIC, false))
                .lore(lang.getTranslated(null, "wizards.item.wand.emptySlot.lore"))
                .build();
    }

    /**
     * Creates a new, un-bound wand ItemStack specifically for use in loot chests.
     *
     * @return A new ItemStack representing a lootable wand.
     */
    public ItemStack createLootableWandItem() {
        LanguageManager lang = plugin.getLanguageManager();
        Component wandName = lang.getTranslated(null, "wizards.item.wand.name.inChest");

        ItemStack wand = ItemBuilder.from(Material.BLAZE_ROD)
                .name(wandName.decoration(TextDecoration.ITALIC, false))
                .glow(true)
                .unbreakable(true)
                .build();

        ItemMeta meta = wand.getItemMeta();
        Objects.requireNonNull(meta, "ItemMeta should not be null");
        meta.getPersistentDataContainer().set(wandItemKey, PersistentDataType.STRING, "loot_wand");
        wand.setItemMeta(meta);

        return wand;
    }

    /**
     * Checks if a given inventory slot index is an owned wand slot.
     *
     * @param wizard The wizard to check against.
     * @param slot   The inventory slot index.
     * @return True if the slot is an owned wand slot, false otherwise.
     */
    public boolean isWandSlot(Wizard wizard, int slot) {
        return wizard != null && slot >= 0 && slot < wizard.getWandsOwned();
    }

    /**
     * Helper to safely get the active game instance.
     *
     * @return The active {@link Wizards} game, or null if none.
     */
    private Wizards getActiveGame() {
        GameManager gm = plugin.getGameManager();
        return (gm != null) ? gm.getActiveGame() : null;
    }

    /**
     * Helper to safely get the Wizard object for a player.
     *
     * @param player The player.
     * @return The {@link Wizard} object, or null if not in a game.
     */
    private Wizard getWizard(Player player) {
        Wizards game = getActiveGame();
        return (game != null) ? game.getWizard(player) : null;
    }
}
