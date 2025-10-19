package dev.thomashanson.wizards.game.potion;

import java.util.Map;
import java.util.Optional;

/**
 * A data-holder class representing a single effect block from the potions.yml config.
 * For example:
 * - type: ATTRIBUTE_MODIFIER
 * attribute: "mana_cost_multiplier"
 * operation: SET
 * value: 0.0
 */
public class PotionEffectConfig {

    /**
     * Represents the valid types of effects that can be defined in potions.yml.
     */
    public enum EffectType {
        ATTRIBUTE_MODIFIER,
        TEMPORARY_ATTRIBUTE_MODIFIER,
        BUKKIT_EFFECT,
        PERIODIC_EFFECT,
        CHANCE,
        ACTION;
    }

    /**
     * Represents the valid operations for the ATTRIBUTE_MODIFIER effect.
     */
    public enum AttributeOperation {
        ADD,
        MULTIPLY,
        SET;
    }

    private final EffectType type;
    private final Map<String, Object> parameters;

    public PotionEffectConfig(Map<String, Object> data) {
        // It's safer to read from the map directly rather than storing it,
        // to prevent potential mutable state issues.
        this.parameters = data;
        this.type = EffectType.valueOf(((String) data.get("type")).toUpperCase());
    }

    public EffectType getType() {
        return type;
    }

    /**
     * Gets a parameter value by its key.
     * @param key The key of the parameter.
     * @return An Optional containing the value, or empty if not present.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key) {
        return Optional.ofNullable((T) parameters.get(key));
    }

    /**
     * Gets a parameter value by its key, or a default value if not present.
     * @param key The key of the parameter.
     * @param defaultValue The value to return if the key is not found.
     * @return The parameter's value or the default value.
     */
    public <T> T get(String key, T defaultValue) {
        return get(key).map(value -> (T) value).orElse(defaultValue);
    }
}
