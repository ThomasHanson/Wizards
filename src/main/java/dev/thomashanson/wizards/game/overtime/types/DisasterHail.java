package dev.thomashanson.wizards.game.overtime.types;

import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.overtime.Disaster;
import dev.thomashanson.wizards.game.spell.SpellType;
import dev.thomashanson.wizards.util.BlockUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DisasterHail extends Disaster {

    public DisasterHail(Wizards game) {

        super (
                game,

                "Hail",

                Stream.of (

                        SpellType.ICE_SHARDS,
                        SpellType.ICE_PRISON,
                        SpellType.FROST_BARRIER,
                        SpellType.SPECTRAL_ARROW

                ).collect(Collectors.toSet()),

                Arrays.asList (
                        "Message 1",
                        "Message 2"
                )
        );
    }

    @Override
    public void strike() {

        Location location = getNextLocation();

        if (location == null)
            return;

        Vector vector = new Vector (
                ThreadLocalRandom.current().nextDouble() - 0.5,
                0.8,
                ThreadLocalRandom.current().nextDouble() - 0.5
        ).normalize();

        vector.multiply(40);

        location.add (
                (ThreadLocalRandom.current().nextDouble() - 0.5) * 7,
                0,
                (ThreadLocalRandom.current().nextDouble() - 0.5) * 7
        );

        location.add(vector);

        final FallingBlock fallingBlock = Objects.requireNonNull(location.getWorld())
                .spawnFallingBlock(location, Bukkit.createBlockData(Material.PACKED_ICE));

        fallingBlock.setMetadata("Hail", new FixedMetadataValue(getGame().getPlugin(), 20F));

        new BukkitRunnable() {

            int i;

            public void run() {

                if (fallingBlock.isValid()) {

                    location.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, fallingBlock.getLocation(), 3, 0.3, 0.3, 0.3);

                    if (i++ % 6 == 0)
                        fallingBlock.getWorld().playSound(fallingBlock.getLocation(), Sound.ENTITY_CAT_HISS, 1.3F, 0F);

                } else {
                    cancel();
                }
            }

        }.runTaskTimer(getGame().getPlugin(), 0L, 0L);

        vector.normalize().multiply(-(0.04 + ((0.5 - 0.05) / 2)));
        fallingBlock.setFireTicks(Integer.MAX_VALUE);
    }

    @EventHandler
    public void onHailHit(EntityChangeBlockEvent event) {

        Entity projectile = event.getEntity();

        float size = 2.5F;
        double damage = 2.5D;

        for(int i = 1; i <= 10; i++) {

            if (Duration.between(getLastStrike(), Instant.now()).toSeconds() >= (((30 * i) + 180) * 1000)) {
                size = 2.5F * i;
                damage = 2.5D * i;
            }
        }

        if (projectile.hasMetadata("Hail")) {

            //CustomExplosion explosion = new CustomExplosion(getArcadeManager().GetDamage(), getArcadeManager().GetExplosion(), projectile.getLocation(), size, "Meteor");
            //explosion.setBlockExplosionSize(size);
            //explosion.setFallingBlockExplosion(false);
            //explosion.setDropItems(false);
            //explosion.setBlocksDamagedEqually(true);

            Objects.requireNonNull(projectile.getLocation().getWorld())
                    .spawnParticle(Particle.EXPLOSION_LARGE, projectile.getLocation(), 3, 1.0, 1.0, 1.0);

            for(Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(projectile.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1F, 1F);
            }

            boolean fall = true;

            for (Entity entity : projectile.getNearbyEntities(size, size, size)) {

                if (!(entity instanceof Player))
                    continue;

                Player player = (Player) entity;

                CustomDamageTick customTick = new CustomDamageTick (
                        damage,
                        EntityDamageEvent.DamageCause.CUSTOM,
                        "Hail Damage",
                        Instant.now(),
                        null
                );

                getGame().getPlugin().getDamageManager().damage(player, customTick);
            }

            for (Block block : BlockUtil.getInRadius(event.getEntity().getLocation(), size, true).keySet()) {

                if (block.getType() == Material.AIR)
                    continue;

                block.setType(Material.PACKED_ICE);

                if (block.getRelative(BlockFace.DOWN).getType() != Material.AIR)
                    continue;

                if (fall)
                    block.getWorld().spawnFallingBlock(block.getLocation(), Bukkit.createBlockData(block.getType()));

                fall = !fall;
                block.setType(Material.AIR);
            }
        }
    }

    @Override
    public void update() {

    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {

        Block block = event.getBlock().getRelative(BlockFace.DOWN);
        Material material = block.getType();

        if (material == Material.ICE || material == Material.PACKED_ICE) {
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot place blocks on ice during overtime!");
            event.setCancelled(true);
        }
    }

    private boolean isOnIce(Player player) {

        Block block = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
        Material material = block.getType();

        return material == Material.ICE || material == Material.PACKED_ICE;
    }
}