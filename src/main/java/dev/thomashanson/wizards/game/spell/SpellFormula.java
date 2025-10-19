package dev.thomashanson.wizards.game.spell;

import java.util.function.BiFunction;

import org.bukkit.configuration.ConfigurationSection;

@FunctionalInterface
public interface SpellFormula extends BiFunction<StatContext, ConfigurationSection, Double> {
}