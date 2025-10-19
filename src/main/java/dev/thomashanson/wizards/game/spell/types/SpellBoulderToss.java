package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;

public class SpellBoulderToss extends Spell implements Tickable {

    private static final List<BoulderInstance> ACTIVE_INSTANCES = new CopyOnWriteArrayList<>();
    private final NamespacedKey boulderKey;

    public SpellBoulderToss(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
        this.boulderKey = new NamespacedKey(plugin, "boulder_toss_boulder");
    }

    @Override
    public boolean cast(Player player, int level) {
        // Prevent player from casting again if they already have boulders active
        if (ACTIVE_INSTANCES.stream().anyMatch(inst -> inst.caster.getUniqueId().equals(player.getUniqueId()))) {
            // Send feedback message
            return false;
        }

        ACTIVE_INSTANCES.add(new BoulderInstance(this, player, level));
        player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1F, 1.2F);
        return true;
    }

    @Override
    public void tick(long gameTick) {
        if (ACTIVE_INSTANCES.isEmpty()) return;

        Iterator<BoulderInstance> iterator = ACTIVE_INSTANCES.iterator();
        while (iterator.hasNext()) {
            BoulderInstance instance = iterator.next();
            if (instance.tick()) {
                iterator.remove();
            }
        }
    }

    @Override
    public int getTickInterval() {
        return 1; // Needs frequent updates for smooth rotation and projectiles
    }

    @Override
    public void cleanup() {
        ACTIVE_INSTANCES.forEach(BoulderInstance::cleanup);
        ACTIVE_INSTANCES.clear();
    }

    private static class BoulderInstance {
        enum Phase { ROTATING, LAUNCHED, DONE }

        final SpellBoulderToss parentSpell;
        final Player caster;
        final int level;
        final List<BoulderProjectile> boulders = new ArrayList<>();
        private Phase currentPhase = Phase.ROTATING;
        private int ticksLived = 0;

        // Configurable stats
        final int rotationDurationTicks;
        final double angularSpeed;
        final double orbitRadius;
        final double damage;
        final double knockback;
        final double launchSpeed;
        final double gravity;

        BoulderInstance(SpellBoulderToss parent, Player caster, int level) {
            this.parentSpell = parent;
            this.caster = caster;
            this.level = level;

            StatContext context = StatContext.of(level);
            int boulderCount = (int) parent.getStat("boulders", level);
            this.rotationDurationTicks = (int) parent.getStat("rotation-ticks", level);
            this.angularSpeed = (2.0 * Math.PI * 2) / rotationDurationTicks; // 2 orbits
            this.orbitRadius = parent.getStat("orbit-radius", level);
            this.damage = parent.getStat("damage", level);
            this.knockback = parent.getStat("knockback", level);
            this.launchSpeed = parent.getStat("launch-speed", level) / 20.0; // BPS to BPT
            this.gravity = parent.getStat("gravity", level);

            spawnBoulders(boulderCount);
        }

        void spawnBoulders(int count) {
            for (int i = 0; i < count; i++) {
                double angleOffset = (2.0 * Math.PI / count) * i;
                boulders.add(new BoulderProjectile(this, angleOffset));
            }
        }

        /** @return true if this entire instance should be removed. */
        boolean tick() {
            ticksLived++;
            if (!caster.isOnline()) {
                cleanup();
                return true;
            }

            if (currentPhase == Phase.ROTATING) {
                tickRotation();
            } else if (currentPhase == Phase.LAUNCHED) {
                tickProjectiles();
            }

            // Remove if all boulders are gone (destroyed or expired)
            if (boulders.isEmpty()) {
                return true;
            }
            return false;
        }

        void tickRotation() {
            if (ticksLived > rotationDurationTicks) {
                launchBoulders();
                return;
            }

            boulders.forEach(boulder -> boulder.tickRotation(ticksLived));
        }

        void tickProjectiles() {
            boulders.removeIf(BoulderProjectile::tickProjectile);
        }

        void launchBoulders() {
            currentPhase = Phase.LAUNCHED;
            caster.playSound(caster.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1F, 1.2F);
            boulders.forEach(boulder -> boulder.launch(caster.getEyeLocation().getDirection()));
        }

        void cleanup() {
            boulders.forEach(BoulderProjectile::remove);
        }
    }

    private static class BoulderProjectile {
        final BoulderInstance parent;
        final ArmorStand armorStand;
        final double initialAngleOffset;
        private double visualSpinAngle = 0;

        // Projectile state
        private Vector velocity;
        private int projectileTicks = 0;

        BoulderProjectile(BoulderInstance parent, double angleOffset) {
            this.parent = parent;
            this.initialAngleOffset = angleOffset;
            this.armorStand = spawnArmorStand(parent.caster.getLocation());
        }

        ArmorStand spawnArmorStand(Location loc) {
            ArmorStand as = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setHelmet(new ItemStack(Material.POLISHED_GRANITE));
            as.getPersistentDataContainer().set(parent.parentSpell.boulderKey, PersistentDataType.BYTE, (byte)1);
            return as;
        }

        void tickRotation(int totalTicks) {
            Location orbitCenter = parent.caster.getLocation().add(0, 1.0, 0);
            double currentAngle = (totalTicks * parent.angularSpeed) + initialAngleOffset;
            double x = parent.orbitRadius * Math.cos(currentAngle);
            double z = parent.orbitRadius * Math.sin(currentAngle);
            armorStand.teleport(orbitCenter.clone().add(x, 0, z));
            visualSpinAngle += Math.toRadians(15);
            armorStand.setHeadPose(new EulerAngle(0, visualSpinAngle, 0));
            checkRotationCollision();
        }

        void checkRotationCollision() {
            for (LivingEntity entity : armorStand.getWorld().getNearbyLivingEntities(armorStand.getLocation(), 1.0)) {
                if (entity.equals(parent.caster)) continue;
                if (armorStand.getBoundingBox().expand(0.5).overlaps(entity.getBoundingBox())) {
                    parent.parentSpell.damage(entity, new CustomDamageTick(parent.damage, EntityDamageEvent.DamageCause.ENTITY_ATTACK, parent.parentSpell.getKey(), Instant.now(), parent.caster, null)); // UPDATED
                    Vector knockback = entity.getLocation().toVector().subtract(armorStand.getLocation().toVector()).normalize().multiply(parent.knockback);
                    entity.setVelocity(entity.getVelocity().add(knockback));
                }
            }
        }

        void launch(Vector direction) {
            this.velocity = direction.clone().normalize().multiply(parent.launchSpeed);
            this.velocity.add(new Vector(0, 0.35, 0)); // Add initial arc
        }

        /** @return true if this projectile should be removed. */
        boolean tickProjectile() {
            projectileTicks++;
            if (projectileTicks > 200 || armorStand.isDead()) { // Max 10 seconds
                remove();
                return true;
            }

            velocity.subtract(new Vector(0, parent.gravity, 0));
            armorStand.teleport(armorStand.getLocation().add(velocity));

            if (armorStand.getLocation().getBlock().getType().isSolid()) {
                // Impact effects
                remove();
                return true;
            }
            
            for (LivingEntity entity : armorStand.getWorld().getNearbyLivingEntities(armorStand.getLocation(), 1.2)) {
                 if (entity.equals(parent.caster)) continue;
                 if (armorStand.getBoundingBox().expand(0.6).overlaps(entity.getBoundingBox())) {
                     parent.parentSpell.damage(entity, new CustomDamageTick(parent.damage, EntityDamageEvent.DamageCause.PROJECTILE, parent.parentSpell.getKey(), Instant.now(), parent.caster, null)); // UPDATED
                     remove();
                     return true;
                 }
            }
            return false;
        }
        
        void remove() {
            armorStand.remove();
        }
    }
}
