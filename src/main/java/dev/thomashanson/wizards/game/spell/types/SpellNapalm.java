package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;

public class SpellNapalm extends Spell {

    // Stores the block transformations loaded from the config
    private final EnumMap<Material, Material> blockTransformations = new EnumMap<>(Material.class);

    public SpellNapalm(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
        // Load the transformations when the spell is initialized
        loadBlockTransformations(config);
    }

    /**
     * Reads the 'block-transformations' section from the config and populates the map.
     */
    private void loadBlockTransformations(ConfigurationSection config) {
        ConfigurationSection transConfig = config.getConfigurationSection("block-transformations");
        if (transConfig == null) return;

        for (String fromKey : transConfig.getKeys(false)) {
            Material fromMat = Material.getMaterial(fromKey);
            String toValue = transConfig.getString(fromKey);
            Material toMat = Material.getMaterial(toValue);

            if (fromMat != null && toMat != null) {
                blockTransformations.put(fromMat, toMat);
            } else {
                plugin.getLogger().warning("[Wizards] Invalid block transformation in " + getKey() + ": " + fromKey + " -> " + toValue);
            }
        }
    }

    @Override
    public boolean cast(Player player, int level) {
        // Get stats from config
        StatContext context = StatContext.of(level);
        double range = getStat("range", level);
        double speed = getStat("speed", level); // Blocks per tick
        double radius = getStat("radius", level); // User requested SL x 2
        double damage = getStat("damage", level);
        int fireTicks = (int) (getStat("fire-duration-seconds",level) * 20);

        // Launch the projectile runnable
        new NapalmProjectile(player, level, range, speed, radius, damage, fireTicks).runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /**
     * This runnable manages the projectile's movement, collision, and explosion.
     */
    private class NapalmProjectile extends BukkitRunnable {

        private final Player caster;
        private final int level;
        private final double maxRange;
        private final double speedPerTick;
        private final double explosionRadius;
        private final double damage;
        private final int fireTicks;

        private final Location location;
        private final Vector direction;
        private final World world;
        private double distanceTraveled = 0;

        // Particle Configuration
        private static final Particle.DustOptions ORANGE_DUST = new Particle.DustOptions(Color.ORANGE, 1.2F);
        private static final Particle.DustOptions RED_DUST = new Particle.DustOptions(Color.RED, 1.0F);

        NapalmProjectile(Player caster, int level, double maxRange, double speed, double radius, double damage, int fireTicks) {
            this.caster = caster;
            this.level = level;
            this.maxRange = maxRange;
            this.speedPerTick = speed;
            this.explosionRadius = radius;
            this.damage = damage;
            this.fireTicks = fireTicks;

            this.location = caster.getEyeLocation();
            this.direction = caster.getLocation().getDirection();
            this.world = caster.getWorld();
        }

        @Override
        public void run() {
            // Store the position *before* moving
            Location lastLocation = location.clone();
            
            // We will interpolate between the last location and the new one
            // by spawning particle clusters every 0.25 blocks
            double step = 0.25;
            for (double d = 0; d < speedPerTick; d += step) {
                // Get the interpolated point
                location.add(direction.clone().multiply(step));
                
                // --- PARTICLE WIDTH REDUCED ---
                // Spawn the dense cluster here
                // Offsets are reduced for a tighter beam.
                world.spawnParticle(Particle.REDSTONE, location, 10, 0.15, 0.15, 0.15, 0, ORANGE_DUST); // Was 0.3
                world.spawnParticle(Particle.REDSTONE, location, 5, 0.1, 0.1, 0.1, 0, RED_DUST);         // Was 0.2
                world.spawnParticle(Particle.FLAME, location, 3, 0.1, 0.1, 0.1, 0.01);                    // Was 0.2
                world.spawnParticle(Particle.SMOKE_NORMAL, location, 3, 0.15, 0.15, 0.15, 0.01);         // Was 0.25
            }
            // The loop above has already moved the projectile 'speedPerTick' blocks
            distanceTraveled += speedPerTick;


            Block block = location.getBlock();

            // --- 1. Check for max range ---
            if (distanceTraveled >= maxRange) {
                // Move back slightly so the explosion is at the end
                explode(location.subtract(direction.clone().multiply(0.5)));
                cancel();
                return;
            }

            // --- 2. Check for block collision ---
            if (block.getType().isSolid()) {
                // Find the *exact* impact point
                Location impactLoc = location.clone().subtract(direction.clone().multiply(speedPerTick * 0.5));
                explode(impactLoc);
                cancel();
                return;
            }

            // --- 3. Check for entity collision ---
            for (LivingEntity target : world.getNearbyLivingEntities(location, 1.5, this::isTargetable)) {
                if (target.equals(caster)) continue;
                explode(target.getLocation());
                cancel();
                return;
            }
        }

        /**
         * Triggers the Molotov-style explosion.
         */
        private void explode(Location center) {
            // --- 1. Sounds (UPDATED) ---
            world.playSound(center, Sound.ENTITY_IRON_GOLEM_ATTACK, 1F, 1F); // The "thud"
            world.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1.5F, 0.8F); // The "whoosh" (Replaced PIG_DEATH)

            // --- 2. Particles ---
            world.spawnParticle(Particle.LAVA, center.clone().add(0, 0.2, 0), 30, 0.3, 0.0, 0.3, 0);

            // --- 3. Damage & Ignite Entities ---
            for (LivingEntity target : world.getNearbyLivingEntities(center, explosionRadius, this::isTargetable)) {
                damage(target, new CustomDamageTick(damage, EntityDamageEvent.DamageCause.FIRE_TICK, "Napalm", Instant.now(), caster, null));
                target.setFireTicks(fireTicks);
            }

            // --- 4. Create Fire Puddle (The Molotov Effect) ---
            createFirePuddle(center);

            // --- 5. Glaze Blocks (The Napalm Effect) ---
            glazeBlocks(center);
        }

        /**
         * Creates a distance-scaled, temporary fire puddle.
         */
        private void createFirePuddle(Location center) {
            List<Block> blocksToClear = new ArrayList<>();

            for (Block block : getBlocksInSphere(center, explosionRadius)) {
                if (!block.getType().isAir() || !block.getRelative(BlockFace.DOWN).getType().isSolid()) {
                    continue;
                }

                double distance = block.getLocation().add(0.5, 0.5, 0.5).distance(center);
                double distanceScalar = Math.min(1.0, distance / explosionRadius);
                long spawnDelay = (long) (20.0 * distanceScalar); // 0-20 tick delay

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        block.setType(Material.FIRE);
                    }
                }.runTaskLater(plugin, spawnDelay);

                blocksToClear.add(block);
            }

            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Block b : blocksToClear) {
                        if (b.getType() == Material.FIRE) {
                            b.setType(Material.AIR);
                        }
                    }
                }
            }.runTaskLater(plugin, fireTicks);
        }

        /**
         * Creates the "glazing" block transformation effect.
         */
        private void glazeBlocks(Location center) {
            // Don't run if the config section was empty
            if (blockTransformations.isEmpty()) return;

            World world = center.getWorld();

            for (Block block : getBlocksInSphere(center, explosionRadius)) {
                Material fromMat = block.getType();

                // 1. Evaporate Water (inspired by original Mineplex code)
                if (fromMat == Material.WATER) {
                    double distance = block.getLocation().add(0.5, 0.5, 0.5).distance(center);
                    // Only evaporate water close to the center
                    if (distance < explosionRadius * 0.5) {
                        block.setType(Material.AIR);
                        world.playSound(block.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0F, 0.8F);
                    }
                    continue; // Skip to next block
                }

                // 2. Transform "Glaze-able" Blocks
                Material toMat = blockTransformations.get(fromMat);
                if (toMat != null) {
                    // We found a valid transformation
                    double distance = block.getLocation().add(0.5, 0.5, 0.5).distance(center);
                    double distanceScalar = Math.min(1.0, distance / explosionRadius);
                    // Apply a 0-10 tick delay based on distance for a "wave"
                    long glazeDelay = (long) (10.0 * distanceScalar);

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            block.setType(toMat);
                            // Play a "fizz" sound for the transformation
                            world.playSound(block.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.8F, 1.2F);
                        }
                    }.runTaskLater(plugin, glazeDelay);
                }
            }
        }

        /**
         * Helper method to get all blocks in a spherical radius.
         */
        private List<Block> getBlocksInSphere(Location center, double radius) {
            List<Block> blocks = new ArrayList<>();
            int r = (int) Math.ceil(radius);
            World world = center.getWorld();

            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        Location loc = center.clone().add(x, y, z);
                        if (loc.distanceSquared(center) <= radius * radius) {
                            blocks.add(loc.getBlock());
                        }
                    }
                }
            }
            return blocks;
        }

        private boolean isTargetable(LivingEntity entity) {
            return !entity.equals(caster) && (entity instanceof Player);
        }
    }
}