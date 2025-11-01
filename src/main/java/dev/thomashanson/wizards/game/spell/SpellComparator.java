package dev.thomashanson.wizards.game.spell;

import java.util.Comparator;

/**
 * Provides various {@link Comparator} implementations for sorting {@link Spell} objects
 * based on different criteria like rarity, name, or stats.
 */
public enum SpellComparator implements Comparator<Spell> {
    
    /**
     * Sorts spells by rarity first (descending), then alphabetically by name.
     */
    RARITY {
        @Override
        public int compare(Spell s1, Spell s2) {
            int rarityCompare = s2.getRarity().compareTo(s1.getRarity());
            return rarityCompare == 0 ? s1.getName().compareToIgnoreCase(s2.getName()) : rarityCompare;
        }
    },

    /**
     * Sorts spells alphabetically by name.
     */
    NAME {
        @Override
        public int compare(Spell s1, Spell s2) {
            return s1.getName().compareToIgnoreCase(s2.getName());
        }
    },

    /**
     * Sorts spells by their mana cost at level 1 (descending).
     */
    MANA {
        @Override
        public int compare(Spell s1, Spell s2) {
            // Compare mana cost at level 1 as a baseline
            return Double.compare(s2.getManaCost(1), s1.getManaCost(1));
        }
    },

    /**
     * Sorts spells by their cooldown at level 1 (descending).
     */
    COOLDOWN {
        @Override
        public int compare(Spell s1, Spell s2) {
            // Compare cooldown at level 1 as a baseline
            return Double.compare(s2.getCooldown(1), s1.getCooldown(1));
        }
    }
}