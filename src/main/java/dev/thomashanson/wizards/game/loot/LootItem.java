package dev.thomashanson.wizards.game.loot;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

class LootItem {

    private final ItemStack item;
    private final int rarity;
    private final int min;
    private final int max;

    LootItem(ItemStack item, int rarity, int min, int max) {
        this.item = item;
        this.rarity = rarity;
        this.min = min;
        this.max = max;
    }

    public LootItem(ItemStack item, int amount) {
        this(item, amount, item.getAmount(), item.getAmount());
    }

    public LootItem(Material material, int amount) {
        this(material, amount, 1);
    }

    private LootItem(Material material, int amount, int min) {
        this.item = new ItemStack(material);
        this.rarity = amount;
        this.min = min;
        this.max = 1;
    }

    ItemStack getItemStack() {
        item.setAmount((ThreadLocalRandom.current().nextInt(Math.max(1, (max - min) + 1)) + min));
        return item;
    }

    int getRarity() {
        return rarity;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }
}