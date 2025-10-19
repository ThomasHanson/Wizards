package dev.thomashanson.wizards.game.spell;

/**
 * An immutable data carrier for calculating spell stat values.
 *
 * @param spellLevel The level of the spell being calculated.
 * @param distance   The distance relevant to the calculation (e.g., travel distance of a projectile).
 */
public record StatContext(int spellLevel, double distance) {

    /**
     * Creates a context with only a spell level.
     */
    public static StatContext of(int spellLevel) {
        return new StatContext(spellLevel, 0);
    }

    /**
     * Creates a context with a spell level and a distance.
     */
    public static StatContext of(int spellLevel, double distance) {
        return new StatContext(spellLevel, distance);
    }
}