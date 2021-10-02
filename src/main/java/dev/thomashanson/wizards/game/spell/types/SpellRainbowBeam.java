package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.util.BlockUtil;
import dev.thomashanson.wizards.util.EntityUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.time.Instant;
import java.util.Objects;

public class SpellRainbowBeam extends Spell {

    @Override
    public void castSpell(Player player, int level) {

        // TODO: 2020-05-13 get entity in sight
        Entity entityInSight = EntityUtil.getEntityInSight(player, 80, true, true, 1.9F);

        if (!(entityInSight instanceof LivingEntity))
            entityInSight = null;

        LivingEntity entityTarget = (LivingEntity) entityInSight;
        Location location;

        Bukkit.broadcastMessage(getSpell().getSpellName() + ": entity in sight? " + (entityTarget != null ? "true" : "false"));

        if (entityTarget != null) {

            location = player.getEyeLocation()
                    .add(player.getEyeLocation().getDirection().normalize()
                            .multiply(0.3 + player.getEyeLocation().distance(entityTarget.getEyeLocation())));

            double damage = (level * 2) + 2;
            double dist = location.distance(player.getLocation()) - (80 * 0.2);

            if (dist > 0) {
                damage -= damage * (dist / (80 * 0.8));
                damage = Math.max(1, damage);
            }

            CustomDamageTick damageTick = new CustomDamageTick (
                    damage,
                    EntityDamageEvent.DamageCause.MAGIC,
                    getSpell().getSpellName(),
                    Instant.now(),
                    player
            );

            damage(entityTarget, damageTick);
            getWizard(player).addAccuracy(true, false);

            player.playSound(entityTarget.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, (level * 2) + 6, 1);

        } else {
            location = player.getLastTwoTargetBlocks(BlockUtil.getNonSolidBlocks(), 80).get(0).getLocation().add(0.5, 0.5, 0.5);
        }

        for (Location line : BlockUtil.getLinesDistancedPoints(player.getEyeLocation().subtract(0, 0.1, 0), location, 0.3)) {

            Objects.requireNonNull(line.getWorld()).spawnParticle(Particle.SPELL_MOB, line, 1, 0, 0, 0, 500);

            // id = 0
            // data = 0
            // offsetX, offsetY, offsetZ = 0
            // speed = 500
            // count = 1
            // radius = 30
            //line.getWorld().playEffect(line, Effect.POTION_SWIRL, 0, 0, 0, 0, 0, 500, 1, 30);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.5F, 1F);
    }
}