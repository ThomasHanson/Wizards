package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.util.MathUtil;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Instant;

public class SpellSplash extends Spell {

    public void castSpell(Player player, int level) {

        player.setFireTicks(0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * (level * 5), 0));

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_SPLASH, 0.4F, 1.5F);

        int radius = 5;

        player.getNearbyEntities(radius, radius, radius)
                .stream()
                .filter(entity -> entity instanceof LivingEntity)
                .forEach(entity -> {

                    if (entity instanceof Player)
                        ((Player) entity).addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * (level * 5), 0));

                    double knockback = 3 + level;
                    double distance = MathUtil.getOffset2D(player.getLocation(), entity.getLocation());

                    CustomDamageTick damageTick = new CustomDamageTick (
                            (double) level * (1 - (distance / 10)),
                            EntityDamageEvent.DamageCause.CUSTOM,
                            getSpell().getSpellName(),
                            Instant.now(),
                            player
                    );

                    damageTick.addKnockback(getSpell().getSpellName(), knockback * (1 - (distance / 10)));
                    damage((LivingEntity) entity, damageTick);
        });
    }
}