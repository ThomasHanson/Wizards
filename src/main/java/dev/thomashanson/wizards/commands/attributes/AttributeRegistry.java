package dev.thomashanson.wizards.commands.attributes;

import java.util.Map;

import dev.thomashanson.wizards.game.Wizard;

public class AttributeRegistry {

    private final Map<String, Wizard.Attribute<?>> attributes;

    public AttributeRegistry() {
        attributes = Map.ofEntries(
            // Now we create specific types, like Attribute<Integer> and Attribute<Float>
            Map.entry("max_wands", new Wizard.Attribute<>(
                Integer.class,
                Wizard::getMaxWands,
                Wizard::setMaxWands // No cast needed!
            )),
            Map.entry("mana", new Wizard.Attribute<>(
                Float.class,
                Wizard::getMana,
                Wizard::setMana // No parsing needed!
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
     * @param wizard The wizard to get the attribute from.
     * @param attribute The name of the attribute.
     * @return The attribute's value, or null if not found.
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
                // This cast is now safe because we just checked the type
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
     * Gets all registered attribute names.
     * @return An array of attribute names.
     */
    public String[] getAttributeNames() {
        return attributes.keySet().toArray(new String[0]);
    }
}