package dev.thomashanson.wizards.game.spell;

import java.util.function.BiFunction;

import org.bukkit.configuration.ConfigurationSection;

/**
 * A functional interface that defines a mathematical formula for calculating a spell stat.
 * <p>
 * This allows for flexible, data-driven stat calculation by passing the
 * current {@link StatContext} (level, distance) and the spell's configuration
 * into a lambda or method reference.
 *
 * @see FormulaRegistry
 * @see SpellStat
 */
@FunctionalInterface
public interface SpellFormula extends BiFunction<StatContext, ConfigurationSection, Double> {
}