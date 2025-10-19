package dev.thomashanson.wizards.game.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a loot table for a chest, using a weighted random selection.
 */
public class ChestLoot {

    private final List<LootItem> lootItems = new ArrayList<>();
    private int totalWeight = 0;

    /**
     * Selects one weighted-random item from the loot table.
     *
     * @return A new ItemStack instance, or null if the loot table is empty.
     */
    @Nullable
    public ItemStack getLoot() {
        if (totalWeight <= 0) {
            return null; // Return null if the loot table is empty
        }

        int random = ThreadLocalRandom.current().nextInt(totalWeight);

        for (LootItem item : lootItems) {
            random -= item.getWeight();
            if (random < 0) {
                return item.createItemStack();
            }
        }
        return null; // Should be unreachable if totalWeight > 0, but is a safe fallback
    }

    public void addLoot(@NotNull Material material, int weight, int minAmount, int maxAmount) {
        addLoot(new ItemStack(material), weight, minAmount, maxAmount);
    }

    public void addLoot(@NotNull Material material, int weight) {
        addLoot(new ItemStack(material), weight, 1, 1);
    }

    public void addLoot(@NotNull ItemStack item, int weight) {
        addLoot(item, weight, item.getAmount(), item.getAmount());
    }



    /**
     * Adds an item to the loot table with a specified weight and stack size range.
     *
     * @param item The item to add. Its current stack size is ignored.
     * @param weight The chance for this item to be chosen. Higher is more common.
     * @param minAmount The minimum stack size to generate.
     * @param maxAmount The maximum stack size to generate.
     */
    public void addLoot(@NotNull ItemStack item, int weight, int minAmount, int maxAmount) {
        if (weight <= 0) return; // Do not add items with no chance of dropping
        
        LootItem lootItem = new LootItem(item, weight, minAmount, maxAmount);
        this.lootItems.add(lootItem);
        this.totalWeight += lootItem.getWeight();
    }

    /**
     * Adds all items from another ChestLoot instance into this one.
     * This is used for the TABLE_INCLUDE functionality.
     *
     * @param otherLoot The loot table to include.
     * @param weight    This parameter is currently unused but kept for signature consistency.
     * The individual weights of the included items are preserved.
     */
    public void addLoot(@NotNull ChestLoot otherLoot, int weight) {
        for (LootItem lootItem : otherLoot.lootItems) {
            // Add a clone of each LootItem from the other table to this one
            this.lootItems.add(lootItem);
            this.totalWeight += lootItem.getWeight();
        }
    }
}