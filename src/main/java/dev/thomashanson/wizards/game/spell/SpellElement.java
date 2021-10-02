package dev.thomashanson.wizards.game.spell;

import net.md_5.bungee.api.ChatColor;

public enum SpellElement {

    ATTACK (
            "Attack Spells",
            "Spells of destruction",
            1, 0, 2,
            101,
            ChatColor.RED
    ),

    SUPPORT (
            "Support Spells",
            "Spells of assistance",
            4, 4, 4,
            201,
            ChatColor.DARK_GREEN
    ),

    ENVIRONMENTAL (
            "Environmental Spells",
            "Spells that generally affect the world itself.",
            7, 6, 8,
            301,
            ChatColor.BLUE
    );

    private final String name;
    private final String description;
    private final int slot;
    private final int firstSlot;
    private final int secondSlot;
    private final int data;
    private final ChatColor color;

    SpellElement(String name, String description,
                 int slot, int firstSlot, int secondSlot,
                 int data,
                 ChatColor color) {
        this.name = name;
        this.description = description;
        this.slot = slot;
        this.firstSlot = firstSlot;
        this.secondSlot = secondSlot;
        this.data = data;
        this.color = color;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getSlot() {
        return slot;
    }

    public int getFirstSlot() {
        return firstSlot;
    }

    public int getSecondSlot() {
        return secondSlot;
    }

    public int getData() {
        return data;
    }

    public ChatColor getColor() {
        return color;
    }
}