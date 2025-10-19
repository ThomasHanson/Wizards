package dev.thomashanson.wizards.game.spell;

import org.bukkit.configuration.ConfigurationSection;

public class SpellStat {

    private final String displayFormat;
    private final SpellFormula formula;
    private final ConfigurationSection config;

    public SpellStat(ConfigurationSection config) {
        // Defaulting to "%.0f" will display the number as a whole integer.
        // Use "%.1f" for one decimal place, etc.
        this.displayFormat = config.getString("display", "%.0f");
        this.formula = FormulaRegistry.get(config.getString("formula", "STATIC"));
        this.config = config;
    }

    public double calculate(StatContext context) {
        return formula.apply(context, config);
    }

    public String getDisplayFormat() {
        return displayFormat;
    }
}