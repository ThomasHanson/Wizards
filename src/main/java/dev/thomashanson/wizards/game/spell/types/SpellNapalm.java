package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.overtime.types.DisasterMeteors;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.util.BlockUtil;
import org.apache.commons.lang.Validate;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SpellNapalm extends Spell {

    private final Map<Material, Material> glazedBlocks = new HashMap<>();

    public SpellNapalm() {
        glazedBlocks.put(Material.STONE, Material.COBBLESTONE);
        glazedBlocks.put(Material.GRASS, Material.DIRT);
        glazedBlocks.put(Material.OAK_FENCE, Material.NETHER_BRICK_FENCE);
        glazedBlocks.put(Material.OAK_STAIRS, Material.NETHER_BRICK_STAIRS);
        glazedBlocks.put(Material.STONE_BRICK_STAIRS, Material.NETHER_BRICK_STAIRS);
        glazedBlocks.put(Material.SAND, Material.GLASS);
        glazedBlocks.put(Material.STONE_BRICKS, Material.NETHER_BRICK);
        glazedBlocks.put(Material.OAK_LOG, Material.NETHERRACK);
        glazedBlocks.put(Material.ACACIA_LOG, Material.NETHERRACK);
        glazedBlocks.put(Material.CLAY, Material.LIGHT_GRAY_GLAZED_TERRACOTTA);
    }

    @Override
    public void castSpell(Player player, int level) {

        final int length = (level * 10) + 5;
        final double multiplier = 0.3 * (getGame().isOvertime() && getGame().getDisaster() instanceof DisasterMeteors ? 2 : 1);
        final Vector vector = player.getLocation().getDirection().normalize().multiply(multiplier);

        final Location playerLocation = player.getLocation().add(0, 2, 0);
        final Location napalmLocation = playerLocation.clone().add(playerLocation.getDirection().normalize().multiply(2));

        Validate.notNull(napalmLocation.getWorld());

        new BukkitRunnable() {

            final List<Block> litOnFire = new ArrayList<>();
            final Map<Block, Double> tempIgnore = new HashMap<>();

            double travelled, size = 1, lastTick;

            @Override
            public void run() {

                napalmLocation.add(vector);

                for (int b = 0; b < size * 20; b++) {

                    float x = ThreadLocalRandom.current().nextFloat();
                    float y = ThreadLocalRandom.current().nextFloat();
                    float z = ThreadLocalRandom.current().nextFloat();

                    while (Math.sqrt((x * x) + (y * y) + (z * z)) >= 1) {
                        x = ThreadLocalRandom.current().nextFloat();
                        y = ThreadLocalRandom.current().nextFloat();
                        z = ThreadLocalRandom.current().nextFloat();
                    }

                    napalmLocation.getWorld().spawnParticle (

                            Particle.REDSTONE,

                            napalmLocation.clone().add (
                                    (size * (x - 0.5)) / 5,
                                    (size * (y - 0.5)) / 5,
                                    (size * (z - 0.5)) / 5
                            ),

                            0,

                            -0.3F,
                            0.35F + (ThreadLocalRandom.current().nextFloat() / 8),
                            0.3F,

                            new Particle.DustOptions(Color.ORANGE, 1)
                    );
                }

                if (lastTick % 3 == 0) {

                    Objects.requireNonNull(napalmLocation.getWorld()).getEntities().forEach(entity -> {

                        if (entity instanceof Player && getWizard((Player) entity) == null)
                            return;

                        double heat = (size * 1.1) - entity.getLocation().distance(napalmLocation);

                        if (heat <= 0)
                            return;

                        if (lastTick % 10 == 0 && heat > 0.2) {

                            if (entity instanceof LivingEntity) {

                                CustomDamageTick damageTick = new CustomDamageTick (
                                        heat / 1.5,
                                        EntityDamageEvent.DamageCause.FIRE,
                                        getSpell().getSpellName(),
                                        Instant.now(),
                                        player
                                );

                                damage((LivingEntity) entity, damageTick);

                            } else {
                                entity.remove();
                                return;
                            }
                        }

                        if (entity instanceof LivingEntity && entity.getFireTicks() < heat * 40) {

                            LivingEntity livingEntity = (LivingEntity) entity;

                            if (livingEntity instanceof Player)
                                if (getGame().getWizard((Player) livingEntity) == null)
                                    return;

                            entity.setFireTicks((int) (heat * 40));
                        }
                    });

                    int newSize = (int) Math.ceil(size * 0.75);

                    for (int y = -newSize; y <= newSize; y++) {

                        if (napalmLocation.getBlockY() + y < 256 && napalmLocation.getBlockY() + y > 0) {

                            for (int x = -newSize; x <= newSize; x++) {
                                for (int z = -newSize; z <= newSize; z++) {

                                    Block block = napalmLocation.clone().add(x, y, z).getBlock();

                                    if (litOnFire.contains(block))
                                        continue;

                                    if (playerLocation.distance(block.getLocation().add(0.5, 0.5, 0.5)) < 2.5)
                                        continue;

                                    double newHeat = newSize - napalmLocation.distance(block.getLocation().add(0.5, 0.5, 0.5));

                                    if (tempIgnore.containsKey(block)) {
                                        if (tempIgnore.remove(block) > newHeat) {
                                            litOnFire.add(block);
                                            continue;
                                        }
                                    }

                                    if (newHeat <= 0)
                                        continue;

                                    if (block.getType() != Material.AIR) {

                                        block.getType().getHardness();
                                        float strength = block.getType().getHardness();

                                        if (strength <= newHeat) {
                                            block.setType(Material.AIR);
                                            block.getWorld().playSound(block.getLocation(), Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1.3F, 0.6F + ((ThreadLocalRandom.current().nextFloat() - 0.5F) / 3F));

                                        } else if (0.2 <= newHeat) {

                                            if (glazedBlocks.containsKey(block.getType())) {
                                                block.setType(glazedBlocks.get(block.getType()));
                                                block.getWorld().playSound(block.getLocation(), Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1.3F, 0.6F + ((ThreadLocalRandom.current().nextFloat() - 0.5F) / 3F));
                                            }

                                        } else if (strength * 2 > size) {

                                            tempIgnore.put(block, newHeat);
                                            continue;
                                        }
                                    }

                                    if (block.getType() == Material.WATER) {

                                        if (newHeat > 1) {
                                            block.setType(Material.AIR);

                                            block.getWorld().playSound(block.getLocation(), Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1.3F, 0);
                                            litOnFire.add(block);
                                        }
                                    } else if (block.getType() == Material.AIR) {

                                        if (!ThreadLocalRandom.current().nextBoolean())
                                            return;

                                        for (int a = 0; a < 6; a++) {

                                            Block relative = block.getRelative(BlockFace.values()[a]);

                                            if (relative.getType() != Material.AIR) {

                                                block.setType(Material.FIRE);
                                                block.getWorld().playSound(block.getLocation(), Sound.BLOCK_WOOL_BREAK, 1.3F, 0.6F + ((ThreadLocalRandom.current().nextFloat() - 0.5F) / 3F));

                                                break;
                                            }
                                        }

                                        litOnFire.add(block);
                                    }
                                }
                            }
                        }
                    }

                    size = Math.min(8, size + 0.06);
                }

                travelled += multiplier;

                if (lastTick++ % 8 == 0)
                    napalmLocation.getWorld().playSound(napalmLocation, Sound.ENTITY_CAT_HISS, Math.min(0.8F + (float) (size * 0.09F), 1.8F), 0F);

                if (travelled >= length) {
                    createFire(napalmLocation);
                    cancel();
                }
            }
        }.runTaskTimer(getGame().getPlugin(), 0L, 1L);
    }

    @Override
    public void cleanup() {
        glazedBlocks.clear();
    }

    private void createFire(final Location location) {

        Validate.notNull(location.getWorld());

        location.getWorld().spawnParticle (

                Particle.LAVA, location,

                30,
                0.3, 0, 0.3,
                0
        );

        final Map<Block, Double> blocks = BlockUtil.getInRadius(location, 3.5, false);

        blocks.keySet().forEach(block -> {
            if (!block.getRelative(BlockFace.DOWN).getType().isSolid())
                Bukkit.getScheduler().scheduleSyncDelayedTask(getGame().getPlugin(), () -> block.setType(Material.FIRE), 60 - (int) (60 * blocks.get(block)));
        });
    }
}