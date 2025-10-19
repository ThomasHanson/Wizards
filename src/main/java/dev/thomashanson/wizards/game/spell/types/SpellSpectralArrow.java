package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.mode.GameTeam;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;

public class SpellSpectralArrow extends Spell implements Tickable {

    private final Map<Arrow, Location> activeArrows = new ConcurrentHashMap<>();
    private final NamespacedKey levelKey;

    public SpellSpectralArrow(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
        this.levelKey = new NamespacedKey(plugin, "spectral_arrow_level");
    }

    @Override
    public boolean cast(Player player, int level) {
        Arrow arrow = player.launchProjectile(Arrow.class);
        arrow.setShooter(player);
        arrow.setVelocity(arrow.getVelocity().multiply(getStat("velocity-multiplier", level, 1.5)));
        
        PersistentDataContainer pdc = arrow.getPersistentDataContainer();
        pdc.set(levelKey, PersistentDataType.INTEGER, level);
        
        activeArrows.put(arrow, arrow.getLocation());
        return true;
    }

    @Override
    public void tick(long gameTick) {
        if (activeArrows.isEmpty()) return;

        Particle particle = Particle.FIREWORKS_SPARK;

        activeArrows.entrySet().removeIf(entry -> {
            Arrow arrow = entry.getKey();
            if (!arrow.isValid() || arrow.isOnGround()) {
                return true;
            }

            // Draw a trail from the arrow's last known position to its current one
            Location lastPos = entry.getValue();
            Location currentPos = arrow.getLocation();
            Vector travel = currentPos.toVector().subtract(lastPos.toVector());

            for (double d = 0; d < travel.length(); d += 0.5) {
                Vector offset = travel.clone().normalize().multiply(d);
                arrow.getWorld().spawnParticle(particle, lastPos.clone().add(offset), 1, 0, 0, 0, 0);
            }
            entry.setValue(currentPos);
            return false;
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        Integer spellLevel = arrow.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        if (spellLevel == null) return;

        event.setCancelled(true);
        arrow.remove();

        if (!(arrow.getShooter() instanceof Player attacker)) return;

        if (target instanceof Player && getGame().map(g -> g.getRelation(attacker, (Player) target) != GameTeam.TeamRelation.ENEMY).orElse(false)) {
            return;
        }
        
        Location origin = activeArrows.getOrDefault(arrow, attacker.getLocation());
        double distance = origin.distance(target.getLocation());
        
        // Correctly use the safe getStat method with the distance context
        StatContext distanceContext = StatContext.of(spellLevel, distance);
        double damage = getStat("damage", distanceContext, 1);

        damage(target, new CustomDamageTick(damage, EntityDamageEvent.DamageCause.PROJECTILE, getKey(), Instant.now(), attacker, distance));
        getWizard(attacker).ifPresent(wizard -> wizard.addAccuracy(true));
        activeArrows.remove(arrow);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow arrow && activeArrows.containsKey(arrow)) {
            arrow.remove();
            
            // The instanceof pattern creates the 'shooter' variable
            if (arrow.getShooter() instanceof Player shooter) {
                // We use the new 'shooter' variable directly, no cast needed
                getWizard(shooter).ifPresent(wizard -> wizard.addAccuracy(false));
            }
            activeArrows.remove(arrow);
        }
    }

    @Override
    public void cleanup() {
        activeArrows.keySet().forEach(arrow -> {
            if (arrow.isValid()) arrow.remove();
        });
        activeArrows.clear();
    }
}