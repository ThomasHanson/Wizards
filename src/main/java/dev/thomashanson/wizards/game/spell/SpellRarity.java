package dev.thomashanson.wizards.game.spell;

/**
 * Defines the rarity of a spell, which controls its loot frequency
 * and the mana reward for collecting a duplicate when already at max level.
 */
public enum SpellRarity {

    /**
     * The most common spells.
     * <b>Loot Frequency:</b> 15
     * <b>Duplicate Mana:</b> 20
     */
    COMMON (15, 20F),

    /**
     * Uncommon spells.
     * <b>Loot Frequency:</b> 10
     * <b>Duplicate Mana:</b> 25
     */
    UNCOMMON (10, 25F),

    /**
     * Spells of medium rarity.
     * <b>Loot Frequency:</b> 5
     * <b>Duplicate Mana:</b> 30
     */
    MEDIUM (5, 30F),

    /**
     * Spells that are less common than average.
     * <b>Loot Frequency:</b> 3
     * <b>Duplicate Mana:</b> 40
     */
    MED_RARE (3, 40F),

    /**
     * The most powerful and hard-to-find spells.
     * <b>Loot Frequency:</b> 1
     * <b>Duplicate Mana:</b> 50
     */
    RARE (1, 50F),

    /**
     * A special rarity for spells that cannot be found in loot chests,
     * such as the default Wizard's Compass.
     * <b>Loot Frequency:</b> 0
     * <b>Duplicate Mana:</b> 0
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