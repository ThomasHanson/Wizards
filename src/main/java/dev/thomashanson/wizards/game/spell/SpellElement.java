package dev.thomashanson.wizards.game.spell;

import org.bukkit.Material;

import net.kyori.adventure.text.format.NamedTextColor;

public enum SpellElement {

    ATTACK(
            "wizards.gui.spellbook.element.attack.name",
            "wizards.gui.spellbook.element.attack.desc",
            Material.IRON_SWORD,
            1, 0, 2, // Header in slot 1, spans columns 0-2
            NamedTextColor.RED
    ),

    UTILITY(
            "wizards.gui.spellbook.element.utility.name",
            "wizards.gui.spellbook.element.utility.desc",
            Material.FEATHER,
            4, 4, 5, // Header in slot 4, spans columns 4-5
            NamedTextColor.AQUA
    ),

    TACTICAL(
            "wizards.gui.spellbook.element.tactical.name",
            "wizards.gui.spellbook.element.tactical.desc",
            Material.ENDER_EYE,
            7, 7, 8, // Header in slot 7, spans columns 7-8
            NamedTextColor.DARK_PURPLE
    );

    private final String nameKey;
    private final String descriptionKey;
    private final Material icon;
    private final int slot;
    private final int firstSlot;
    private final int secondSlot;
    private final NamedTextColor color;

    SpellElement(
            String nameKey, String descriptionKey, Material icon,
            int slot, int firstSlot, int secondSlot,
            NamedTextColor color) {
        this.nameKey = nameKey;
        this.descriptionKey = descriptionKey;
        this.icon = icon;
        this.slot = slot;
        this.firstSlot = firstSlot;
        this.secondSlot = secondSlot;
        this.color = color;
    }

    public String getNameKey() { return nameKey; }
    public String getDescriptionKey() { return descriptionKey; }
    public Material getIcon() { return icon; }
    public int getSlot() { return slot; }
    public int getFirstSlot() { return firstSlot; }
    public int getSecondSlot() { return secondSlot; }
    public NamedTextColor getColor() { return color; }
}