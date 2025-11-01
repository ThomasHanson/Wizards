package dev.thomashanson.wizards.game.kit;

import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.spell.Spell;

/**
 * Represents the abstract base for all player kits.
 * <p>
 * This class stores all data-driven properties for a kit, loaded from the
 * database (e.g., name, description, cost, base stats).
 * <p>
 * Subclasses must implement abstract methods to define the kit's unique,
 * code-driven behaviors, such as applying special modifiers ({@link #applyModifiers}),
 * granting initial spells ({@link #applyInitialSpells}), or handling
 * game events by implementing {@link Listener} methods.
 *
 * @see KitManager
 * @see KitSelectMenu
 */
public abstract class WizardsKit implements Listener {

    // --- Fields loaded from the database ---
    private final int id;
    private final String key;
    private final String nameKey;
    private final String descriptionKey;
    private final float baseMaxMana;
    private final float maxManaPerLevel;
    private final int baseWands;
    private final int baseMaxWands;
    private final int maxWandsPerLevel;
    private final float baseManaRegen;
    private final float manaRegenPerLevel;
    private final UnlockType unlockType;
    private final int unlockCost;
    private Material icon;

    // --- Abstract methods for unique, coded behaviors ---
    public abstract void playSpellEffect(Player player, Location location);
    public abstract void playIntro(Player player);
    public abstract void applyModifiers(Wizard wizard, int kitLevel);

    /**
     * Allows kits to grant specific starting spells. THIS REMAINS.
     */
    public abstract void applyInitialSpells(Wizard wizard);

    /**
     * Modifies the max level for a given spell. THIS REMAINS.
     */
    public abstract int getModifiedMaxSpellLevel(Spell spell, int currentDefaultMaxLevel);

    public WizardsKit(Map<String, Object> data) {
        this.id = (int) data.get("kit_id");
        this.key = (String) data.get("kit_key");
        this.nameKey = (String) data.get("name_key");
        this.descriptionKey = (String) data.get("description_key");
        this.baseMaxMana = (float) data.get("base_max_mana");
        this.maxManaPerLevel = (float) data.get("max_mana_per_level");
        this.baseWands = (int) data.get("base_wands");
        this.baseMaxWands = (int) data.get("base_max_wands");
        this.maxWandsPerLevel = (int) data.get("max_wands_per_level");
        this.baseManaRegen = (float) data.get("base_mana_regen");
        this.manaRegenPerLevel = (float) data.get("mana_regen_per_level");
        this.unlockType = UnlockType.valueOf((String) data.get("unlock_type"));
        this.unlockCost = (int) data.get("unlock_cost");
        try {
            this.icon = Material.valueOf((String) data.get("icon_material"));
        } catch (IllegalArgumentException | NullPointerException e) {
            this.icon = Material.BOOK;
        }
    }

    /**
     * Gets a formatted list of strings describing the benefits of a specific level.
     * Each subclass will implement this to describe its unique upgrades.
     *
     * @param level The kit level (1-5) to describe.
     * @return A list of strings for the GUI lore.
     */
    public abstract List<String> getLevelDescription(int level);

    public float getInitialMaxMana(int kitLevel) {
        return baseMaxMana + (maxManaPerLevel * (Math.max(0, kitLevel - 1)));
    }

    public int getInitialWands() {
        return baseWands;
    }

    public int getInitialMaxWands(int kitLevel) {
        return baseMaxWands + (maxWandsPerLevel * (Math.max(0, kitLevel - 1)));
    }

    public float getBaseManaPerTick(int kitLevel) {
        return baseManaRegen + (manaRegenPerLevel * (Math.max(0, kitLevel - 1)));
    }

    // --- Getters for the stored data ---
    public int getId() { return id; }
    public String getKey() { return key; }
    public String getNameKey() { return nameKey; }
    public String getDescriptionKey() { return descriptionKey; }
    public UnlockType getUnlockType() { return unlockType; }
    public int getUnlockCost() { return unlockCost; }
    public Material getIcon() { return icon; }

    // Helper enum to match the database
    public enum UnlockType {
        DEFAULT, COINS, ACHIEVEMENTS
    }
}