package dev.thomashanson.wizards.game.spell;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

/**
 * A registry for standardized, reusable mathematical formulas used to calculate spell stats.
 * <p>
 * This class provides a set of powerful, data-driven formulas that read their parameters
 * directly from a spell's configuration section in {@code spells.yml}. This allows for
 * complex spell balancing and design without changing any Java code.
 * <p>
 * Formulas are retrieved by their constant string key (e.g., {@link #STATIC}, {@link #LEVEL_SCALING}).
 *
 * @see SpellStat
 * @see SpellFormula
 * @see StatContext
 */
public final class FormulaRegistry {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private FormulaRegistry() {}

    // --- Formula Name Constants for use in spells.yml ---

    /**
     * A simple, static value.
     * <p>
     * <b>Formula:</b> {@code value}
     * <p>
     * <b>Config:</b> {@code { value: 5.0 }}
     */
    public static final String STATIC = "STATIC";

    /**
     * A value that scales linearly with the spell's level.
     * <p>
     * <b>Formula:</b> {@code (SpellLevel * multiplier) + base}
     * <p>
     * <b>Config:</b> {@code { base: 5.0, multiplier: 1.5 }}
     */
    public static final String LEVEL_SCALING = "LEVEL_SCALING";

    /**
     * A complex value that scales with both spell level and distance.
     * Used for Spectral Arrow's damage calculation.
     * <p>
     * <b>Formula:</b> {@code base + (distance * distanceMultiplier) / (divisor - (SpellLevel * levelMultiplier))}
     * <p>
     * <b>Config:</b> {@code { base: 6.0, distanceMultiplier: 1.0, divisor: 7.0, levelMultiplier: 1.0, maxLevel: 6 }}
     */
    public static final String DISTANCE_SCALING = "DISTANCE_SCALING";

    /**
     * A value that starts at a base amount and is reduced by a percentage over a specified distance.
     * Used for Rainbow Beam's damage falloff.
     * <p>
     * <b>Formula:</b> {@code baseDamage * (1 - falloffPercent)}
     * <p>
     * <b>Config:</b> {@code { base: 10.0, multiplier: 1.0, falloff-start-distance: 30.0, falloff-end-distance: 80.0, min-damage-hearts: 0.5 }}
     */
    public static final String PERCENT_DISTANCE_SCALING = "PERCENT_DISTANCE_SCALING";

    /**
     * The internal map storing the formula implementations.
     */
    private static final Map<String, SpellFormula> FORMULAS;
    
    /**
     * The default formula to use if a requested formula is not found.
     */
    private static final SpellFormula DEFAULT_FORMULA;

    static {
        final Map<String, SpellFormula> formulas = new HashMap<>();

        // Default formula if none is specified.
        // Formula: value
        // Config: { value: 5.0 }
        DEFAULT_FORMULA = (context, config) -> config.getDouble("value", 0.0);
        formulas.put(STATIC, DEFAULT_FORMULA);

        // A flexible linear scaling formula based on spell level.
        // Formula: (SL * multiplier) + base
        // Config: { base: 5.0, multiplier: 1.5 }
        formulas.put(LEVEL_SCALING, (context, config) ->
                // System calculates level as 1-based, so we adjust for the formula (level - 1)
                config.getDouble("base", 0.0) + (config.getDouble("multiplier", 1.0) * (context.spellLevel() - 1))
        );

        // The configurable distance and level scaling formula.
        // Formula: base + (distance * distanceMultiplier) / (divisor - (SL * levelMultiplier))
        // Config: { base: 6.0, bonus: 3.0, distanceMultiplier: 1.0, divisor: 7.0, levelMultiplier: 1.0, maxLevel: 6 }
        formulas.put(DISTANCE_SCALING, (context, config) -> {
            // Cap the effective level for the calculation if specified
            int maxEffectiveLevel = config.getInt("maxLevel", context.spellLevel());
            int effectiveLevel = Math.min(context.spellLevel(), maxEffectiveLevel);

            double base = config.getDouble("base", 0.0);
            double bonus = config.getDouble("bonus", 0.0); // An extra flat value to add at the end
            double distanceMultiplier = config.getDouble("distanceMultiplier", 1.0);

            double divisor = config.getDouble("divisor", 1.0);
            double levelMultiplier = config.getDouble("levelMultiplier", 1.0);

            // The core calculation for the denominator
            double denominator = divisor - (effectiveLevel * levelMultiplier);

            // Prevent division by zero or negative denominators, which would invert the damage scaling
            if (denominator <= 0) {
                // If the denominator is invalid, use the defined max value.
                return config.getDouble("maxValue", 20.0);
            }

            double distanceComponent = (context.distance() * distanceMultiplier) / denominator;

            return base + distanceComponent + bonus;
        });

        // A formula that calculates a base damage, then reduces it by a percentage
        // over a specified distance range.
        formulas.put(PERCENT_DISTANCE_SCALING, (context, config) -> {
            // 1. Calculate the initial damage before any falloff
            double baseDamage = config.getDouble("base", 0.0) + (config.getDouble("multiplier", 1.0) * (context.spellLevel() - 1));

            // 2. Get the falloff parameters from the config
            double falloffStart = config.getDouble("falloff-start-distance", 0.0);
            double falloffEnd = config.getDouble("falloff-end-distance", 80.0);
            // Convert hearts to health points for internal calculation
            double minDamage = config.getDouble("min-damage-hearts", 0.5) * 2; 

            // 3. If the target is within the no-falloff zone, return full damage
            if (context.distance() <= falloffStart) {
                return baseDamage;
            }

            // 4. Calculate the percentage of damage to remove
            double falloffRange = falloffEnd - falloffStart;
            if (falloffRange <= 0) return baseDamage; // Avoid division by zero

            double distanceIntoFalloff = context.distance() - falloffStart;
            double reductionPercent = Math.max(0, Math.min(1, distanceIntoFalloff / falloffRange));

            // 5. Apply the reduction and ensure it doesn't go below the minimum
            double finalDamage = baseDamage * (1 - reductionPercent);
            return Math.max(minDamage, finalDamage);
        });

        FORMULAS = Collections.unmodifiableMap(formulas);
    }

    /**
     * Retrieves a spell formula by its name.
     *
     * @param name The name of the formula, case-insensitive.
     * @return The corresponding {@link SpellFormula}. Returns the STATIC formula if not found.
     */
    @NotNull
    public static SpellFormula get(@NotNull String name) {
        return FORMULAS.getOrDefault(name.toUpperCase(), DEFAULT_FORMULA);
    }
}