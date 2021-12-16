package dev.thomashanson.wizards.util.menu;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/** @noinspection UnusedReturnValue*/
public class ItemBuilder implements Supplier<ItemStack> {

    private final List<BiConsumer<ItemStack, ItemMeta>> consumerList;

    private final Material material;

    public ItemBuilder(Material material) {
        this.consumerList = new LinkedList<>();
        this.material = material;
    }

    private ItemBuilder with(BiConsumer<ItemStack, ItemMeta> consumer) {
        consumerList.add(consumer);
        return this;
    }

    private ItemBuilder withIf(BiPredicate<ItemStack, ItemMeta> predicate, BiConsumer<ItemStack, ItemMeta> consumer) {

        return with((stack, meta) -> {
            if (predicate.test(stack, meta)) {
                consumerList.add(consumer);
            }
        });
    }

    private ItemBuilder withIfOrElse(BiPredicate<ItemStack, ItemMeta> predicate, BiConsumer<ItemStack, ItemMeta> ifTrue, BiConsumer<ItemStack, ItemMeta> ifFalse) {

        return with((stack, meta) -> {

            if (predicate.test(stack, meta)) {
                consumerList.add(ifTrue);
                return;
            }

            consumerList.add(ifFalse);
        });
    }

    public ItemBuilder withName(String name) {
        return with((stack, meta) -> meta.setDisplayName(name));
    }

    public ItemBuilder withNameIfOrElse(BiPredicate<ItemStack, ItemMeta> predicate, String name, BiConsumer<ItemStack, ItemMeta> consumer) {
        return withIfOrElse(predicate, (stack, meta) -> meta.setDisplayName(name), consumer);
    }

    public ItemBuilder withLore(String... lore) {
        return withLore(Arrays.asList(lore));
    }

    public ItemBuilder withLore(List<String> lore) {
        return with((stack, meta) -> meta.setLore(lore));
    }

    public ItemBuilder withLoreIf(BiPredicate<ItemStack, ItemMeta> predicate, String... lore) {
        return withIf(predicate, (stack, meta) -> meta.setLore(Arrays.asList(lore)));
    }

    public ItemBuilder withAmount(int amount) {
        return with((stack, meta) -> stack.setAmount(amount));
    }

    public ItemBuilder withCustomModelData(int data) {
        return with((item, meta) -> meta.setCustomModelData(data));
    }

    public ItemBuilder withEnchantment(Enchantment enchantment, int level) {
        return with(((item, meta) -> meta.addEnchant(enchantment, level, false)));
    }

    public ItemBuilder withPotionColor(Color color) {

        return with((item, meta) -> {
            if (meta instanceof PotionMeta)
                ((PotionMeta) meta).setColor(color);
        });
    }

    public ItemBuilder hideAttributes() {
        return with(((item, meta) -> {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
        }));
    }

    @Override
    public ItemStack get() {

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        consumerList.forEach(consumer -> consumer.accept(item, meta));
        item.setItemMeta(meta);

        return item;
    }
}