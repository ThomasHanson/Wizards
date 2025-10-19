package dev.thomashanson.wizards.game.overtime.types; // Assumed package

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.overtime.Disaster;

public class DisasterEarthquake extends Disaster {

    // --- Configuration for Hail Timing ---
    private static final long INITIAL_STRIKE_INTERVAL_MS = 9000;
    private static final long MINIMUM_STRIKE_INTERVAL_MS = 750;
    private static final long STRIKE_INTERVAL_REDUCTION_PER_TICK_MS = 2;

    private static final double SHAKE_RADIUS = 8.0;
    private static final double DAMAGE_RADIUS = 6.0;
    private static final double PLAYER_DAMAGE_RADIUS = 7.0;
    private static final int BLOCKS_TO_AFFECT = 25;
    private static final int BLOCKS_TO_FALL = 8;
    private static final double DAMAGE_AMOUNT = 8.0; // Example damage

    public DisasterEarthquake(Wizards game) {
        super(game,
            "wizards.disaster.earthquake.name",
            Collections.emptySet(),
            Arrays.asList(
                "wizards.disaster.earthquake.announce.1",
                "wizards.disaster.earthquake.announce.2",
                "wizards.disaster.earthquake.announce.3",
                "wizards.disaster.final_announce"
            )
        );
    }

    @Override
    protected long getInitialStrikeIntervalMs() {
        return INITIAL_STRIKE_INTERVAL_MS;
    }

    @Override
    protected long getMinimumStrikeIntervalMs() {
        return MINIMUM_STRIKE_INTERVAL_MS;
    }

    @Override
    protected long getStrikeIntervalReductionPerTickMs() {
        return STRIKE_INTERVAL_REDUCTION_PER_TICK_MS;
    }

    @Override
    protected void strikeAt(Location strikeLocation) {
        if (strikeLocation == null || strikeLocation.getWorld() == null) {
            return;
        }

        World world = strikeLocation.getWorld();
        Random random = ThreadLocalRandom.current();

        // --- Visuals and Sounds for the initial strike ---
        world.playSound(strikeLocation, Sound.ENTITY_WARDEN_SONIC_BOOM, 2F, 0.5F);
        world.playSound(strikeLocation, Sound.ENTITY_GENERIC_EXPLODE, 1.5F, 0.7F);
        strikeLocation.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, strikeLocation.clone().add(0,1,0), 10, 2, 2, 2, 0.1);


        // --- Block Damage and Falling Blocks ---
        List<Block> affectedBlocks = new ArrayList<>();
        for (int i = 0; i < BLOCKS_TO_AFFECT + BLOCKS_TO_FALL; i++) {
            int xOffset = random.nextInt((int) (SHAKE_RADIUS * 2)) - (int) SHAKE_RADIUS;
            int zOffset = random.nextInt((int) (SHAKE_RADIUS * 2)) - (int) SHAKE_RADIUS;
            Location blockLoc = strikeLocation.clone().add(xOffset, 0, zOffset);
            
            // Find the actual surface block
            Block surfaceBlock = world.getHighestBlockAt(blockLoc);
            if (surfaceBlock.getY() < getGame().getInitialMapMinY()) continue; // Don't affect blocks below map base

            if (!surfaceBlock.isEmpty() && surfaceBlock.getType() != Material.BEDROCK && surfaceBlock.getType() != Material.AIR) {
                // Check if block is within current game bounds
                if (surfaceBlock.getX() >= getGame().getCurrentMinX() && surfaceBlock.getX() < getGame().getCurrentMaxX() &&
                    surfaceBlock.getZ() >= getGame().getCurrentMinZ() && surfaceBlock.getZ() < getGame().getCurrentMaxZ()) {
                    affectedBlocks.add(surfaceBlock);
                }
            }
        }

        Collections.shuffle(affectedBlocks);
        int fallCounter = 0;

        for (Block block : affectedBlocks) {
            Location blockCenter = block.getLocation().add(0.5, 0.5, 0.5); // Center for particles

            // Visuals for shaking ground
            BlockData blockData = block.getBlockData();
            world.spawnParticle(Particle.BLOCK_DUST, blockCenter, 20, 0.5, 0.5, 0.5, blockData);
            world.playSound(block.getLocation(), Sound.BLOCK_STONE_BREAK, 1F, 0.8F + random.nextFloat() * 0.4F);


            if (fallCounter < BLOCKS_TO_FALL && block.getY() > world.getMinHeight() + 1 && block.getType().isSolid()) {
                // Make some blocks fall into the void
                BlockData currentBlockData = block.getBlockData();
                block.setType(Material.AIR, true); // Remove block
                FallingBlock fallingBlock = world.spawnFallingBlock(block.getLocation().add(0.5,0,0.5), currentBlockData);
                fallingBlock.setDropItem(false); // Don't drop item
                fallingBlock.setHurtEntities(true); // Can hurt entities it lands on
                // Give it a slight random velocity to make it look more chaotic
                fallingBlock.setVelocity(new Vector((random.nextDouble() - 0.5) * 0.3, -0.3 - random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 0.3));
                fallCounter++;
            } else if (block.getType().isSolid() && block.getType() != Material.BEDROCK) {
                // Damage other blocks (e.g., turn to cracked variants or cobble)
                Material type = block.getType();
                if (type == Material.STONE) block.setType(Material.COBBLESTONE);
                else if (type == Material.STONE_BRICKS) block.setType(Material.CRACKED_STONE_BRICKS);
                else if (type == Material.DEEPSLATE_BRICKS) block.setType(Material.CRACKED_DEEPSLATE_BRICKS);
                else if (type.getHardness() < 2.0f && type.getHardness() > 0.1f) { // Example: damage "softer" blocks more easily
                    block.breakNaturally(); // Break less sturdy blocks
                }
            }
        }

        // --- Player Damage ---
        // A slight delay for players to react to the initial ground shake
        new BukkitRunnable() {
            @Override
            public void run() {
                if (strikeLocation.getWorld() == null) return; // World might have changed
                
                strikeLocation.getWorld().getNearbyEntities(strikeLocation, PLAYER_DAMAGE_RADIUS, PLAYER_DAMAGE_RADIUS, PLAYER_DAMAGE_RADIUS).forEach(entity -> {
                    if (entity instanceof Player && getGame().getPlayers(true).contains((Player) entity)) {
                        Player player = (Player) entity;
                        // Ensure player is within the current game bounds
                        if (player.getLocation().getX() >= getGame().getCurrentMinX() && player.getLocation().getX() < getGame().getCurrentMaxX() &&
                            player.getLocation().getZ() >= getGame().getCurrentMinZ() && player.getLocation().getZ() < getGame().getCurrentMaxZ()) {
                            
                            // Check if player is on "shaking" ground (close to the epicenter or affected blocks)
                            if (player.getLocation().distanceSquared(strikeLocation) <= PLAYER_DAMAGE_RADIUS * PLAYER_DAMAGE_RADIUS) {
                                // getGame().getPlugin().getDamageManager().damage(player, null, DAMAGE_AMOUNT, DamageCause.C, getName());
                                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1F, 0.7F);
                                player.spawnParticle(Particle.CRIT, player.getLocation().add(0,1,0), 15, 0.3,0.3,0.3, 0.1);
                            }
                        }
                    }
                });
            }
        }.runTaskLater(getGame().getPlugin(), 10L); // 0.5 second delay
    }
}