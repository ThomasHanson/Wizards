package dev.thomashanson.wizards.game.spell;

import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.Wizards;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.NPC;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.time.Instant;

public abstract class Spell implements Listener {

    /**
     * Represents a spell that can also be cast
     * from a block.
     */
    public interface SpellBlock {

        /**
         * Gets called when a player casts a spell
         * that can be cast from a block.
         * @param player The player who cast the spell.
         * @param block  The block the player interacted with.
         * @param level  The spell level the player has unlocked.
         */
        void castSpell(Player player, Block block, int level);
    }

    /**
     * Represents a spell that is able to be deflected. This is currently
     * only used for the Light Shield spell. If a spell collides with the
     * shield, it will be deflected.
     */
    public interface Deflectable {

        /**
         * Gets called when a spell is deflected.
         * @param player    The player who cast the spell.
         * @param level     The spell level the player has unlocked.
         * @param direction The direction to deflect the spell in.
         */
        void deflectSpell(Player player, int level, Vector direction);
    }

    /**
     * Represents a spell that is able to
     * be cancelled during use.
     */
    public interface Cancellable {

        /**
         * Gets called when a spell gets cancelled. This is typically
         * called if a player swaps wands during the use of a spell and
         * if the spell hits a certain percentage complete.
         * @param player The player who cast the spell.
         */
        void cancelSpell(Player player);
    }

    /**
     * Represents some spell data that is associated when you cast
     * a spell. This data includes the slot it was cast from, the
     * spell that was cast, and whether or not the user quick
     * casted the spell or not.
     */
    public static class SpellData {

        private final int slot;
        private final Spell spell;
        private final boolean quickCast;

        public SpellData(int slot, Spell spell, boolean quickCast) {
            this.slot = slot;
            this.spell = spell;
            this.quickCast = quickCast;
        }

        public int getSlot() {
            return slot;
        }

        public Spell getSpell() {
            return spell;
        }

        public boolean isQuickCast() {
            return quickCast;
        }
    }

    private Wizards game;
    private SpellType spell;

    /**
     * Represents the current target of
     * the spell. Used for team games so
     * that spells will not target players
     * on the same team.
     */
    private Player target;

    private boolean
            deflected = false,
            cancelled = false,
            cancelOnSwap = false;

    /**
     * Represents the completion of the
     * spell has multiple parts or is not
     * cast instantly, then it should be
     * set respectively.
     */
    private double progress;

    /**
     * Called when a spell is cast. This will only
     * be called if the player the level is greater
     * than 0 and if the player is still alive.
     * @param player The player who cast the spell.
     * @param level  The spell level the player has unlocked.
     */
    public abstract void castSpell(Player player, int level);

    /**
     * Called when the game is ending. This function
     * can be called within any spell class to clean
     * any collections to prevent memory leaks and
     * improve performance.
     */
    public void cleanup() {}

    /**
     * Charges the player the correct amount of mana
     * when the spell is cast and handles the backend
     * work.
     */
    public void charge(Player player) {

        Wizard wizard = game.getWizard(player);

        if (wizard == null)
            return;

        wizard.setMana(wizard.getMana() - wizard.getManaCost(spell));
        wizard.setUsedSpell(this.getSpell());

        game.updateWandTitle(player);
    }

    /**
     * Charges the player the correct amount of mana
     * with a specific multiplier when the spell is
     * cast and handles the backend work.
     */
    public void charge(Player player, float multiplier) {

        Wizard wizard = game.getWizard(player);

        if (wizard == null)
            return;

        wizard.setMana(wizard.getMana() - (wizard.getManaCost(spell) * multiplier));
        wizard.setUsedSpell(this.getSpell());

        game.updateWandTitle(player);
    }

    protected void damage(NPC npc, DamageTick tick) {
        getGame().getPlugin().getDamageManager().damage(npc, tick);
    }

    protected void damage(LivingEntity entity, DamageTick tick) {
        getGame().getPlugin().getDamageManager().damage(entity, tick);
    }

    protected boolean isShield(Entity entity) {
        return entity.hasMetadata("Shield");
    }

    protected boolean isBoulder(Entity entity) {
        return entity.hasMetadata("Boulder");
    }

    protected int getSpellLevel(Player player) {
        return game.getLevel(player, spell);
    }

    protected Wizard getWizard(Player player) {
        return game.getWizard(player);
    }

    protected Wizards getGame() {
        return game;
    }

    public void setGame(Wizards game) {
        this.game = game;
    }

    public SpellType getSpell() {
        return spell;
    }

    public void setSpell(SpellType spell) {
        this.spell = spell;
    }

    public Player getTarget() {
        return target;
    }

    public void setTarget(Player target) {
        this.target = target;
    }

    public boolean isDeflected() {
        return deflected;
    }

    // TODO: 12/15/21 add field for damage to be done to the shield too
    public void setDeflected(boolean deflected, Player shieldUser) {

        this.deflected = deflected;

        if (deflected && shieldUser != null) {

            CustomDamageTick damageTick = new CustomDamageTick (
                    0.5,
                    EntityDamageEvent.DamageCause.CONTACT,
                    "Shield Deflection",
                    Instant.now(),
                    shieldUser
            );

            damage(shieldUser, damageTick);
        }
    }

    protected boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public boolean isCancelOnSwap() {
        return cancelOnSwap;
    }

    protected void setCancelOnSwap() {
        this.cancelOnSwap = true;
    }

    public double getProgress() {
        return progress;
    }

    protected void setProgress(double progress) {
        this.progress = progress;
    }
}