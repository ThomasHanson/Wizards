package dev.thomashanson.wizards.game.loot;

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a single, weighted item within a loot table.
 * This class is an immutable data container.
 */
class LootItem {

    private final ItemStack itemPrototype;
    private final int weight;
    private final int minAmount;
    private final int maxAmount;

    /**
     * @param itemPrototype The ItemStack to use as a template. Its amount is ignored.
     * @param weight The chance for this item to be chosen. Higher is more common.
     * @param minAmount The minimum stack size to generate.
     * @param maxAmount The maximum stack size to generate.
     */
    LootItem(@NotNull ItemStack itemPrototype, int weight, int minAmount, int maxAmount) {
        this.itemPrototype = itemPrototype;
        this.weight = weight;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
    }

    /**
     * Creates and returns a new, unique ItemStack instance based on this loot entry.
     * It is critical that this returns a CLONE to prevent state corruption.
     *
     * @return A new ItemStack with a randomized amount.
     */
    public ItemStack createItemStack() {
        ItemStack newItem = itemPrototype.clone();
        
        if (minAmount >= maxAmount) {
            newItem.setAmount(minAmount);
        } else {
            int amount = ThreadLocalRandom.current().nextInt(minAmount, maxAmount + 1);
            newItem.setAmount(amount);
        }
        
        return newItem;
    }

    public int getWeight() {
        return weight;
    }
}