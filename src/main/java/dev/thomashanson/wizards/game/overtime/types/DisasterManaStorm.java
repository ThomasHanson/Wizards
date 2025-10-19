package dev.thomashanson.wizards.game.overtime.types; // Assumed package

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.overtime.Disaster;

public class DisasterManaStorm extends Disaster {

    // --- Configuration for Hail Timing ---
    private static final long INITIAL_STRIKE_INTERVAL_MS = 9000;
    private static final long MINIMUM_STRIKE_INTERVAL_MS = 750;
    private static final long STRIKE_INTERVAL_REDUCTION_PER_TICK_MS = 2;

    private static final double IMPACT_RADIUS = 6.0;
    private static final double MANA_REFILL_RADIUS = 5.0;
    private static final float EXPLOSION_POWER = 0.8F; // Very small explosion
    private static final double MANA_REFILL_AMOUNT = 50.0; // Example mana amount
    private static final int MANA_BLOBS_PER_STRIKE = 2;

    public DisasterManaStorm(Wizards game) {
        super(game,
            "wizards.disaster.mana_storm.name",
            Collections.emptySet(),
            Arrays.asList(
                "wizards.disaster.mana_storm.announce.1",
                "wizards.disaster.mana_storm.announce.2",
                "wizards.disaster.mana_storm.announce.3",
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

        for (int i = 0; i < MANA_BLOBS_PER_STRIKE; i++) {
             Location individualImpactLoc = strikeLocation.clone().add(
                (ThreadLocalRandom.current().nextDouble() - 0.5) * IMPACT_RADIUS * 0.7,
                0,
                (ThreadLocalRandom.current().nextDouble() - 0.5) * IMPACT_RADIUS * 0.7
            );
            // Ensure it's within game bounds
            individualImpactLoc.setX(Math.max(getGame().getCurrentMinX() + 0.5, Math.min(individualImpactLoc.getX(), getGame().getCurrentMaxX() - 0.5)));
            individualImpactLoc.setZ(Math.max(getGame().getCurrentMinZ() + 0.5, Math.min(individualImpactLoc.getZ(), getGame().getCurrentMaxZ() - 0.5)));

            spawnManaBlob(individualImpactLoc);
        }
    }

    private void spawnManaBlob(Location impactLocation) {
        World world = impactLocation.getWorld();
        if (world == null) return;

        Location spawnHeightLocation = impactLocation.clone().add(0, 18 + ThreadLocalRandom.current().nextInt(12), 0);
        
        // We won't use a real falling block for mana, just particles simulating its descent
        world.playSound(spawnHeightLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8F, 1.5F);

        new BukkitRunnable() {
            Location currentParticleLoc = spawnHeightLocation.clone();
            Vector direction = impactLocation.clone().subtract(spawnHeightLocation).toVector().normalize().multiply(0.8); // Speed of descent
            int ticksLived = 0;

            @Override
            public void run() {
                if (ticksLived > 120 || currentParticleLoc.distanceSquared(impactLocation) < 2.0*2.0 ) { // Max 6 seconds or close to target
                    this.cancel();
                    handleManaImpact(impactLocation); // Use the intended impact location
                    return;
                }

                // Trail particles
                world.spawnParticle(Particle.SPELL_WITCH, currentParticleLoc, 10, 0.3, 0.3, 0.3, 0.05);
                world.spawnParticle(Particle.ENCHANTMENT_TABLE, currentParticleLoc, 5, 0.4, 0.4, 0.4, 0.1);
                
                currentParticleLoc.add(direction);
                ticksLived++;
            }
        }.runTaskTimer(getGame().getPlugin(), 0L, 1L);
    }


    private void handleManaImpact(Location impactLocation) {
        World world = impactLocation.getWorld();
        if (world == null) return;

        // Ensure impact is within current game bounds
        if (!(impactLocation.getX() >= getGame().getCurrentMinX() && impactLocation.getX() < getGame().getCurrentMaxX() &&
              impactLocation.getZ() >= getGame().getCurrentMinZ() && impactLocation.getZ() < getGame().getCurrentMaxZ())) {
            return; // Impact out of bounds
        }

        // --- Visuals and Sound for Impact ---
        world.playSound(impactLocation, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5F, 1.2F);
        world.playSound(impactLocation, Sound.ENTITY_PLAYER_LEVELUP, 1F, 0.8F); // Sound for mana gain
        world.spawnParticle(Particle.DRAGON_BREATH, impactLocation, 100, 1.5, 0.5, 1.5, 0.05);
        world.spawnParticle(Particle.PORTAL, impactLocation, 80, 2, 1, 2, 0.2);

        // --- Minor Block Destruction ---
        // world.createExplosion(impactLocation, EXPLOSION_POWER, false, false); // No fire, minimal block breaking
        // Manual, gentler destruction:
        for (int dx = -(int)(EXPLOSION_POWER+1); dx <= (int)(EXPLOSION_POWER+1); dx++) {
            for (int dy = -(int)(EXPLOSION_POWER+1); dy <= (int)(EXPLOSION_POWER+1); dy++) {
                for (int dz = -(int)(EXPLOSION_POWER+1); dz <= (int)(EXPLOSION_POWER+1); dz++) {
                    if (dx*dx + dy*dy + dz*dz > (EXPLOSION_POWER+1)*(EXPLOSION_POWER+1)) continue;
                    Block block = impactLocation.clone().add(dx, dy, dz).getBlock();
                     if (block.getType().getHardness() < 1.0f && block.getType().isSolid() && block.getType() != Material.BEDROCK) { // Only very weak blocks
                        if (ThreadLocalRandom.current().nextFloat() < 0.3) { // 30% chance to break
                           block.breakNaturally();
                        }
                    }
                }
            }
        }


        // --- Refill Mana for Nearby Wizards ---
        world.getNearbyEntities(impactLocation, MANA_REFILL_RADIUS, MANA_REFILL_RADIUS, MANA_REFILL_RADIUS).forEach(entity -> {
            if (entity instanceof Player && getGame().getPlayers(true).contains(entity)) {
                Player player = (Player) entity;

                // Ensure player is within current game bounds
                if (!(player.getLocation().getX() >= getGame().getCurrentMinX() && player.getLocation().getX() < getGame().getCurrentMaxX() &&
                      player.getLocation().getZ() >= getGame().getCurrentMinZ() && player.getLocation().getZ() < getGame().getCurrentMaxZ())) {
                   return;
               }
                
                // Placeholder for your mana system:
                // getGame().getManaManager().addMana(player, MANA_REFILL_AMOUNT);
                // Example: player.sendMessage(ChatColor.AQUA + "You feel a surge of mana! (+" + MANA_REFILL_AMOUNT + ")");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1F, 1.5F);
                player.spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation().add(0,1,0), 10, 0.5,0.5,0.5,0.1);
                
                // For demonstration, if you don't have a mana system yet, you could give saturation or absorption
                // player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 100, 0)); 
            }
        });
    }
}