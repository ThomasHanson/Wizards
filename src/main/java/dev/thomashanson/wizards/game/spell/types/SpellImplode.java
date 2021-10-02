package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.game.overtime.types.DisasterEarthquake;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.util.BlockUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class SpellImplode extends Spell implements Spell.SpellBlock {

    @Override
    public void castSpell(Player player, int level) {

        List<Block> targets = player.getLastTwoTargetBlocks(BlockUtil.getNonSolidBlocks(), 50);

        if (targets.size() > 1)
            castSpell(player, targets.get(0), level);
    }

    @Override
    public void castSpell(Player player, Block block, int level) {

        final Location centerLocation = block.getLocation().clone().add(0.5, 0.5, 0.5);
        final List<Block> affectedBlocks = new ArrayList<>();
        int size = (int) (1.5F + (level * 0.7F));

        if (getGame().isOvertime() && getGame().getDisaster() instanceof DisasterEarthquake)
            size *= 3;

        for (int x = -size * 2; x <= size * 2; x++) {
            for (int y = -size * 2; y <= size * 2; y++) {
                for (int z = -size * 2; z <= size * 2; z++) {

                    Block affectedBlock = block.getRelative(x, y, z);

                    if (affectedBlock.getType() == Material.AIR || affectedBlock.getType() == Material.BEDROCK || affectedBlocks.contains(affectedBlock))
                        continue;

                    if (
                            (centerLocation.distance(affectedBlock.getLocation().add(0.5, 0.5, 0.5)) + Math.abs(y / 4D))
                                    <= ((size * 2) + ThreadLocalRandom.current().nextFloat())
                    ) {

                        affectedBlocks.add(affectedBlock);
                    }
                }
            }
        }

        if (affectedBlocks.isEmpty())
            return;

        Collections.shuffle(affectedBlocks);

        new BukkitRunnable() {

            int cycles;
            Iterator<Block> iterator;

            public void run() {

                if (!affectedBlocks.isEmpty()) {

                    Block block = affectedBlocks.get(ThreadLocalRandom.current().nextInt(affectedBlocks.size()));

                    block.getWorld().playSound (
                            block.getLocation(),
                            ThreadLocalRandom.current().nextBoolean() ? Sound.BLOCK_GRAVEL_BREAK : Sound.BLOCK_GRASS_BREAK,
                            2, ThreadLocalRandom.current().nextFloat() / 4
                    );
                }

                if (cycles % 3 == 0) {

                    for (int a = 0; a < Math.ceil(affectedBlocks.size() / 3D); a++) {

                        if (iterator == null || !iterator.hasNext())
                            iterator = affectedBlocks.iterator();

                        Block block = iterator.next();

                        if (block.getType() == Material.AIR)
                            continue;

                        for (int i = 0; i < 6; i++) {

                            BlockFace face = BlockFace.values()[i];
                            Block relative = block.getRelative(face);

                            if (!relative.getType().isSolid()) {

                                relative.getWorld().spawnParticle (

                                        Particle.BLOCK_CRACK,

                                        relative.getLocation().add (
                                                0.5 + (face.getModX() * 0.6D),
                                                0.5 + (face.getModY() * 0.6D),
                                                0.5 + (face.getModZ() * 0.6D)
                                        ),

                                        6, // count

                                        face.getModX() / 2F, // offsetX
                                        face.getModX() / 2F, // offsetY
                                        face.getModX() / 2F, // offsetZ

                                        0, // speed

                                        relative.getBlockData()
                                );
                            }
                        }
                    }
                }

                if (affectedBlocks.isEmpty()) {
                    cancel();

                } else if (cycles++ >= 20) {

                    affectedBlocks.removeIf(block -> block.getType() == Material.AIR);

                    //Wizards.getArcadeManager().GetExplosion().BlockExplosion(effectedBlocks, centerLocation, false);

                    affectedBlocks.forEach(block -> {

                        if (block.getState() instanceof InventoryHolder) {

                            InventoryHolder holder = (InventoryHolder) block.getState();

                            for (ItemStack item : holder.getInventory().getContents())
                                player.getWorld().dropItemNaturally(block.getLocation(), item);
                        }

                        block.setType(Material.AIR);
                    });

                    for (Player online : Bukkit.getOnlinePlayers())
                        online.playSound(online == player ? player.getLocation() : centerLocation, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5F, 1.5F);

                    cancel();
                }
            }
        }.runTaskTimer(getGame().getPlugin(), 0L, 0L);
    }
}