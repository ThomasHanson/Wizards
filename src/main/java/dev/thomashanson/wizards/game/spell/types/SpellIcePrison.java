package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;
import dev.thomashanson.wizards.projectile.CustomProjectile;
import dev.thomashanson.wizards.projectile.ProjectileData;
import dev.thomashanson.wizards.util.BlockUtil;

public class SpellIcePrison extends Spell implements CustomProjectile, Tickable {

    private static final Map<Block, Instant> PRISON_BLOCKS = new ConcurrentHashMap<>();
    private static final org.bukkit.block.data.BlockData ICE_DATA = Material.ICE.createBlockData();

    public SpellIcePrison(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        StatContext context = StatContext.of(level);
        ProjectileData.Builder dataBuilder = new ProjectileData.Builder(getGame().orElse(null), player, this)
                .hitPlayer(true).hitBlock(true)
                .impactSound(Sound.ENTITY_SILVERFISH_HURT, 2F, 1F)
                .maxTicksLived((int) getStat("projectile-lifespan-ticks", level))
                .customData("level", level);

        Vector velocity = player.getEyeLocation().getDirection().multiply(getStat("projectile-speed", level));
        plugin.getProjectileManager().launchProjectile(player.getEyeLocation(), new ItemStack(Material.ICE), velocity, dataBuilder);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.2F, 0.8F);
        return true;
    }

    @Override
    public void onCollide(LivingEntity hitEntity, Block hitBlock, ProjectileData data) {
        Location impactLocation = (hitEntity != null) ? hitEntity.getLocation() : hitBlock.getLocation();
        Integer level = data.getCustomData("level", Integer.class);
        if (level == null) return;

        double radius = getStat("radius", level);
        long durationSeconds = (long) getStat("duration", level);
        long meltOffsetMillis = (long) (getStat("melt-random-offset-seconds", level) * 1000L);

        Map<Block, Double> blocksInRadius = BlockUtil.getBlocksInRadius(impactLocation, radius);

        for (Block block : blocksInRadius.keySet()) {
            if (block.getType().isAir() || !block.getType().isSolid() || block.isLiquid()) {
                
                // --- NEW FORMATION EFFECT ---
                block.setType(Material.ICE);
                block.getWorld().spawnParticle(Particle.BLOCK_CRACK, block.getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0, ICE_DATA);
                block.getWorld().playSound(block.getLocation(), Sound.BLOCK_GLASS_PLACE, 1.0F, 1.5F);
                // --- END NEW ---

                long randomOffset = ThreadLocalRandom.current().nextLong(meltOffsetMillis);
                PRISON_BLOCKS.put(block, Instant.now().plusSeconds(durationSeconds).plusMillis(randomOffset));
            }
        }
    }

    @Override
    public void tick(long gameTick) {
        if (PRISON_BLOCKS.isEmpty()) return;

        Instant now = Instant.now();
        PRISON_BLOCKS.entrySet().removeIf(entry -> {
            if (now.isAfter(entry.getValue())) {
                Block block = entry.getKey();
                if (block.getType() == Material.ICE) {
                    block.setType(Material.AIR);
                    // --- NEW MELT EFFECT ---
                    playMeltEffect(block);
                    // --- END NEW ---
                }
                return true;
            }
            return false;
        });
    }
    
    @Override
    public void cleanup() {
        PRISON_BLOCKS.keySet().forEach(block -> {
            if (block.getType() == Material.ICE) block.setType(Material.AIR);
        });
        PRISON_BLOCKS.clear();
    }

    /**
     * NEW: Helper method to play a consistent shatter effect.
     */
    private void playMeltEffect(Block block) {
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8F, 1.2F);
        block.getWorld().spawnParticle(Particle.BLOCK_CRACK, block.getLocation().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, 0, ICE_DATA);
    }

    @EventHandler
    public void onMelt(BlockFadeEvent event) {
        if (PRISON_BLOCKS.containsKey(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (PRISON_BLOCKS.containsKey(event.getBlock())) {
            event.setDropItems(false);
            event.getBlock().setType(Material.AIR);
            
            // --- UPDATED to use helper method ---
            playMeltEffect(event.getBlock());
            
            PRISON_BLOCKS.remove(event.getBlock());
        }
    }
}