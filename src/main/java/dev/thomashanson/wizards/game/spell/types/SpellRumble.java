package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.overtime.types.DisasterEarthquake;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.util.BlockUtil;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class SpellRumble extends Spell implements Spell.SpellBlock {

    @Override
    public void castSpell(Player player, int level) {

        Block block = player.getLocation().subtract(0, 1, 0).getBlock();

        if (!block.getType().isSolid())
            block = block.getRelative(BlockFace.DOWN);

        castSpell(player, block, level);
    }

    @Override
    public void castSpell(Player player, Block block, int level) {

        if (!block.getType().isSolid())
            return;

        final BlockFace direction = BlockUtil.getFace(player.getEyeLocation().getYaw());

        final int damage = 4 + (level * 2);
        final int maxDistance = 10 * level;

        final int multiplier = getGame().isOvertime() && getGame().getDisaster() instanceof DisasterEarthquake ? 2 : 1;

        playEffect(block);

        new BukkitRunnable() {

            private Block currentBlock = block;
            private int distanceTravelled = 0;
            private final List<Integer> affected = new ArrayList<>();
            private List<Block> previousBlocks = new ArrayList<>();

            @Override
            public void run() {

                if (!player.isOnline() || player.getGameMode() == GameMode.SPECTATOR) {
                    endRun();
                    return;
                }

                boolean found = false;

                for (int y : new int[] { 0, 1, -1, -2 }) {

                    if (currentBlock.getY() + y <= 0)
                        continue;

                    Block relative = currentBlock.getRelative(direction).getRelative(0, y, 0);

                    if (relative.getType().isSolid() && !relative.getRelative(0, 1, 0).getType().isSolid()) {
                        found = true;
                        currentBlock = relative;
                        break;
                    }
                }

                if (!found) {
                    endRun();
                    return;
                }

                List<Block> affectedBlocks = new ArrayList<>();

                BlockFace[] faces = BlockUtil.getSideBlockFaces(direction);

                affectedBlocks.add(currentBlock);
                playEffect(currentBlock);

                int size = (int) (Math.min(4, Math.floor(distanceTravelled / (8D - level))) + 1);

                for (int i = 1; i <= size; i++) {
                    for (BlockFace face : faces) {

                        Block relative = currentBlock.getRelative(face, i);

                        if (relative.getType().isSolid()) {
                            affectedBlocks.add(relative);
                            playEffect(relative);
                        }
                    }
                }

                for (Block b : BlockUtil.getDiagonalBlocks(currentBlock, direction, size - 2)) {

                    if (b.getType().isSolid()) {
                        affectedBlocks.add(b);
                        playEffect(b);
                    }
                }

                previousBlocks.addAll(affectedBlocks);

                for (Block previousBlock : previousBlocks) {

                    for (Entity entity : previousBlock.getChunk().getEntities()) {

                        if (entity instanceof LivingEntity && player != entity && !affected.contains(entity.getEntityId())) {

                            if (entity instanceof Tameable) {

                                AnimalTamer tamer = ((Tameable) entity).getOwner();

                                if (tamer != null && tamer.equals(player))
                                    continue;
                            }

                            Location entityLocation = entity.getLocation();

                            if (entityLocation.getBlockX() == previousBlock.getX() && entityLocation.getBlockZ() == previousBlock.getZ()) {

                                if (entity instanceof Player)
                                    if (((Player) entity).getGameMode() == GameMode.SPECTATOR)
                                        continue;

                                double height = entityLocation.getY() - previousBlock.getY();

                                if (height >= 0 && height <= 2.5) {

                                    CustomDamageTick rumbleTick = new CustomDamageTick (
                                            damage * multiplier,
                                            EntityDamageEvent.DamageCause.CONTACT,
                                            getSpell().getSpellName(),
                                            Instant.now(),
                                            player
                                    );

                                    rumbleTick.addKnockback(getSpell().getSpellName(), 1);
                                    damage((LivingEntity) entity, rumbleTick);

                                    if (entity instanceof Player)
                                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 1, false));
                                }

                                affected.add(entity.getEntityId());
                            }
                        }
                    }
                }

                previousBlocks = affectedBlocks;

                if (distanceTravelled++ >= maxDistance)
                    endRun();
            }

            private void endRun() {

                List<Block> blocks = new ArrayList<>();
                BlockFace[] faces = BlockUtil.getSideBlockFaces(direction);

                blocks.add(currentBlock);

                for (int i = 1; i <= Math.min(4, Math.floor(distanceTravelled / (8D - level))) + 1; i++) {

                    for (BlockFace face : faces) {

                        Block relative = currentBlock.getRelative(face, i);

                        if (relative.getType().isSolid())
                            blocks.add(relative);
                    }
                }

                for (Block block : blocks) {

                    List<Block> toExplode = new ArrayList<>();
                    toExplode.add(block);

                    BlockFace[] newFaces = new BlockFace[] {
                            BlockFace.EAST, BlockFace.WEST,
                            BlockFace.SOUTH, BlockFace.NORTH,
                            BlockFace.UP, BlockFace.DOWN
                    };

                    for (BlockFace face : newFaces) {

                        if (ThreadLocalRandom.current().nextBoolean()) {

                            Block relative = block.getRelative(face);

                            if (relative.getType() != Material.AIR && relative.getType() != Material.BEDROCK) {

                                if (!toExplode.contains(relative)) {
                                    toExplode.add(relative);
                                    playEffect(relative);
                                }
                            }
                        }
                    }

                    for (LivingEntity entity : block.getWorld().getEntitiesByClass(LivingEntity.class)) {

                        if (entity instanceof Player && ((Player) entity).getGameMode() == GameMode.SPECTATOR)
                            continue;

                        if (entity instanceof Tameable) {

                            AnimalTamer tamer = ((Tameable) entity).getOwner();

                            if (tamer != null && tamer.equals(player))
                                continue;
                        }

                        double distance = Integer.MAX_VALUE;

                        for (Block explodeBlock : toExplode) {
                            double currentDist = explodeBlock.getLocation().add(0.5, 0.5, 0.5).distance(entity.getLocation());
                            distance = Math.min(distance, currentDist);
                        }

                        if (distance < 2) {

                            CustomDamageTick rumbleExplosionTick = new CustomDamageTick (
                                    ((1 + (level / 5.0)) * (2 - distance) * multiplier),
                                    EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
                                    getSpell().getSpellName() + " Explosion",
                                    Instant.now(),
                                    player
                            );

                            damage(entity, rumbleExplosionTick);
                        }
                    }
                }

                cancel();
            }

        }.runTaskTimer(getGame().getPlugin(), 5L, 1L);
    }

    private void playEffect(Block block) {

        block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, block.getType());
        Block relative = block.getRelative(BlockFace.UP);

        if (!relative.getType().isSolid())
            relative.breakNaturally();
    }
}
