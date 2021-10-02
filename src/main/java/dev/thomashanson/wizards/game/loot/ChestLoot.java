package dev.thomashanson.wizards.game.loot;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ChestLoot {

    private final List<LootItem> lootItems = new ArrayList<>();
    private int totalLoot;

    public ItemStack getLoot() {

        int loot = ThreadLocalRandom.current().nextInt(totalLoot);

        for (LootItem item : lootItems) {

            loot -= item.getRarity();

            if (loot < 0)
                return item.getItemStack();
        }

        return null;
    }

    public void addLoot(ItemStack item, int rarity) {
        addLoot(item, rarity, item.getAmount(), item.getAmount());
    }

    public void addLoot(ItemStack item, int rarity, int minStackSize, int maxStackSize) {
        addLoot(new LootItem(item, rarity, minStackSize, maxStackSize));
    }

    public void addLoot(Material material, int rarity) {
        addLoot(material, rarity, 1, 1);
    }

    public void addLoot(Material material, int rarity, int minStackSize, int maxStackSize) {
        addLoot(new ItemStack(material), rarity, minStackSize, maxStackSize);
    }

    private void addLoot(LootItem item) {
        totalLoot += item.getRarity();
        lootItems.add(item);
    }
}
