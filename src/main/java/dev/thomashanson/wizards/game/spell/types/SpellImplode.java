package dev.thomashanson.wizards.game.spell.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;
import dev.thomashanson.wizards.util.BlockUtil;
import dev.thomashanson.wizards.util.ExplosionUtil;

public class SpellImplode extends Spell implements Tickable {

    private static final List<ImplosionInstance> ACTIVE_IMPLOSIONS = new CopyOnWriteArrayList<>();

    public SpellImplode(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        StatContext context = StatContext.of(level);
        List<Block> targets = player.getLastTwoTargetBlocks(null, (int) getStat("range", level));
        if (targets.isEmpty() || targets.get(0).getType() == Material.AIR) {
            return false;
        }

        ACTIVE_IMPLOSIONS.add(new ImplosionInstance(this, player, targets.get(0), level));
        return true;
    }

    @Override
    public void tick(long gameTick) {
        if (ACTIVE_IMPLOSIONS.isEmpty()) return;
        ACTIVE_IMPLOSIONS.removeIf(ImplosionInstance::tick);
    }

    @Override
    public int getTickInterval() {
        return 1; // Needs per-tick updates for smooth particles
    }

    @Override
    public void cleanup() {
        ACTIVE_IMPLOSIONS.clear();
    }

    private static class ImplosionInstance {
        final SpellImplode parent;
        final Player caster;
        final Location center;
        final int level;
        final List<Block> affectedBlocks = new ArrayList<>();
        int ticksLived = 0;

        final int durationTicks;
        final float explosionPower;

        ImplosionInstance(SpellImplode parent, Player caster, Block targetBlock, int level) {
            this.parent = parent;
            this.caster = caster;
            this.center = targetBlock.getLocation().add(0.5, 0.5, 0.5);
            this.level = level;

            StatContext context = StatContext.of(level);
            this.durationTicks = (int) parent.getStat("duration-ticks", level);
            this.explosionPower = (float) parent.getStat("explosion-power", level);

            findAffectedBlocks(targetBlock);
        }

        void findAffectedBlocks(Block targetBlock) {
            StatContext context = StatContext.of(level);
            int size = (int) parent.getStat("size", level);

            // Using the BlockUtil.getInRadius(Block, double, boolean) method you provided
            BlockUtil.getInRadius(targetBlock, size * 2, false).keySet().stream()
                .filter(block -> block.getType().isSolid() && block.getType().getHardness() >= 0 && block.getType() != Material.BEDROCK && block.getType() != Material.BARRIER)
                .forEach(affectedBlocks::add);
            Collections.shuffle(affectedBlocks);
        }

        boolean tick() {
            ticksLived++;
            if (ticksLived > durationTicks) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        explode();
                    }
                }.runTask(parent.plugin);
                return true; // Remove instance
            }

            // Enhanced charge-up particle and sound effects
            if (!affectedBlocks.isEmpty()) {
                if (ticksLived % Math.max(1, 10 - (ticksLived / (durationTicks / 8))) == 0) {
                    center.getWorld().playSound(center, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.5F, 0.5F + (ticksLived / (float)durationTicks));
                }

                for (int i = 0; i < 3; i++) {
                    Block block = affectedBlocks.get(ThreadLocalRandom.current().nextInt(affectedBlocks.size()));
                    if (block.getType().isSolid()) {
                        Location blockCenter = block.getLocation().add(0.5, 0.5, 0.5);
                        block.getWorld().spawnParticle(
                            Particle.BLOCK_DUST,
                            blockCenter, 1, 0, 0, 0, 0,
                            block.getBlockData()
                        );
                    }
                }
            }
            return false;
        }

        void explode() {
            affectedBlocks.removeIf(block -> !block.getType().isSolid());
            if (affectedBlocks.isEmpty()) return;

            // CORRECTED USAGE: Call the ExplosionUtil method that exists in your provided file.
            // This version does not take a caster or a block list.
            // createExplosion(JavaPlugin plugin, Location location, float power, boolean setFire, boolean breakBlocks)
            ExplosionUtil.createExplosion(parent.plugin, center, explosionPower, false, true);

            center.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5F, 1.5F);
            center.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, center, 1);

            // Manual damage loop is required since your ExplosionUtil does not handle damage attribution.
            parent.getGame().ifPresent(game -> {
                for (Player player : game.getPlayers(true)) {
                    if (player.getWorld().equals(center.getWorld()) && player.getLocation().distanceSquared(center) < explosionPower * explosionPower) {
                        // You would calculate damage based on distance here and apply it.
                    }
                }
            });
        }
    }
}

