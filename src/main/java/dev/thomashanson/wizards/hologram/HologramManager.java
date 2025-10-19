package dev.thomashanson.wizards.hologram;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;

import dev.thomashanson.wizards.WizardsPlugin;
import net.kyori.adventure.text.Component;

public class HologramManager implements Listener {

    // TODO: Load from config.yml
    private static final int VIEW_DISTANCE_SQUARED = 40 * 40; // 40 blocks

    private final WizardsPlugin plugin;
    private final ProtocolManager protocolManager;

    // Main storage for all holograms
    private final Map<UUID, Hologram> activeHolograms = new ConcurrentHashMap<>();
    private final Map<UUID, Hologram> entityTrackedHolograms = new ConcurrentHashMap<>(); // Entity UUID -> Hologram
    private BukkitTask entityTrackingTask;

    // Tracks which players can see which holograms
    private final Map<UUID, Set<Hologram>> playerVisibleHolograms = new ConcurrentHashMap<>();

    public HologramManager(WizardsPlugin plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startEntityTrackingTask();
    }

    public void stopUpdates() {
        if (entityTrackingTask != null && !entityTrackingTask.isCancelled()) {
            entityTrackingTask.cancel();
        }

        // Create a copy of the list to avoid errors while modifying the original
        List<Hologram> hologramsToClear = new ArrayList<>(activeHolograms.values());
        hologramsToClear.forEach(this::deleteHologram);

        activeHolograms.clear();
        entityTrackedHolograms.clear();
        playerVisibleHolograms.clear();
    }

    public Hologram createHologram(Location location, List<Component> lines) {
        Hologram hologram = new Hologram(location, lines);
        activeHolograms.put(hologram.getHologramId(), hologram);
        // Check for nearby players who should see it immediately
        updateViewer(hologram);
        return hologram;
    }

    public void trackEntity(Entity entity, Hologram hologram) {
        entityTrackedHolograms.put(entity.getUniqueId(), hologram);
    }
    
    public void deleteHologram(Hologram hologram) {
        if (hologram == null) return;
        
        activeHolograms.remove(hologram.getHologramId());
        entityTrackedHolograms.values().remove(hologram);
        
        // Force-hide it from any player who could see it
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            hideHologram(player, hologram);
        }
    }

    @EventHandler
    private void onJoin(PlayerJoinEvent event) {
        playerVisibleHolograms.put(event.getPlayer().getUniqueId(), new HashSet<>());
        updateViewer(event.getPlayer()); // Check for holograms on login
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent event) {
        playerVisibleHolograms.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return; // Player only moved their head, no need to update
        }
        updateViewer(event.getPlayer());
    }
    
    private void updateViewer(Player player) {
        Set<Hologram> currentlyVisible = playerVisibleHolograms.getOrDefault(player.getUniqueId(), new HashSet<>());
        
        for (Hologram hologram : activeHolograms.values()) {
            boolean inRange = hologram.getLocation().distanceSquared(player.getLocation()) <= VIEW_DISTANCE_SQUARED;
            boolean canSee = currentlyVisible.contains(hologram);

            if (inRange && !canSee) {
                showHologram(player, hologram);
            } else if (!inRange && canSee) {
                hideHologram(player, hologram);
            }
        }
    }

    private void updateViewer(Hologram hologram) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (hologram.getLocation().distanceSquared(player.getLocation()) <= VIEW_DISTANCE_SQUARED) {
                showHologram(player, hologram);
            }
        }
    }

    private void showHologram(Player player, Hologram hologram) {
        Set<Hologram> visible = playerVisibleHolograms.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        if (visible.add(hologram)) { // Returns true if the hologram wasn't already in the set
            hologram.getLines().forEach(line -> {
                protocolManager.sendServerPacket(player, line.createSpawnPacket());
                protocolManager.sendServerPacket(player, line.createMetadataPacket());
            });
        }
    }

    private void hideHologram(Player player, Hologram hologram) {
        Set<Hologram> visible = playerVisibleHolograms.get(player.getUniqueId());
        if (visible != null && visible.remove(hologram)) { // Returns true if the hologram was in the set
            hologram.getLines().forEach(line -> protocolManager.sendServerPacket(player, line.createDestroyPacket()));
        }
    }

    private void startEntityTrackingTask() {
        this.entityTrackingTask = new BukkitRunnable() {
            @Override
            public void run() {
                // This loop runs ASYNCHRONOUSLY, so it doesn't lag the server.
                Iterator<Map.Entry<UUID, Hologram>> iterator = entityTrackedHolograms.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<UUID, Hologram> entry = iterator.next();
                    Hologram hologram = entry.getValue();
                    UUID entityUUID = entry.getKey();

                    // Schedule a NEW SYNCHRONOUS task to safely interact with the game world.
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            // This code block now runs on the MAIN SERVER THREAD.
                            Entity entity = plugin.getServer().getEntity(entityUUID);

                            if (entity == null || !entity.isValid() || entity.isDead()) {
                                deleteHologram(hologram);
                                // We must remove the entry from the original map, which is thread-safe (ConcurrentHashMap)
                                entityTrackedHolograms.remove(entityUUID);
                                return; // Stop this specific synchronous task
                            }

                            // The teleport method automatically updates all lines
                            hologram.teleport(entity.getLocation().add(0, 0.5, 0)); // Adjust offset as needed

                            // Broadcast teleport packets to players who can see this hologram
                            for (UUID playerUUID : playerVisibleHolograms.keySet()) {
                                if (playerVisibleHolograms.get(playerUUID).contains(hologram)) {
                                    Player p = plugin.getServer().getPlayer(playerUUID);
                                    if (p != null) {
                                        hologram.getLines().forEach(line -> protocolManager.sendServerPacket(p, line.createTeleportPacket()));
                                    }
                                }
                            }
                        }
                    }.runTask(plugin); // Use runTask() to execute on the next server tick.
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 2L);
    }
}