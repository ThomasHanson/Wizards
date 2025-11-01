package dev.thomashanson.wizards.game;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import com.google.common.base.Function;

import dev.thomashanson.wizards.game.manager.WizardManager;
import dev.thomashanson.wizards.game.overtime.types.DisasterLightning;
import dev.thomashanson.wizards.game.potion.PotionType;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.SpellManager;
import dev.thomashanson.wizards.game.spell.types.SpellSpite;
import dev.thomashanson.wizards.util.MathUtil;

/**
 * Represents a player who is an active participant in a Wizards game.
 * <p>
 * This class acts as a data-holder and state machine for a player, storing all
 * game-specific information such as mana, cooldowns, known spells, wand assignments,
 * and active potion effects. It is created when a player enters a game and
 * destroyed when they are eliminated or the game ends.
 * <p>
 * It provides the core API for interacting with a player's in-game status,
 * separate from their persistent Bukkit {@link Player} entity.
 *
 * @see WizardManager
 * @see Wizards
 */
public class Wizard {

    // --- Attribute System for Commands ---
    public static class Attribute<T> {
        private final Class<T> type;
        private final Function<Wizard, T> getter;
        private final BiConsumer<Wizard, T> setter;

        public Attribute(Class<T> type, Function<Wizard, T> getter, BiConsumer<Wizard, T> setter) {
            this.type = type;
            this.getter = getter;
            this.setter = setter;
        }

        public T get(Wizard wizard) { return getter.apply(wizard); }
        public void set(Wizard wizard, T value) { setter.accept(wizard, value); }
        public Class<T> getType() { return type; }
    }
    
    /**
     * Defines the visual state of a spell on a wand, used to determine
     * the lore, item amount, and Bukkit cooldown graphic.
     */
    public enum DisplayType {
        /** Spell is ready to be cast. */
        AVAILABLE,
        /** Spell is blocked by the Spite spell. */
        DISABLED_BY_SPITE,
        /** Spell cannot be cast due to insufficient mana. */
        NOT_ENOUGH_MANA,
        /** Spell is on its normal cooldown. */
        SPELL_COOLDOWN
    }

    private Wizards game;
    private final SpellManager spellManager;

    private final Set<Integer> unlockedKitIds = new HashSet<>();

    private final UUID uniqueId;
    private int maxWands;
    private float mana, maxMana;
    private float manaPerTick = 2.5F / 20F;
    private int wandsOwned, soulStars;

    private final Map<String, Instant> cooldowns = new HashMap<>();
    private final Map<String, Integer> knownSpells = new HashMap<>();
    private String[] assignedWands;

    private float baseManaMultiplier = 1, baseCooldownMultiplier = 1;
    private float baseManaRegenMultiplier = 1;
    private float tempManaMultiplier = 1, tempCooldownMultiplier = 1;
    private float tempManaRegenMultiplier = 1;

    private BossBar manaBar, potionStatusBar;
    private PotionType activePotion;
    private int hitSpells, missedSpells;
    private final Set<Location> chestsLooted = new HashSet<>();

    private String disabledSpellBySpite;
    private Instant disabledSpellSpiteUsableTime;

    private boolean spellBookOpen = false;

    public Wizard(Wizards game, UUID uniqueId, int maxWands, float maxMana) {
        this.game = game;
        this.spellManager = game.getPlugin().getSpellManager();
        this.uniqueId = uniqueId;
        this.maxMana = maxMana;
        setMaxWands(maxWands);
    }
    
    /**
     * Cleans up all Bukkit-specific resources tied to this Wizard.
     * This is called by {@link WizardManager#reset()} when the game ends
     * to prevent BossBar memory leaks.
     */
    public void cleanup() {
        if (manaBar != null) manaBar.removeAll();
        if (potionStatusBar != null) potionStatusBar.removeAll();
    }

    public void revert() {
        tempManaMultiplier = 1;
        tempCooldownMultiplier = 1;
        tempManaRegenMultiplier = 1;
    }

    public int getLevel(String spellKey) {
        return knownSpells.getOrDefault(spellKey.toUpperCase(), 0);
    }

    public float getManaCost(Spell spell) {
        int level = getLevel(spell.getKey());
        float cost = spell.getManaCost(level); // The base mana cost from the Spell object
        cost *= getManaMultiplier(); // Apply any general mana multipliers the wizard has

        // Get the single instance of the Spite spell from the SpellManager
        Spell spiteSpell = spellManager.getSpell("SPITE");

        // Check if the loaded spell is actually the SpellSpite class
        if (spiteSpell instanceof SpellSpite) {
            // Cast it and call the new public method to get the mana penalty for this specific player
            cost += ((SpellSpite) spiteSpell).getManaCostModifier(getPlayer());
        }

        return Math.max(0, cost);
    }

    public void setCooldown(String spellKey, Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            cooldowns.remove(spellKey.toUpperCase());
        } else {
            cooldowns.put(spellKey.toUpperCase(), Instant.now().plus(duration));
        }
    }

    public Instant getCooldown(String spellKey) {
        Instant expiry = cooldowns.get(spellKey.toUpperCase());
        return (expiry != null && expiry.isAfter(Instant.now())) ? expiry : Instant.now();
    }

    public double getSpellCooldown(Spell spell) {
        int level = getLevel(spell.getKey());
        double cooldownTicks = spell.getCooldown(level);
        cooldownTicks *= getCooldownMultiplier();

        if (game.isOvertime() && game.getDisaster() instanceof DisasterLightning) {
            if (spell.getKey().equalsIgnoreCase("FIREBALL")) {
                cooldownTicks /= 2;
            }
        }
        return Math.max(0, cooldownTicks / 20.0);
    }

    public float getManaPerTick() {
        float base = this.manaPerTick + ((soulStars >= 1 ? 0.20F + (soulStars - 1) * 0.15F : 0F)) / 20F;
        return base * getManaRegenMultiplier();
    }

    public void addMana(float manaAmount) {
        this.mana = Math.min(mana + manaAmount, this.maxMana);
    }

    public void removeMana(float manaAmount) {
        this.mana = Math.max(mana - manaAmount, 0F);
    }

    public void learnSpell(Spell spell) {
        String key = spell.getKey().toUpperCase();
        knownSpells.put(key, getLevel(key) + 1);
    }

    public Spell getSpell(int slot) {
        if (slot < 0 || assignedWands == null || slot >= assignedWands.length) return null;
        String spellKey = assignedWands[slot];
        return (spellKey != null) ? spellManager.getSpell(spellKey) : null;
    }

    public void setSpell(int slot, String spellKey) {
        if (slot < 0 || assignedWands == null || slot >= assignedWands.length) return;
        assignedWands[slot] = (spellKey != null) ? spellKey.toUpperCase() : null;
    }

    public void addAccuracy(boolean hit) {
        if (hit) {
            hitSpells++;
            Player player = getPlayer();
            if (player != null) player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1F, 1.25F);
        } else {
            missedSpells++;
        }
    }

    public double getSpellAccuracy() {
        if (hitSpells == 0) return 0.0;
        if (missedSpells == 0) return 100.0;
        return (double) hitSpells / (hitSpells + missedSpells) * 100.0;
    }

    public Set<String> getKnownSpellKeys() {
        return new HashSet<>(knownSpells.keySet());
    }

    public void addUnlockedKit(int kitId) {
        unlockedKitIds.add(kitId);
    }

    public boolean hasKit(int kitId) {
        return kitId == 1 || unlockedKitIds.contains(kitId);
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(uniqueId);
    }

    public void setMaxWands(int newMaxWands) {
        String[] newWandsArray = new String[newMaxWands];
        if (this.assignedWands != null) {
            System.arraycopy(this.assignedWands, 0, newWandsArray, 0, Math.min(this.assignedWands.length, newMaxWands));
        }
        this.assignedWands = newWandsArray;
        this.maxWands = newMaxWands;
    }

    public String getDisabledSpellBySpite() {
        if (disabledSpellBySpite != null && disabledSpellSpiteUsableTime != null && Instant.now().isAfter(disabledSpellSpiteUsableTime)) {
            disabledSpellBySpite = null;
            disabledSpellSpiteUsableTime = null;
        }
        return disabledSpellBySpite;
    }

    public void setDisabledSpellBySpite(String spellKey, Instant usableTime) {
        this.disabledSpellBySpite = (spellKey != null) ? spellKey.toUpperCase() : null;
        this.disabledSpellSpiteUsableTime = usableTime;
    }

    public Instant getDisabledSpellSpiteUsableTime() {
        return this.disabledSpellSpiteUsableTime;
    }

    public boolean isSpellDisabledBySpite(String spellKey) {
        String disabledKey = getDisabledSpellBySpite();
        return disabledKey != null && disabledKey.equalsIgnoreCase(spellKey);
    }
    
    public String getManaBarTitle() {
        return String.format("§b%.0f/%.0f Mana §7| §a%.1f MPS", Math.max(0, mana), maxMana, getManaPerTick() * 20);
    }

    public String getPotionBarTitle() {
        if (activePotion != null) {
            Duration potionDuration = game.getWizardManager().getPotionDuration(getPlayer(), activePotion);
            if (potionDuration != null) {
                return String.format("%s §7| §e%s", activePotion.getPotionName(), MathUtil.formatTime(potionDuration.toMillis()));
            }
        }
        return "";
    }

    // --- Simple Getters/Setters ---
    public Wizards getGame() { return game; }
    public void setGame(Wizards game) { this.game = game; }
    public UUID getUniqueId() { return uniqueId; }
    public int getMaxWands() { return maxWands; }
    public float getMana() { return mana; }
    public void setMana(float mana) { this.mana = mana; }
    public float getMaxMana() { return maxMana; }
    public void setMaxMana(float maxMana) { this.maxMana = maxMana; }
    public int getWandsOwned() { return wandsOwned; }
    public void setWandsOwned(int wandsOwned) { this.wandsOwned = wandsOwned; }
    public String getSpellKey(int slot) {
        if (slot < 0 || assignedWands == null || slot >= assignedWands.length) return null;
        return assignedWands[slot];
    }
    public float getManaMultiplier() { return baseManaMultiplier * tempManaMultiplier; }
    public void setManaPerSecond(float manaPerSecond) { this.manaPerTick = manaPerSecond / 20F; }
    public void setManaMultiplier(float manaMultiplier, boolean temporary) { if (temporary) tempManaMultiplier = manaMultiplier; else baseManaMultiplier = manaMultiplier; }
    private float getManaRegenMultiplier() { return baseManaRegenMultiplier * tempManaRegenMultiplier; }
    public void setManaRegenMultiplier(float manaRegenMultiplier, boolean temporary) { if (temporary) tempManaRegenMultiplier = manaRegenMultiplier; else baseManaRegenMultiplier = manaRegenMultiplier; }
    public float getCooldownMultiplier() { return baseCooldownMultiplier * tempCooldownMultiplier; }
    public void setCooldownMultiplier(float cooldownMultiplier, boolean temporary) { if (temporary) tempCooldownMultiplier = cooldownMultiplier; else baseCooldownMultiplier = cooldownMultiplier; }
    public BossBar getManaBar() { return manaBar; }
    public void setManaBar(BossBar manaBar) { this.manaBar = manaBar; }
    public BossBar getPotionStatusBar() { return potionStatusBar; }
    public void setPotionStatusBar(BossBar potionStatusBar) { this.potionStatusBar = potionStatusBar; }
    public PotionType getActivePotion() { return activePotion; }
    public void setActivePotion(PotionType activePotion) { this.activePotion = activePotion; }
    public Set<Location> getChestsLooted() { return chestsLooted; }
    public boolean hasSpellBookOpen() { return spellBookOpen; }
    public void setSpellBookOpen(boolean spellBookOpen) { this.spellBookOpen = spellBookOpen; }
    public void addSoulStar() { this.soulStars++; }
}

