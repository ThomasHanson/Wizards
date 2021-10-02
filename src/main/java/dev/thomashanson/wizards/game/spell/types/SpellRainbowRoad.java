package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.util.BlockUtil;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SpellRainbowRoad extends Spell {

    private BukkitTask updateTask;

    private final Map<UUID, Map<Block, Instant>> blocks = new HashMap<>();

    private final List<Material> rainbowMaterials = Arrays.asList (
            Material.WHITE_STAINED_GLASS,
            Material.ORANGE_STAINED_GLASS,
            Material.MAGENTA_STAINED_GLASS,
            Material.LIGHT_BLUE_STAINED_GLASS,
            Material.YELLOW_STAINED_GLASS,
            Material.LIME_STAINED_GLASS,
            Material.PINK_STAINED_GLASS,
            Material.GRAY_STAINED_GLASS,
            Material.LIGHT_GRAY_STAINED_GLASS,
            Material.CYAN_STAINED_GLASS,
            Material.PURPLE_STAINED_GLASS,
            Material.BLUE_STAINED_GLASS,
            Material.BROWN_STAINED_GLASS,
            Material.GREEN_STAINED_GLASS,
            Material.RED_STAINED_GLASS
    );

    @Override
    public void castSpell(Player player, int level) {

        if (updateTask == null)
            startUpdates();

        if (player.getVelocity().getY() < 0) {

            player.setVelocity(new Vector(0, 0, 0));

            if (!((LivingEntity) player).isOnGround())
                player.teleport(player.getLocation().add(0, 0.75, 0));
        }

        final BlockFace facing = BlockUtil.getFace(player.getEyeLocation().getYaw());
        double yMod = Math.min(Math.max(player.getLocation().getPitch() / 30, -1), 1);

        final Vector vector = new Vector(facing.getModX(), -yMod, facing.getModZ());
        final Location location = player.getLocation().getBlock().getLocation().add(0.5, -0.5, 0.5);
        final int maxDistance = 3 + (10 * level);

        buildRoad(player, location, facing, 0);

        new BukkitRunnable() {

            int blocks, colorProgress;

            @Override
            public void run() {

                if (!getGame().isLive() || blocks++ >= maxDistance) {
                    cancel();
                    return;
                }

                colorProgress = buildRoad(player, location, facing, colorProgress);
                location.add(vector);
            }

        }.runTaskTimer(getGame().getPlugin(), 5L, 5L);
    }

    @Override
    public void cleanup() {
        updateTask.cancel();
        blocks.clear();
    }

    private void startUpdates() {

        this.updateTask = new BukkitRunnable() {

            @Override
            public void run() {

                Collection<Map<Block, Instant>> values = blocks.values();

                for (Map<Block, Instant> instantMap : values) {

                    Iterator<Map.Entry<Block, Instant>> iterator = instantMap.entrySet().iterator();

                    while (iterator.hasNext()) {

                        Map.Entry<Block, Instant> entry = iterator.next();

                        if (entry.getValue().isBefore(Instant.now())) {

                            iterator.remove();

                            if (rainbowMaterials.contains(entry.getKey().getType()))
                                entry.getKey().setType(Material.AIR);
                        }
                    }
                }
            }

        }.runTaskTimer(getGame().getPlugin(), 0L, 1L);
    }

    private int buildRoad(Player player, Location location, BlockFace facing, int colorProgress) {

        Block block = location.getBlock();
        BlockFace[] faces = BlockUtil.getSideBlockFaces(facing);

        List<Block> blockList = new ArrayList<>();
        blockList.add(block);

        for (int i = 0; i < 2; i++)
            blockList.add(block.getRelative(faces[i]));

        blockList.addAll(BlockUtil.getDiagonalBlocks(block, facing, 1));

        boolean playSound = false;

        for (Block b : blockList) {

            if (!getGame().isInsideMap(b.getLocation()))
                continue;

            if (!blockList.contains(block) && block.getType().isSolid())
                continue;

            b.setType(rainbowMaterials.get(colorProgress++ % rainbowMaterials.size()));
            b.getWorld().playEffect(b.getLocation(), Effect.STEP_SOUND, Material.WHITE_WOOL);

            if (!blocks.containsKey(player.getUniqueId()))
                blocks.put(player.getUniqueId(), new HashMap<>());

            Map<Block, Instant> road = blocks.get(player.getUniqueId());
            road.put(b, Instant.now().plusSeconds(((14 + ThreadLocalRandom.current().nextInt(7)))));

            playSound = true;
        }

        if (playSound)
            block.getWorld().playSound(block.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.5F, 1F);

        return colorProgress;
    }

    @EventHandler
    public void onFall(EntityDamageEvent event) {

        if (!blocks.containsKey(event.getEntity().getUniqueId()))
            return;

        if (event.getCause() != EntityDamageEvent.DamageCause.FALL)
            return;

        Player player = (Player) event.getEntity();

        Block block = player.getLocation().subtract(0, 1, 0).getBlock();
        Map<Block, Instant> road = blocks.get(player.getUniqueId());

        if (road == null || !road.containsKey(block))
            return;

        event.setCancelled(true);
        blocks.remove(player.getUniqueId());
    }
}