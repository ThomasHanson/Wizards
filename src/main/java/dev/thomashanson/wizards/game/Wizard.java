package dev.thomashanson.wizards.game;

import dev.thomashanson.wizards.game.overtime.types.DisasterLightning;
import dev.thomashanson.wizards.game.potion.PotionType;
import dev.thomashanson.wizards.game.spell.SpellType;
import dev.thomashanson.wizards.game.spell.types.SpellSpite;
import dev.thomashanson.wizards.util.MathUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class Wizard {

    /**
     * Represents which cooldown type will
     * get displayed on the wizard's action
     * bar.
     */
    public enum DisplayType {

        /**
         * Occurs when a player gets spited by
         * another player. This will last up to
         * 60 seconds.
         */
        DISABLED_SPELL,

        /**
         * Occurs when it will take a longer time
         * to generate the mana than the cooldown
         * itself.
         */
        NOT_ENOUGH_MANA,

        /**
         * Occurs when the spell is on any other
         * cooldown.
         */
        SPELL_COOLDOWN
    }

    private Wizards game;

    private Wizard oldWizard;
    private Instant timeCopied;

    private final UUID uniqueId;
    private final int maxWands;
    private float mana, maxMana;
    private float manaPerTick = 2.5F / 20F;
    private int wandsOwned, soulStars;

    private final Map<SpellType, Instant> cooldowns = new HashMap<>();

    /**
     * The cost modifiers.
     */
    private float manaModifier = 1, cooldownModifier = 1;

    /**
     * The regeneration speed modifiers.
     */
    private float manaRate = 1, cooldownRate = 1;

    private BossBar manaBar, potionStatusBar;

    private PotionType activePotion;

    /**
     * Spell accuracy counts.
     */
    private int hitSpells, missedSpells;

    private final Set<Location> chestsLooted = new HashSet<>();

    private final SpellType[] assignedWands;

    private SpellType disabledSpell;
    private Instant disabledUsableTime;

    private final Map<SpellType, Integer> knownSpells = new HashMap<>();

    public Wizard(UUID uniqueId, int maxWands, float maxMana) {
        this.uniqueId = uniqueId;
        this.maxWands = maxWands;
        this.maxMana = maxMana;
        this.assignedWands = new SpellType[maxWands];
    }

    public void revert() {

        if (oldWizard == null)
            return;

        setManaModifier(oldWizard.getManaModifier(), false);
        setCooldownModifier(oldWizard.getCooldownModifier(), false);

        /*
         * Account for power surges
         */
        Duration sinceLastSurge = Duration.between(game.getLastSurge(), Instant.now());

        for (int i = 0; i < Math.ceil((double) sinceLastSurge.toMinutes() / 2); i++)
            decreaseCooldown();

        setManaRate(oldWizard.getManaRate(), false);
        setCooldownRate(oldWizard.getCooldownRate(), false);
    }

    public int getLevel(SpellType spell) {
        return knownSpells.getOrDefault(spell, 0);
    }

    public float getManaCost(SpellType spell) {

        float cost =
                Math.max (0,
                        Math.round (
                                ((spell.getManaChange() * getLevel(spell))
                                + spell.getMana() - spell.getManaChange())
                                * (spell == SpellType.SPEED_BOOST ? 1F : this.manaModifier)
                        )
                );

        if (hasActiveSpite(uniqueId))
            cost += 8;

        return cost;
    }

    public void setUsedSpell(SpellType spell) {

        long cooldown = getSpellCooldown(spell);

        if (cooldown > 0)
            cooldowns.put(spell, Instant.now().plus(Duration.ofSeconds(cooldown)));
    }

    Instant getCooldown(SpellType spell) {

        if (cooldowns.containsKey(spell))
            if (cooldowns.get(spell).isAfter(Instant.now()))
                return cooldowns.get(spell);

        return Instant.now();
    }

    public long getSpellCooldown(SpellType spell) {

        long cooldown = Math.max (0,

                Math.round (

                        ((spell.getCooldownChange() * getLevel(spell)) +
                                spell.getCooldown() - spell.getCooldownChange()) *

                                (spell == SpellType.HEAL ||
                                spell == SpellType.SPEED_BOOST ||
                                spell == SpellType.RAINBOW_ROAD

                                ? 1 : this.cooldownModifier)
                )
        );

        if (getGame().isOvertime() && game.getDisaster() instanceof DisasterLightning)
            if (spell == SpellType.FIREBALL)
                cooldown /= 2;

        return cooldown;
    }

    float getManaPerTick() {

        return (
                manaPerTick + (
                        (soulStars >= 1 ? 0.20F : 0F) +
                                (soulStars >= 1 ? (soulStars - 1) * 0.15F : 0)
                                        * manaRate
                ) / 20F
        );
    }

    public void addMana(float newMana) {
        this.mana = Math.min(mana + newMana, this.maxMana);
    }

    void learnSpell(SpellType spell) {
        knownSpells.put(spell, getLevel(spell) + 1);
    }

    public SpellType getSpell(int slot) {
        return assignedWands[slot];
    }

    public void setSpell(int slot, SpellType spell) {
        assignedWands[slot] = spell;
    }

    public void decreaseCooldown() {
        this.cooldownModifier -= 0.1F;
    }

    void addSoulStar() {
        this.soulStars++;
    }

    public void addAccuracy(boolean hit) {
        addAccuracy(hit, true);
    }

    public void addAccuracy(boolean hit, boolean sound) {

        if (hit) {

            hitSpells++;

            if (sound) {

                Player player = Bukkit.getPlayer(uniqueId);

                if (player != null)
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1F, 1.25F);
            }

        } else {
            missedSpells++;
        }
    }

    public double getSpellAccuracy() {
        return (double) hitSpells / missedSpells;
    }

    public Set<SpellType> getKnownSpells() {
        return knownSpells.keySet();
    }

    String getManaBarTitle() {
        return (int) Math.floor(mana) + "/" + (int) maxMana + " mana" + " ".repeat(20) + (getManaPerTick() * 20) + " MPS";
    }

    String getPotionBarTitle() {

        if (activePotion != null) {

            return activePotion.getPotionName() + " ".repeat(10) +
                    MathUtil.formatTime(game.getPotionDuration(getPlayer(), activePotion).toMillis(), 1);
        }

        return "";
    }

    public Wizards getGame() {
        return game;
    }

    public void setGame(Wizards game) {
        this.game = game;
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public Wizard getOldWizard() {
        return oldWizard;
    }

    public Instant getTimeCopied() {
        return timeCopied;
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(uniqueId);
    }

    int getMaxWands() {
        return maxWands;
    }

    public float getMana() {
        return mana;
    }

    public void setMana(float mana) {
        this.mana = mana;
    }

    public float getMaxMana() {
        return maxMana;
    }

    public void setMaxMana(float maxMana) {
        this.maxMana = maxMana;
    }

    void setManaPerTick(float manaPerTick) {
        this.manaPerTick = manaPerTick;
    }

    public int getWandsOwned() {
        return wandsOwned;
    }

    void setWandsOwned(int wandsOwned) {
        this.wandsOwned = wandsOwned;
    }

    public int getSoulStars() {
        return soulStars;
    }

    public void setSoulStars(int soulStars) {
        this.soulStars = soulStars;
    }

    public float getManaModifier() {
        return manaModifier;
    }

    public void setManaModifier(float manaModifier, boolean temporary) {

        if (temporary) {
            oldWizard = this;
            timeCopied = Instant.now();
        }

        this.manaModifier = manaModifier;
    }

    public float getCooldownModifier() {
        return cooldownModifier;
    }

    public void setCooldownModifier(float cooldownModifier, boolean temporary) {

        if (temporary) {
            oldWizard = this;
            timeCopied = Instant.now();
        }

        this.cooldownModifier = cooldownModifier;
    }

    public float getManaRate() {
        return manaRate;
    }

    public void setManaRate(float manaRate, boolean temporary) {

        if (temporary) {
            oldWizard = this;
            timeCopied = Instant.now();
        }

        this.manaRate = manaRate;
    }

    private float getCooldownRate() {
        return cooldownRate;
    }

    private void setCooldownRate(float cooldownRate, boolean temporary) {

        if (temporary) {
            oldWizard = this;
            timeCopied = Instant.now();
        }

        this.cooldownRate = cooldownRate;
    }

    BossBar getManaBar() {
        return manaBar;
    }

    void setManaBar(BossBar manaBar) {
        this.manaBar = manaBar;
    }

    BossBar getPotionStatusBar() {
        return potionStatusBar;
    }

    void setPotionStatusBar(BossBar potionStatusBar) {
        this.potionStatusBar = potionStatusBar;
    }

    public PotionType getActivePotion() {
        return activePotion;
    }

    public void setActivePotion(PotionType activePotion) {
        this.activePotion = activePotion;
    }

    Set<Location> getChestsLooted() {
        return chestsLooted;
    }

    private boolean hasActiveSpite(UUID uuid) {

        // Spite spell is not active
        if (!game.getSpells().containsKey(SpellType.SPITE))
            return false;

        SpellSpite spite = (SpellSpite) game.getSpells().get(SpellType.SPITE);
        return spite.getSpited().containsKey(uuid);
    }

    SpellType getDisabledSpell() {
        return disabledSpell;
    }

    public void setDisabledSpell(SpellType disabledSpell) {
        this.disabledSpell = disabledSpell;
        this.disabledUsableTime = Instant.now().plus(Duration.ofMinutes(1));
    }

    Instant getDisabledUsableTime() {
        return disabledUsableTime;
    }
}