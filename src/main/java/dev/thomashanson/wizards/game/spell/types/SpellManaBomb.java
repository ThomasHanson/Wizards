package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;

public class SpellManaBomb extends Spell implements Tickable {

    private static final List<ManaBombInstance> ACTIVE_BOMBS = new CopyOnWriteArrayList<>();

    public SpellManaBomb(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        ACTIVE_BOMBS.add(new ManaBombInstance(this, player, level));
        return true;
    }

    @Override
    public void tick(long gameTick) {
        if (ACTIVE_BOMBS.isEmpty()) return;
        ACTIVE_BOMBS.removeIf(ManaBombInstance::tick);
    }

    @Override
    public void cleanup() {
        ACTIVE_BOMBS.clear();
    }

    private static class ManaBombInstance {
        final SpellManaBomb parent;
        final Player caster;
        final int level;
        Location location;
        Vector velocity;

        // Configurable stats
        final double maxRangeSq;
        final double gravity;
        final double damage;
        final float explosionRadius;

        final Location origin;

        ManaBombInstance(SpellManaBomb parent, Player caster, int level) {
            this.parent = parent;
            this.caster = caster;
            this.level = level;
            this.location = caster.getEyeLocation();
            this.origin = this.location.clone();

            StatContext context = StatContext.of(level);
            double speed = parent.getStat("speed", level);
            double range = parent.getStat("range", level);
            this.maxRangeSq = range * range;
            this.gravity = parent.getStat("gravity", level);
            this.damage = parent.getStat("damage", level);
            this.explosionRadius = (float) parent.getStat("explosion-radius", level);

            this.velocity = location.getDirection().normalize().multiply(speed);
            // Add an upward arc
            if (velocity.getY() < 0.2) {
                velocity.setY(velocity.getY() + 0.25);
            }
        }

        /** @return true if this instance should be removed. */
        boolean tick() {
            if (!caster.isOnline() || location.distanceSquared(origin) > maxRangeSq) {
                explode();
                return true;
            }

            location.add(velocity);
            velocity.subtract(new Vector(0, gravity, 0));

            if (location.getBlock().getType().isSolid()) {
                explode();
                return true;
            }

            for (LivingEntity entity : location.getWorld().getNearbyLivingEntities(location, 1.0)) {
                if (entity.equals(caster)) continue;
                explode();
                return true;
            }

            location.getWorld().spawnParticle(Particle.REDSTONE, location, 1, new Particle.DustOptions(Color.AQUA, 1.2F));
            location.getWorld().playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3F, 0.7F);
            return false;
        }

        void explode() {
            location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 1.2F);
            location.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, location, 1);

            for (LivingEntity entity : location.getWorld().getNearbyLivingEntities(location, explosionRadius)) {
                if (parent.getWizard((Player) entity).isEmpty()) continue;

                double distance = entity.getLocation().distance(location);
                double proximity = Math.max(0, 1.0 - (distance / explosionRadius));
                double finalDamage = damage * proximity;

                if (finalDamage > 0.1) {
                    parent.damage(entity, new CustomDamageTick(finalDamage, EntityDamageEvent.DamageCause.MAGIC, parent.getKey(), Instant.now(), caster, null));
                }
            }
        }
    }
}
