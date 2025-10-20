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
import dev.thomashanson.wizards.game.spell.StatContext;
import dev.thomashanson.wizards.util.BlockUtil;

public class SpellFrostBarrier extends Spell implements Spell.SpellBlock, Tickable {

    private static final List<BarrierInstance> ACTIVE_BARRIERS = new ArrayList<>();
    private static final Map<Block, Instant> BARRIER_BLOCKS = new ConcurrentHashMap<>();

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
                if (entry.getKey().getType() == Material.PACKED_ICE) {
                    entry.getKey().setType(Material.AIR);
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

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (BARRIER_BLOCKS.containsKey(event.getBlock())) {
            event.setCancelled(true);
            event.getBlock().setType(Material.AIR);
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
            // UPDATED: getFace now requires a boolean. 'false' is correct for a straight wall.
            this.facing = BlockUtil.getFace(caster.getEyeLocation().getYaw(), false);

            StatContext context = StatContext.of(level);
            this.width = (int) parent.getStat("width", level);
            this.height = (int) parent.getStat("height", level);
            this.durationSeconds = (long) parent.getStat("duration-seconds", level);
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
            // UPDATED: Replaced the removed utility method with a simple, local helper.
            BlockFace[] growDirections = getWallDirections(facing);

            for (BlockFace direction : growDirections) {
                // This loop builds outwards from the center block in both directions.
                for (int i = 1; i <= width / 2; i++) {
                    Block relative = center.getRelative(direction, i);
                    if (relative.getType().isSolid()) break; // Stop this direction if we hit something
                    placeBlock(relative);
                }
            }
        }

        private BlockFace[] getWallDirections(BlockFace facing) {
            return switch (facing) {
                case NORTH, SOUTH -> new BlockFace[]{BlockFace.WEST, BlockFace.EAST};
                case WEST, EAST -> new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH};
                default -> new BlockFace[0]; // Should not happen with getFace(..., false)
            };
        }

        void placeBlock(Block block) {
            if (block.getType() != Material.AIR) return;
            block.setType(Material.PACKED_ICE, false);
            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_GLASS_PLACE, 1F, 1.2F);

            long randomOffset = ThreadLocalRandom.current().nextLong((durationSeconds / 4) * 1000);
            BARRIER_BLOCKS.put(block, Instant.now().plusSeconds(durationSeconds).plusMillis(randomOffset));
        }
    }
}
