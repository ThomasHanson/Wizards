package dev.thomashanson.wizards.game.manager;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.projectile.ProjectileData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

public class ProjectileManager {

    private final WizardsPlugin plugin;

    private BukkitTask updateTask;
    private final Map<Entity, ProjectileData> thrown = new WeakHashMap<>();

    public ProjectileManager(WizardsPlugin plugin) {
        this.plugin = plugin;
        updateAll();
    }

    public void addThrow(Entity entity, ProjectileData data) {
        thrown.put(entity, data);
    }

    private void updateAll() {

        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {

            if (getPlugin().getGameManager().getActiveGame() == null)
                return;

            for (Iterator<Map.Entry<Entity, ProjectileData>> iterator = thrown.entrySet().iterator(); iterator.hasNext();) {

                Map.Entry<Entity, ProjectileData> entry = iterator.next();
                Entity key = entry.getKey();

                if (key.isDead() || !key.isValid() || thrown.get(key).hasCollided())
                    iterator.remove();
            }

            for (ProjectileData data : thrown.values())
                data.playEffect();

        }, 0L, 0L);
    }

    public void stopUpdates() {
        updateTask.cancel();
    }

    private WizardsPlugin getPlugin() {
        return plugin;
    }
}