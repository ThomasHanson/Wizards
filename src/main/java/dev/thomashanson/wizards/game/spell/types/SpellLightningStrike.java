package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;
import dev.thomashanson.wizards.util.ExplosionUtil;

public class SpellLightningStrike extends Spell {

    private final NamespacedKey lightningKey;
    private final NamespacedKey casterKey;
    private final NamespacedKey damageKey;

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

        RayTraceResult rayTrace = player.getWorld().rayTraceBlocks(player.getEyeLocation(), player.getEyeLocation().getDirection(), maxRange, FluidCollisionMode.NEVER, true);
        Location strikeLocation = (rayTrace != null && rayTrace.getHitBlock() != null) ? rayTrace.getHitPosition().toLocation(player.getWorld()) : player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(maxRange));

        strikeLocation.getWorld().spawnParticle(Particle.VILLAGER_ANGRY, strikeLocation.clone().add(0, 1.3, 0), 7, 0.5, 0.3, 0.5);
        strikeLocation.getWorld().playSound(strikeLocation, Sound.ENTITY_CAT_HISS, 1F, 1F);

        long strikeDelay = (long) getStat("strike-delay-ticks", level);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            double distance = player.getLocation().distance(strikeLocation);
            StatContext distanceContext = StatContext.of(level, distance);
            double damage = getStats().get("damage").calculate(distanceContext);

            LightningStrike lightning = strikeLocation.getWorld().strikeLightning(strikeLocation);
            PersistentDataContainer pdc = lightning.getPersistentDataContainer();
            pdc.set(lightningKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(casterKey, PersistentDataType.STRING, player.getUniqueId().toString());
            pdc.set(damageKey, PersistentDataType.DOUBLE, damage);

            float explosionRadius = (float) getStat("explosion-radius", level);
            ExplosionUtil.createExplosion(plugin, strikeLocation, explosionRadius, getStat("sets-fire", level) > 0, true);

        }, strikeDelay);

        return true;
    }

    @EventHandler
    public void onEntityDamageByLightning(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LightningStrike) || !(event.getEntity() instanceof LivingEntity)) return;

        LightningStrike lightning = (LightningStrike) event.getDamager();
        PersistentDataContainer pdc = lightning.getPersistentDataContainer();
        if (!pdc.has(lightningKey, PersistentDataType.BYTE)) return;

        event.setCancelled(true);

        String casterUUIDString = pdc.get(casterKey, PersistentDataType.STRING);
        Double damageAmount = pdc.get(damageKey, PersistentDataType.DOUBLE);
        if (casterUUIDString == null || damageAmount == null) return;

        Player caster = Bukkit.getPlayer(UUID.fromString(casterUUIDString));
        if (caster == null) return;

        LivingEntity target = (LivingEntity) event.getEntity();
        damage(target, new CustomDamageTick(damageAmount, EntityDamageEvent.DamageCause.LIGHTNING, getKey(), Instant.now(), caster, null));
        target.setFireTicks((int) getStat("fire-ticks-on-hit", 0));
    }
}
