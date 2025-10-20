package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;
import dev.thomashanson.wizards.util.BlockUtil;

public class SpellNapalm extends Spell implements Tickable {

    private final List<NapalmInstance> activeNapalms = new ArrayList<>();
    private final Map<Material, Material> blockTransformations = new HashMap<>();

    public SpellNapalm(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);

        ConfigurationSection transformSection = config.getConfigurationSection("block-transformations");
        if (transformSection != null) {
            for (String from : transformSection.getKeys(false)) {
                Material fromMat = Material.matchMaterial(from);
                Material toMat = Material.matchMaterial(transformSection.getString(from));
                if (fromMat != null && toMat != null) {
                    blockTransformations.put(fromMat, toMat);
                }
            }
        }
    }

    @Override
    public boolean cast(Player player, int level) {
        activeNapalms.add(new NapalmInstance(this, player, level));
        return true;
    }

    @Override
    public void tick(long gameTick) {
        if (activeNapalms.isEmpty()) return;
        activeNapalms.removeIf(NapalmInstance::tick);
    }

    @Override
    public void cleanup() {
        activeNapalms.clear();
    }

    private static class NapalmInstance {
        final SpellNapalm parent;
        final Player caster;
        final int level;
        Location location;
        Vector velocity;

        // Configurable stats
        final double maxDistance;
        final double speedPerTick;
        final double initialSize;
        final double maxSize;
        final double sizeGrowth;

        double currentSize;
        double distanceTravelled = 0;

        NapalmInstance(SpellNapalm parent, Player caster, int level) {
            this.parent = parent;
            this.caster = caster;
            this.level = level;
            this.location = caster.getEyeLocation().add(caster.getLocation().getDirection().multiply(2));

            StatContext context = StatContext.of(level);
            this.maxDistance = parent.getStat("distance", level);
            double speedBPS = parent.getStat("speed-bps", level);
            this.speedPerTick = speedBPS / 20.0;
            this.initialSize = parent.getStat("initial-size", level);
            this.maxSize = parent.getStat("max-size", level);
            this.sizeGrowth = parent.getStat("size-growth-per-tick", level);
            this.currentSize = initialSize;
            
            this.velocity = caster.getLocation().getDirection().normalize().multiply(speedPerTick);
        }

        boolean tick() {
            if (!caster.isOnline() || distanceTravelled >= maxDistance || location.getBlock().getType().isSolid()) {
                explode();
                return true;
            }

            location.add(velocity);
            distanceTravelled += speedPerTick;
            currentSize = Math.min(maxSize, currentSize + sizeGrowth);

            renderParticles();
            damageEntities();
            transformBlocks();

            return false;
        }

        void renderParticles() {
            for (int i = 0; i < currentSize * 15; i++) {
                Vector offset = Vector.getRandom().subtract(new Vector(0.5, 0.5, 0.5)).normalize().multiply(currentSize * ThreadLocalRandom.current().nextDouble());
                location.getWorld().spawnParticle(Particle.REDSTONE, location.clone().add(offset), 0, -0.3, 0.4, 0.3, new Particle.DustOptions(Color.ORANGE, 1.2F));
            }
        }

        void damageEntities() {
            double baseDamage = parent.getStat("damage-per-tick", level);
            int fireTicks = (int) parent.getStat("fire-ticks", level);

            for (LivingEntity entity : location.getWorld().getNearbyLivingEntities(location, currentSize)) {
                if (entity.equals(caster)) continue;
                
                double distanceFactor = 1.0 - (entity.getLocation().distance(location) / currentSize);
                if (distanceFactor <= 0) continue;

                parent.damage(entity, new CustomDamageTick(baseDamage * distanceFactor, EntityDamageEvent.DamageCause.FIRE, parent.getKey(), Instant.now(), caster, null));
                entity.setFireTicks(Math.max(entity.getFireTicks(), (int) (fireTicks * distanceFactor)));
            }
        }

        void transformBlocks() {
            int radius = (int) Math.ceil(currentSize);
            // UPDATED: The method is now getBlocksInRadius.
            for (Block block : BlockUtil.getBlocksInRadius(location, radius).keySet()) {
                if (parent.blockTransformations.containsKey(block.getType())) {
                    block.setType(parent.blockTransformations.get(block.getType()));
                } else if (block.getType().getHardness() < currentSize && block.getType().isSolid()) {
                    if (ThreadLocalRandom.current().nextDouble() < 0.1) { // 10% chance to break block
                        block.breakNaturally();
                    }
                }
            }
        }

        void explode() {
            int explosionFireTicks = (int) parent.getStat("explosion-fire-ticks", level);
            double explosionDamage = parent.getStat("explosion-damage", level);
            
            location.getWorld().spawnParticle(Particle.LAVA, location, 30, 0.3, 0.3, 0.3);
            location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.5F, 1.0F);

            for (LivingEntity entity : location.getWorld().getNearbyLivingEntities(location, maxSize)) {
                 if (entity.equals(caster)) continue;
                 parent.damage(entity, new CustomDamageTick(explosionDamage, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, parent.getKey() + ".explosion", Instant.now(), caster, null));
                 entity.setFireTicks(Math.max(entity.getFireTicks(), explosionFireTicks));
            }
        }
    }
}