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
 */
public final class FormulaRegistry {

    private FormulaRegistry() {} // Prevent instantiation

    // --- Formula Name Constants for use in spells.yml ---
    public static final String STATIC = "STATIC";
    public static final String LEVEL_SCALING = "LEVEL_SCALING";
    public static final String DISTANCE_SCALING = "DISTANCE_SCALING"; // The new generic formula

    private static final Map<String, SpellFormula> FORMULAS;
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
                (context.spellLevel() * config.getDouble("multiplier", 1.0)) + config.getDouble("base", 0.0)
        );

        // The new, highly configurable distance and level scaling formula.
        // This completely replaces the hard-coded SPECTRAL_ARROW_DAMAGE.
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
                // If the denominator is invalid, you can define a max value or a different fallback formula
                return config.getDouble("maxValue", 20.0);
            }

            double distanceComponent = (context.distance() * distanceMultiplier) / denominator;

            return base + distanceComponent + bonus;
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
