package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.util.ExplosionUtil;
import dev.thomashanson.wizards.util.effects.ParticleConfig;
import dev.thomashanson.wizards.util.effects.ParticleUtil;

public class SpellRumble extends Spell implements Spell.SpellBlock, Tickable {

    private static final List<RumbleInstance> ACTIVE_RUMBLES = new ArrayList<>();

    // Mineplex's array for 8-directional movement
    private static final BlockFace[] RADIAL = {
            BlockFace.SOUTH, BlockFace.SOUTH_WEST, BlockFace.WEST, BlockFace.NORTH_WEST,
            BlockFace.NORTH, BlockFace.NORTH_EAST, BlockFace.EAST, BlockFace.SOUTH_EAST
    };

    public SpellRumble(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    // Mineplex's logic for casting in the air
    @Override
    public boolean cast(Player player, int level) {
        Block block = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
        if (!block.getType().isSolid()) {
            block = block.getRelative(BlockFace.DOWN);
        }
        return castSpell(player, block, level);
    }

    @Override
    public boolean castSpell(Player player, Block block, int level) {
        if (!block.getType().isSolid()) return false;
        ACTIVE_RUMBLES.add(new RumbleInstance(this, player, block, level));
        return true;
    }

    @Override
    public void tick(long gameTick) {
        if (ACTIVE_RUMBLES.isEmpty()) return;
        ACTIVE_RUMBLES.removeIf(RumbleInstance::tick);
    }

    @Override
    public void cleanup() {
        ACTIVE_RUMBLES.clear();
    }

    private static class RumbleInstance {
        final SpellRumble parent;
        final Player caster;
        final int level;
        final BlockFace direction;
        Block currentBlock;
        int distance = 0;
        final Set<Integer> effectedEntities = new HashSet<>();

        // Configurable stats
        final int maxDistance;
        final double travelDamage;
        final double finaleDamageBase;
        final PotionEffect slowEffect;
        final double blockVelocity;

        RumbleInstance(SpellRumble parent, Player caster, Block startBlock, int level) {
            this.parent = parent;
            this.caster = caster;
            this.level = level;

            // Get direction from player's yaw, just like Mineplex
            this.direction = RADIAL[Math.round(caster.getEyeLocation().getYaw() / 45f) & 0x7];
            this.currentBlock = startBlock;

            this.maxDistance = (int) parent.getStat("range", level);
            this.travelDamage = parent.getStat("travel-damage", level);
            this.finaleDamageBase = parent.getStat("finale-damage-base", level);
            this.slowEffect = new PotionEffect(PotionEffectType.SLOW, (int) parent.getStat("slow-duration-ticks", level), (int) parent.getStat("slow-amplifier", level) - 1);
            this.blockVelocity = parent.getStat("block-velocity", level);
        }

        /** @return true if this instance should be removed */
        boolean tick() {
            if (!caster.isOnline()) {
                return true; // Caster logged off
            }
            
            if (distance >= maxDistance) {
                explode();
                return true;
            }

            Block nextBlock = findNextBlock();
            if (nextBlock == null) {
                explode();
                return true;
            }
            currentBlock = nextBlock;
            distance++;

            affectArea();
            return false;
        }

        /**
         * Finds the next solid block in the path, checking up/down hills.
         * (Identical to Mineplex logic)
         */
        Block findNextBlock() {
            // Check y=0, then y=1 (up a block), then y=-1, y=-2 (down)
            for (int y : new int[]{0, 1, -1, -2}) {
                if (currentBlock.getY() + y <= 0) continue;

                Block relative = currentBlock.getRelative(direction).getRelative(0, y, 0);
                if (relative.getType().isSolid() && !relative.getRelative(BlockFace.UP).getType().isSolid()) {
                    return relative; // Found the next ground block
                }
            }
            return null; // Hit a wall or cliff
        }

        /**
         * Damages entities and plays effects in a widening area.
         */
        void affectArea() {
            // Calculate widening size, exactly like Mineplex
            int size = (int) (Math.min(4, Math.floor(distance / (8D - level))) + 1);
            BlockFace[] sideFaces = getPerpendicularFaces(direction);

            Set<Block> affectedBlocks = new HashSet<>();

            // Center
            affectedBlocks.add(currentBlock);

            // Sides
            for (int i = 1; i <= size; i++) {
                for (BlockFace face : sideFaces) {
                    Block b = currentBlock.getRelative(face, i);
                    if (b.getType().isSolid()) {
                        affectedBlocks.add(b);
                    }
                }
            }
            
            // --- New Visuals ---
            for (Block block : affectedBlocks) {
                if (block.getType().isSolid()) {
                    // Create a shockwave of cracking earth particles
                    ParticleConfig config = new ParticleConfig(Particle.BLOCK_CRACK, 10, 0.1, 0.1, 0.1, 0.1, block.getBlockData());
                    ParticleUtil.createShockwave(block.getLocation().add(0.5, 1.01, 0.5), 1.0, config);
                    block.getWorld().playSound(block.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0F, 0.8F);
                }

                // --- Damage Logic ---
                Location checkLoc = block.getLocation().add(0.5, 1.5, 0.5);
                for (LivingEntity entity : block.getWorld().getNearbyLivingEntities(checkLoc, 0.5, 0.8, 0.5)) {
                    if (entity.equals(caster) || effectedEntities.contains(entity.getEntityId())) continue;

                    if (entity instanceof Player && parent.getWizard((Player) entity).isEmpty()) continue;
                    
                    effectedEntities.add(entity.getEntityId());
                    parent.damage(entity, new CustomDamageTick(travelDamage, EntityDamageEvent.DamageCause.CONTACT, parent.getKey(), Instant.now(), caster, null));
                    entity.addPotionEffect(slowEffect);
                }
            }
        }

        /**
         * The finale explosion, identical to Mineplex's.
         */
        void explode() {
            Location explosionCenter = currentBlock.getLocation().add(0.5, 1.0, 0.5);
            
            // 1. Collect blocks for visual explosion
            int size = (int) (Math.min(4, Math.floor(distance / (8D - level))) + 1);
            BlockFace[] sideFaces = getPerpendicularFaces(direction);
            List<Block> blocksToExplode = new ArrayList<>();

            blocksToExplode.add(currentBlock);
            for (int i = 1; i <= size; i++) {
                for (BlockFace face : sideFaces) {
                    Block b = currentBlock.getRelative(face, i);
                    if (b.getType().isSolid()) {
                        blocksToExplode.add(b);
                    }
                }
            }
            
            // 2. Play Sound
            explosionCenter.getWorld().playSound(explosionCenter, Sound.ENTITY_GENERIC_EXPLODE, 1.5F, 1.0F);

            // 3. Create Visual Block Debris (No Damage)
            ExplosionUtil.ExplosionConfig config = new ExplosionUtil.ExplosionConfig(
                    false, 100L, 80, 0.6,
                    this.blockVelocity, 0.4, 0.8
            );
            ExplosionUtil.createExplosion(parent.plugin, explosionCenter, blocksToExplode, config, false);

            // 4. Apply Finale Damage (Mineplex logic)
            for (LivingEntity entity : explosionCenter.getWorld().getNearbyLivingEntities(explosionCenter, size + 2, size + 2, size + 2)) {
                if (entity.equals(caster)) continue;
                if (entity instanceof Player && parent.getWizard((Player) entity).isEmpty()) continue;

                double dist = entity.getLocation().distance(explosionCenter);
                if (dist < 2.5) {
                    // Mineplex damage formula: (base + scaling) * falloff
                    double damage = (this.finaleDamageBase + (level / 5D)) * (2.5 - dist);
                    parent.damage(entity, new CustomDamageTick(damage, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, parent.getKey() + ".finale", Instant.now(), caster, null));
                }
            }
        }

        /**
         * Helper to get perpendicular faces for the fissure.
         */
        private BlockFace[] getPerpendicularFaces(BlockFace direction) {
            return switch (direction) {
                case NORTH, SOUTH -> new BlockFace[]{BlockFace.WEST, BlockFace.EAST};
                case WEST, EAST -> new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH};
                case NORTH_EAST, SOUTH_WEST -> new BlockFace[]{BlockFace.NORTH_WEST, BlockFace.SOUTH_EAST};
                case NORTH_WEST, SOUTH_EAST -> new BlockFace[]{BlockFace.NORTH_EAST, BlockFace.SOUTH_WEST};
                default -> new BlockFace[0];
            };
        }
    }
}