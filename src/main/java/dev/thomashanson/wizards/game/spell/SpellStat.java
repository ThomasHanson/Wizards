package dev.thomashanson.wizards.game.spell;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Represents a single configurable stat for a spell (e.g., "damage", "range").
 * This class holds the {@link SpellFormula} used to calculate the stat's value
 * and the display format for rendering it in item lore.
 *
 * @see Spell
 * @see FormulaRegistry
 */
public class SpellStat {

    private final String displayFormat;
    private final SpellFormula formula;
    private final ConfigurationSection config;

    /**
     * Creates a new SpellStat by parsing its configuration section from {@code spells.yml}.
     *
     * @param config The {@link ConfigurationSection} for this specific stat.
     */
    public SpellStat(ConfigurationSection config) {
        // Defaulting to "%.0f" will display the number as a whole integer.
        // Use "%.1f" for one decimal place, etc.
        this.displayFormat = config.getString("display", "%.0f");
        this.formula = FormulaRegistry.get(config.getString("formula", "STATIC"));
        this.config = config;
    }

    /**
     * Calculates the final value of this stat based on the provided context.
     *
     * @param context The {@link StatContext} (spell level, distance) to use.
     * @return The calculated value.
     */
    public double calculate(StatContext context) {
        return formula.apply(context, config);
    }

    /**
     * @return The {@link String#format(String, Object...)} string used to display this stat.
     */
    public String getDisplayFormat() {
        return displayFormat;
    }
}