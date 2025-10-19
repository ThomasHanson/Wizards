package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.spell.Spell;

public class SpellGust extends Spell {

    public SpellGust(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        double range = getStat("range", level);
        double baseStrength = getStat("strength", level);

        Vector baseVector = player.getLocation().getDirection().setY(0).normalize();
        baseVector.multiply(1.5).setY(0.3).normalize();

        boolean castSuccess = false;

        for (LivingEntity target : player.getWorld().getNearbyLivingEntities(player.getLocation(), range)) {
            if (target.equals(player) || !(target instanceof Player) || getWizard((Player) target).isEmpty()) {
                continue;
            }
            if (!player.hasLineOfSight(target)) {
                continue;
            }

            double distance = player.getEyeLocation().distance(target.getLocation());
            if (distance >= range) {
                continue;
            }

            double proximityFactor = Math.max(0.2, 1.0 - (distance / range));
            Vector finalVelocity = baseVector.clone().multiply(baseStrength * proximityFactor);

            target.setVelocity(finalVelocity);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1F, 0.7F);

            damage(target, new CustomDamageTick(0, EntityDamageEvent.DamageCause.CUSTOM, getKey(), Instant.now(), player, null)); // UPDATED
            castSuccess = true;
        }

        if (castSuccess) {
            player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1F, 0.7F);
        }
        return castSuccess;
    }
}
