package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.util.BlockUtil;

public class SpellFrostBarrier extends Spell implements Spell.SpellBlock, Tickable {

    private static final List<BarrierInstance> ACTIVE_BARRIERS = new ArrayList<>();
    private static final Map<Block, Instant> BARRIER_BLOCKS = new ConcurrentHashMap<>();
    private static final org.bukkit.block.data.BlockData PACKED_ICE_DATA = Material.PACKED_ICE.createBlockData();

    public SpellFrostBarrier(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        Location location = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(1.5));
        return castSpell(player, location.getBlock().getRelative(BlockFace.DOWN), level);
    }

    @Override
    public boolean castSpell(Player player, Block block, int level) {
        ACTIVE_BARRIERS.add(new BarrierInstance(this, player, block.getRelative(BlockFace.UP), level));
        return true;
    }

    @Override
    public void tick(long gameTick) {
        // Tick active building processes
        ACTIVE_BARRIERS.removeIf(BarrierInstance::tick);

        // Tick melting process for all blocks
        if (BARRIER_BLOCKS.isEmpty()) return;

        Instant now = Instant.now();
        Iterator<Map.Entry<Block, Instant>> iterator = BARRIER_BLOCKS.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Block, Instant> entry = iterator.next();
            if (now.isAfter(entry.getValue())) {
                Block block = entry.getKey();
                if (block.getType() == Material.PACKED_ICE) {
                    block.setType(Material.AIR);
                    // --- NEW MELT EFFECT ---
                    playMeltEffect(block);
                    // --- END NEW ---
                }
                iterator.remove();
            }
        }
    }

    @Override
    public void cleanup() {
        ACTIVE_BARRIERS.clear();
        BARRIER_BLOCKS.keySet().forEach(block -> {
            if (block.getType() == Material.PACKED_ICE) block.setType(Material.AIR);
        });
        BARRIER_BLOCKS.clear();
    }

    /**
     * NEW: Helper method to play a consistent shatter effect.
     */
    private void playMeltEffect(Block block) {
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8F, 1.2F);
        block.getWorld().spawnParticle(Particle.BLOCK_CRACK, block.getLocation().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, 0, PACKED_ICE_DATA);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (BARRIER_BLOCKS.containsKey(event.getBlock())) {
            event.setCancelled(true);
            event.getBlock().setType(Material.AIR);
            
            // --- UPDATED to use helper method ---
            playMeltEffect(event.getBlock());
            
            BARRIER_BLOCKS.remove(event.getBlock());
        }
    }

    @EventHandler
    public void onBlockMelt(BlockFadeEvent event) {
        if (BARRIER_BLOCKS.containsKey(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    private static class BarrierInstance {
        final SpellFrostBarrier parent;
        final Block startBlock;
        final BlockFace facing;
        final int width;
        final int height;
        final long durationSeconds;
        private int currentHeight = 0;

        BarrierInstance(SpellFrostBarrier parent, Player caster, Block startBlock, int level) {
            this.parent = parent;
            this.startBlock = startBlock;
            this.facing = BlockUtil.getFace(caster.getEyeLocation().getYaw(), false);

            this.width = (int) parent.getStat("width", level);
            this.height = (int) parent.getStat("height", level);
            this.durationSeconds = (long) parent.getStat("duration", level);
        }

        /** @return true if this building instance is finished and should be removed */
        boolean tick() {
            if (currentHeight >= height) {
                return true;
            }

            Block currentLevelBlock = startBlock.getRelative(0, currentHeight, 0);
            buildWallSegment(currentLevelBlock);

            currentHeight++;
            return false;
        }

        void buildWallSegment(Block center) {
            placeBlock(center);
            BlockFace[] growDirections = getWallDirections(facing);

            for (BlockFace direction : growDirections) {
                for (int i = 1; i <= width / 2; i++) {
                    Block relative = center.getRelative(direction, i);
                    if (relative.getType().isSolid()) break; 
                    placeBlock(relative);
                }
            }
        }

        private BlockFace[] getWallDirections(BlockFace facing) {
            return switch (facing) {
                case NORTH, SOUTH -> new BlockFace[]{BlockFace.WEST, BlockFace.EAST};
                case WEST, EAST -> new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH};
                default -> new BlockFace[0];
            };
        }

        void placeBlock(Block block) {
            if (block.getType() != Material.AIR) return;
            block.setType(Material.PACKED_ICE, false);
            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_GLASS_PLACE, 1F, 1.2F);
            
            // --- NEW FORMATION EFFECT ---
            block.getWorld().spawnParticle(Particle.BLOCK_CRACK, block.getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0, PACKED_ICE_DATA);
            // --- END NEW ---

            long randomOffset = ThreadLocalRandom.current().nextLong((durationSeconds / 4) * 1000);
            BARRIER_BLOCKS.put(block, Instant.now().plusSeconds(durationSeconds).plusMillis(randomOffset));
        }
    }
}