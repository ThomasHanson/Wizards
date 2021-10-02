package dev.thomashanson.wizards.game.spell;

import java.util.Comparator;

public enum SpellComparator implements Comparator<SpellType> {

    /**
     * Represents a comparator which sorts
     * spells by rarity first, and if they
     * have the same rarity, it will sort
     * alphabetically.
     */
    RARITY {
        public int compare(SpellType o1, SpellType o2) {
            int number = Integer.compare(o2.getRarity().getLootAmount(), o1.getRarity().getLootAmount());
            return number == 0 ? o1.getSpellName().compareToIgnoreCase(o2.getSpellName()) : number;
        }
    },

    /**
     * Represents a comparator which sorts
     * spells alphabetically.
     */
    NAME {
        public int compare(SpellType o1, SpellType o2) {
            return o1.getSpellName().compareToIgnoreCase(o2.getSpellName());
        }
    },

    /**
     * Represents a comparator which sorts
     * spells by mana.
     */
    MANA {
        public int compare(SpellType o1, SpellType o2) {
            return Float.compare(o2.getMana(), o1.getMana());
        }
    },

    /**
     * Represents a comparator which sorts
     * spells by cooldown.
     */
    COOLDOWN {
        public int compare(SpellType o1, SpellType o2) {
            return Integer.compare(o2.getCooldown(), o1.getCooldown());
        }
    }
}