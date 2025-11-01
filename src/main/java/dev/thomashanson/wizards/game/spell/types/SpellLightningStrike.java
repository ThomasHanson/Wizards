package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;

public class SpellLightningStrike extends Spell implements Tickable {

    private final NamespacedKey lightningKey;
    private final NamespacedKey casterKey;
    private final NamespacedKey damageKey;

    private static final List<TrackedDebris> ACTIVE_DEBRIS = new CopyOnWriteArrayList<>();

    public SpellLightningStrike(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
        this.lightningKey = new NamespacedKey(plugin, "lightning_spell");
        this.casterKey = new NamespacedKey(plugin, "lightning_caster");
        this.damageKey = new NamespacedKey(plugin, "lightning_damage");
    }

    @Override
    public boolean cast(Player player, int level) {
        StatContext context = StatContext.of(level);
        double maxRange = getStat("max-range", level);

        // This is the correct aiming logic
        RayTraceResult rayTrace = player.getWorld().rayTraceBlocks(player.getEyeLocation(), player.getEyeLocation().getDirection(), maxRange, FluidCollisionMode.NEVER, true);
        Location strikeLocation;
        
        if (rayTrace != null && rayTrace.getHitBlock() != null) {
            strikeLocation = rayTrace.getHitPosition().toLocation(player.getWorld());
        } else {
            strikeLocation = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(maxRange));
        }
        // --- END AIMING LOGIC ---

        long strikeDelayTicks = (long) getStat("strike-delay-ticks", level);

        // --- THIS IS THE CORRECT DELAY LOGIC ---
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                // Wait for the delay, or stop if the player logs off
                if (ticks >= strikeDelayTicks || !player.isOnline()) {
                    strike(player, level, strikeLocation);
                    cancel();
                    return;
                }

                // --- ALL ANTICIPATION EFFECTS ---
                // 1. New "Static Charge" effects
                strikeLocation.getWorld().spawnParticle(Particle.CRIT_MAGIC, strikeLocation, 5, 0.5, 0.5, 0.5, 0.1);
                strikeLocation.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, strikeLocation, 2, 0.5, 0.5, 0.5, 0.05);

                // 2. Original Mineplex "Angry Villager" effect
                strikeLocation.getWorld().spawnParticle(Particle.VILLAGER_ANGRY, strikeLocation.clone().add(0, 1.3, 0), 7, 0.5, 0.3, 0.5, 0);
                // --- END EFFECTS ---

                if (ticks % 5 == 0) {
                    strikeLocation.getWorld().playSound(strikeLocation, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5F, 0.5F);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L); // Run every tick
        // --- END DELAY LOGIC ---

        return true;
    }

    private void strike(Player player, int level, Location strikeLocation) {
        if (!player.isOnline()) return;

        double distance = player.getLocation().distance(strikeLocation);
        StatContext distanceContext = StatContext.of(level, distance);
        double damage = getStats().get("damage").calculate(distanceContext);
        
        LightningStrike lightning = strikeLocation.getWorld().strikeLightning(strikeLocation);
        PersistentDataContainer pdc = lightning.getPersistentDataContainer();
        pdc.set(lightningKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(casterKey, PersistentDataType.STRING, player.getUniqueId().toString());
        pdc.set(damageKey, PersistentDataType.DOUBLE, damage);
        
        Block centerBlock = strikeLocation.getBlock();
        List<Block> blocksToLaunch = new ArrayList<>();
        double blockVelocity = getStat("block-velocity", level);
        double fireChance = getStat("fire-chance", level);
        
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Block relative = centerBlock.getRelative(x, y, z);
                    if (!relative.getType().isAir() && relative.getType().isSolid()) {
                        blocksToLaunch.add(relative);
                    } else if (relative.getType().isAir()) {
                        if (Math.random() < fireChance) {
                            relative.setType(Material.FIRE);
                        }
                    }
                }
            }
        }
        
        for (Block block : blocksToLaunch) {
            if (block.getType() != Material.AIR) {
                org.bukkit.block.data.BlockData blockData = block.getBlockData();
                block.setType(Material.AIR);
                
                Location spawnLocation = block.getLocation().add(0.5, 0.5, 0.5);
                FallingBlock fallingBlock = block.getWorld().spawnFallingBlock(spawnLocation, blockData);
                fallingBlock.setDropItem(false);
                fallingBlock.setHurtEntities(false);
                
                Vector direction = spawnLocation.toVector().subtract(strikeLocation.toVector()).normalize();
                direction.add(new Vector(Math.random() - 0.5, Math.random() * 0.5, Math.random() - 0.5).multiply(0.5));
                
                fallingBlock.setVelocity(direction.multiply(blockVelocity));
                ACTIVE_DEBRIS.add(new TrackedDebris(fallingBlock, player, level));
            }
        }
    }

    @Override
    public void tick(long gameTick) {
        if (ACTIVE_DEBRIS.isEmpty()) return;
        ACTIVE_DEBRIS.removeIf(TrackedDebris::tick);
    }

    @Override
    public void cleanup() {
        ACTIVE_DEBRIS.forEach(TrackedDebris::cleanup);
        ACTIVE_DEBRIS.clear();
    }

    @EventHandler
    public void onEntityDamageByLightning(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LightningStrike lightning) || !(event.getEntity() instanceof LivingEntity target)) return;

        PersistentDataContainer pdc = lightning.getPersistentDataContainer();
        if (!pdc.has(lightningKey, PersistentDataType.BYTE)) return;

        event.setCancelled(true);

        String casterUUIDString = pdc.get(casterKey, PersistentDataType.STRING);
        Double damageAmount = pdc.get(damageKey, PersistentDataType.DOUBLE);
        if (casterUUIDString == null || damageAmount == null) return;

        Player caster = Bukkit.getPlayer(UUID.fromString(casterUUIDString));
        if (caster == null) return;

        damage(target, new CustomDamageTick(damageAmount, EntityDamageEvent.DamageCause.LIGHTNING, getKey(), Instant.now(), caster, null));
        target.setFireTicks((int) getStat("fire-ticks-on-hit", 0));
    }
    
    @EventHandler
    public void onDebrisLand(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock) {
            ACTIVE_DEBRIS.removeIf(debris -> debris.fallingBlock.equals(event.getEntity()));
        }
    }

    private class TrackedDebris {
        final FallingBlock fallingBlock;
        final Player caster;
        final int level;
        final double damage;
        int ticksLived = 0;

        TrackedDebris(FallingBlock fallingBlock, Player caster, int level) {
            this.fallingBlock = fallingBlock;
            this.caster = caster;
            this.level = level;
            this.damage = getStat("debris-damage", level, 2.0);
        }

        boolean tick() {
            ticksLived++;
            if (!fallingBlock.isValid() || ticksLived > 60) {
                return true;
            }
            
            for (LivingEntity target : fallingBlock.getWorld().getNearbyLivingEntities(fallingBlock.getLocation(), 0.8)) {
                if (target.equals(caster)) continue;
                if (target instanceof Player && getWizard((Player) target).isEmpty()) continue;
                
                SpellLightningStrike.this.damage(target, new CustomDamageTick(
                        damage, EntityDamageEvent.DamageCause.CONTACT, getKey() + ".debris", Instant.now(), caster, null
                ));
                
                target.getWorld().spawnParticle(Particle.BLOCK_CRACK, fallingBlock.getLocation(), 20, 0.2, 0.2, 0.2, 0, fallingBlock.getBlockData());
                target.getWorld().playSound(fallingBlock.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0F, 1.0F);
                
                cleanup();
                return true;
            }
            
            return false;
        }

        void cleanup() {
            if (fallingBlock.isValid()) {
                fallingBlock.remove();
            }
        }
    }
}