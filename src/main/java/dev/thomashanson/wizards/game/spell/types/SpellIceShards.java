package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
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

public class SpellIceShards extends Spell implements CustomProjectile, Tickable {

    private static final List<ShardVolley> ACTIVE_VOLLEYS = new ArrayList<>();

    public SpellIceShards(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        // When cast, create a new volley manager and let the tick() method handle firing.
        ACTIVE_VOLLEYS.add(new ShardVolley(this, player, level));
        return true;
    }

    @Override
    public void tick(long gameTick) {
        if (ACTIVE_VOLLEYS.isEmpty()) return;
        ACTIVE_VOLLEYS.removeIf(ShardVolley::tick);
    }
    
    @Override
    public void cleanup() {
        ACTIVE_VOLLEYS.clear();
    }

    @Override
    public void onCollide(LivingEntity hitEntity, Block hitBlock, ProjectileData data) {
        Integer level = data.getCustomData("level", Integer.class);
        if (level == null) return;
        
        if (hitEntity != null && data.getThrower() instanceof Player caster) {
            damage(hitEntity, new CustomDamageTick(getStat("damage", level), EntityDamageEvent.DamageCause.PROJECTILE, getKey(), Instant.now(), caster, null));
        }

        Location impact = (hitEntity != null) ? hitEntity.getLocation() : hitBlock.getLocation();
        impact.getWorld().spawnParticle(Particle.ITEM_CRACK, impact, 30, 0.2, 0.2, 0.2, 0.1, new ItemStack(Material.ICE));
    }

    private static class ShardVolley {
        final SpellIceShards parent;
        final Player caster;
        final int level;

        final int totalShots;
        final int shotDelay;
        
        private int shotsFired = 0;
        private int tickCounter = 0;

        ShardVolley(SpellIceShards parent, Player caster, int level) {
            this.parent = parent;
            this.caster = caster;
            this.level = level;

            StatContext context = StatContext.of(level);
            this.totalShots = (int) parent.getStat("shards", level);
            this.shotDelay = (int) parent.getStat("delay-ticks", level);
        }

        /** @return true if this volley is complete and should be removed */
        boolean tick() {
            if (!caster.isOnline()) return true;

            if (tickCounter % shotDelay == 0) {
                if (shotsFired >= totalShots) {
                    return true;
                }
                fireShard();
                shotsFired++;
            }
            tickCounter++;
            return false;
        }

        void fireShard() {
            StatContext context = StatContext.of(level);
            ProjectileData.Builder dataBuilder = new ProjectileData.Builder(parent.getGame().orElse(null), caster, parent)
                .hitPlayer(true).hitBlock(true)
                .impactSound(Sound.BLOCK_GLASS_BREAK, 1.2F, 1.0F)
                .maxTicksLived((int) parent.getStat("projectile-lifespan-ticks", level))
                .customData("level", level);

            Vector velocity = caster.getEyeLocation().getDirection().normalize().multiply(parent.getStat("projectile-speed", level));
            parent.plugin.getProjectileManager().launchProjectile(caster.getEyeLocation(), new ItemStack(Material.GHAST_TEAR), velocity, dataBuilder);
            caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2F, 1.2F);
        }
    }
}
