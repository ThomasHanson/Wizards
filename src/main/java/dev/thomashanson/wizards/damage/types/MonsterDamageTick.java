package dev.thomashanson.wizards.damage.types;

import java.text.DecimalFormat;
import java.time.Instant;
import java.util.UUID;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.Nullable;

import dev.thomashanson.wizards.damage.DamageConfig;
import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.game.manager.DamageManager;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.thomashanson.wizards.util.EntityUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/**
 * A {@link DamageTick} implementation for damage caused by a non-player {@link LivingEntity} (a monster).
 * This class stores the attacking entity and whether the attack was ranged.
 */
public class MonsterDamageTick extends DamageTick {

    public static final DecimalFormat DISTANCE_FORMAT = new DecimalFormat("#.#");

    /**
     * The entity that dealt the damage. Can be null if the entity despawned.
     */
    private LivingEntity entity;
    
    /**
     * The UUID of the attacking entity, for persistent tracking.
     */
    private final UUID attackerId;

    /**
     * The name of the attacker, captured at the time of the event.
     */
    private final String attackerName;

    /**
     * The distance of the attack, or 0.0 for melee.
     */
    private final double distance;
    
    /**
     * True if the attack was a projectile or exceeded the melee range threshold.
     */
    private final boolean ranged;

    /**
     * Creates a new damage tick caused by a monster.
     *
     * @param damage      The amount of damage dealt.
     * @param cause       The underlying {@link EntityDamageEvent.DamageCause}.
     * @param reason      The internal reason (e.g., "Monster Melee").
     * @param timestamp   The time the damage occurred.
     * @param entity      The {@link LivingEntity} that dealt the damage.
     * @param distanceVal The distance of the attack, or null for melee.
     */
    public MonsterDamageTick(double damage, EntityDamageEvent.DamageCause cause, String reason, Instant timestamp, LivingEntity entity, @Nullable Double distanceVal) {
        super(damage, cause, reason, timestamp);
        this.entity = entity;
        this.attackerId = entity.getUniqueId();
        this.attackerName = EntityUtil.getEntityName(entity);

        if (distanceVal != null) {
            this.distance = distanceVal;
            this.ranged = true;
        } else {
            this.distance = 0.0;
            this.ranged = false;
        }
    }

    @Override
    public boolean matches(DamageTick tick) {
        return tick instanceof MonsterDamageTick other &&
               this.getAttackerId().equals(other.getAttackerId()) &&
               this.getReason().equals(other.getReason());
    }

    @Override
    public Component getDeathMessage(Player victim, LanguageManager lang, DamageManager damageManager) {
        Component victimName = victim.displayName().color(NamedTextColor.RED);
        DamageConfig.MonsterConfig msgConfig = damageManager.getConfig().deathMessages().monster();

        if (isRanged()) {
            String distanceStr = DISTANCE_FORMAT.format(getDistance());
            return lang.getTranslated(victim, msgConfig.ranged(),
                Placeholder.component("victim_name", victimName),
                Placeholder.unparsed("monster_name", getAttackerName()),
                Placeholder.unparsed("distance", distanceStr)
            );
        } else {
            return lang.getTranslated(victim, msgConfig.melee(),
                Placeholder.component("victim_name", victimName),
                Placeholder.unparsed("monster_name", getAttackerName())
            );
        }
    }

    @Override
    public Component getSingleLineSummary(Player viewer, LanguageManager lang, DamageManager damageManager) {
        if (isRanged()) {
            String distanceStr = DISTANCE_FORMAT.format(getDistance());
            return lang.getTranslated(viewer, "wizards.damage.summary.monster.ranged",
                Placeholder.unparsed("monster_name", getAttackerName()),
                Placeholder.unparsed("distance", distanceStr)
            );
        } else {
            return lang.getTranslated(viewer, "wizards.damage.summary.monster.melee",
                Placeholder.unparsed("monster_name", getAttackerName())
            );
        }
    }

    /**
     * @return The {@link LivingEntity} that attacked, or null if it is no longer loaded.
     */
    public final LivingEntity getEntity() { return entity; }
    
    /**
     * Updates the cached {@link LivingEntity} reference.
     *
     * @param entity The new entity reference.
     */
    public void setEntity(LivingEntity entity) { this.entity = entity; }
    
    /**
     * @return The {@link UUID} of the attacker.
     */
    public final UUID getAttackerId() { return attackerId; }

    /**
     * @return The name of the attacker (e.g., "Zombie").
     */
    public final String getAttackerName() { return attackerName; }
    
    /**
     * @return The distance of the attack, or 0.0 if it was melee.
     */
    protected final double getDistance() { return distance; }
    
    /**
     * @return True if the attack was ranged, false if it was melee.
     */
    public final boolean isRanged() { return ranged; }
}