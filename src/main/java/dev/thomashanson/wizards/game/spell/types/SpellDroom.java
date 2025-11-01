package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;
import dev.thomashanson.wizards.util.ExplosionUtil;
import dev.thomashanson.wizards.util.effects.ParticleConfig;
import dev.thomashanson.wizards.util.effects.ParticleUtil;

public class SpellDroom extends Spell implements Tickable {

    private final NamespacedKey droomKey;
    private final NamespacedKey casterKey;
    private final NamespacedKey levelKey;

    private final Map<UUID, FallingBlock> activeAnvils = new ConcurrentHashMap<>();

    private static final ParticleConfig ANVIL_SHATTER_CONFIG = new ParticleConfig(
            Particle.BLOCK_CRACK, 20, 0.5, 0.1, 0.1, 0.1, Material.ANVIL.createBlockData()
    );

    public SpellDroom(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
        this.droomKey = new NamespacedKey(plugin, "droom_anvil");
        this.casterKey = new NamespacedKey(plugin, "droom_caster");
        this.levelKey = new NamespacedKey(plugin, "droom_level");
    }

    @Override
    public boolean cast(Player player, int level) {
        // Use the multi-target logic from Mineplex's code
        double radius = getStat("radius", level);
        double spawnHeight = getStat("spawn-height", level);

        List<Player> targets = new ArrayList<>();
        targets.add(player); // Always target the caster

        // Find other nearby players
        player.getNearbyEntities(radius, radius * 3, radius).forEach(entity -> {
            if (entity instanceof Player && !entity.equals(player) && getWizard((Player) entity).isPresent())
                targets.add((Player) entity);
        });

        for (Player target : targets) {
            Location loc = target.getLocation().clone().add(0, spawnHeight, 0);
            
            while (loc.getBlock().getType() != Material.AIR && loc.getY() < player.getWorld().getMaxHeight()) {
                loc.add(0, 1, 0);
            }
            
            FallingBlock anvil = target.getWorld().spawnFallingBlock(loc, Bukkit.createBlockData(Material.ANVIL));
            
            PersistentDataContainer pdc = anvil.getPersistentDataContainer();
            pdc.set(droomKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(casterKey, PersistentDataType.STRING, player.getUniqueId().toString());
            pdc.set(levelKey, PersistentDataType.INTEGER, level);
            
            anvil.getWorld().playSound(anvil.getLocation(), Sound.BLOCK_ANVIL_USE, 1.9F, 0F);
            activeAnvils.put(anvil.getUniqueId(), anvil);
        }

        return true;
    }

    @Override
    public void tick(long gameTick) {
        if (activeAnvils.isEmpty()) return;

        activeAnvils.values().removeIf(anvil -> {
            if (!anvil.isValid()) {
                return true;
            }
            anvil.getWorld().spawnParticle(Particle.SMOKE_LARGE, anvil.getLocation(), 2, 0.1, 0.1, 0.1, 0.01);
            return false;
        });
    }

    @Override
    public void cleanup() {
        activeAnvils.values().forEach(anvil -> {
            if (anvil.isValid()) anvil.remove();
        });
        activeAnvils.clear();
    }

    @EventHandler
    public void onAnvilLand(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock anvil)) return;

        PersistentDataContainer pdc = anvil.getPersistentDataContainer();
        if (!pdc.has(droomKey, PersistentDataType.BYTE)) return;

        activeAnvils.remove(anvil.getUniqueId());
        event.setCancelled(true); // Prevent the anvil block from forming

        String casterUUIDString = pdc.get(casterKey, PersistentDataType.STRING);
        Integer level = pdc.get(levelKey, PersistentDataType.INTEGER);
        if (casterUUIDString == null || level == null) return;

        Player caster = Bukkit.getPlayer(UUID.fromString(casterUUIDString));
        // Caster can be null if they logged off, but the anvil should still explode.

        StatContext context = StatContext.of(level);
        Location impactLocation = anvil.getLocation();

        // --- Visual & Audio Effects ---
        impactLocation.getWorld().playSound(impactLocation, Sound.BLOCK_ANVIL_LAND, 2.0F, 0.8F);
        impactLocation.getWorld().playSound(impactLocation, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 1.0F);

        float explosionPower = (float) getStat("explosion-power", level);
        ParticleUtil.createShockwave(impactLocation, explosionPower * 0.8, ANVIL_SHATTER_CONFIG);

        ExplosionUtil.ExplosionConfig config = new ExplosionUtil.ExplosionConfig(
            false, 100L, 60, 0.3, 0.6, 0.5, 0.5
        );
        ExplosionUtil.createExplosion(plugin, impactLocation, explosionPower, config, false);
        anvil.remove();

        // --- NEW: Damage Logic (from Mineplex code) ---
        if (caster == null) return; // Can't deal damage if caster is offline

        // Get damage stats from config
        double baseDamage = getStat("base-damage", level, 6.0);
        double damagePerLevel = getStat("damage-per-level", level, 4.0);
        double baseRadius = getStat("damage-radius-base", level, 1.0);
        double radiusPerLevel = getStat("damage-radius-per-level", level, 0.5);

        double maxDamage = baseDamage + (level * damagePerLevel);
        double damageRadius = baseRadius + (level * radiusPerLevel);
        
        // Loop through nearby entities
        for (LivingEntity target : impactLocation.getWorld().getNearbyLivingEntities(impactLocation, damageRadius, damageRadius, damageRadius)) {
            if (target.equals(caster)) continue;
            if (target instanceof Player && getWizard((Player) target).isEmpty()) continue;

            double distance = target.getLocation().distance(impactLocation);
            if (distance > damageRadius) continue;

            // Apply linear falloff
            double proximity = Math.max(0, 1.0 - (distance / damageRadius));
            double finalDamage = maxDamage * proximity;

            if (finalDamage > 0.1) {
                damage(target, new CustomDamageTick(
                        finalDamage,
                        EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
                        getKey(),
                        Instant.now(),
                        caster,
                        null
                ));
            }
        }
    }
}