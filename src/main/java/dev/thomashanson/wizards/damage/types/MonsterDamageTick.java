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

public class MonsterDamageTick extends DamageTick {

    public static final DecimalFormat DISTANCE_FORMAT = new DecimalFormat("#.#");

    private LivingEntity entity;
    private final UUID attackerId;
    private final String attackerName;

    private final double distance;
    private final boolean ranged;

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

    // Unchanged getters and setters
    public final LivingEntity getEntity() { return entity; }
    public void setEntity(LivingEntity entity) { this.entity = entity; }
    public final UUID getAttackerId() { return attackerId; }
    public final String getAttackerName() { return attackerName; }
    protected final double getDistance() { return distance; }
    public final boolean isRanged() { return ranged; }
}