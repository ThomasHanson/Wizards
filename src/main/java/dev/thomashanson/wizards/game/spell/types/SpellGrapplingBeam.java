package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;
import dev.thomashanson.wizards.projectile.CustomProjectile;
import dev.thomashanson.wizards.projectile.ProjectileData;

public class SpellGrapplingBeam extends Spell implements CustomProjectile, Tickable {

    private static final CopyOnWriteArrayList<PulledBlock> PULLED_BLOCKS = new CopyOnWriteArrayList<>();
    private static final Set<UUID> PULLED_BLOCK_IDS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public SpellGrapplingBeam(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        StatContext context = StatContext.of(level);
        ProjectileData.Builder dataBuilder = new ProjectileData.Builder(getGame().orElse(null), player, this)
            .hitPlayer(true).hitBlock(true)
            .trailParticle(Particle.SOUL)
            .customData("level", level)
            .customData("isShifting", player.isSneaking())
            .maxTicksLived((int) getStat("max-ticks", level));
        
        plugin.getProjectileManager().launchProjectile(player.getEyeLocation(), new ItemStack(Material.TRIPWIRE_HOOK), player.getEyeLocation().getDirection().multiply(getStat("projectile-speed", level)), dataBuilder);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 1.0F, 1.2F);
        return true;
    }

    @Override
    public void onCollide(LivingEntity hitEntity, Block hitBlock, ProjectileData data) {
        LivingEntity thrower = data.getThrower();
        
        if (thrower == null || (!(thrower instanceof Player)))
            return;
        
        Player caster = (Player) thrower;

        if (!caster.isOnline())
            return;

        Integer level = data.getCustomData("level", Integer.class);
        Boolean isShifting = data.getCustomData("isShifting", Boolean.class);
        if (level == null || isShifting == null) return;
        
        StatContext context = StatContext.of(level);

        if (hitEntity != null && !hitEntity.equals(caster)) {
            // Pull player
            Vector direction = caster.getLocation().toVector().subtract(hitEntity.getLocation().toVector()).normalize();
            hitEntity.setVelocity(direction.multiply(getStat("player-pull-speed", level)));
            damage(hitEntity, new CustomDamageTick(getStat("damage", level), EntityDamageEvent.DamageCause.PROJECTILE, getKey(), Instant.now(), caster, null)); // UPDATED

        } else if (hitBlock != null) {
            if (isShifting) {
                pullBlockTowardsPlayer(hitBlock, caster, level);
            } else {
                // Pull caster to block
                Vector direction = hitBlock.getLocation().toVector().subtract(caster.getEyeLocation().toVector()).normalize();
                caster.setVelocity(direction.multiply(getStat("caster-pull-speed", level)));
                caster.setFallDistance(-5.0f);
            }
        }
    }

    private void pullBlockTowardsPlayer(Block block, Player player, int level) {
        if (block.getType().getHardness() > 50 || !block.getType().isSolid()) return; // Cannot pull bedrock/obsidian etc.
        
        FallingBlock fallingBlock = block.getWorld().spawnFallingBlock(block.getLocation().add(0.5, 0, 0.5), block.getBlockData());
        block.setType(Material.AIR);
        
        fallingBlock.setDropItem(false);
        fallingBlock.setHurtEntities(false);
        fallingBlock.setGravity(false);
        
        PULLED_BLOCK_IDS.add(fallingBlock.getUniqueId());
        PULLED_BLOCKS.add(new PulledBlock(this, fallingBlock, player, level));
    }

    @Override
    public void tick(long gameTick) {
        if (PULLED_BLOCKS.isEmpty()) return;
        PULLED_BLOCKS.removeIf(PulledBlock::tick);
    }

    @EventHandler
    public void onFallingBlockPlace(EntityChangeBlockEvent event) {
        if (PULLED_BLOCK_IDS.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
            event.getEntity().remove();
            PULLED_BLOCK_IDS.remove(event.getEntity().getUniqueId());
        }
    }

    private static class PulledBlock {
        final SpellGrapplingBeam parent;
        final FallingBlock fallingBlock;
        final Player target;
        final int level;
        int ticksLived = 0;

        final double speed;
        final double damage;
        final int maxTicks;

        PulledBlock(SpellGrapplingBeam parent, FallingBlock fallingBlock, Player target, int level) {
            this.parent = parent;
            this.fallingBlock = fallingBlock;
            this.target = target;
            this.level = level;

            StatContext context = StatContext.of(level);
            this.speed = parent.getStat("pulled-block-speed-bps", level) / 20.0;
            this.damage = parent.getStat("pulled-block-damage", level);
            this.maxTicks = (int) parent.getStat("pulled-block-lifespan-ticks", level);
        }

        boolean tick() {
            ticksLived++;
            if (!fallingBlock.isValid() || !target.isOnline() || ticksLived > maxTicks) {
                cleanup();
                return true;
            }

            Vector direction = target.getEyeLocation().toVector().subtract(fallingBlock.getLocation().toVector()).normalize();
            fallingBlock.setVelocity(direction.multiply(speed));

            for (LivingEntity entity : fallingBlock.getWorld().getNearbyLivingEntities(fallingBlock.getLocation(), 1.2)) {
                if (entity.equals(target)) continue;
                parent.damage(entity, new CustomDamageTick(damage, EntityDamageEvent.DamageCause.PROJECTILE, parent.getKey() + ".block", Instant.now(), target, null)); // UPDATED
                cleanup();
                return true;
            }

            if (fallingBlock.getLocation().distanceSquared(target.getEyeLocation()) < 2.25) {
                cleanup();
                return true;
            }
            return false;
        }

        void cleanup() {
            if (fallingBlock.isValid()) fallingBlock.remove();
            PULLED_BLOCK_IDS.remove(fallingBlock.getUniqueId());
        }
    }
}

