package dev.thomashanson.wizards.hologram;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;

import net.kyori.adventure.text.Component;

/**
 * Manages the entire lifecycle of all holograms on the server.
 * This class handles packet creation, visibility tracking, and background tasks
 * for proximity checks and entity tracking.
 */
public final class HologramManager implements Listener {

    private final JavaPlugin plugin;
    private final ProtocolManager protocolManager;

    // Core data structures
    private final Map<Integer, Hologram> hologramsById = new ConcurrentHashMap<>();
    private final Map<UUID, Hologram> attachedHolograms = new ConcurrentHashMap<>(); // Attached Entity UUID -> Hologram
    private final Map<UUID, Set<Integer>> visibleHolograms = new ConcurrentHashMap<>(); // Player UUID -> Set of visible hologram entity IDs

    // Background tasks
    private BukkitTask proximityCheckTask;
    private BukkitTask entityTrackingTask;

    public HologramManager(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    /**
     * Initializes the manager, registers listeners, and starts background tasks.
     * Should be called in the plugin's onEnable method.
     */
    public void initialize() {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // This task periodically checks which players should see which public holograms.
        // It runs less frequently as it's less critical than smooth entity tracking.
        this.proximityCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runProximityCheck, 20L, 20L); // Run once per second

        // This task updates the position of holograms attached to entities.
        // It runs frequently to ensure smooth movement.
        this.entityTrackingTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runEntityTracking, 2L, 2L); // Run every 2 ticks
    }

    /**
     * Disables the manager, cancels tasks, and despawns all holograms for all players.
     * Should be called in the plugin's onDisable method to prevent ghost holograms.
     */
    public void shutdown() {
        if (proximityCheckTask != null) {
            proximityCheckTask.cancel();
        }
        if (entityTrackingTask != null) {
            entityTrackingTask.cancel();
        }

        // Despawn all holograms for all online players
        new ArrayList<>(hologramsById.values()).forEach(this::deleteHologram);

        hologramsById.clear();
        attachedHolograms.clear();
        visibleHolograms.clear();
    }

    /**
     * Creates a new hologram with default properties.
     *
     * @param location The initial location of the hologram.
     * @param text     The initial lines of text.
     * @return The created {@link Hologram} instance.
     */
    public Hologram createHologram(@NotNull Location location, @NotNull List<Component> text) {
        return createHologram(location, text, HologramProperties.builder().build());
    }

    /**
     * Creates a new hologram with custom properties.
     *
     * @param location   The initial location of the hologram.
     * @param text       The initial lines of text.
     * @param properties The {@link HologramProperties} to apply.
     * @return The created {@link Hologram} instance.
     */
    public Hologram createHologram(@NotNull Location location, @NotNull List<Component> text, @NotNull HologramProperties properties) {
        Hologram hologram = new Hologram(this, location, text, properties);
        hologramsById.put(hologram.getEntityId(), hologram);

        // If it's a public hologram, immediately check for nearby players
        if (properties.visibility() == HologramProperties.Visibility.PUBLIC) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isPlayerInViewDistance(player, hologram)) {
                    showHologram(player, hologram);
                }
            }
        }
        return hologram;
    }

    // --- Event Handlers ---

    @EventHandler
    private void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        visibleHolograms.put(player.getUniqueId(), ConcurrentHashMap.newKeySet());

        // When a player joins, check which public holograms they should see
        for (Hologram hologram : getPublicHolograms()) {
            if (isPlayerInViewDistance(player, hologram)) {
                showHologram(player, hologram);
            }
        }
    }

    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up memory for the quitting player
        visibleHolograms.remove(event.getPlayer().getUniqueId());
    }

    // --- Background Tasks ---

    /**
     * Task to check player proximity to public holograms. This is more performant
     * than listening to PlayerMoveEvent, as it decouples the check from player movement
     * and processes all players in a single batch.
     */
    private void runProximityCheck() {
        Collection<Hologram> publicHolograms = getPublicHolograms();
        if (publicHolograms.isEmpty()) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            Set<Integer> currentlyVisible = getVisibleHologramIds(player);

            for (Hologram hologram : publicHolograms) {
                boolean canSee = currentlyVisible.contains(hologram.getEntityId());
                boolean inRange = isPlayerInViewDistance(player, hologram);

                if (inRange && !canSee) {
                    showHologram(player, hologram);
                } else if (!inRange && canSee) {
                    hideHologram(player, hologram);
                }
            }
        }
    }

    /**
     * Task to update the locations of entity-attached holograms.
     */
    private void runEntityTracking() {
        if (attachedHolograms.isEmpty()) {
            return;
        }

        // Use an iterator to safely remove entries while iterating
        attachedHolograms.entrySet().removeIf(entry -> {
            Entity entity = Bukkit.getEntity(entry.getKey());
            Hologram hologram = entry.getValue();

            if (entity == null || !entity.isValid()) {
                // Entity is gone, delete the hologram and stop tracking it.
                deleteHologram(hologram);
                return true; // Remove from map
            }

            Location newLocation = entity.getLocation().add(hologram.getAttachmentOffset());
            hologram.setLocationInternal(newLocation);
            teleportHologram(hologram);
            return false; // Keep in map
        });
    }

    // --- Internal API for Hologram Class ---

    void showHologram(@NotNull Player player, @NotNull Hologram hologram) {
        Set<Integer> visibleIds = getVisibleHologramIds(player);
        if (visibleIds.add(hologram.getEntityId())) {
            sendPacket(player, createSpawnPacket(hologram));
            sendPacket(player, createMetadataPacket(hologram, player));
        }
    }

    void hideHologram(@NotNull Player player, @NotNull Hologram hologram) {
        Set<Integer> visibleIds = getVisibleHologramIds(player);
        if (visibleIds.remove(hologram.getEntityId())) {
            sendPacket(player, createDestroyPacket(hologram));
        }
    }

    void deleteHologram(@NotNull Hologram hologram) {
        if (hologramsById.remove(hologram.getEntityId()) == null) {
            return; // Already deleted
        }

        if (hologram.isAttached()) {
            removeEntityTracking(hologram);
        }

        // Despawn for all players who can currently see it
        for (Player player : Bukkit.getOnlinePlayers()) {
            hideHologram(player, hologram);
        }
    }

    void updateHologramContent(@NotNull Hologram hologram) {
        // Update for all players currently viewing this hologram
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isViewer(player, hologram)) {
                sendPacket(player, createMetadataPacket(hologram, player));
            }
        }
    }



    void updateHologramContent(@NotNull Player player, @NotNull Hologram hologram) {
        // Update for a single player, only if they can see it
        if (isViewer(player, hologram)) {
            sendPacket(player, createMetadataPacket(hologram, player));
        }
    }

    void teleportHologram(@NotNull Hologram hologram) {
        PacketContainer teleportPacket = createTeleportPacket(hologram);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isViewer(player, hologram)) {
                sendPacket(player, teleportPacket);
            }
        }
    }

    void addEntityTracking(@NotNull Hologram hologram) {
        if (hologram.getAttachedEntityUuid() == null) return;
        attachedHolograms.put(hologram.getAttachedEntityUuid(), hologram);
    }

    void removeEntityTracking(@NotNull Hologram hologram) {
        if (hologram.getAttachedEntityUuid() == null) return;
        attachedHolograms.remove(hologram.getAttachedEntityUuid());
    }

    // --- Packet Creation ---

    private PacketContainer createSpawnPacket(Hologram hologram) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY);
        Location loc = hologram.getLocation();

        packet.getIntegers().write(0, hologram.getEntityId());
        packet.getUUIDs().write(0, hologram.getEntityUuid());
        packet.getEntityTypeModifier().write(0, EntityType.TEXT_DISPLAY);
        packet.getDoubles()
            .write(0, loc.getX())
            .write(1, loc.getY())
            .write(2, loc.getZ());
        packet.getBytes()
            .write(0, (byte) (loc.getPitch() * 256.0F / 360.0F))
            .write(1, (byte) (loc.getYaw() * 256.0F / 360.0F));

        return packet;
    }

    private PacketContainer createMetadataPacket(Hologram hologram, Player player) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
        packet.getIntegers().write(0, hologram.getEntityId());

        HologramProperties props = hologram.getProperties();
        List<Component> lines = hologram.getLinesFor(player);

        // Combine all Adventure lines into one
        Component combinedText = Component.join(Component.newline(), lines);

        // Convert Adventure -> JSON -> WrappedChatComponent
        String json = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(combinedText);
        WrappedChatComponent wrappedChatComponent = WrappedChatComponent.fromJson(json);

        // Unwrap to the native handle (IChatBaseComponent) for compatibility
        Object nmsComponentHandle = wrappedChatComponent.getHandle();

        // Wrap inside an Optional of the handle
        Optional<Object> optionalComponent = Optional.ofNullable(nmsComponentHandle);

        List<WrappedDataValue> dataValues = new ArrayList<>();

        // Index 23 = TextDisplay text field (expects Optional<IChatBaseComponent>)
        dataValues.add(new WrappedDataValue(
            23,
            WrappedDataWatcher.Registry.getChatComponentSerializer(true),
            optionalComponent
        ));

        packet.getDataValueCollectionModifier().write(0, dataValues);
        return packet;
    }

    private PacketContainer createTeleportPacket(Hologram hologram) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_TELEPORT);
        Location loc = hologram.getLocation();

        packet.getIntegers().write(0, hologram.getEntityId());
        packet.getDoubles()
            .write(0, loc.getX())
            .write(1, loc.getY())
            .write(2, loc.getZ());
        packet.getBytes()
            .write(0, (byte) (loc.getYaw() * 256.0F / 360.0F))
            .write(1, (byte) (loc.getPitch() * 256.0F / 360.0F));
        packet.getBooleans().write(0, true); // onGround

        return packet;
    }

    private PacketContainer createDestroyPacket(Hologram hologram) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
        packet.getIntLists().write(0, List.of(hologram.getEntityId()));
        return packet;
    }

    // --- Utility Methods ---

    private void sendPacket(Player player, PacketContainer packet) {
        if (packet == null) return;
        try {
            protocolManager.sendServerPacket(player, packet);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to send hologram packet to " + player.getName());
            e.printStackTrace();
        }
    }
    
    private Set<Integer> getVisibleHologramIds(Player player) {
        return visibleHolograms.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
    }

    private boolean isViewer(Player player, Hologram hologram) {
        return getVisibleHologramIds(player).contains(hologram.getEntityId());
    }

    private boolean isPlayerInViewDistance(Player player, Hologram hologram) {
        Location playerLoc = player.getLocation();
        Location hologramLoc = hologram.getLocation();

        // Check world first for efficiency
        if (playerLoc.getWorld() != hologramLoc.getWorld()) {
            return false;
        }

        int viewDistance = hologram.getProperties().viewDistance();
        return playerLoc.distanceSquared(hologramLoc) <= (long) viewDistance * viewDistance;
    }

    private Collection<Hologram> getPublicHolograms() {
        return hologramsById.values().stream()
            .filter(h -> h.getProperties().visibility() == HologramProperties.Visibility.PUBLIC)
            .toList();
    }
}