package dev.thomashanson.wizards.projectile;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import dev.thomashanson.wizards.WizardsPlugin;

public class ProjectileManager implements Listener {

    private final WizardsPlugin plugin;
    private BukkitTask updateTask;
    private final Map<UUID, ProjectileData> activeProjectiles = new ConcurrentHashMap<>();

    public ProjectileManager(WizardsPlugin plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startUpdates();
    }

    /**
     * Launches a projectile.
     *
     * @param spawnLocation The location to spawn the item.
     * @param itemStack     The ItemStack representing the projectile.
     * @param velocity      The initial velocity of the projectile.
     * @param dataBuilder   A pre-configured ProjectileData.Builder.
     * @return The launched Item entity.
     */
    public Item launchProjectile(Location spawnLocation, ItemStack itemStack, Vector velocity, ProjectileData.Builder dataBuilder) {
        if (spawnLocation.getWorld() == null) {
            throw new IllegalArgumentException("Spawn location must have a valid world.");
        }
        Item item = spawnLocation.getWorld().dropItem(spawnLocation, itemStack);
        item.setVelocity(velocity);
        item.setGravity(true);

        ProjectileData projectileData = dataBuilder
                .itemEntity(item)
                .build();

        activeProjectiles.put(item.getUniqueId(), projectileData);
        return item;
    }

    /**
     * Retrieves the ProjectileData for a given projectile entity.
     *
     * @param entity The entity, which should be an Item projectile.
     * @return The ProjectileData associated with the entity, or null if not found.
     */
    public ProjectileData getProjectileData(Entity entity) {
        if (entity == null) return null;
        return activeProjectiles.get(entity.getUniqueId());
    }

    private void startUpdates() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
        }

        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (activeProjectiles.isEmpty()) {
                    return;
                }

                Iterator<Map.Entry<UUID, ProjectileData>> iterator = activeProjectiles.entrySet().iterator();

                while (iterator.hasNext()) {
                    ProjectileData data = iterator.next().getValue();
                    
                    // tickSingleProjectile now contains all the logic and returns true if the projectile should be removed.
                    boolean shouldRemove = tickSingleProjectile(data);

                    if (shouldRemove) {
                        iterator.remove();
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Contains all logic for updating a single projectile for one tick.
     * This was moved out of the old ProjectileData class.
     * @return True if the projectile hit something or expired and should be removed.
     */
    private boolean tickSingleProjectile(ProjectileData data) {
        data.incrementTicks();
        Item itemEntity = data.getItemEntity();
        Location loc = itemEntity.getLocation();

        if (!itemEntity.isValid() || itemEntity.isDead()) {
            return true; // Remove if invalid
        }

        if (data.getTrailParticle() != null) {
            loc.getWorld().spawnParticle(data.getTrailParticle(), loc, 1, 0, 0, 0, 0);
        }

        // Performant entity collision check
        if (data.canHitPlayer()) {
            for (LivingEntity nearby : loc.getNearbyLivingEntities(2.0)) {
                if (data.getIgnoredEntities().contains(nearby.getUniqueId())) continue;
                if (nearby instanceof Player p && p.getGameMode() == GameMode.SPECTATOR) continue;

                if (nearby.getBoundingBox().expand(data.getHitboxExpansion()).overlaps(itemEntity.getBoundingBox())) {
                    playImpactEffect(data, nearby.getEyeLocation());
                    data.getCallback().onCollide(nearby, null, data);
                    itemEntity.remove();
                    return true;
                }
            }
        }

        // Block collision check
        if (data.canHitBlock() && itemEntity.isOnGround()) {
            playImpactEffect(data, loc);
            data.getCallback().onCollide(null, loc.getBlock(), data);
            itemEntity.remove();
            return true;
        }

        // Expiration check
        if (data.getTicksLived() >= data.getMaxTicksLived()) {
            playImpactEffect(data, loc);
            data.getCallback().onCollide(null, null, data);
            itemEntity.remove();
            return true;
        }

        return false; // Keep projectile alive for next tick
    }

    private void playImpactEffect(ProjectileData data, Location location) {
        if (data.getImpactSound() != null) {
            location.getWorld().playSound(location, data.getImpactSound(), data.getSoundVolume(), data.getSoundPitch());
        }
    }

    public void stopUpdates() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    public void clearAllProjectiles() {
        for (ProjectileData data : activeProjectiles.values()) {
            if (data.getItemEntity() != null && data.getItemEntity().isValid()) {
                data.getItemEntity().remove();
            }
        }
        activeProjectiles.clear();
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (activeProjectiles.containsKey(event.getItem().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
