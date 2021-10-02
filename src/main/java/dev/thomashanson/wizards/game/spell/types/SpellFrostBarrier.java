package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.util.BlockUtil;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class SpellFrostBarrier extends Spell implements Spell.SpellBlock {

    private int updateTaskID;
    private Map<Block, Instant> blocks = new HashMap<>();

    @Override
    public void castSpell(Player player, int level) {
        Location location = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(1.5));
        castSpell(player, location.getBlock().getRelative(BlockFace.DOWN), level);
    }

    @Override
    public void castSpell(Player player, Block block, int level) {

        final Block starter = block.getRelative(BlockFace.UP);
        final int width = 4 + (level * 2);
        final BlockFace facing = BlockUtil.getFace(player.getEyeLocation().getYaw());
        final int wallHeight = 1 + level;

        updateTaskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(getGame().getPlugin(), () -> update(updateTaskID), 0L, 1L);

        new BukkitRunnable() {

            Block newBlock = starter;
            int currentRun;

            @Override
            public void run() {

                currentRun++;

                BlockFace[] faces = new BlockFace[1]; //UtilShapes.getCornerBlockFaces(block, facing);

                if (newBlock.getType() == Material.AIR) {
                    newBlock.setType(Material.PACKED_ICE);
                    blocks.put(newBlock, Instant.now().plusSeconds(((20 + ThreadLocalRandom.current().nextInt(10)))));
                }

                newBlock.getWorld().playEffect(newBlock.getLocation(), Effect.STEP_SOUND, newBlock.getType());

                for (BlockFace face : faces) {

                    for (int i = 1; i < width; i++) {

                        Block relative = block.getRelative(face.getModX() * i, 0, face.getModZ() * i);

                        if (relative.getType().isSolid())
                            break;

                        block.setType(Material.PACKED_ICE);
                        relative.getWorld().playEffect(relative.getLocation(), Effect.STEP_SOUND, relative.getType());

                        blocks.put(relative, Instant.now().plusSeconds(((20 + ThreadLocalRandom.current().nextInt(10)))));
                    }
                }

                newBlock = block.getRelative(BlockFace.UP);

                if (currentRun >= wallHeight)
                    cancel();
            }

        }.runTaskTimer(getGame().getPlugin(), 0L, 5L);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {

        Block block = event.getBlock();

        if (blocks.containsKey(block)) {
            event.setCancelled(true);
            block.setType(Material.AIR);
        }
    }

    @EventHandler
    public void onBlockMelt(BlockFadeEvent event) {

        Block block = event.getBlock();

        if (blocks.containsKey(block)) {
            event.setCancelled(true);
            block.setType(Material.AIR);
        }
    }

    public void update(int taskID) {

        Iterator<Map.Entry<Block, Instant>> iterator = blocks.entrySet().iterator();

        while (iterator.hasNext()) {

            Map.Entry<Block, Instant> entry = iterator.next();

            if (entry.getValue().isBefore(Instant.now())) {

                iterator.remove();
                Bukkit.getScheduler().cancelTask(taskID);

                if (entry.getKey().getType() == Material.PACKED_ICE)
                    entry.getKey().setType(Material.AIR);
            }
        }
    }
}
