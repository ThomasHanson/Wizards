package dev.thomashanson.wizards.game.spell.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;
import dev.thomashanson.wizards.util.ExplosionUtil;

public class SpellImplode extends Spell implements Tickable {

    private static final List<ImplosionInstance> ACTIVE_IMPLOSIONS = new CopyOnWriteArrayList<>();
    private static final Random RANDOM = new Random();

    public SpellImplode(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        StatContext context = StatContext.of(level);
        List<Block> targets = player.getLastTwoTargetBlocks(null, (int) getStat("range", level));
        
        // Mineplex code used list.get(0) (the air block in front of the target)
        if (targets.isEmpty()) {
            return false;
        }
        
        // Use the air block as the center, just like Mineplex
        Block centerBlock = targets.get(0); 

        ACTIVE_IMPLOSIONS.add(new ImplosionInstance(this, player, centerBlock, level));
        return true;
    }

    @Override
    public void tick(long gameTick) {
        if (ACTIVE_IMPLOSIONS.isEmpty()) return;
        ACTIVE_IMPLOSIONS.removeIf(ImplosionInstance::tick);
    }

    @Override
    public int getTickInterval() {
        return 1; // Needs per-tick updates for Mineplex-accurate particles
    }

    @Override
    public void cleanup() {
        ACTIVE_IMPLOSIONS.clear();
    }

    private static class ImplosionInstance {
        final SpellImplode parent;
        final Player caster;
        final Location center;
        final Block centerBlock;
        final int level;
        final List<Block> affectedBlocks = new ArrayList<>();
        int ticksLived = 0;

        final int durationTicks;
        final double blockVelocity;
        final float size; // This is now calculated from the config

        ImplosionInstance(SpellImplode parent, Player caster, Block targetBlock, int level) {
            this.parent = parent;
            this.caster = caster;
            this.centerBlock = targetBlock;
            this.center = targetBlock.getLocation().add(0.5, 0.5, 0.5);
            this.level = level;

            this.durationTicks = (int) parent.getStat("duration-ticks", level);
            this.blockVelocity = parent.getStat("block-velocity", level);

            double baseSize = parent.getStat("base-size", level, 1.5);
            double sizePerLevel = parent.getStat("size-per-level", level, 0.7);
            
            // Calculate the final size
            this.size = (float) (baseSize + (level * sizePerLevel));
            // --- END UPDATED LOGIC ---

            findAffectedBlocks();
            Collections.shuffle(affectedBlocks);
        }

        /**
         * Replicates the exact block-finding logic from Mineplex's SpellImplode,
         * but removes the check that excludes InventoryHolders (chests, etc.).
         */
        void findAffectedBlocks() {
            for (int x = (int) (-size * 2); x <= (int) (size * 2); x++) {
                for (int y = (int) (-size * 2); y <= (int) (size * 2); y++) {
                    for (int z = (int) (-size * 2); z <= (int) (size * 2); z++) {
                        
                        Block effectedBlock = centerBlock.getRelative(x, y, z);

                        if (effectedBlock.getType() == Material.AIR || effectedBlock.getType() == Material.BEDROCK || affectedBlocks.contains(effectedBlock)) {
                            continue;
                        }

                        // Mineplex distance check (squashed sphere)
                        if ((center.distance(effectedBlock.getLocation().add(0.5, 0.5, 0.5)) + Math.abs(y / 4D))
                                <= ((size * 2) + RANDOM.nextFloat())) {
                            
                            // We *include* InventoryHolders here, per user request
                            affectedBlocks.add(effectedBlock);
                        }
                    }
                }
            }
        }

        /**
         * Replicates the Mineplex charge-up and explosion logic.
         * @return true if the instance is finished.
         */
        boolean tick() {
            ticksLived++;
            
            if (ticksLived > durationTicks) {
                // Run explosion logic on the main thread
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        explode();
                    }
                }.runTask(parent.plugin);
                return true; // Remove instance
            }

            // --- Charge-up Phase (identical to Mineplex) ---
            
            // 1. Sound effect
            if (!affectedBlocks.isEmpty()) {
                Block block = affectedBlocks.get(RANDOM.nextInt(affectedBlocks.size()));
                block.getWorld().playSound(block.getLocation(),
                        RANDOM.nextBoolean() ? Sound.BLOCK_GRAVEL_STEP : Sound.BLOCK_GRASS_STEP, // Modern sound equivalents
                        2F, RANDOM.nextFloat() / 4F);
            }

            // 2. Particle effect (every 3 ticks)
            if (ticksLived % 3 == 0) {
                // Iterate a third of the blocks
                for (int i = 0; i < Math.ceil(affectedBlocks.size() / 3D); i++) {
                    if (affectedBlocks.isEmpty()) break;
                    
                    Block block = affectedBlocks.get(RANDOM.nextInt(affectedBlocks.size()));
                    if (block.getType().isAir()) continue;

                    // Spawn particles from adjacent air faces
                    for (BlockFace face : BlockFace.values()) {
                        Block b = block.getRelative(face);
                        if (!b.getType().isSolid()) { // Check for air/foliage
                            
                            Location particleLoc = block.getLocation().add(
                                    0.5 + (face.getModX() * 0.6D),
                                    0.5 + (face.getModY() * 0.6D),
                                    0.5 + (face.getModZ() * 0.6D)
                            );

                            // Spawn BLOCK_CRACK particles moving *away* from the block
                            block.getWorld().spawnParticle(
                                    Particle.BLOCK_CRACK,
                                    particleLoc,
                                    6, // count
                                    face.getModX() / 2F, // offsetX
                                    face.getModY() / 2F, // offsetY
                                    face.getModZ() / 2F, // offsetZ
                                    0, // speed
                                    block.getBlockData() // data
                            );
                        }
                    }
                }
            }
            
            return false;
        }

        /**
         * Replicates the Mineplex "BlockExplosion" call using our ExplosionUtil,
         * which correctly handles spilling chest contents.
         */
        void explode() {
            // Final filter to remove any blocks that became air
            affectedBlocks.removeIf(block -> !block.getType().isSolid());
            if (affectedBlocks.isEmpty()) return;

            // Play the Mineplex explosion sound
            center.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5F, 1.5F);

            // Use our ExplosionUtil, which handles item spilling and non-griefing block explosions
            ExplosionUtil.ExplosionConfig config = new ExplosionUtil.ExplosionConfig(
                    false,  // regenerateBlocks (false = don't regenerate)
                    100L,   // regenerationDelayTicks
                    80,     // debrisLifespanTicks (4 seconds)
                    0.7,    // debrisChance (high chance for a big effect)
                    this.blockVelocity, // velocityStrength (from config)
                    0.4,    // velocityYAward
                    0.8     // itemVelocityModifier (items fly out)
            );

            // This single call will turn blocks to air, spill chest items, and launch debris
            ExplosionUtil.createExplosion(parent.plugin, center, affectedBlocks, config, false);
        }
    }
}