package dev.thomashanson.wizards.damage.types;

import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.game.manager.DamageManager;
import dev.thomashanson.wizards.util.EntityUtil;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.text.DecimalFormat;
import java.time.Instant;

public class MonsterDamageTick extends DamageTick {

    private LivingEntity entity;
    private double distance;
    private boolean ranged = false;

    public MonsterDamageTick(double damage, String name, Instant timestamp, LivingEntity entity) {
        super(damage, EntityDamageEvent.DamageCause.ENTITY_ATTACK, name, timestamp);
        this.entity = entity;
    }

    public MonsterDamageTick(double damage, String name, Instant timestamp, LivingEntity entity, double distance) {
        super(damage, EntityDamageEvent.DamageCause.ENTITY_ATTACK, name, timestamp);
        this.entity = entity;
        this.distance = distance;
        this.ranged = true;
    }

    private double getDistance() {
        return distance;
    }

    private boolean isRanged() {
        return ranged;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public void setEntity(LivingEntity entity) {
        this.entity = entity;
    }

    String getMessageTemplate() {

        if (isRanged()) {

            DecimalFormat decimalFormat = new DecimalFormat("#.#");

            return
                    DamageManager.BASE_COLOR + "Shot by " + DamageManager.ACCENT_COLOR + "{ATTACKER}" + DamageManager.BASE_COLOR + " from "+
                            DamageManager.ACCENT_COLOR + decimalFormat.format(getDistance()) + "m " + DamageManager.BASE_COLOR + "away";

        } else {
            return DamageManager.BASE_COLOR + "Attacked by " + DamageManager.ACCENT_COLOR +  "{ATTACKER}";
        }
    }

    String getDeathMessageTemplate(Player player) {

        if (isRanged()) {

            DecimalFormat decimalFormat = new DecimalFormat("#.#");

            return
                    DamageManager.ACCENT_COLOR + player.getDisplayName() + DamageManager.BASE_COLOR + " was killed by " + DamageManager.ACCENT_COLOR + "{KILLER}" + DamageManager.BASE_COLOR + " from "+
                            DamageManager.ACCENT_COLOR + decimalFormat.format(getDistance()) + "m " + DamageManager.BASE_COLOR + "away";

        } else {
            return DamageManager.ACCENT_COLOR + player.getDisplayName() + DamageManager.BASE_COLOR + " was killed by " + DamageManager.ACCENT_COLOR +  "{KILLER}";
        }
    }

    @Override
    public boolean matches(DamageTick tick) {
        return (tick instanceof MonsterDamageTick) && getEntity().getUniqueId().equals(((MonsterDamageTick) tick).getEntity().getUniqueId());
    }

    @Override
    public String getDeathMessage(Player player) {
        return getDeathMessageTemplate(player).replace("{KILLER}", EntityUtil.getEntityName(getEntity()));

    }

    @Override
    public String getSingleLineSummary() {
        return getMessageTemplate().replace("{ATTACKER}", EntityUtil.getEntityName(getEntity()));
    }
}