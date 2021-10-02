package dev.thomashanson.wizards.game.spell;

public enum SpellRarity {

    /**
     * Represents the most common spell that
     * can be found in chests. If a wizard
     * finds a duplicate spell, they will
     * gain an additional 20 mana.
     */
    COMMON (15, 20F),

    /**
     * Represents an uncommon spell that can
     * be found in chests. If a wizard finds
     * a duplicate spell, they will gain an
     * additional 25 mana.
     */
    UNCOMMON (10, 25F),

    /**
     * Represents a medium rarity spell that can
     * be found in chests. If a wizard finds
     * a duplicate spell, they will gain an
     * additional 30 mana.
     */
    MEDIUM (5, 30F),

    /**
     * Represents a medium rare spell that can
     * be found in chests. If a wizard finds
     * a duplicate spell, they will gain an
     * additional 40 mana.
     */
    MED_RARE (3, 40F),

    /**
     * Represents the rarest spell that can
     * be found in chests. If a wizard finds
     * a duplicate spell, they will gain an
     * additional 50 mana.
     */
    RARE (1, 50F),

    /**
     * Represents a spell that will not
     * spawn in any chest. Currently used
     * for Wizard's Compass since all players
     * unlock it and cannot level it up further.
     */
    NONE (0, 0);

    /**
     * The frequency in chests.
     */
    private final int lootAmount;

    /**
     * The amount of mana gain (if duplicate).
     */
    private final float manaGain;

    SpellRarity(int lootAmount, float manaGain) {
        this.lootAmount = lootAmount;
        this.manaGain = manaGain;
    }

    public int getLootAmount() {
        return lootAmount;
    }

    public float getManaGain() {
        return manaGain;
    }
}