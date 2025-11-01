package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.spell.Spell;


/**
 * Implementation of the "Boulder Toss" spell.
 * <p>
 * This spell operates in two phases, managed by {@link BoulderInstance}:
 * 1.  **Rotation Phase:** Summons several {@link BoulderProjectile}s that orbit
 * the caster, damaging any enemies they touch.
 * 2.  **Launch Phase:** After a set duration, all orbiting boulders are
 * launched forward in the caster's direction as projectiles.
 * <p>
 * This class implements {@link Tickable} to update the state of all
 * active boulders every tick.
 */
public class SpellBoulderToss extends Spell implements Tickable {

    private final Map<UUID, BoulderInstance> activeInstances = new ConcurrentHashMap<>();
    private final NamespacedKey boulderKey;

    public SpellBoulderToss(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
        this.boulderKey = new NamespacedKey(plugin, "boulder_toss_boulder");
    }

    @Override
    public boolean cast(Player player, int level) {
        if (activeInstances.containsKey(player.getUniqueId())) {
            // Send feedback message to player
            return false;
        }

        activeInstances.put(player.getUniqueId(), new BoulderInstance(this, player, level));
        player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1F, 1.2F);
        return true;
    }

    @Override
    public void tick(long gameTick) {
        if (activeInstances.isEmpty()) return;

        final Iterator<BoulderInstance> iterator = activeInstances.values().iterator();
        while (iterator.hasNext()) {
            final BoulderInstance instance = iterator.next();
            if (instance.isDone()) {
                iterator.remove();
            } else {
                instance.tick();
            }
        }
    }

    @Override
    public int getTickInterval() {
        return 1; // Needs frequent updates for smooth visuals.
    }

    @Override
    public void cleanup() {
        activeInstances.values().forEach(BoulderInstance::cleanup);
        activeInstances.clear();
    }

    /**
     * Manages the state for a single "Boulder Toss" cast by a player.
     * It controls the phase (ROTATING, LAUNCHED) and owns the list of
     * individual boulders.
     */
    private static class BoulderInstance {
        private enum Phase { ROTATING, LAUNCHED }

        private final SpellBoulderToss parentSpell;
        private final Player caster;
        private final int level;
        private final List<BoulderProjectile> boulders = new ArrayList<>();

        private Phase currentPhase = Phase.ROTATING;
        private int ticksLived = 0;
        private boolean done = false;

        // Configurable stats
        private final int rotationDurationTicks;
        private final double angularSpeed;
        private final double orbitRadius;
        private final double orbitYOffset;
        private final double damage;
        private final double knockback;
        private final double launchSpeed;
        private final double launchArc;
        private final double gravity;

        BoulderInstance(SpellBoulderToss parent, Player caster, int level) {
            this.parentSpell = parent;
            this.caster = caster;
            this.level = level;

            final int boulderCount = (int) parent.getStat("boulders", level, 3);
            final double orbits = parent.getStat("orbits", level, 2.0);
            this.rotationDurationTicks = (int) parent.getStat("rotation-ticks", level, 100);
            this.angularSpeed = (2.0 * Math.PI * orbits) / rotationDurationTicks;
            this.orbitRadius = parent.getStat("orbit-radius", level, 2.5);
            this.orbitYOffset = parent.getStat("orbit-y-offset", level, 1.0);
            this.damage = parent.getStat("damage", level, 5.0);
            this.knockback = parent.getStat("knockback", level, 0.4);
            this.launchSpeed = parent.getStat("launch-speed-bps", level, 25.0) / 20.0; // BPS to BPT
            this.launchArc = parent.getStat("launch-arc", level, 0.35);
            this.gravity = parent.getStat("gravity", level, 0.03);

            spawnBoulders(boulderCount);
        }

        private void spawnBoulders(int count) {
            for (int i = 0; i < count; i++) {
                double angleOffset = (2.0 * Math.PI / count) * i;
                boulders.add(new BoulderProjectile(this, angleOffset));
            }
        }

        void tick() {
            ticksLived++;
            if (!caster.isOnline()) {
                cleanup();
                return;
            }

            if (currentPhase == Phase.ROTATING) {
                tickRotation();
            } else {
                tickProjectiles();
            }

            if (boulders.isEmpty()) {
                this.done = true;
            }
        }

        private void tickRotation() {
            if (ticksLived > rotationDurationTicks) {
                launchBoulders();
                return;
            }
            boulders.forEach(boulder -> boulder.tickRotation(ticksLived));
        }

        private void tickProjectiles() {
            boulders.removeIf(BoulderProjectile::tickProjectile);
        }

        private void launchBoulders() {
            currentPhase = Phase.LAUNCHED;
            caster.playSound(caster.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1F, 1.2F);
            boulders.forEach(boulder -> boulder.launch(caster.getEyeLocation().getDirection()));
        }

        void cleanup() {
            boulders.forEach(BoulderProjectile::remove);
            boulders.clear();
            this.done = true;
        }
        
        boolean isDone() { return done; }
    }

    /**
     * Represents a single boulder, acting as both an orbiting hazard
     * and a final projectile.
     * <p>
     * This class wraps an {@link ArmorStand} to represent the boulder visually
     * and handles its own movement, collision, and cleanup logic.
     */
    private static class BoulderProjectile {
        private final BoulderInstance parent;
        private final ArmorStand armorStand;
        private final double initialAngleOffset;

        // Configurable stats
        private final double rotationHitbox;
        private final double projectileHitbox;
        private final int projectileMaxTicks;
        private final double visualSpinSpeed;

        private double visualSpinAngle = 0;
        private Vector velocity;
        private int projectileTicks = 0;

        BoulderProjectile(BoulderInstance parent, double angleOffset) {
            this.parent = parent;
            this.initialAngleOffset = angleOffset;

            final int level = parent.level;
            final SpellBoulderToss spell = parent.parentSpell;
            this.rotationHitbox = spell.getStat("rotation-hitbox", level, 1.0);
            this.projectileHitbox = spell.getStat("projectile-hitbox", level, 1.2);
            this.projectileMaxTicks = (int) spell.getStat("projectile-max-ticks", level, 200);
            this.visualSpinSpeed = Math.toRadians(spell.getStat("visual-spin-degrees", level, 15.0));

            this.armorStand = spawnArmorStand(parent.caster.getLocation());
        }

        private ArmorStand spawnArmorStand(Location loc) {
            return loc.getWorld().spawn(loc, ArmorStand.class, as -> {
                as.setVisible(false);
                as.setGravity(false);
                as.setMarker(true);
                as.getEquipment().setHelmet(new ItemStack(Material.POLISHED_GRANITE));
                as.getPersistentDataContainer().set(parent.parentSpell.boulderKey, PersistentDataType.BYTE, (byte) 1);
            });
        }

        void tickRotation(int totalTicks) {
            final Location orbitCenter = parent.caster.getLocation().add(0, parent.orbitYOffset, 0);
            final double currentAngle = (totalTicks * parent.angularSpeed) + initialAngleOffset;
            final double x = parent.orbitRadius * Math.cos(currentAngle);
            final double z = parent.orbitRadius * Math.sin(currentAngle);

            armorStand.teleport(orbitCenter.clone().add(x, 0, z));
            visualSpinAngle += visualSpinSpeed;
            armorStand.setHeadPose(new EulerAngle(0, visualSpinAngle, 0));
            
            checkCollision(rotationHitbox, DamageCause.ENTITY_ATTACK, false);
        }

        void launch(Vector direction) {
            this.velocity = direction.clone().normalize().multiply(parent.launchSpeed);
            this.velocity.add(new Vector(0, parent.launchArc, 0));
        }

        /** @return true if this projectile should be removed. */
        boolean tickProjectile() {
            projectileTicks++;
            if (projectileTicks > projectileMaxTicks || !armorStand.isValid()) {
                remove();
                return true;
            }

            velocity.subtract(new Vector(0, parent.gravity, 0));
            armorStand.teleport(armorStand.getLocation().add(velocity));

            if (armorStand.getLocation().getBlock().getType().isSolid()) {
                // Impact effects could go here
                remove();
                return true;
            }

            return checkCollision(projectileHitbox, DamageCause.PROJECTILE, true);
        }

        /** @return true if collision occurred and projectile should be removed. */
        private boolean checkCollision(double radius, DamageCause cause, boolean removeOnHit) {
            for (LivingEntity entity : armorStand.getWorld().getNearbyLivingEntities(armorStand.getLocation(), radius)) {
                if (entity.equals(parent.caster)) continue;

                if (armorStand.getBoundingBox().expand(radius * 0.5).overlaps(entity.getBoundingBox())) {
                    parent.parentSpell.damage(entity, new CustomDamageTick(parent.damage, cause, parent.parentSpell.getKey(), Instant.now(), parent.caster, null));
                    
                    final Vector kb = entity.getLocation().toVector().subtract(armorStand.getLocation().toVector()).normalize().multiply(parent.knockback);
                    entity.setVelocity(entity.getVelocity().add(kb));
                    
                    if (removeOnHit) {
                        remove();
                        return true;
                    }
                }
            }
            return false;
        }

        void remove() {
            if (armorStand.isValid()) {
                armorStand.remove();
            }
        }
    }
}