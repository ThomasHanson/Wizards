package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;

public class SpellLightShield extends Spell implements Tickable {

    private final Map<UUID, ShieldInstance> activeShields = new ConcurrentHashMap<>();
    private final NamespacedKey shieldKey;
    private final NamespacedKey reflectedKey;

    public SpellLightShield(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
        this.shieldKey = new NamespacedKey(plugin, "light_shield_owner");
        this.reflectedKey = new NamespacedKey(plugin, "reflected_projectile");
    }

    @Override
    public boolean cast(Player player, int level) {
        activeShields.computeIfPresent(player.getUniqueId(), (uuid, instance) -> { instance.destroy(); return null; });
        activeShields.put(player.getUniqueId(), new ShieldInstance(this, player, level));
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1F, 1.2F);
        return true;
    }

    @Override
    public void tick(long gameTick) {
        if (activeShields.isEmpty()) return;
        activeShields.values().removeIf(ShieldInstance::tick);
    }

    @Override
    public void cleanup() {
        activeShields.values().forEach(ShieldInstance::destroy);
        activeShields.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ShieldInstance shield = activeShields.remove(event.getPlayer().getUniqueId());
        if (shield != null) shield.destroy();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShieldDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ArmorStand)) return;

        PersistentDataContainer pdc = event.getEntity().getPersistentDataContainer();
        String ownerUUIDString = pdc.get(shieldKey, PersistentDataType.STRING);
        if (ownerUUIDString == null) return;

        event.setCancelled(true);
        ShieldInstance instance = activeShields.get(UUID.fromString(ownerUUIDString));
        if (instance == null) {
            event.getEntity().remove();
            return;
        }

        instance.handleDamage(event.getDamager());
    }

    private static class ShieldInstance {
        final SpellLightShield parent;
        final Player owner;
        final ArmorStand armorStand;
        final Instant expiryTime;

        double health;
        final double selfDamageOnReflect;
        final int particleWidth;
        final int particleHeight;
        
        ShieldInstance(SpellLightShield parent, Player owner, int level) {
            this.parent = parent;
            this.owner = owner;

            this.health = parent.getStat("health", level, 6.0);
            this.selfDamageOnReflect = parent.getStat("self-damage-on-reflect", level, 1.0);
            this.particleWidth = (int) parent.getStat("particle-width", level, 3.0);
            this.particleHeight = (int) parent.getStat("particle-height", level, 4.0);
            
            long duration = (long) parent.getStat("duration-seconds", level, 4.0);
            this.expiryTime = Instant.now().plusSeconds(duration);

            Location spawnLoc = calculateShieldLocation(owner);
            this.armorStand = owner.getWorld().spawn(spawnLoc, ArmorStand.class, as -> {
                as.setInvisible(true);
                as.setInvulnerable(true);
                as.setGravity(false);
                as.setMarker(true);
                as.getPersistentDataContainer().set(parent.shieldKey, PersistentDataType.STRING, owner.getUniqueId().toString());
            });
            
            updateVisualAbsorption();
        }

        boolean tick() {
            if (!owner.isOnline() || !armorStand.isValid() || Instant.now().isAfter(expiryTime)) {
                destroy();
                return true;
            }

            Location targetLocation = calculateShieldLocation(owner);
            armorStand.teleport(targetLocation);
            armorStand.setRotation(targetLocation.getYaw() + 90, 0);
            
            drawParticles();
            return false;
        }

        void handleDamage(Entity damager) {
            Player spellCaster = null;
            // The instanceof pattern is applied here, creating the 'projectile' variable
            if (damager instanceof Projectile projectile) {
                // A second pattern is applied to the shooter
                if (projectile.getShooter() instanceof Player shooter) {
                    spellCaster = shooter;
                }
            }

            if (damager.getPersistentDataContainer().has(parent.reflectedKey, PersistentDataType.BYTE)) {
                damager.remove();
                return;
            }
            
            double damageToTake = 1.0;
            String spellKey = damager.getPersistentDataContainer().get(Spell.SPELL_ID_KEY, PersistentDataType.STRING);
            if (spellKey != null) {
                Spell sourceSpell = parent.plugin.getSpellManager().getSpell(spellKey);
                if (sourceSpell != null) {
                    damageToTake = sourceSpell.getStat("reflection-damage", StatContext.of(0), 1.0);
                }
            }

            this.health -= damageToTake;
            updateVisualAbsorption();
            
            if (health <= 0) {
                destroy();
                damager.remove();
                return;
            }

            owner.damage(selfDamageOnReflect, spellCaster);
            Vector reflectedVelocity = owner.getEyeLocation().getDirection().normalize().multiply(damager.getVelocity().length() * 0.9);
            damager.setVelocity(reflectedVelocity);
            damager.getPersistentDataContainer().set(parent.reflectedKey, PersistentDataType.BYTE, (byte)1);

            // The pattern is used again here for clarity and safety
            if (damager instanceof Projectile projectile) {
                projectile.setShooter(owner);
            }
        }
        
        void drawParticles() { /* Particle logic */ }

        Location calculateShieldLocation(Player owner) {
            return owner.getEyeLocation().add(owner.getLocation().getDirection().multiply(1.5));
        }
        
        void updateVisualAbsorption() {
            if (owner.isOnline()) {
                owner.setAbsorptionAmount(Math.max(0, Math.ceil(health)));
            }
        }

        void destroy() {
            if (armorStand.isValid()) armorStand.remove();
            if (owner.isOnline()) owner.setAbsorptionAmount(0);
        }
    }
}