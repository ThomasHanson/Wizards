package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.util.MathUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class SpellFireball extends Spell {

    private final NamespacedKey fireballKey;
    private final NamespacedKey casterKey;
    private final NamespacedKey levelKey;

    public SpellFireball(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
        this.fireballKey = new NamespacedKey(plugin, "fireball_spell");
        this.casterKey = new NamespacedKey(plugin, "fireball_caster");
        this.levelKey = new NamespacedKey(plugin, "fireball_level");
    }

    @Override
    public boolean cast(Player player, int level) {
        Fireball fireball = player.launchProjectile(Fireball.class);
        
        Vector velocity = player.getEyeLocation().getDirection().normalize().multiply(getStat("velocity", level, 1.8));
        fireball.setVelocity(velocity);
        fireball.setYield((float) getStat("yield", level, 1.5));
        fireball.setIsIncendiary(getStat("is-incendiary", level, 1.0) > 0);
        fireball.setBounce(false);

        PersistentDataContainer pdc = fireball.getPersistentDataContainer();
        pdc.set(fireballKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(casterKey, PersistentDataType.STRING, player.getUniqueId().toString());
        pdc.set(levelKey, PersistentDataType.INTEGER, level);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.9F, 1.1F);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFireballExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof Fireball fireball)) return;

        PersistentDataContainer pdc = fireball.getPersistentDataContainer();
        if (!pdc.has(fireballKey, PersistentDataType.BYTE)) return;

        String casterUUIDString = pdc.get(casterKey, PersistentDataType.STRING);
        Integer level = pdc.get(levelKey, PersistentDataType.INTEGER);
        if (casterUUIDString == null || level == null) return;
        
        Player caster = Bukkit.getPlayer(UUID.fromString(casterUUIDString));
        if (caster == null) return;

        // Use new destroy-inventories stat to protect chests
        if (getStat("destroy-inventories", level, 0.0) <= 0) {
            event.blockList().removeIf(block -> Tag.SHULKER_BOXES.isTagged(block.getType()) || block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST || block.getType() == Material.BARREL);
        }

        final double effectRadius = getStat("radius", level, 4.0);
        final Location explosionLocation = event.getLocation();
        
        explosionLocation.getWorld().playSound(explosionLocation, Sound.ENTITY_GENERIC_EXPLODE, 1.5F, 1.0F);

        for (Entity entity : Objects.requireNonNull(explosionLocation.getWorld()).getNearbyEntities(explosionLocation, effectRadius, effectRadius, effectRadius)) {
            if (entity instanceof LivingEntity livingEntity) {
                double distance = explosionLocation.distance(livingEntity.getEyeLocation());
                double proximity = Math.max(0, 1.0 - (distance / effectRadius));

                if (livingEntity.getUniqueId().equals(caster.getUniqueId())) {
                    handleCasterJump(livingEntity, level, explosionLocation);
                } else {
                    handleTargetDamage(livingEntity, caster, level, explosionLocation, proximity);
                }
            }
        }
    }

    private void handleCasterJump(LivingEntity caster, int level, Location explosionLocation) {
        Vector jumpVector = caster.getEyeLocation().toVector().subtract(explosionLocation.toVector());
        if (jumpVector.lengthSquared() < 0.001) jumpVector = new Vector(0, 1, 0);

        // UPDATED: Method renamed to applyVelocity with a simpler signature.
        double strength = getStat("jump-strength", level, 1.4);
        double yAdd = getStat("jump-y-add", level, 0.6);
        double yMax = getStat("jump-y-max", level, 1.2);
        MathUtil.applyVelocity(caster, jumpVector.normalize(), strength, 0, yAdd, yMax);

        double selfDamage = getStat("self-damage", level);
        if (selfDamage > 0 && caster instanceof Player) {
            damage(caster, new CustomDamageTick(selfDamage, EntityDamageEvent.DamageCause.CUSTOM, getKey() + ".self", Instant.now(), (Player) caster, null));
        }
    }

    private void handleTargetDamage(LivingEntity target, Player caster, int level, Location explosionLocation, double proximity) {
        Vector direction = target.getEyeLocation().toVector().subtract(explosionLocation.toVector()).normalize();
        if (direction.getY() < 0.15) direction.setY(0.15).normalize();

        // UPDATED: Method renamed to applyVelocity with a simpler signature.
        double strength = getStat("knockback-strength", level, 1.2) * proximity;
        double yAdd = getStat("knockback-y-add", level, 0.4) * proximity;
        double yMax = getStat("knockback-y-max", level, 1.0);
        MathUtil.applyVelocity(target, direction, strength, 0, yAdd, yMax);

        double damageAmount = Math.max(1.0, getStat("damage", level) * proximity);
        damage(target, new CustomDamageTick(damageAmount, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, getKey(), Instant.now(), caster, null));

        int fireTicks = (int) (getStat("fire-duration-ticks", level) * proximity);
        target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVanillaFireballDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Fireball fireball && fireball.getPersistentDataContainer().has(fireballKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }
}