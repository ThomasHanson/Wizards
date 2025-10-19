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
import dev.thomashanson.wizards.game.mode.GameTeam;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;

public class SpellManaBolt extends Spell implements Tickable {

    private static final List<ManaBoltInstance> ACTIVE_BOLTS = new CopyOnWriteArrayList<>();

    public SpellManaBolt(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        ACTIVE_BOLTS.add(new ManaBoltInstance(this, player, level));
        return true;
    }

    @Override
    public void tick(long gameTick) {
        if (ACTIVE_BOLTS.isEmpty()) return;
        ACTIVE_BOLTS.removeIf(ManaBoltInstance::tick);
    }
    
    @Override
    public void cleanup() {
        ACTIVE_BOLTS.clear();
    }

    private static class ManaBoltInstance {
        final SpellManaBolt parent;
        Player caster;
        final int level;
        Location location;
        Vector direction;
        
        final double speed;
        final double maxRangeSq;
        final double damage;
        final double homingStrength;
        
        private Location origin;

        ManaBoltInstance(SpellManaBolt parent, Player caster, int level) {
            this.parent = parent;
            this.caster = caster;
            this.level = level;
            this.location = caster.getEyeLocation();
            this.origin = this.location.clone();

            StatContext context = StatContext.of(level);
            this.speed = parent.getStat("speed-bps", level) / 20.0;
            double range = parent.getStat("range", level);
            this.maxRangeSq = range * range;
            this.damage = parent.getStat("damage", level);
            this.homingStrength = parent.getStat("homing-strength", level);
            
            this.direction = location.getDirection().normalize().multiply(speed);
            location.getWorld().playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 0.5F);
        }

        boolean tick() {
            if (!caster.isOnline() || location.distanceSquared(origin) > maxRangeSq) {
                burst(false);
                return true;
            }

            updateHoming();
            location.add(direction);

            if (location.getBlock().getType().isSolid()) {
                burst(false);
                return true;
            }
            
            for (LivingEntity entity : location.getWorld().getNearbyLivingEntities(location, 1.2)) {
                if (entity.equals(caster)) continue;
                parent.damage(entity, new CustomDamageTick(damage, EntityDamageEvent.DamageCause.MAGIC, parent.getKey(), Instant.now(), caster, null));
                burst(true);
                return true;
            }
            
            location.getWorld().spawnParticle(Particle.REDSTONE, location, 1, new Particle.DustOptions(Color.AQUA, 1F));
            return false;
        }

        void updateHoming() {
            if (homingStrength <= 0) return;
            parent.getGame().flatMap(game -> game.getPlayers(true).stream()
                .filter(p -> !p.equals(caster) && game.getRelation(p, caster) == GameTeam.TeamRelation.ENEMY)
                .min(java.util.Comparator.comparingDouble(p -> p.getEyeLocation().distanceSquared(location)))
            ).ifPresent(target -> {
                Vector toTarget = target.getEyeLocation().toVector().subtract(location.toVector());
                direction.add(toTarget.normalize().multiply(homingStrength)).normalize().multiply(speed);
            });
        }

        void burst(boolean hit) {
            location.getWorld().spawnParticle(Particle.CRIT_MAGIC, location, 60, 0.5, 0.5, 0.5, 0.2);
            location.getWorld().playSound(location, Sound.ENTITY_BAT_TAKEOFF, 1.2F, 1F);
            
            if (hit) {
                parent.getWizard(caster).ifPresent(wizard -> wizard.addAccuracy(true));
            } else {
                parent.getWizard(caster).ifPresent(wizard -> wizard.addAccuracy(false));
            }
        }
        
        // This method can be called by SpellLightShield
        public void reflect(Player reflector, Vector newDirection) {
            this.caster = reflector;
            this.origin = this.location.clone(); // Reset range check
            this.direction = newDirection.normalize().multiply(speed);
        }
    }
}

