package dev.thomashanson.wizards.commands.attributes;

import java.util.Map;

import dev.thomashanson.wizards.game.Wizard;

/**
 * A registry that maps simple string names to type-safe {@link Wizard.Attribute} objects.
 * This allows for getting and setting attributes on a {@link Wizard} using
 * command-line arguments, with type-checking and parsing handled automatically.
 *
 * @see Wizard.Attribute
 * @see AttributesCommand
 */
public class AttributeRegistry {

    private final Map<String, Wizard.Attribute<?>> attributes;

    /**
     * Constructs a new AttributeRegistry and initializes the mapping
     * of all available wizard attributes.
     */
    public AttributeRegistry() {
        attributes = Map.ofEntries(
            Map.entry("max_wands", new Wizard.Attribute<>(
                Integer.class,
                Wizard::getMaxWands,
                Wizard::setMaxWands
            )),
            Map.entry("mana", new Wizard.Attribute<>(
                Float.class,
                Wizard::getMana,
                Wizard::setMana
            )),
            Map.entry("max_mana", new Wizard.Attribute<>(
                Float.class,
                Wizard::getMaxMana,
                Wizard::setMaxMana
            )),
            Map.entry("mana_per_sec", new Wizard.Attribute<>(
                Float.class,
                wizard -> wizard.getManaPerTick() * 20F,
                Wizard::setManaPerSecond
            )),
            // Note: The setters below might need to be overloaded in your Wizard class
            // to accept only a float to work as a direct method reference.
            Map.entry("mana_cost_multiplier", new Wizard.Attribute<>(
                Float.class,
                Wizard::getManaMultiplier,
                (wizard, value) -> wizard.setManaMultiplier(value, false)
            )),
            Map.entry("cooldown_multiplier", new Wizard.Attribute<>(
                Float.class,
                Wizard::getCooldownMultiplier,
                (wizard, value) -> wizard.setCooldownMultiplier(value, false)
            ))
        );
    }

    /**
     * Gets the value of a specific attribute for a wizard.
     *
     * @param wizard    The wizard to get the attribute from.
     * @param attribute The case-insensitive name of the attribute.
     * @return The attribute's value, or null if the attribute name is not found.
     */
    public Object getAttribute(Wizard wizard, String attribute) {
        Wizard.Attribute<?> attr = attributes.get(attribute.toLowerCase());
        return attr != null ? attr.get(wizard) : null;
    }

    /**
     * Sets the value of a specific attribute for a wizard.
     * @param wizard The wizard to modify.
     * @param attribute The name of the attribute.
     * @param value The new value to set.
     */
    public <T> void setAttribute(Wizard wizard, String attributeName, T value) {
        Wizard.Attribute<?> rawAttr = attributes.get(attributeName.toLowerCase());

        if (rawAttr != null) {
            // Check if the provided value is the correct type for the attribute
            if (rawAttr.getType().isInstance(value)) {
                @SuppressWarnings("unchecked")
                Wizard.Attribute<T> typedAttr = (Wizard.Attribute<T>) rawAttr;
                typedAttr.set(wizard, value);
                
            } else {
                // Handle the error gracefully, e.g., log a warning
                System.err.println(
                    "Type mismatch for attribute '" + attributeName + "'. " +
                    "Expected " + rawAttr.getType().getSimpleName() + 
                    " but got " + value.getClass().getSimpleName() + "."
                );
            }
        }
    }

    /**
     * Retrieves the raw {@link Wizard.Attribute} object for a given wizard and attribute name.
     *
     * @param wizard        The wizard (used for context, though currently unused in implementation).
     * @param attributeName The case-insensitive name of the attribute.
     * @return The {@link Wizard.Attribute} object, or null if not found.
     */
    public Wizard.Attribute<?> getAttributeObject(Wizard wizard, String attributeName) {
        return attributes.get(attributeName.toLowerCase());
    }

    /**
     * Gets all registered attribute names.
     *
     * @return A string array of all attribute keys.
     */
    public String[] getAttributeNames() {
        return attributes.keySet().toArray(new String[0]);
    }
}