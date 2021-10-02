package dev.thomashanson.wizards.game.spell;

import com.google.common.collect.ImmutableMap;
import dev.thomashanson.wizards.game.spell.types.*;
import dev.thomashanson.wizards.util.menu.ItemBuilder;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public enum SpellType {

    BOULDER_TOSS (

            "Boulder Toss", 5,
            20, 0, // TODO: 2020-05-07 set mana/cooldown change
            35, 0,
            Material.GRANITE,
            SpellRarity.MEDIUM,
            SpellElement.ATTACK, WandElement.EARTH,
            SpellBoulderToss.class,

            ImmutableMap.of (
                    "Damage", 2,
                    "Boulders", "SL + 1"
            ),

            "Raise mighty rocks around you to shield",
            "yourself! Then cast them out where you",
            "face with an astonishing crash!"
    ),

    DOPPELGANGER (

            "Doppelganger", 1,
            15, 0,
            50, 0,
            Material.ARMOR_STAND,
            SpellRarity.RARE,
            SpellElement.ENVIRONMENTAL, WandElement.LIFE,
            SpellDoppelganger.class,

            ImmutableMap.of (
                    "Clone Health", 6,
                    "Length (seconds)", 12
            ),

            "Throw out a realistic to distract your enemies,",
            "turning invisible in the process."
    ),

    DROOM (

            "Droom", 3,
            40, -3,
            15, -4,
            Material.ANVIL,
            SpellRarity.UNCOMMON,
            SpellElement.ATTACK, WandElement.EARTH,
            SpellDroom.class,

            ImmutableMap.of (
                    "Damage", "(SL * 2) + 3",
                    "Radius", "4 + (SL * 2)"
            ),

            "Summons exploding anvils over everyone",
            "near you! This also includes the caster!"
    ),

    FIREBALL (

            "Fireball", 3,
            30, -3,
            15, -2,
            Material.FIRE_CHARGE,
            SpellRarity.UNCOMMON,
            SpellElement.ATTACK, WandElement.FIRE,
            SpellFireball.class,

            ImmutableMap.of (
                    "Damage", "SL + 3",
                    "Fire Ticks", 80
            ),

            "Be an object of fear and awe!",
            "Summon a blazing fireball!"
    ),

    FLASH (

            "Flash", 3,
            20, 0,
            50, -5,
            Material.ENDER_PEARL,
            SpellRarity.MED_RARE,
            SpellElement.SUPPORT, WandElement.LIFE,
            SpellFlash.class,

            ImmutableMap.of (
                    "Range", "(SL * 10) + 20",
                    "Fall Immunity (seconds)", "SL"
            ),

            "Teleport to the block you are looking at!",
            "Disables fall damage for SL seconds."
    ),

    FOCUS (

            "Focus", 1,
            30, 0, // TODO: 2020-05-07 set mana/cooldown change
            60, 0,
            Material.CARROT_ON_A_STICK,
            SpellRarity.MEDIUM,
            SpellElement.SUPPORT, WandElement.LIFE,
            SpellFocus.class,

            Collections.emptyMap(),

            "Enter a tight focus, which will increase your mana",
            "regeneration and charge up your next attack.",
            "You are incapable of fast movement during this time.",
            "Any attacks you endure will shatter your focus,",
            "stunning you momentarily."
    ),

    FROST_BARRIER (

            "Frost Barrier", 3,
            10, 0,
            20, -5,
            Material.PACKED_ICE,
            SpellRarity.MED_RARE,
            SpellElement.ENVIRONMENTAL, WandElement.ICE,
            SpellFrostBarrier.class,

            ImmutableMap.of (
                    "Height", "SL + 1",
                    "Width", "(SL * 2) + 4"
            ),

            "Create a wall of packed ice!"
    ),

    FROSTBITE (

            "Frostbite", 3,
            20, 0,
            40, 0, // TODO: 2020-05-20 mana/cooldown change
            Material.SNOWBALL,
            SpellRarity.MED_RARE,
            SpellElement.ENVIRONMENTAL, WandElement.ICE,
            SpellFrostbite.class,

            ImmutableMap.of (
                    "Radius", "SL * 2",
                    "Length (seconds)", 15,
                    "Slowness Level", 2,
                    "Effect Length (seconds)", 15
            ),

            "Cast out a freezing aura. Wherever",
            "it so lands will be cursed to be",
            "frozen, spreading the corruption to",
            "any unfortunate victims within the area."
    ),

    GUST (

            "Gust", 3,
            15, 0,
            20, 0,
            Material.QUARTZ,
            SpellRarity.MEDIUM,
            SpellElement.ENVIRONMENTAL, WandElement.MANA,
            SpellGust.class,

            ImmutableMap.of (
                    "Gust Size (blocks)", "(SL * 3) + 10",
                    "Gust Strength", "SL * 30%"
            ),

            "Cast the spell and watch your enemies fly!",
            "Spell strength decreases with distance."
    ),

    GRAPPLING_BEAM (

            "Grappling Beam", 3,
            25, 0, // TODO: 2020-06-04 mana/cooldown change
            20, 0,
            Material.FISHING_ROD,
            SpellRarity.UNCOMMON,
            SpellElement.ENVIRONMENTAL, WandElement.MANA,
            SpellGrapplingBeam.class,

            ImmutableMap.of (
                    "Damage", "SL + 1",
                    "Range", "(SL * 5) + 5",
                    "Slam Damage", 3
            ),

            "Cast out a versatile hook! Reel in",
            "cowards who attempt to flee thy wrath,",
            "or send yourself lying forwards to where",
            "the hook lands!"
    ),

    HEAL (

            "Heal", 5,
            20, 0,
            90, -1,
            Material.EXPERIENCE_BOTTLE,
            SpellRarity.MEDIUM,
            SpellElement.SUPPORT, WandElement.LIFE,
            SpellHeal.class,

            ImmutableMap.of (
                    "Regeneration Level", 1,
                    "Regeneration Length (seconds)", "SL + 5"
            ),

            "Low on health and need to retreat?",
            "Use this! Heal yourself up!"
    ),

    ICE_PRISON (

            "Ice Prison", 3,
            25, 2,
            20, 0,
            Material.ICE,
            SpellRarity.MED_RARE,
            SpellElement.ENVIRONMENTAL, WandElement.ICE,
            SpellIcePrison.class,

            ImmutableMap.of (
                    "Prison Radius", "SL + 3"
            ),

            "On impact creates a mighty ice prison",
            "to capture thy enemies!"
    ),

    ICE_SHARDS (

            "Ice Shards", 3,
            30, 0,
            20, -2,
            Material.GHAST_TEAR,
            SpellRarity.MED_RARE,
            SpellElement.ATTACK, WandElement.ICE,
            SpellIceShards.class,

            ImmutableMap.of (
                    "Damage (each)", 1,
                    "Shards", "SL + 1"
            ),

            "Overwhelm your opponent with shards!",
            "Each shard is fired half a second after",
            "the last, allowing you to pummel your",
            "enemies senseless!"
    ),

    IMPLODE (

            "Implode", 3,
            50, -2,
            30, -3,
            Material.IRON_SHOVEL,
            SpellRarity.MED_RARE,
            SpellElement.ENVIRONMENTAL, WandElement.EARTH,
            SpellImplode.class,

            ImmutableMap.of (
                    "Range", 50,
                    "Implosion Height", "SL",
                    "Implosion Width", "SL * 2"
            ),

            "Gathers the blocks at target location",
            "and scatters them about the area!"
    ),

    LIGHT_SHIELD (

            "Light Shield", 3,
            30, 0,
            45, 0, // TODO: 2020-05-20 mana/cooldown change
            Material.GOLD_INGOT,
            SpellRarity.MED_RARE,
            SpellElement.SUPPORT, WandElement.MANA,
            SpellLightShield.class,

            ImmutableMap.of (
                    "Durability (health)", 6,
                    "Length (secs)", "SL * 3",
                    "Height", 4,
                    "Width", "SL + 2"
            ),

            "Defend yourself with a temporary shield that",
            "reflects most attacks! To keep track of its",
            "health, check your hearts!"
    ),

    LIGHTNING_STRIKE (

            "Lightning Strike", 3,
            50, 0,
            20, 0,
            Material.GOLDEN_AXE,
            SpellRarity.UNCOMMON,
            SpellElement.ATTACK, WandElement.MANA,
            SpellLightningStrike.class,

            // TODO: 2020-05-18 balancing

            ImmutableMap.of (
                    "Damage", "(SL * 2) + 1"
            ),

            "Summon a mighty lightning strike to hit",
            "the target you point out! The lightning",
            "strike also contains fire!"
    ),

    MANA_BOLT (

            "Mana Bolt", 3,
            15, -2,
            5, 0,
            Material.CYAN_DYE,
            SpellRarity.COMMON,
            SpellElement.ATTACK, WandElement.MANA,
            SpellManaBolt.class,

            ImmutableMap.of (
                    "Damage", "SL + 2",
                    "Range", "(SL * 10) + 20"
            ),

            "Basic spell all beginner mages are taught.",
            "This creates a missile of mana commonly",
            "attributed towards the magic profession and",
            "homes in towards the closest target!"
    ),

    MANA_BOMB (

            "Mana Bomb", 5,
            10, 10,
            5, 5,
            Material.BARRIER,
            SpellRarity.UNCOMMON,
            SpellElement.ATTACK, WandElement.MANA,
            SpellManaBomb.class,

            ImmutableMap.of (
                    "Damage", "SL + 3",
                    "Range", 12
            ),

            "Lob a tightly-packed ball of mana in an arc!",
            "Explodes on contact, dealing damage to anyone",
            "nearby. The higher this spell is leveled, the",
            "more mana you can fit in for a larger explosion!"
    ),

    NAPALM (

            "Napalm", 5,
            60, 5,
            60, -10,
            Material.BLAZE_POWDER,
            SpellRarity.RARE,
            SpellElement.ATTACK, WandElement.FIRE,
            SpellNapalm.class,

            ImmutableMap.of (
                    "Length", "(SL * 10) + 5",
                    "Speed", "6 BPS",
                    "Explosion Size", "SL * 2"
            ),

            "Creates a ball of fire that grows the longer",
            "it lives. At a large size, it even burns away",
            "nearby blocks!"
    ),

    RAINBOW_BEAM (

            "Rainbow Beam", 5,
            5, 3,
            8, 1,
            Material.EMERALD,
            SpellRarity.UNCOMMON,
            SpellElement.ATTACK, WandElement.MANA,
            SpellRainbowBeam.class,

            ImmutableMap.of (
                    "Damage", "SL + 1",
                    "Range", 80
            ),

            "Firing rainbow beams of love and hope!",
            "This spell damages the target instantly!",
            "Damage decreases by 0.2 per block after",
            "30 blocks."
    ),

    RAINBOW_ROAD (

            "Rainbow Road", 3,
            30, 0,
            15, -1,
            Material.LIME_STAINED_GLASS,
            SpellRarity.MED_RARE,
            SpellElement.ENVIRONMENTAL, WandElement.EARTH,
            SpellRainbowRoad.class,

            ImmutableMap.of (
                    "Length", "SL * 10"
            ),

            "Summon into being a mighty road of",
            "rainbows for thee to walk on! No fall",
            "damage when landing on your own road."
    ),

    RUMBLE (

            "Rumble", 3,
            30, 0,
            15, -1,
            Material.PODZOL,
            SpellRarity.UNCOMMON,
            SpellElement.ATTACK, WandElement.EARTH,
            SpellRumble.class,

            ImmutableMap.of (
                    "Damage", "SL + 2",
                    "Range", "SL * 10",
                    "Explosion Damage", "SL / 4",
                    "Slowness Level", 2
            ),

            "Create a targeted earthquake in the",
            "direction you face! Explodes with",
            "damage at the end! Affected players",
            "lose their footing!"
    ),

    SCARLET_STRIKES (

            "Scarlet Strikes", 3,
            35, 0,
            30, 0, // TODO: 2020-06-03 mana/cooldown change
            Material.REDSTONE,
            SpellRarity.MEDIUM,
            SpellElement.ATTACK, WandElement.FIRE,
            SpellScarletStrikes.class,

            ImmutableMap.of (
                    "Damage", 0.75,
                    "Max Strikes", "SL + 2",
                    "Stun Time (secs, each)", 0.5,
                    "Movement Speed", "12 - Strikes BPS",
                    "Range", "(SL + Strikes) * 10"
            ),

            "Summon and charge strikes in front of",
            "you. Left-click again at any time to",
            "send them all forward, stunning and",
            "damaging your opponents!"
    ),

    SOUL_EXCHANGE (

            "Soul Exchange", 1,
            0, 0,
            60, 0,
            Material.BARRIER,
            SpellRarity.RARE,
            SpellElement.ENVIRONMENTAL, WandElement.LIFE,
            SpellSoulExchange.class,

            ImmutableMap.of (
                    "Extra Mana", 35,
                    "Extra Max Mana", 15
            ),

            "Sacrifice one heart permanently in",
            "exchange for max mana, along with",
            "a little bonus. Beware, sacrificing",
            "your life force often has severe side",
            "effects!"
    ),

    SPECTRAL_ARROW (

            "Spectral Arrow", 3,
            40, -5,
            15, -2,
            Material.ARROW,
            SpellRarity.MED_RARE,
            SpellElement.ATTACK, WandElement.DARK,
            SpellSpectralArrow.class,

            ImmutableMap.of (
                    "Damage", "6 + (D / (7 - SL)) + 3"
            ),

            "Shoot an arrow that penetrates!",
            "Further the distance, higher the damage!"
    ),

    SPEED_BOOST (

            "Speed Boost", 2,
            20, 0,
            45, 0,
            Material.SUGAR,
            SpellRarity.MED_RARE,
            SpellElement.SUPPORT, WandElement.LIFE,
            SpellSpeedBoost.class,

            ImmutableMap.of (
                    "Speed Level", "SL + 1",
                    "Effect Length (secs)", 20
            ),

            "Gain a speed potion effect to outrun your enemies"
    ),

    SPLASH (

            "Splash", 3,
            20, 0,
            15, 0,
            Material.WATER_BUCKET,
            SpellRarity.COMMON,
            SpellElement.ATTACK, WandElement.ICE,
            SpellSplash.class,

            ImmutableMap.of (
                    "Damage", "SL",
                    "Range", 5,
                    "Knockback", "3 + SL",
                    "Effect Length (secs)", "SL * 5"
            ),

            "Splash a large wave of water around you,",
            "knocking back anyone within in range! You",
            "get soaked in the process, extinguishing fire."
    ),

    SPITE (

            "Spite", 1,
            15, 0,
            50, 0,
            Material.BARRIER,
            SpellRarity.MED_RARE,
            SpellElement.ENVIRONMENTAL, WandElement.DARK,
            SpellSpite.class,

            Collections.emptyMap(),

            "A dark aura surrounds the users of this spell",
            "momentarily after casting. If hit with a spell",
            "during this time, the foe who hit you will be",
            "unable to use that spell for one minute. In",
            "addition, spells they use will cost extra mana.",
            "They can only have one disabled spell at a time."
    ),

    SUMMON_WOLVES (

            "Summon Wolves", 3,
            80, -10,
            160, 0,
            Material.BONE,
            SpellRarity.MEDIUM,
            SpellElement.ENVIRONMENTAL, WandElement.LIFE,
            SpellSummonWolves.class,

            ImmutableMap.of (
                    "Wolves", "SL + 2",
                    "Wolves Speed Level", "SL"
            ),

            "Summons a pack of wolves and assigns you as",
            "the leader. They will fight for you and after",
            "30 seconds, will disappear"
    ),

    TORNADO (

            "Tornado", 3,
            30, 0, // TODO: 2020-06-18 mana/cooldown change
            20, 0,
            Material.QUARTZ,
            SpellRarity.MEDIUM,
            SpellElement.ENVIRONMENTAL, WandElement.MANA,
            SpellTornado.class,

            ImmutableMap.of (
                    "Starting Speed", 1,
                    "Max Speed", 10,
                    "Knockback", "2 * SL + 7"
            ),

            "Unleash a tornado that will launch anyone in",
            "its path into the clouds! It starts slow, but",
            "rapidly gains speed throughout its lifetime."
    ),

    TRAP_RUNE (

            "Trap Rune", 3,
            25, 0,
            30, -1,
            Material.TNT,
            SpellRarity.MED_RARE,
            SpellElement.ENVIRONMENTAL, WandElement.DARK,
            SpellTrapRune.class,

            ImmutableMap.of (
                    "Damage", "(SL * 2) + 3",
                    "Range", "(SL * 4) + 12",
                    "Rune Size", "SL"
            ),

            "Draws an explosion rune on the ground! The rune",
            "takes 5 seconds to prepare and also damages you!"
    ),

    WIZARDS_COMPASS (

            "Wizard's Compass", 1,
            5, 0,
            4, 0,
            Material.COMPASS,
            SpellRarity.NONE,
            SpellElement.ENVIRONMENTAL, WandElement.LIFE,
            SpellWizardsCompass.class,

            Collections.emptyMap(),

            "Displays particles pointing to the closest enemy!"
    );

    static {

        List<SpellType> spells = new ArrayList<>(Arrays.asList(values()));
        spells.sort(SpellComparator.RARITY);

        for (SpellType spell : spells) {

            spell.setSlot(9 + spell.getSpellElement().getFirstSlot());

            for (SpellType type : spells) {

                if (
                        spell != type
                                && spell.getSpellElement() == type.getSpellElement()
                                && spell.getSlot() <= type.getSlot()
                ) {

                    spell.setSlot(type.getSlot());
                    int divSlot = spell.getSlot() % 9;

                    if (divSlot >= 8 || divSlot + 1 > spell.getSpellElement().getSecondSlot()) {
                        spell.setSlot((spell.getSlot() - divSlot) + 9 + spell.getSpellElement().getFirstSlot());
                    } else {
                        spell.setSlot(spell.getSlot() + 1);
                    }
                }
            }

            if (spell.getSlot() > 54)
                Bukkit.getLogger().info("Assigning " + spell.name() + " to " + spell.getSlot());
        }
    }

    public static SpellType getSpell(String spellName) {

        for (SpellType spell : values()) {

            String currentName = spell.getSpellName();

            if (spellName.equalsIgnoreCase(currentName) || spellName.startsWith(currentName))
                return spell;
        }

        return null;
    }

    private String spellName;
    private int maxLevel;
    private float mana, manaChange;
    private int cooldown, cooldownChange;
    private Material icon;
    private int slot;
    private SpellRarity rarity;
    private SpellElement spellElement;
    private WandElement wandElement;
    private Class<? extends Spell> spellClass;
    private Map<String, Object> stats;
    private String[] description;

    /**
     * Creates a new spell type.
     * @param spellName         The name of the spell.
     * @param maxLevel          The highest level spell.
     * @param mana              The base amount of mana it will cost to use.
     * @param manaChange        The mana change per level.
     * @param cooldown          The base amount of time until the spell recharges.
     * @param cooldownChange    The time change per spell level.
     * @param icon              The alternate icon (if resource pack disabled).
     * @param rarity            The frequency in chest loot.
     * @param spellElement      The element of the spell.
     * @param wandElement       The element of the wand.
     * @param spellClass        The spell class (implements Listener).
     * @param stats             The stats associated with the spell.
     * @param description       The spell book description.
     * @see SpellRarity
     * @see SpellElement
     * @see WandElement
     * @see Spell
     */

    SpellType(String spellName,
              int maxLevel,
              float mana, float manaChange,
              int cooldown, int cooldownChange,
              Material icon,
              SpellRarity rarity,
              SpellElement spellElement, WandElement wandElement,
              Class<? extends Spell> spellClass,
              Map<String, Object> stats,
              String... description) {

        this.spellName = spellName;
        this.maxLevel = maxLevel;
        this.mana = mana;
        this.manaChange = manaChange;
        this.cooldown = cooldown;
        this.cooldownChange = cooldownChange;
        this.icon = icon;
        this.rarity = rarity;
        this.spellElement = spellElement;
        this.wandElement = wandElement;
        this.spellClass = spellClass;
        this.stats = stats;
        this.description = description;
    }

    private ItemStack createSpell(ItemStack item) {

        ItemBuilder builder = new ItemBuilder(item.getType());

        builder.withName(ChatColor.GOLD + "" + ChatColor.BOLD +
                "Spell: " + spellElement.getColor() + spellName)
                .withCustomModelData(1);

        return builder.get();
    }

    public ItemStack getSpellBook() {

        return createSpell (

                new ItemBuilder(icon)
                        .withCustomModelData(1)
                        .withLore("", ChatColor.AQUA + "Click to level up this spell")
                        .get()
        );
    }

    public String getSpellName() {
        return spellName;
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

    public Material getIcon() {
        return icon;
    }

    public int getSlot() {
        return slot;
    }

    private void setSlot(int slot) {
        this.slot = slot;
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

    public String[] getDescription() {
        return description;
    }
}