package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;
import dev.thomashanson.wizards.util.BlockUtil;
import dev.thomashanson.wizards.util.ExplosionUtil;

public class SpellRumble extends Spell implements Spell.SpellBlock, Tickable {

    private static final List<RumbleInstance> ACTIVE_RUMBLES = new ArrayList<>();

    public SpellRumble(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        Block block = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
        return castSpell(player, block, level);
    }

    @Override
    public boolean castSpell(Player player, Block block, int level) {
        if (!block.getType().isSolid()) return false;
        ACTIVE_RUMBLES.add(new RumbleInstance(this, player, block, level));
        return true;
    }

    @Override
    public void tick(long gameTick) {
        if (ACTIVE_RUMBLES.isEmpty()) return;
        ACTIVE_RUMBLES.removeIf(RumbleInstance::tick);
    }

    @Override
    public void cleanup() {
        ACTIVE_RUMBLES.clear();
    }

    private static class RumbleInstance {
        final SpellRumble parent;
        final Player caster;
        final int level;
        final BlockFace direction;
        Block currentBlock;
        int distance = 0;

        // Configurable stats
        final int maxDistance;
        final double damage;
        final float explosionPower;
        final double explosionDamage;
        final PotionEffect slowEffect;

        RumbleInstance(SpellRumble parent, Player caster, Block startBlock, int level) {
            this.parent = parent;
            this.caster = caster;
            this.level = level;

            this.direction = BlockUtil.getFace(caster.getEyeLocation().getYaw(), false);
            this.currentBlock = startBlock;

            StatContext context = StatContext.of(level);
            this.maxDistance = (int) parent.getStat("max-distance", level);
            this.damage = parent.getStat("damage", level);
            this.explosionPower = (float) parent.getStat("explosion-power", level);
            this.explosionDamage = parent.getStat("explosion-damage", level);
            this.slowEffect = new PotionEffect(PotionEffectType.SLOW, (int) parent.getStat("slow-duration-ticks", level), (int) parent.getStat("slow-amplifier", level) - 1);
        }

        /** @return true if this instance should be removed */
        boolean tick() {
            if (!caster.isOnline() || distance >= maxDistance) {
                explode();
                return true;
            }

            Block nextBlock = findNextBlock();
            if (nextBlock == null) {
                explode();
                return true;
            }
            currentBlock = nextBlock;
            distance++;

            affectArea();
            return false;
        }

        Block findNextBlock() {
            for (int y = 1; y >= -2; y--) {
                Block relative = currentBlock.getRelative(direction).getRelative(0, y, 0);
                if (relative.getType().isSolid() && !relative.getRelative(BlockFace.UP).getType().isSolid()) {
                    return relative;
                }
            }
            return null;
        }

        void affectArea() {
            // Use a context with distance to allow the width to grow over time
            StatContext context = StatContext.of(level, distance);
            int width = (int) parent.getStat("width", level);

            // The method now takes a Location instead of a Block.
            // We get the block's center to ensure the radius is uniform.
            for (Block block : BlockUtil.getBlocksInRadius(currentBlock.getLocation().add(0.5, 0.5, 0.5), width).keySet()) {
                if (block.getType().isSolid()) {
                    playEffect(block);
                    damageEntitiesAbove(block);
                }
            }
        }
        
        void damageEntitiesAbove(Block block) {
            Location checkLoc = block.getLocation().add(0.5, 1.5, 0.5);
            for (LivingEntity entity : block.getWorld().getNearbyLivingEntities(checkLoc, 0.5, 1.0, 0.5)) {
                if (entity.equals(caster)) continue;
                parent.damage(entity, new CustomDamageTick(damage, EntityDamageEvent.DamageCause.CONTACT, parent.getKey(), Instant.now(), caster, null));
                entity.addPotionEffect(slowEffect);
            }
        }

        void explode() {
            Location explosionCenter = currentBlock.getLocation().add(0.5, 1.0, 0.5);

            // ExplosionUtil now uses a configuration record for its parameters.
            // This creates a standard visual-only explosion that does not regenerate blocks.
            ExplosionUtil.ExplosionConfig config = new ExplosionUtil.ExplosionConfig(
                false,      // regenerateBlocks
                100L, // regenerationDelayTicks
                60,      // debrisLifespanTicks
                0.3,            // debrisChance
                0.6,        // velocityStrength
                0.5,          // velocityYAward
                0.5     // itemVelocityModifier
            );

            ExplosionUtil.createExplosion(parent.plugin, explosionCenter, explosionPower, config, true);

            for (LivingEntity entity : explosionCenter.getWorld().getNearbyLivingEntities(explosionCenter, explosionPower)) {
                if (entity.equals(caster)) continue;
                double distanceFactor = Math.max(0, 1.0 - (entity.getLocation().distance(explosionCenter) / explosionPower));
                double finalDamage = explosionDamage * distanceFactor;
                parent.damage(entity, new CustomDamageTick(finalDamage, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, parent.getKey() + ".explosion", Instant.now(), caster, null));
            }
        }

        void playEffect(Block block) {
            block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, block.getType());
        }
    }
}

