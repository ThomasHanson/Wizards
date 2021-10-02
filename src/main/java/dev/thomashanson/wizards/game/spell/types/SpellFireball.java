package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.util.MathUtil;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;

public class SpellFireball extends Spell implements Spell.Deflectable {

    @Override
    public void castSpell(Player player, int level) {

        Fireball fireball = player.launchProjectile(Fireball.class);
        Vector vector = player.getEyeLocation().getDirection().normalize().multiply(0.14);

        fireball.setVelocity(vector);

        fireball.setBounce(false);
        fireball.setYield(0F);

        fireball.setShooter(player);
        fireball.setMetadata("Wizard", new FixedMetadataValue(getGame().getPlugin(), getWizard(player)));

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 0.5F, 5F);
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent event) {

        if (event.getEntityType() != EntityType.FIREBALL)
            return;

        final double radius = 3.0;
        final Location location = event.getLocation();

        Wizard wizard = (Wizard) event.getEntity().getMetadata("Wizard").get(0).value();

        int spellLevel = wizard != null ? wizard.getLevel(getSpell()) : 1;

        Collection<Entity> nearby = Objects.requireNonNull(location.getWorld()).getNearbyEntities(location, radius, radius, radius);

        for (Entity entity : nearby) {

            Location nearbyLocation = entity.getLocation();

            final double heightForceFinal = Math.max(-4.0, Math.min(4.0, (2.3 + (spellLevel / 4.5)) / 2.0));
            final double radiusForceFinal = Math.max(-4.0, Math.min(4.0, -1.0 * 3.0 / 2.0));

            MathUtil.setVelocity (
                    entity, location.toVector().subtract(nearbyLocation.toVector()),
                    radiusForceFinal,
                    true, heightForceFinal, 0, 4.0,
                    false
            );
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {

        if (!(event.getDamager() instanceof Fireball))
            return;

        if (!event.getDamager().hasMetadata("Wizard"))
            return;

        if (!(event.getEntity() instanceof Player))
            return;

        Wizard wizard = (Wizard) event.getDamager().getMetadata("Wizard").get(0).value();
        boolean isAlive = wizard != null && getGame().getWizards().contains(wizard);

        final int spellLevel = isAlive ? wizard.getLevel(getSpell()) : 1;

        CustomDamageTick damageTick = new CustomDamageTick (
                spellLevel + 3,
                EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
                getSpell().getSpellName(),
                Instant.now(),
                isAlive ? wizard.getPlayer() : null
        );

        damage((LivingEntity) event.getEntity(), damageTick);

        event.getEntity().setFireTicks(80);
    }

    @Override
    public void deflectSpell(Player player, int level, Vector direction) {

    }
}