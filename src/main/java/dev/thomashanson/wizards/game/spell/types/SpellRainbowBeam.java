package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;

public class SpellRainbowBeam extends Spell {

    public SpellRainbowBeam(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        StatContext context = StatContext.of(level);
        Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection();
        World world = player.getWorld();

        double maxRange = getStat("range", level);
        double particleStep = getStat("particle-step", level);
        double hitboxSize = getStat("hitbox-size", level);

        Location beamEndPoint;
        LivingEntity hitEntity = null;

        Predicate<Entity> filter = entity -> entity instanceof LivingEntity && !entity.getUniqueId().equals(player.getUniqueId()) && !entity.isDead();
        RayTraceResult entityRayTrace = world.rayTraceEntities(eyeLocation, direction, maxRange, hitboxSize, filter);

        if (entityRayTrace != null && entityRayTrace.getHitEntity() instanceof LivingEntity) {
            hitEntity = (LivingEntity) entityRayTrace.getHitEntity();
            beamEndPoint = hitEntity.getEyeLocation();
            handleEntityHit(player, hitEntity, level);

        } else {
            RayTraceResult blockRayTrace = world.rayTraceBlocks(eyeLocation, direction, maxRange, FluidCollisionMode.NEVER, true);
            beamEndPoint = (blockRayTrace != null) ? blockRayTrace.getHitPosition().toLocation(world) : eyeLocation.clone().add(direction.multiply(maxRange));
            getWizard(player).ifPresent(wizard -> wizard.addAccuracy(false));
        }

        renderBeam(eyeLocation, beamEndPoint, particleStep);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.5F, 1.0F);
        return true;
    }

    private void handleEntityHit(Player caster, LivingEntity target, int level) {
        double distance = caster.getEyeLocation().distance(target.getEyeLocation());
        StatContext distanceContext = StatContext.of(level, distance);
        
        double finalDamage = getStats().get("damage").calculate(distanceContext);

        damage(target, new CustomDamageTick(finalDamage, EntityDamageEvent.DamageCause.MAGIC, getKey(), Instant.now(), caster, null));
        getWizard(caster).ifPresent(wizard -> wizard.addAccuracy(true));
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.2F, 1.5F);
    }

    private void renderBeam(Location start, Location end, double step) {
        World world = start.getWorld();
        if (world == null) return;

        Vector vector = end.toVector().subtract(start.toVector());
        double length = vector.length();
        if (length < step) return;
        vector.normalize().multiply(step);

        float hueIncrement = 360.0f / (float) (length / step);
        float currentHue = ThreadLocalRandom.current().nextFloat() * 360.0f;

        for (double d = 0; d < length; d += step) {
            currentHue = (currentHue + hueIncrement) % 360.0f;
            java.awt.Color awtColor = java.awt.Color.getHSBColor(currentHue / 360.0f, 1.0f, 1.0f);
            Particle.DustOptions dustOptions = new Particle.DustOptions(Color.fromRGB(awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue()), 1.0F);

            Location particlePoint = start.clone().add(vector.clone().multiply(d / step));
            world.spawnParticle(Particle.REDSTONE, particlePoint, 1, 0, 0, 0, 0, dustOptions);
        }
    }
}
