package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Snow;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;
import dev.thomashanson.wizards.projectile.CustomProjectile;
import dev.thomashanson.wizards.projectile.ProjectileData;

public class SpellFrostbite extends Spell implements CustomProjectile, Tickable {

    private final List<FrostbiteInstance> activeInstances = new ArrayList<>();
    private final Map<Block, FrostbiteInstance> snowBlocks = new ConcurrentHashMap<>();

    public SpellFrostbite(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        StatContext context = StatContext.of(level);
        ProjectileData.Builder dataBuilder = new ProjectileData.Builder(getGame().orElse(null), player, this)
                .hitPlayer(true).hitBlock(true)
                .trailParticle(Particle.SNOWFLAKE)
                .impactSound(Sound.BLOCK_SNOW_HIT, 1.5F, 0.8F)
                .maxTicksLived((int) getStat("projectile-lifespan-ticks", level))
                .customData("level", level);

        Vector velocity = player.getEyeLocation().getDirection().multiply(getStat("projectile-speed", level));
        plugin.getProjectileManager().launchProjectile(player.getEyeLocation(), new ItemStack(Material.SNOWBALL), velocity, dataBuilder);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1.2F, 1.0F);
        return true;
    }

    @Override
    public void onCollide(LivingEntity hitEntity, Block hitBlock, ProjectileData data) {
        Location impactLocation = (hitEntity != null) ? hitEntity.getLocation() : hitBlock.getLocation();
        Integer level = data.getCustomData("level", Integer.class);
        if (level == null) return;

        activeInstances.add(new FrostbiteInstance(this, impactLocation, level));
    }

    @Override
    public void tick(long gameTick) {
        if (activeInstances.isEmpty()) return;
        activeInstances.removeIf(instance -> instance.tick(gameTick));
    }

    @Override
    public void cleanup() {
        activeInstances.forEach(FrostbiteInstance::cleanup);
        activeInstances.clear();
        snowBlocks.clear();
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (snowBlocks.containsKey(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockFade(BlockFadeEvent event) {
        if (snowBlocks.containsKey(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    private static class FrostbiteInstance {
        enum Phase { SPREADING, ACTIVE, DONE }

        final SpellFrostbite parent;
        final Location center;
        final int level;
        final List<Block> potentialBlocks = new ArrayList<>();

        // Configurable stats
        final int radius;
        final int spreadDurationTicks;
        final int blocksPerSpreadTick;
        final PotionEffect slowEffect;
        final PotionEffect noJumpEffect;

        private Phase phase = Phase.SPREADING;
        private int ticksLived = 0;

        FrostbiteInstance(SpellFrostbite parent, Location center, int level) {
            this.parent = parent;
            this.center = center;
            this.level = level;

            StatContext context = StatContext.of(level);
            this.radius = (int) parent.getStat("radius", level);
            this.spreadDurationTicks = (int) parent.getStat("spread-duration-ticks", level);

            int slowAmplifier = (int) parent.getStat("slowness-amplifier", level) - 1;
            int effectDuration = (int) parent.getStat("effect-duration-ticks", level);
            this.slowEffect = new PotionEffect(PotionEffectType.SLOW, effectDuration, slowAmplifier, true, false, true);
            this.noJumpEffect = new PotionEffect(PotionEffectType.JUMP, effectDuration, 128, true, false, true);

            findPotentialBlocks();
            this.blocksPerSpreadTick = (int) Math.max(1, (float) potentialBlocks.size() / spreadDurationTicks);
        }

        boolean tick(long gameTick) {
            ticksLived++;

            if (phase == Phase.SPREADING) {
                tickSpreading();
            }

            if (phase == Phase.ACTIVE) {
                if (gameTick % 10 == 0) { // Apply effects twice per second
                    tickPlayerEffects();
                }
                tickMelting();
            }

            // If spreading is done and no blocks remain, mark for removal
            if (phase == Phase.ACTIVE && parent.snowBlocks.values().stream().noneMatch(this::equals)) {
                phase = Phase.DONE;
            }
            
            return phase == Phase.DONE;
        }

        void tickSpreading() {
            for (int i = 0; i < blocksPerSpreadTick && !potentialBlocks.isEmpty(); i++) {
                placeSnow(potentialBlocks.remove(0));
            }

            if (potentialBlocks.isEmpty()) {
                phase = Phase.ACTIVE;
            }
        }

        void tickPlayerEffects() {
            center.getWorld().getPlayers().stream()
                    .filter(p -> p.getLocation().distanceSquared(center) < (radius + 2) * (radius + 2))
                    .filter(p -> parent.snowBlocks.containsKey(p.getLocation().getBlock().getRelative(BlockFace.DOWN)))
                    .forEach(p -> {
                        p.addPotionEffect(slowEffect);
                        p.addPotionEffect(noJumpEffect);
                    });
        }

        void tickMelting() {
            parent.snowBlocks.entrySet().removeIf(entry -> {
                if (entry.getValue().equals(this) && Instant.now().isAfter(entry.getValue().getExpiry(entry.getKey()))) {
                    Block block = entry.getKey();
                    if (block.getType() == Material.SNOW) {
                        block.setType(Material.AIR);
                        block.getWorld().spawnParticle(Particle.SNOWFLAKE, block.getLocation().add(0.5, 0.2, 0.5), 5);
                    }
                    return true;
                }
                return false;
            });
        }

        void placeSnow(Block block) {
            StatContext context = StatContext.of(level);
            long baseLifespan = (long) parent.getStat("lifespan-seconds", level) * 1000L;
            long randomOffset = (long) (parent.getStat("lifespan-random-offset", level) * 1000L);

            Instant expiry = Instant.now().plusMillis(baseLifespan + ThreadLocalRandom.current().nextLong(randomOffset));

            block.setType(Material.SNOW, false);
            if (block.getBlockData() instanceof Snow snow) {
                double distanceRatio = block.getLocation().distance(center) / (double) radius;
                int layers = Math.max(1, (int) (3 - (distanceRatio * 2)));
                snow.setLayers(Math.min(snow.getMaximumLayers(), layers));
                block.setBlockData(snow, true);
            }

            parent.snowBlocks.put(block, this);
            setExpiry(block, expiry);
        }

        private final Map<Block, Instant> expiryMap = new HashMap<>();
        Instant getExpiry(Block block) { return expiryMap.get(block); }
        void setExpiry(Block block, Instant instant) { expiryMap.put(block, instant); }

        void findPotentialBlocks() {
            // Logic to find valid blocks to place snow on, similar to original but cleaner
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + z * z > radius * radius) continue;
                    Block block = center.getWorld().getHighestBlockAt(center.getBlockX() + x, center.getBlockZ() + z).getRelative(BlockFace.UP);
                    if (block.getType().isAir() && block.getRelative(BlockFace.DOWN).getType().isSolid()) {
                        potentialBlocks.add(block);
                    }
                }
            }
            potentialBlocks.sort(Comparator.comparingDouble(b -> b.getLocation().distanceSquared(center)));
        }

        void cleanup() {
            parent.snowBlocks.entrySet().removeIf(entry -> {
                if (entry.getValue().equals(this)) {
                    if (entry.getKey().getType() == Material.SNOW) {
                        entry.getKey().setType(Material.AIR);
                    }
                    return true;
                }
                return false;
            });
        }
    }
}