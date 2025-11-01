package dev.thomashanson.wizards.game.state.listener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.event.CustomDeathEvent;
import dev.thomashanson.wizards.event.PotionConsumeEvent;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.thomashanson.wizards.game.manager.PlayerStatsManager.StatType;
import dev.thomashanson.wizards.game.potion.PotionType;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.hologram.Hologram;
import dev.thomashanson.wizards.hologram.HologramProperties;
import dev.thomashanson.wizards.util.DebugUtil;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import net.kyori.adventure.text.Component;

public class GameListener implements Listener {
    
    private final WizardsPlugin plugin;
    private final Wizards game;

    public GameListener(WizardsPlugin plugin, Wizards game) {
        this.plugin = plugin;
        this.game = game;
    }

    @EventHandler
    public void onChestOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        Inventory inventory = event.getInventory();

        if (inventory.getType() != InventoryType.CHEST) return;

        Wizard wizard = game.getWizard(player); // Uses WizardManager delegation

        if (game.isLive() && wizard == null) { // If game is live, player must be a wizard
            event.setCancelled(true);
            return;
        }

        if (wizard != null && inventory.getLocation() != null) {
            if (wizard.getChestsLooted().add(inventory.getLocation().clone())) {
                game.incrementStat(player, StatType.CHESTS_LOOTED, 1);
            }
        }
    }

    
    @EventHandler
    public void interactMenu(PlayerInteractEvent event) {

        Player player = event.getPlayer();
        Action action = event.getAction();

        Wizard wizard = game.getWizard(player);

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)
            game.castSpell(event.getPlayer(), event.getClickedBlock());

        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {

            ItemStack item = event.getItem();

            if (item != null) {
                // REFACTORED: Check against the base item, but ideally give player a translated one.
                if (item.getType() == Material.ENCHANTED_BOOK) { // Looser check
                    game.getSpellBook().showBook(player);
                } else if (item.getType() == Material.NETHER_STAR) { // Looser check
                    game.getKitSelectMenu().showMenu(player);
                }
            }

            if (!game.isLive() || wizard == null)
                return;

            if (player.getInventory().getHeldItemSlot() >= wizard.getWandsOwned())
                return;

            if (player.getInventory().getHeldItemSlot() >= wizard.getMaxWands())
                return;

            if (event.getClickedBlock() == null || !(event.getClickedBlock().getState() instanceof InventoryHolder))
                game.getSpellBook().showBook(player);
        }
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {

        if (game == null)
            return;

        game.getWandManager().handleItemHeld(event);
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Wizard wizard = game.getWizardManager().getWizard(player);

        if (game.getWandManager().isWandSlot(wizard, player.getInventory().getHeldItemSlot())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {

        Inventory clickedInventory = event.getClickedInventory();

        if (event.getClickedInventory() == null || !(event.getWhoClicked() instanceof Player)) {
            return;
        }

        game.getWandManager().handleInventoryClick(event);

        Player player = (Player) event.getWhoClicked();
        Wizard wizard = game.getWizard(player);

        if (wizard == null) return;

        if (clickedInventory.getType() == InventoryType.PLAYER) {
            game.handlePlayerInventoryClick(event, player, wizard);
        } else if (clickedInventory.getHolder() instanceof Chest || clickedInventory.getHolder() instanceof DoubleChest) {
            game.handleChestInventoryClick(event, player, wizard);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (game.isLive() && game.getWizard(event.getEntity()) != null) {
            event.getDrops().clear();
        }
    }

    @EventHandler
    public void onWizardDeath(CustomDeathEvent event) {
        LivingEntity victim = event.getVictim();

        if (!(victim instanceof Player))
            return;

        if (!game.isLive()) return;

        Player player = (Player) victim;
        Wizard wizard = game.getWizard(player);

        if (wizard == null) return;

        game.getWizardManager().handleDeathOrQuit(player, true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Wizard wizard = game.getWizard(player);

        if (wizard != null) {
            int heldSlot = player.getInventory().getHeldItemSlot();

            // Logic for player's wand slots (0 to wizard.getMaxWands() - 1)
            // Note: In a player's inventory, hotbar slots are 0-8. Other main inventory slots are 9-35.
            // We assume wand slots are directly mapped to the first N slots of the player inventory (0, 1, 2, ...).
            if (heldSlot >= 0 && heldSlot < wizard.getMaxWands()) {
                event.setCancelled(true); // Prevent breaking blocks with wand slots
            }
        }
    }

    @EventHandler
    public void onSpawn(ItemSpawnEvent event) {
        ItemStack itemStack = event.getEntity().getItemStack();
        Spell spell = game.getSpell(itemStack);
        LanguageManager lang = plugin.getLanguageManager();

        if (game.getDroppedGameItems().contains(event.getEntity())) {
            event.getEntity().setInvulnerable(true);
        }

        // Use a List<Component> to properly support multi-line text.
        List<Component> hologramLines = new ArrayList<>();

        if (spell != null) {
            // For spells, create two separate Component objects for the two lines.
            hologramLines.add(lang.getTranslated(null, "wizards.hologram.spell.line1"));
            hologramLines.add(lang.getTranslated(null, spell.getName()));

        } else if (itemStack.getType() == Material.BLAZE_ROD && itemStack.getItemMeta() != null && itemStack.getItemMeta().hasCustomModelData()) {
            hologramLines.add(lang.getTranslated(null, "wizards.hologram.wand"));

            // Restore the glow removal logic for wands that were glowing in chests
            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null && meta.hasEnchants()) {
                event.getEntity().setItemStack(ItemBuilder.from(itemStack).glow(false).build());
            }

        } else if (itemStack.getType() == Material.NETHER_STAR) {
            hologramLines.add(lang.getTranslated(null, "wizards.item.soul.name"));
        }

        // Proceed only if we have text to display.
        if (!hologramLines.isEmpty()) {
            Item itemEntity = event.getEntity();

            // 1. Define properties for the hologram. We'll add an offset to raise it above the item.
            HologramProperties properties = HologramProperties.builder()
                    .attachmentOffset(new Vector(0, 0.5, 0)) // Lifts the hologram 0.5 blocks up
                    .build();

            // 2. Create the hologram using the manager.
            Hologram hologram = plugin.getHologramManager().createHologram(
                itemEntity.getLocation(),
                hologramLines,
                properties
            );

            // 3. Attach the hologram to the item entity. The manager now handles all updates.
            hologram.attachTo(itemEntity);

            // Your existing logic to track the item
            game.getDroppedGameItems().add(itemEntity);
        }
    }

    @EventHandler
    public void onDespawn(ItemDespawnEvent event) {
        // If the item is one of our special game items, prevent despawn
        if (game.getDroppedGameItems().contains(event.getEntity())) {
            event.getEntity().setTicksLived(1); // Reset ticks lived to prevent despawn
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        Wizard wizard = game.getWizard(player); // Use delegated method

        Item itemEntity = event.getItem();
        ItemStack itemStack = itemEntity.getItemStack();
        boolean isGameItemConsumed = false;

        if (game.getDroppedGameItems().contains(itemEntity)) {
            // This was a special game-dropped item, handle hologram removal
            // plugin.getHologramManager().destroyHologram(itemEntity); // Assuming this method exists
            game.getDroppedGameItems().remove(itemEntity);
        }

        LanguageManager lang = plugin.getLanguageManager();

        Spell spell = game.getSpell(itemStack); // Ensure getSpell uses PDC

        if (spell != null) {
            if (wizard == null && game.isLive()) { event.setCancelled(true); return; } // Non-wizards can't pick up spells in live game
            game.learnSpell(player, spell); // learnSpell handles wizard null check internally
            isGameItemConsumed = true;
        } else if (itemStack.getType() == Material.BLAZE_ROD && itemStack.getItemMeta().hasCustomModelData()) { // Check for your custom wand
            if (wizard == null && game.isLive()) { event.setCancelled(true); return; }
            game.getWandManager().gainWand(player);
            isGameItemConsumed = true;
        } else if (itemStack.getType() == Material.NETHER_STAR) { // Assuming this is the "Wizard Soul"
            if (wizard == null) { if(game.isLive()) event.setCancelled(true); return; } // Only wizards can pick up souls
            
            wizard.addSoulStar();
            game.getWizardManager().updateActionBar(wizard); // Update action bar via WizardManager
            player.sendMessage(lang.getTranslated(player, "wizards.soulAbsorbed"));
            isGameItemConsumed = true;
        }

        if (isGameItemConsumed) {
            event.setCancelled(true);
            itemEntity.remove();
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7F, 1.5F); // More fitting sound
        }
    }

    // disablePunching, onItemDrop, preventCrafting, onArmorEquip, onPlace remain mostly unchanged
    // Ensure they use getWizard() which delegates to WizardManager.
    // onItemConsume is significantly changed.
    // ... (these event handlers) ...

    @EventHandler
    public void onItemConsume(PlayerItemConsumeEvent event) {
        LanguageManager lang = plugin.getLanguageManager();

        Player player = event.getPlayer();
        Wizard wizard = game.getWizard(player);

        ItemStack item = event.getItem();
        PotionType consumedPotionType = game.getPotion(item);

        if (wizard == null || consumedPotionType == null)
            return;

        // Custom PotionConsumeEvent for your game logic/other plugins

        game.incrementStat(player, StatType.POTIONS_DRANK, 1);

        PotionConsumeEvent wizardsPotionEvent = new PotionConsumeEvent(player, consumedPotionType);
        Bukkit.getPluginManager().callEvent(wizardsPotionEvent);
        if (wizardsPotionEvent.isCancelled()) {
            // If your custom event is cancelled, prevent Bukkit's consumption and our logic
            event.setCancelled(true); 
            return;
        }

        PotionType currentlyActivePotionOnWizard = wizard.getActivePotion();
        dev.thomashanson.wizards.game.potion.Potion potionInstanceToActivate = game.getPotions().get(consumedPotionType);

        if (potionInstanceToActivate == null) {
            DebugUtil.debugMessage("Potion instance not found for type: " + consumedPotionType.name(), player);
            return;
        }

        // Handle previously active potion
        if (currentlyActivePotionOnWizard != null) {
            dev.thomashanson.wizards.game.potion.Potion currentPotionInstance = game.getPotions().get(currentlyActivePotionOnWizard);
            if (currentPotionInstance != null) {
                currentPotionInstance.deactivate(wizard); // Deactivate effects on wizard object
            }
            game.getWizardManager().playerPotionEffectCancelled(player, currentlyActivePotionOnWizard); // Notify manager

            if (!currentlyActivePotionOnWizard.equals(consumedPotionType)) {
                player.sendMessage(lang.getTranslated(player, "wizards.potionsMix")); // REFACTORED
            }
        }

        // Bukkit's event.setItem(null) will happen automatically if not cancelled,
        // or you can do it explicitly if you cancel Bukkit's event but still want to "consume" your custom item.
        // For standard potion drinking, let Bukkit handle removing the item by not cancelling PlayerItemConsumeEvent.
        // If it's a custom item that looks like a potion, you might do event.setCancelled(true) and manage item removal yourself.

        // Activate the new potion
        potionInstanceToActivate.onActivate(wizard); // Apply effects to wizard object
        game.getWizardManager().playerConsumedPotion(player, consumedPotionType, Instant.now()); // Notify manager to track it

        String messageKey = null;
        switch (consumedPotionType) {
            case RUSHER:
                messageKey = "wizards.potion.activated.rusher";
                break;
            case WISDOM:
                messageKey = "wizards.potion.activated.wisdom";
                break;
            case LUCK:
                messageKey = "wizards.potion.activated.luck";
                break;
            case REGENERATION:
                messageKey = "wizards.potion.activated.regeneration";
                break;
            case SIGHT:
                messageKey = "wizards.potion.activated.sight";
                break;
            case GAMBLER:
                messageKey = "wizards.potion.activated.gambler";
                break;
            case VOLATILE:
                messageKey = "wizards.potion.activated.volatile";
                break;
            case IRON:
                messageKey = "wizards.potion.activated.iron";
                break;
            case FROZEN:
                messageKey = "wizards.potion.activated.frozen";
                break;
            // MANA potion does not need a message, so it's excluded.
            default:
                break;
        }

        if (messageKey != null) {
            player.sendMessage(lang.getTranslated(player, messageKey));
        }

        event.setItem(new ItemStack(Material.AIR));
    }
}