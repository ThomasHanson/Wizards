package dev.thomashanson.wizards.game.potion;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.potion.types.PotionFrozen;
import dev.thomashanson.wizards.game.potion.types.PotionGambler;
import dev.thomashanson.wizards.game.potion.types.PotionIron;
import dev.thomashanson.wizards.game.potion.types.PotionLuck;
import dev.thomashanson.wizards.game.potion.types.PotionMana;
import dev.thomashanson.wizards.game.potion.types.PotionRegeneration;
import dev.thomashanson.wizards.game.potion.types.PotionRusher;
import dev.thomashanson.wizards.game.potion.types.PotionSight;
import dev.thomashanson.wizards.game.potion.types.PotionVolatile;
import dev.thomashanson.wizards.game.potion.types.PotionWisdom;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.ChatColor;

public enum PotionType {

    FROZEN (
            "Frozen Potion",
            Color.fromRGB(210, 226, 241),
            PotionFrozen.class,
            "All spells have a 25% chance of inflicting Slowness IV for 2 seconds.",
            "This potion does not stack."
    ),

    GAMBLER (
            "Gambler's Potion",
            Color.fromRGB(228, 154, 58),
            PotionGambler.class,
            "Every spell with either:",
            "  - Cost no mana OR 2x mana",
            "  - Deal no damage OR 2x damage"
    ),

    IRON (
            "Iron Potion",
            Color.fromRGB(127, 131, 146),
            PotionIron.class,
            "You take 33% less damage. Speed is halved."
    ),

    LUCK (
            "Potion of Luck",
            Color.fromRGB(51, 153, 0),
            PotionLuck.class,
            "Spells have a 20% chance to do double their bonus.",
            "There is also a 3% chance for collected spells to do nothing at all."
    ),

    MANA (
            "Mana Potion", 0,
            Color.fromRGB(83, 137, 132),
            PotionMana.class,
            "You immediately receive an additional 100 mana."
    ),

    REGENERATION (
            "Regeneration Potion", 7,
            Color.fromRGB(205, 92, 171),
            PotionRegeneration.class,
            "You’ll rapidly gain health at the rate of one heart a second.",
            "During this time, you’ll take 2x damage."
    ),

    RUSHER (
            "Rusher's Potion",
            Color.fromRGB(192, 164, 77),
            PotionRusher.class,
            "Cooldowns decreased by 20%.",
            "Costs increased by 25%."
    ),

    SIGHT (
            "Wizard's Sight",
            Color.fromRGB(31, 31, 161),
            PotionSight.class,
            "Your foes have glowing outlines."
    ),

    VOLATILE (
            "Volatile Potion",
            Color.fromRGB(255, 69, 0),
            PotionVolatile.class,
            "All offensive spells explode, dealing 25% extra damage.",
            "You explode when hit, making you take 33% extra damage."
    ),

    WISDOM (
            "Potion of Wisdom",
            Color.fromRGB(118, 158, 229),
            PotionWisdom.class,
            "Mana Regeneration 2.5x faster than normal.",
            "Spell cooldowns increased by 15-20%."
    );

    private final String potionName;
    private final Duration duration;
    private final Color color;
    private final Class<? extends Potion> potionClass;
    private final String[] description;

    /**
     * Creates a new potion type, with duration of 30 seconds.
     * @param potionName The name of the potion.
     * @param color The potion color visible in-game.
     * @param potionClass The potion class (implements Listener).
     * @param description The lore description.
     * @see Potion
     */

    PotionType(String potionName, Color color, Class<? extends Potion> potionClass, String... description) {
        this (potionName, 30, color, potionClass, description);
    }

    /**
     * Creates a new potion type.
     * @param potionName The name of the potion.
     * @param seconds The length of the spell (in seconds).
     * @param color The potion color visible in-game.
     * @param potionClass The potion class (implements Listener).
     * @param description The lore description.
     * @see Potion
     */

    PotionType(String potionName, int seconds, Color color, Class<? extends Potion> potionClass, String... description) {
        this.potionName = potionName;
        this.duration = Duration.ofSeconds(seconds);
        this.color = color;
        this.potionClass = potionClass;
        this.description = description;
    }

    public ItemStack createPotion() {

        List<Component> potionLore = new ArrayList<>();

        potionLore.add(Component.text(""));
        Arrays.stream(description).forEach(line -> potionLore.add(Component.text(ChatColor.GRAY + line)));
        
        ItemStack potionItem  = ItemBuilder
                .from(Material.POTION)
                .name(Component.text(ChatColor.WHITE + potionName + (!duration.isZero() ? (" (" + duration.toSeconds() + " seconds" + ")") : "")))
                .lore(potionLore)
                .build();

        ItemMeta itemMeta = potionItem .getItemMeta();

        if (itemMeta != null) {
                itemMeta.getPersistentDataContainer().set(Wizards.POTION_ID_KEY, PersistentDataType.STRING, this.name());
                
                PotionMeta potionMeta = (PotionMeta) itemMeta;
                potionMeta.setColor(color);
                
                potionItem.setItemMeta(potionMeta);
        }

        return potionItem;
    }

    public String getPotionName() {
        return potionName;
    }

    public Duration getDuration() {
        return duration;
    }

    public Class<? extends Potion> getPotionClass() {
        return potionClass;
    }
}