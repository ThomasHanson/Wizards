package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.projectile.CustomProjectile;
import dev.thomashanson.wizards.projectile.ProjectileData;
import dev.thomashanson.wizards.util.BlockUtil;
import dev.thomashanson.wizards.util.EntityUtil;
import dev.thomashanson.wizards.util.MathUtil;
import dev.thomashanson.wizards.util.npc.NPC;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Snow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class SpellFrostbite extends Spell implements CustomProjectile {

    private BukkitTask updateTask;
    private final Map<Block, Instant> snowBlocks = new HashMap<>();

    @Override
    public void castSpell(Player player, int level) {

        if (updateTask == null)
            startUpdates();

        ArmorStand stand = EntityUtil.makeProjectile(player.getEyeLocation(), Material.SNOW_BLOCK);
        stand.setMetadata("SL", new FixedMetadataValue(getGame().getPlugin(), getSpellLevel(player)));

        Vector vector = player.getLocation().getDirection();

        vector.normalize();
        vector.multiply(1.7);
        vector.setY(Math.min(vector.getY() + 0.2, 10));

        stand.setVelocity(vector);

        getGame().getPlugin().getProjectileManager().addThrow (

                stand,

                new ProjectileData (

                        getGame(),

                        stand, player, this,
                        false, true,

                        Particle.SNOW_SHOVEL,
                        Sound.BLOCK_SNOW_HIT, 1F, 1F
                )
        );
    }

    @Override
    public void cleanup() {
        updateTask.cancel();
        snowBlocks.clear();
    }

    @Override
    public void onCollide(LivingEntity hitEntity, NPC hitNPC, Block hitBlock, ProjectileData data) {

        int spellLevel = data.getEntity().getMetadata("SL").get(0).asInt();

        if (hitBlock != null) {

            Map<Block, Double> inRadius = BlockUtil.getInRadius(hitBlock.getLocation(), spellLevel * 2, true);

            for (Block block : inRadius.keySet()) {

                snowBlocks.put(block, Instant.now().plusSeconds(((15 + ThreadLocalRandom.current().nextInt(6)))));

                if (block.getRelative(BlockFace.DOWN).getType() == Material.AIR) {
                    block.setType(Material.SNOW_BLOCK);

                } else {

                    if (block.getType().isSolid())
                        continue;

                    block.setType(Material.SNOW);

                    Snow snow = (Snow) block.getBlockData();
                    snow.setLayers(ThreadLocalRandom.current().nextInt(snow.getMinimumLayers(), snow.getMaximumLayers()));
                    block.setBlockData(snow);
                }
            }
        }
    }

    private void startUpdates() {

        this.updateTask = new BukkitRunnable() {

            @Override
            public void run() {

                Iterator<Map.Entry<Block, Instant>> iterator = snowBlocks.entrySet().iterator();

                while (iterator.hasNext()) {

                    Map.Entry<Block, Instant> entry = iterator.next();

                    if (entry.getValue().isBefore(Instant.now())) {

                        iterator.remove();
                        entry.getKey().setType(Material.AIR);
                    }
                }
            }

        }.runTaskTimer(getGame().getPlugin(), 0L, 1L);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {

        Player player = event.getPlayer();

        if (event.getTo() == null)
            return;

        Block blockTo = event.getTo().getBlock();

        if (blockTo.getType() != Material.SNOW) {

            player.removePotionEffect(PotionEffectType.SLOW);
            player.removePotionEffect(PotionEffectType.JUMP);

            return;
        }

        for (Block block : snowBlocks.keySet()) {

            if (MathUtil.getOffset2D(block.getLocation(), blockTo.getLocation()) <= 0) {

                player.addPotionEffect(
                        new PotionEffect(
                                PotionEffectType.SLOW,
                                (int) (Duration.between(Instant.now(), snowBlocks.get(block)).toSeconds() * 20), 1
                        )
                );

                player.addPotionEffect(
                        new PotionEffect(
                                PotionEffectType.JUMP,
                                (int) (Duration.between(Instant.now(), snowBlocks.get(block)).toSeconds() * 20), 250
                        )
                );
            }
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {

        if (snowBlocks.containsKey(event.getBlock()))
            event.setCancelled(true);
    }
}