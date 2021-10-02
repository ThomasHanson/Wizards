package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.spell.Spell;
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

        int
                radius = 5, //(int) getValue(player, "Radius"),
                knockback = 3 + level; //(int) getValue(player, "Knockback");

        player.getNearbyEntities(radius, radius, radius)
                .stream()
                .filter(entity -> entity instanceof LivingEntity)
                .forEach(entity -> {

                    if (entity instanceof Player)
                        ((Player) entity).addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * (level * 5), 0));

                    /*
                     * Deal damage & knockback to targets, lowering to 50% at the end of range
                     */
                    CustomDamageTick splashTick = new CustomDamageTick (
                            level,
                            EntityDamageEvent.DamageCause.CUSTOM,
                            getSpell().getSpellName(),
                            Instant.now(),
                            player
                    );

                    splashTick.addKnockback(getSpell().getSpellName(), knockback);
                    damage((LivingEntity) entity, splashTick);
        });
    }
}