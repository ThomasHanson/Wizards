package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.overtime.types.DisasterLightning;
import dev.thomashanson.wizards.game.spell.Spell;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.time.Instant;

public class SpellGust extends Spell {

    public void castSpell(Player player, int level) {

        final Vector vector = player.getLocation()
                .getDirection()
                .setY(0)
                .normalize()
                .multiply(1.5)
                .setY(0.3)
                .multiply(1.2 + (level * 0.4));

        final double gustSize = (level * 3) + 10;

        for (Player target : player.getWorld().getPlayers()) {

            if (player.getUniqueId().equals(target.getUniqueId()))
                continue;

            if (getWizard(target) == null)
                continue;

            double offset = player.getEyeLocation().distance(target.getLocation());

            if (offset >= gustSize)
                continue;

            if (!player.canSee(target))
                continue;

            Vector power = vector.clone().multiply(Math.max(0.2, 1 - (offset / gustSize)));

            if (getGame().isOvertime() && getGame().getDisaster() instanceof DisasterLightning)
                power = power.clone().multiply(2);

            target.setVelocity(power);

            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1F, 0.7F);
            player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1F, 0.7F);

            // For kill credit
            CustomDamageTick damageTick = new CustomDamageTick (
                    0,
                    EntityDamageEvent.DamageCause.VOID,
                    getSpell().getSpellName(),
                    Instant.now(),
                    player
            );

            damage(target, damageTick);
        }
    }
}