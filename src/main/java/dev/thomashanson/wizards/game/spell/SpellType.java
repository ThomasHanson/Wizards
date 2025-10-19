package dev.thomashanson.wizards.game.spell;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.Map;

import org.bukkit.Material;

import com.google.common.collect.ImmutableMap;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.thomashanson.wizards.game.spell.types.SpellBoulderToss;
import dev.thomashanson.wizards.game.spell.types.SpellDoppelganger;
import dev.thomashanson.wizards.game.spell.types.SpellDroom; // TriumphGUI's ItemBuilder
import dev.thomashanson.wizards.game.spell.types.SpellFireball;
import dev.thomashanson.wizards.game.spell.types.SpellFlash;
import dev.thomashanson.wizards.game.spell.types.SpellFocus;
import dev.thomashanson.wizards.game.spell.types.SpellFrostBarrier;
import dev.thomashanson.wizards.game.spell.types.SpellFrostbite;
import dev.thomashanson.wizards.game.spell.types.SpellGrapplingBeam;
import dev.thomashanson.wizards.game.spell.types.SpellGust;
import dev.thomashanson.wizards.game.spell.types.SpellHeal;
import dev.thomashanson.wizards.game.spell.types.SpellHyperDash;
import dev.thomashanson.wizards.game.spell.types.SpellIcePrison;
import dev.thomashanson.wizards.game.spell.types.SpellIceShards;
import dev.thomashanson.wizards.game.spell.types.SpellImplode;
import dev.thomashanson.wizards.game.spell.types.SpellLightShield;
import dev.thomashanson.wizards.game.spell.types.SpellLightningStrike;
import dev.thomashanson.wizards.game.spell.types.SpellManaBolt;
import dev.thomashanson.wizards.game.spell.types.SpellManaBomb;
import dev.thomashanson.wizards.game.spell.types.SpellNapalm;
import dev.thomashanson.wizards.game.spell.types.SpellRainbowBeam;
import dev.thomashanson.wizards.game.spell.types.SpellRainbowRoad;
import dev.thomashanson.wizards.game.spell.types.SpellRumble;
import dev.thomashanson.wizards.game.spell.types.SpellScarletStrikes;
import dev.thomashanson.wizards.game.spell.types.SpellSoulExchange;
import dev.thomashanson.wizards.game.spell.types.SpellSpectralArrow;
import dev.thomashanson.wizards.game.spell.types.SpellSpeedBoost;
import dev.thomashanson.wizards.game.spell.types.SpellSpite;
import dev.thomashanson.wizards.game.spell.types.SpellSplash;
import dev.thomashanson.wizards.game.spell.types.SpellSummonWolves;
import dev.thomashanson.wizards.game.spell.types.SpellTrapRune;
import dev.thomashanson.wizards.game.spell.types.SpellWizardsCompass;

public enum SpellType {

    // --- Enum Values (Populated with default English names and descriptions) ---
    BOULDER_TOSS (
            "wizards.spell.boulder_toss", "Boulder Toss",
            5, 20, 0, 35, 0, Material.GRANITE, SpellRarity.MEDIUM,
            SpellElement.ATTACK, WandElement.EARTH, SpellBoulderToss.class,
            ImmutableMap.of("Damage", 2, "Boulders", "SL + 1"),
            "wizards.spell.boulder_toss.description", "Unleash a boulder that crushes enemies."
    ),
    DOPPELGANGER (
            "wizards.spell.doppelganger", "Doppelganger",
            1, 15, 0, 50, 0, Material.ARMOR_STAND, SpellRarity.RARE,
            SpellElement.ATTACK, WandElement.LIFE, SpellDoppelganger.class,
            ImmutableMap.of("Clone Health", 6, "Length (seconds)", 12),
            "wizards.spell.doppelganger.description", "Summon a clone to aid you in battle."
    ),
    DROOM (
            "wizards.spell.droom", "Droom",
            3, 40, -3, 15, -4, Material.ANVIL, SpellRarity.UNCOMMON,
            SpellElement.ATTACK, WandElement.EARTH, SpellDroom.class,
            ImmutableMap.of("Damage", "(SL * 2) + 3", "Radius", "4 + (SL * 2)"),
            "wizards.spell.droom.description", "Cast a powerful spell that deals damage."
    ),
    FIREBALL (
            "wizards.spell.fireball", "Fireball",
            3, 30, -3, 15, -1, Material.FIRE_CHARGE, SpellRarity.UNCOMMON,
            SpellElement.ATTACK, WandElement.FIRE, SpellFireball.class,
            ImmutableMap.of("Damage", "SL + 3", "Fire Ticks", 80),
            "wizards.spell.fireball.description", "Launch a fiery projectile at your enemies."
    ),
    FLASH (
            "wizards.spell.flash", "Flash",
            3, 20, 0, 50, -5, Material.ENDER_PEARL, SpellRarity.MED_RARE,
            SpellElement.ATTACK, WandElement.LIFE, SpellFlash.class,
            ImmutableMap.of("Range", "(SL * 10) + 20", "Fall Immunity (seconds)", "SL"),
            "wizards.spell.flash.description", "Quickly dash to evade attacks."
    ),
    FOCUS (
            "wizards.spell.focus", "Focus",
            1, 30, 0, 60, 0, Material.CARROT_ON_A_STICK, SpellRarity.MEDIUM,
            SpellElement.ATTACK, WandElement.LIFE, SpellFocus.class,
            Collections.emptyMap(),
            "wizards.spell.focus.description", "Concentrate to enhance your spellcasting."
    ),
    FROST_BARRIER (
            "wizards.spell.frost_barrier", "Frost Barrier",
            3, 10, 0, 20, -5, Material.PACKED_ICE, SpellRarity.MED_RARE,
            SpellElement.ATTACK, WandElement.ICE, SpellFrostBarrier.class,
            ImmutableMap.of("Height", "SL + 1", "Width", "(SL * 2) + 4"),
            "wizards.spell.frost_barrier.description", "Create a barrier of ice for protection."
    ),
    FROSTBITE (
            "wizards.spell.frostbite", "Frostbite",
            3, 20, 0, 40, 0, Material.SNOWBALL, SpellRarity.MED_RARE,
            SpellElement.ATTACK, WandElement.ICE, SpellFrostbite.class,
            ImmutableMap.of("Radius", "SL * 2", "Length (seconds)", 15, "Slowness Level", 2, "Effect Length (seconds)", 15),
            "wizards.spell.frostbite.description", "Inflict frost damage and slow enemies."
    ),
    GUST (
            "wizards.spell.gust", "Gust",
            3, 15, 0, 20, 0, Material.QUARTZ, SpellRarity.MEDIUM,
            SpellElement.ATTACK, WandElement.MANA, SpellGust.class,
            ImmutableMap.of("Gust Size (blocks)", "(SL * 3) + 10", "Gust Strength", "SL * 30%"),
            "wizards.spell.gust.description", "Generate a strong gust of wind."
    ),
    GRAPPLING_BEAM (
            "wizards.spell.grappling_beam", "Grappling Beam",
            3, 25, 0, 20, 0, Material.FISHING_ROD, SpellRarity.UNCOMMON,
            SpellElement.ATTACK, WandElement.MANA, SpellGrapplingBeam.class,
            ImmutableMap.of("Damage", "SL + 1", "Range", "(SL * 5) + 5", "Slam Damage", 3),
            "wizards.spell.grappling_beam.description", "Use a beam to grapple onto surfaces."
    ),
    HEAL (
            "wizards.spell.heal", "Heal",
            5, 20, 0, 90, -1, Material.APPLE, SpellRarity.MEDIUM,
            SpellElement.ATTACK, WandElement.LIFE, SpellHeal.class,
            ImmutableMap.of("Regeneration Level", 1, "Regeneration Length (seconds)", "SL + 5"),
            "wizards.spell.heal.description", "Heal yourself or allies."
    ),
    HYPER_DASH (
            "wizards.spell.hyper_dash", "Hyper Dash",
            3, 10, -1, 20, -1, Material.GLOWSTONE_DUST, SpellRarity.MED_RARE,
            SpellElement.ATTACK, WandElement.FIRE, SpellHyperDash.class,
            ImmutableMap.of("Stat", 1),
            "wizards.spell.hyper_dash.description", "Perform a rapid dash, potentially dealing damage."
    ),
    ICE_PRISON (
            "wizards.spell.ice_prison", "Ice Prison",
            3, 25, 2, 20, 0, Material.ICE, SpellRarity.MED_RARE,
            SpellElement.ATTACK, WandElement.ICE, SpellIcePrison.class,
            ImmutableMap.of("Prison Radius", "SL + 3"),
            "wizards.spell.ice_prison.description", "Trap your enemies in a prison of ice."
    ),
    ICE_SHARDS (
            "wizards.spell.ice_shards", "Ice Shards",
            3, 30, 0, 20, -2, Material.GHAST_TEAR, SpellRarity.MED_RARE,
            SpellElement.ATTACK, WandElement.ICE, SpellIceShards.class,
            ImmutableMap.of("Damage (each)", 1, "Shards", "SL + 1"),
            "wizards.spell.ice_shards.description", "Launch shards of ice at foes."
    ),
    IMPLODE (
            "wizards.spell.implode", "Implode",
            3, 50, -2, 30, -3, Material.IRON_SHOVEL, SpellRarity.MED_RARE,
            SpellElement.ATTACK, WandElement.EARTH, SpellImplode.class,
            ImmutableMap.of("Range", 50, "Implosion Height", "SL", "Implosion Width", "SL * 2"),
            "wizards.spell.implode.description", "Cause an implosion that damages surrounding enemies."
    ),
    LIGHT_SHIELD (
            "wizards.spell.light_shield", "Light Shield",
            3, 30, 0, 45, 0, Material.GOLD_INGOT, SpellRarity.MED_RARE,
            SpellElement.ATTACK, WandElement.MANA, SpellLightShield.class,
            ImmutableMap.of("Durability (health)", 6, "Length (secs)", "SL * 3", "Height", 4, "Width", "SL + 2"),
            "wizards.spell.light_shield.description", "Create a shield of light for defense."
    ),
    LIGHTNING_STRIKE (
            "wizards.spell.lightning_strike", "Lightning Strike",
            3, 50, 0, 20, 0, Material.GOLDEN_AXE, SpellRarity.UNCOMMON,
            SpellElement.ATTACK, WandElement.MANA, SpellLightningStrike.class,
            ImmutableMap.of("Damage", "(SL * 2) + 1"),
            "wizards.spell.lightning_strike.description", "Strike foes with a bolt of lightning."
    ),
    MANA_BOLT (
            "wizards.spell.mana_bolt", "Mana Bolt",
            3, 15, -2, 5, 0, Material.CYAN_DYE, SpellRarity.COMMON,
            SpellElement.ATTACK, WandElement.MANA, SpellManaBolt.class,
            ImmutableMap.of("Damage", "SL + 2", "Range", "(SL * 10) + 20"),
            "wizards.spell.mana_bolt.description", "Launch a bolt of pure mana at your enemies."
    ),
    MANA_BOMB (
            "wizards.spell.mana_bomb", "Mana Bomb",
            5, 10, 10, 5, 5, Material.PRISMARINE_SHARD, SpellRarity.UNCOMMON,
            SpellElement.ATTACK, WandElement.MANA, SpellManaBomb.class,
            ImmutableMap.of("Damage", "SL + 3", "Range", 12),
            "wizards.spell.mana_bomb.description", "Lob a tightly-packed ball of mana in an arc! Explodes on contact, dealing damage to anyone nearby. The higher this spell is leveled, the more mana you can fit in for a larger explosion!"
    ),
    NAPALM (
            "wizards.spell.napalm", "Napalm",
            5, 60, 5, 60, -10, Material.BLAZE_POWDER, SpellRarity.RARE,
            SpellElement.ATTACK, WandElement.FIRE, SpellNapalm.class,
            ImmutableMap.of("Length", "(SL * 10) + 5", "Speed", "6 BPS", "Explosion Size", "SL * 2"),
            "wizards.spell.napalm.description", "Creates a ball of fire that grows the longer it lives. At a large size, it even burns away nearby blocks!"
    ),
    RAINBOW_BEAM (
            "wizards.spell.rainbow_beam", "Rainbow Beam",
            5, 5, 3, 8, 1, Material.EMERALD, SpellRarity.UNCOMMON,
            SpellElement.ATTACK, WandElement.MANA, SpellRainbowBeam.class,
            ImmutableMap.of("Damage", "SL + 1", "Range", 80),
            "wizards.spell.rainbow_beam.description", "Firing rainbow beams of love and hope! This spell damages the target instantly! Damage decreases by 0.2 per block after 30 blocks."
    ),
    RAINBOW_ROAD (
            "wizards.spell.rainbow_road", "Rainbow Road",
            3, 30, 0, 15, -1, Material.LIME_STAINED_GLASS, SpellRarity.MED_RARE,
            SpellElement.ATTACK, WandElement.EARTH, SpellRainbowRoad.class,
            ImmutableMap.of("Length", "SL * 10"),
            "wizards.spell.rainbow_road.description", "Summon into being a mighty road of rainbows for thee to walk on! No fall damage when landing on your own road."
    ),
    RUMBLE (
            "wizards.spell.rumble", "Rumble",
            3, 30, 0, 15, -1, Material.PISTON, SpellRarity.UNCOMMON,
            SpellElement.ATTACK, WandElement.EARTH, SpellRumble.class,
            ImmutableMap.of("Damage", "SL + 2", "Range", "SL * 10", "Explosion Damage", "SL / 4", "Slowness Level", 2),
            "wizards.spell.rumble.description", "Create a targeted earthquake in the direction you face! Explodes with damage at the end! Affected players lose their footing!"
    ),
    SCARLET_STRIKES (
            "wizards.spell.scarlet_strikes", "Scarlet Strikes",
            3, 35, 0, 30, 0, Material.REDSTONE, SpellRarity.MEDIUM,
            SpellElement.ATTACK, WandElement.FIRE, SpellScarletStrikes.class,
            ImmutableMap.of("Damage", 0.75, "Max Strikes", "SL + 2", "Stun Time (secs, each)", 0.5, "Movement Speed", "12 - Strikes", "Range", "(SL + Strikes) * 10"),
            "wizards.spell.scarlet_strikes.description", "Summon and charge strikes in front of you. Left-click again at any time to send them all forward, stunning and damaging your opponents!"
    ),
    SOUL_EXCHANGE (
            "wizards.spell.soul_exchange", "Soul Exchange",
            1, 0, 0, 60, 0, Material.END_CRYSTAL, SpellRarity.RARE,
            SpellElement.ATTACK, WandElement.LIFE, SpellSoulExchange.class,
            ImmutableMap.of("Instant Mana", 30, "Extra Max Mana", 15),
            "wizards.spell.soul_exchange.description", "Sacrifice one heart permanently in exchange for max mana, along with a little bonus. Beware, sacrificing your life force often has severe side effects!"
    ),
    SPECTRAL_ARROW (
            "wizards.spell.spectral_arrow", "Spectral Arrow",
            3, 40, -5, 15, -2, Material.ARROW, SpellRarity.MED_RARE,
            SpellElement.ATTACK, WandElement.DARK, SpellSpectralArrow.class,
            ImmutableMap.of("Damage", "6 + (D / (7 - SL)) + 3"),
            "wizards.spell.spectral_arrow.description", "Shoot an arrow that penetrates! Further the distance, higher the damage!"
    ),
    SPEED_BOOST (
            "wizards.spell.speed_boost", "Speed Boost",
            2, 20, 0, 45, 0, Material.SUGAR, SpellRarity.MED_RARE,
            SpellElement.ATTACK, WandElement.LIFE, SpellSpeedBoost.class,
            ImmutableMap.of("wizards.stat.speed_level", "SL + 1", "wizards.stat.effect_length", "20"),
            "wizards.desc.speed_boost", "Gain a speed potion effect to outrun your enemies."
    ),
    SPLASH (
            "wizards.spell.splash", "Splash",
            3, 20, 0, 15, 0, Material.WATER_BUCKET, SpellRarity.COMMON,
            SpellElement.ATTACK, WandElement.ICE, SpellSplash.class,
            ImmutableMap.of("wizards.stat.damage", "SL", "wizards.stat.range", "5", "wizards.stat.knockback", "3 + SL", "wizards.stat.effect_length", "SL * 5"),
            "wizards.desc.splash", "Splash a large wave of water around you, knocking back anyone within range! You get soaked in the process, extinguishing fire."
    ),
    SPITE (
            "wizards.spell.spite", "Spite",
            1, 15, 0, 50, 0, Material.CHARCOAL, SpellRarity.MED_RARE,
            SpellElement.ATTACK, WandElement.DARK, SpellSpite.class,
            Collections.emptyMap(),
            "wizards.desc.spite", "A dark aura surrounds the users of this spell momentarily after casting. If hit with a spell during this time, the foe who hit you will be unable to use that spell for one minute. In addition, spells they use will cost extra mana. They can only have one disabled spell at a time."
    ),
    SUMMON_WOLVES (
            "wizards.spell.summon_wolves", "Summon Wolves",
            3, 80, -10, 160, 0, Material.BONE, SpellRarity.MEDIUM,
            SpellElement.ATTACK, WandElement.LIFE, SpellSummonWolves.class,
            ImmutableMap.of("wizards.stat.wolves", "SL + 2", "wizards.stat.wolves_speed_level", "SL"),
            "wizards.desc.summon_wolves", "Summons a pack of wolves and assigns you as the leader. They will fight for you and after 30 seconds, will disappear."
    ),
    TRAP_RUNE (
            "wizards.spell.trap_rune", "Trap Rune",
            3, 25, 0, 30, -1, Material.TNT_MINECART, SpellRarity.MED_RARE,
            SpellElement.ATTACK, WandElement.DARK, SpellTrapRune.class,
            ImmutableMap.of("wizards.stat.damage", "(SL * 2) + 3", "wizards.stat.range", "(SL * 4) + 12", "wizards.stat.rune_size", "SL"),
            "wizards.desc.trap_rune", "Draws an explosion rune on the ground! The rune takes 5 seconds to prepare and also damages you!"
    ),
    WIZARDS_COMPASS (
            "wizards.spell.wizards_compass", "Wizard's Compass",
            1, 5, 0, 4, 0, Material.COMPASS, SpellRarity.NONE,
            SpellElement.ATTACK, WandElement.LIFE, SpellWizardsCompass.class,
            Collections.emptyMap(),
            "wizards.desc.wizards_compass", "Displays particles pointing to the closest enemy!"
    );

    private static final DecimalFormat COOLDOWN_FORMAT = new DecimalFormat("0.#");

    // --- Constructor ---
    private final String spellNameKey;
    private final String defaultSpellName;
    private final int maxLevel;
    private float mana;
    private float manaChange;
    private int cooldown;
    private int cooldownChange;
    private final Material iconMaterial;
    private int slot;
    private final SpellRarity rarity;
    private final SpellElement spellElement;
    private final WandElement wandElement;
    private final Class<? extends Spell> spellClass;
    private final Map<String, Object> stats;
    private final String descriptionKey;
    private final String defaultDescription;

    SpellType(String spellNameKey, String defaultSpellName, int maxLevel, float mana, float manaChange, int cooldown, int cooldownChange, Material iconMaterial, SpellRarity rarity, SpellElement spellElement, WandElement wandElement, Class<? extends Spell> spellClass, Map<String, Object> stats, String descriptionKey, String defaultDescription) {
        this.spellNameKey = spellNameKey;
        this.defaultSpellName = defaultSpellName;
        this.maxLevel = maxLevel;
        this.mana = mana;
        this.manaChange = manaChange;
        this.cooldown = cooldown;
        this.cooldownChange = cooldownChange;
        this.iconMaterial = iconMaterial;
        this.rarity = rarity;
        this.spellElement = spellElement;
        this.wandElement = wandElement;
        this.spellClass = spellClass;
        this.stats = stats;
        this.descriptionKey = descriptionKey;
        this.defaultDescription = defaultDescription;
    }

    // --- Getters (some restored, some adjusted) ---
    public String getSpellName() { // Returns the default English name
        return defaultSpellName;
    }

    public String getSpellNameKey() {
        return spellNameKey;
    }

    public String getDescription() { // Returns the default English description
        return defaultDescription;
    }

    public String getDescriptionKey() {
        return descriptionKey;
    }

    public Material getIcon() { // Restored: returns the Material
        return iconMaterial;
    }

    public LanguageManager getLanguageManager() {
        return WizardsPlugin.getInstance().getLanguageManager();
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public float getMana() {
        return mana;
    }

    public void setMana(float mana) {
        this.mana = mana;
    }

    public float getManaChange() {
        return manaChange;
    }

    public void setManaChange(float manaChange) {
        this.manaChange = manaChange;
    }

    public int getCooldown() {
        return cooldown;
    }

    public void setCooldown(int cooldown) {
        this.cooldown = cooldown;
    }

    public int getCooldownChange() {
        return cooldownChange;
    }

    public void setCooldownChange(int cooldownChange) {
        this.cooldownChange = cooldownChange;
    }

    // getIconMaterial() can be kept if used, or removed if getIcon() is sufficient
    public Material getIconMaterial() {
        return iconMaterial;
    }

    public SpellRarity getRarity() {
        return rarity;
    }

    public SpellElement getSpellElement() {
        return spellElement;
    }

    public WandElement getWandElement() {
        return wandElement;
    }

    public Class<? extends Spell> getSpellClass() {
        return spellClass;
    }

    public Map<String, Object> getStats() {
        return stats;
    }

    // --- Static Lookup Method (Restored) ---
    /**
     * Gets a spell by its translation key or default English name.
     * Tries matching by key first, then by default name (case-insensitive).
     * @param nameOrKey The spell name (English) or translation key.
     * @return The SpellType, or null if not found.
     */
    public static SpellType getSpell(String nameOrKey) {
        for (SpellType spell : values()) {
            if (spell.getSpellNameKey().equalsIgnoreCase(nameOrKey)) {
                return spell;
            }
        }
        // Fallback to checking default English name
        for (SpellType spell : values()) {
            if (spell.getSpellName().equalsIgnoreCase(nameOrKey)) {
                return spell;
            }
        }
        return null;
    }
}