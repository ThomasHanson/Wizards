package dev.thomashanson.wizards.game.state.types;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.FieldAccessException;
import com.comphenix.protocol.wrappers.WrappedBlockData;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.overtime.Disaster;
import dev.thomashanson.wizards.game.state.GameState;
import dev.thomashanson.wizards.game.state.listener.StateListenerProvider;
import dev.thomashanson.wizards.map.LocalGameMap;
import dev.thomashanson.wizards.util.BlockUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class OvertimeState extends GameState implements Listener {

    // A unique entity ID counter for the fake packets to prevent collision with real entities.
    private static final AtomicInteger FAKE_ENTITY_ID_COUNTER = new AtomicInteger(Integer.MAX_VALUE);

    // --- All values below should be loaded from a configuration file (e.g., config.yml) ---
    private static final Duration OVERTIME_DURATION = Duration.ofMinutes(10);
    private static final Duration DISASTER_MESSAGE_INTERVAL = Duration.ofSeconds(2);
    private static final long OVERTIME_WORLD_TIME = 15000L;
    private static final double MINIMUM_MAP_DIMENSION = 5.0;
    private static final Material IGNORE_SHRINK_MATERIAL = Material.BEDROCK;
    private static final int BLOCKS_TO_PROCESS_PER_TICK = 200; // The batch size for how many blocks to change per tick.
    private static final int ASYNC_COLLECTOR_INTERVAL_TICKS = 40; // Collect new blocks every 2 seconds.
    private static final int VISUAL_RANGE_BLOCKS = 64;

    private BukkitTask updateTask;
    private Instant lastMessageTime;
    private Disaster disaster;

    // --- High-Performance Map Shrinking Fields ---
    private final Queue<Location> blockProcessingQueue = new ConcurrentLinkedQueue<>();
    private BukkitTask asyncCollectorTask;
    private BukkitTask syncProcessorTask;

    private double initialMinX, initialMaxX, initialMinZ, initialMaxZ;
    private double mapMinY, mapMaxY;
    private double totalShrinkableWidth, totalShrinkableDepth;

    @Override
    public void onEnable(WizardsPlugin plugin) {
        super.onEnable(plugin);
        this.disaster = getGame().getDisaster();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        LocalGameMap map = getGame().getActiveMap();
        if (map == null || !map.isLoaded()) {
            getPlugin().getLogger().severe("[OvertimeState] CRITICAL: ActiveMap is null or not loaded. Cannot start overtime.");
            setState(new LobbyState());
            return;
        }

        initializeDimensions(map);
        startCoreTasks(plugin);
        startShrinkerTasks(plugin);

        Bukkit.getLogger().info("[OvertimeState] Enabled. High-performance map shrinking initiated.");
    }

    private void initializeDimensions(LocalGameMap map) {
        this.initialMinX = map.getBounds().getMinX();
        this.initialMaxX = map.getBounds().getMaxX();
        this.initialMinZ = map.getBounds().getMinZ();
        this.initialMaxZ = map.getBounds().getMaxZ();
        this.mapMinY = map.getBounds().getMinY();
        this.mapMaxY = map.getBounds().getMaxY();

        if (mapMinY >= mapMaxY || this.initialMinX >= this.initialMaxX || this.initialMinZ >= this.initialMaxZ) {
            getPlugin().getLogger().severe("[OvertimeState] CRITICAL: Invalid initial map dimensions. Aborting overtime.");
            setState(new LobbyState());
            return;
        }

        getGame().initializeOvertimeBorders();

        this.totalShrinkableWidth = Math.max(0, (this.initialMaxX - this.initialMinX) - MINIMUM_MAP_DIMENSION);
        this.totalShrinkableDepth = Math.max(0, (this.initialMaxZ - this.initialMinZ) - MINIMUM_MAP_DIMENSION);
    }

    private void startCoreTasks(WizardsPlugin plugin) {
        AtomicInteger messageIndex = new AtomicInteger(0);
        this.lastMessageTime = Instant.now();

        this.updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (Duration.between(getStartTime(), Instant.now()).compareTo(OVERTIME_DURATION) >= 0) {
                    setState(new ThanosState());
                    return;
                }

                if (Duration.between(lastMessageTime, Instant.now()).compareTo(DISASTER_MESSAGE_INTERVAL) >= 0) {
                    lastMessageTime = Instant.now();
                    if (messageIndex.get() < disaster.getMessages().size()) {
                        String messageKey = disaster.getMessages().get(messageIndex.getAndIncrement());
                        plugin.getGameManager().announce("", false); // Blank line
                        plugin.getGameManager().announce(messageKey, true);
                        plugin.getGameManager().announce("", false); // Blank line
                    }
                }

                World world = getGame().getActiveMap().getWorld();
                if (world != null) {
                    world.setTime(OVERTIME_WORLD_TIME);
                }
                disaster.update();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void startShrinkerTasks(WizardsPlugin plugin) {
        // ---- ASYNC COLLECTOR TASK (Producer) ----
        // Periodically calculates which block layers should be removed and adds their locations to the queue.
        this.asyncCollectorTask = new BukkitRunnable() {
            // Store the last integer coordinate boundaries that we have processed.
            private int lastMinX = (int) Math.floor(initialMinX);
            private int lastMaxX = (int) Math.ceil(initialMaxX);
            private int lastMinZ = (int) Math.floor(initialMinZ);
            private int lastMaxZ = (int) Math.ceil(initialMaxZ);

            @Override
            public void run() {
                long elapsedMillis = Duration.between(getStartTime(), Instant.now()).toMillis();
                if (elapsedMillis >= OVERTIME_DURATION.toMillis()) {
                    this.cancel();
                    return;
                }
                double elapsedRatio = Math.min(1.0, (double) elapsedMillis / OVERTIME_DURATION.toMillis());

                // Calculate the new ideal floating-point boundaries
                double idealMinX = initialMinX + (totalShrinkableWidth / 2.0) * elapsedRatio;
                double idealMaxX = initialMaxX - (totalShrinkableWidth / 2.0) * elapsedRatio;
                double idealMinZ = initialMinZ + (totalShrinkableDepth / 2.0) * elapsedRatio;
                double idealMaxZ = initialMaxZ - (totalShrinkableDepth / 2.0) * elapsedRatio;

                // Convert new ideal boundaries to integer block coordinates
                int newMinX = (int) Math.floor(idealMinX);
                int newMaxX = (int) Math.ceil(idealMaxX);
                int newMinZ = (int) Math.floor(idealMinZ);
                int newMaxZ = (int) Math.ceil(idealMaxZ);

                // Collect blocks between the last integer boundary and the new one
                List<Location> locationsToQueue = new ArrayList<>();
                World world = getGame().getActiveMap().getWorld();

                // Collect positive X direction (minX is increasing)
                collectBlocks(world, lastMinX, newMinX, lastMinZ, lastMaxZ, true, locationsToQueue);
                // Collect negative X direction (maxX is decreasing)
                collectBlocks(world, newMaxX, lastMaxX, lastMinZ, lastMaxZ, true, locationsToQueue);
                // Collect positive Z direction (minZ is increasing)
                collectBlocks(world, lastMinX, lastMaxX, lastMinZ, newMinZ, false, locationsToQueue);
                // Collect negative Z direction (maxZ is decreasing)
                collectBlocks(world, lastMinX, lastMaxX, newMaxZ, lastMaxZ, false, locationsToQueue);

                blockProcessingQueue.addAll(locationsToQueue);

                // Update the last known integer boundaries for the next run
                lastMinX = newMinX;
                lastMaxX = newMaxX;
                lastMinZ = newMinZ;
                lastMaxZ = newMaxZ;
            }

            private void collectBlocks(World world, int startPrimary, int endPrimary, int startOrth, int endOrth, boolean isX, List<Location> collection) {
                // Ensure we only iterate if there's a change
                if (startPrimary == endPrimary) return;

                for (int p = startPrimary; p < endPrimary; p++) {
                    for (int o = startOrth; o < endOrth; o++) {
                        for (int y = (int) mapMinY; y <= (int) mapMaxY; y++) {
                            collection.add(isX ? new Location(world, p, y, o) : new Location(world, o, y, p));
                        }
                    }
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0L, ASYNC_COLLECTOR_INTERVAL_TICKS);

        // ---- SYNC PROCESSOR TASK (Consumer) ----
        // Runs every tick to process a small batch of blocks from the queue.
        this.syncProcessorTask = new BukkitRunnable() {
            int ticksElapsed = 0;

            @Override
            public void run() {
                if (blockProcessingQueue.isEmpty() && Duration.between(getStartTime(), Instant.now()).compareTo(OVERTIME_DURATION) > 0) {
                    this.cancel(); // Stop if queue is empty and we are past overtime duration.
                    return;
                }

                for (int i = 0; i < BLOCKS_TO_PROCESS_PER_TICK && !blockProcessingQueue.isEmpty(); i++) {
                    Location loc = blockProcessingQueue.poll();
                    if (loc == null || loc.getWorld() == null) continue;

                    Block block = loc.getBlock();
                    if (block.getType().isAir() || block.getType() == IGNORE_SHRINK_MATERIAL) {
                        continue;
                    }

                    sendFakeFallingBlockPacket(loc, block.getBlockData()); // Create visual effect
                    block.setType(Material.AIR, false); // Change server state without physics updates
                }

                // Periodically update the game's official world border
                if (++ticksElapsed % 20 == 0) {
                    long elapsedMillis = Duration.between(getStartTime(), Instant.now()).toMillis();
                    double elapsedRatio = Math.min(1.0, (double) elapsedMillis / OVERTIME_DURATION.toMillis());

                    double idealMinX = initialMinX + (totalShrinkableWidth / 2.0) * elapsedRatio;
                    double idealMaxX = initialMaxX - (totalShrinkableWidth / 2.0) * elapsedRatio;
                    double idealMinZ = initialMinZ + (totalShrinkableDepth / 2.0) * elapsedRatio;
                    double idealMaxZ = initialMaxZ - (totalShrinkableDepth / 2.0) * elapsedRatio;

                    getGame().updateOvertimeBorders(idealMinX, idealMaxX, idealMinZ, idealMaxZ);
                }
            }
        }.runTaskTimer(plugin, 5L, 1L);
    }

    private void sendFakeFallingBlockPacket(Location loc, BlockData blockData) {

        if (syncProcessorTask == null || syncProcessorTask.isCancelled()) {
            return;
        }

        try {
            ProtocolManager manager = ProtocolLibrary.getProtocolManager();
            if (manager == null) return;

            // Use Paper's efficient player iteration to find nearby players
            List<Player> nearbyPlayers = new ArrayList<>();
            final double visualRangeSquared = VISUAL_RANGE_BLOCKS * VISUAL_RANGE_BLOCKS;
            for (Player player : loc.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(loc) < visualRangeSquared) {
                    nearbyPlayers.add(player);
                }
            }

            if (nearbyPlayers.isEmpty()) return;

            WrappedBlockData wrappedData = WrappedBlockData.createData(blockData);

            PacketContainer packet = manager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
            packet.getIntegers().write(0, FAKE_ENTITY_ID_COUNTER.decrementAndGet()); // Entity ID
            packet.getEntityTypeModifier().write(0, EntityType.FALLING_BLOCK);

            packet.getDoubles()
                    .write(0, loc.getX() + 0.5)
                    .write(1, loc.getY())
                    .write(2, loc.getZ() + 0.5);

            // The 'data' field for a falling block is its block state ID.
            // We write the INTEGER ID to the INTEGER field at index 4.
            packet.getIntegers().write(4, BlockUtil.getBlockStateId(blockData));

            for (Player player : nearbyPlayers) {
                manager.sendServerPacket(player, packet);
            }

        } catch (FieldAccessException e) {
            getPlugin().getLogger().severe("Error sending fake falling block packet. Visuals will be disabled to prevent console spam.");
            e.printStackTrace();

            // Cancel the task to stop it from running again and causing more errors.
            if (syncProcessorTask != null) {
                syncProcessorTask.cancel();
            }
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (updateTask != null) updateTask.cancel();
        if (asyncCollectorTask != null) asyncCollectorTask.cancel();
        if (syncProcessorTask != null) syncProcessorTask.cancel();
        blockProcessingQueue.clear();

        if (getGame().areOvertimeBordersActive()) getGame().resetOvertimeBorders();
        HandlerList.unregisterAll(this);
        Bukkit.getLogger().info("[OvertimeState] Disabled. Optimized map shrinking stopped.");
    }

    @EventHandler
    public void onServerPing(ServerListPingEvent event) {
        Wizards game = getGame();
        if (game == null || disaster == null || game.getCurrentMode() == null) return;
        event.setMaxPlayers(game.getCurrentMode().getMaxPlayers());

        int width = (int) (game.getCurrentMaxX() - game.getCurrentMinX());
        int depth = (int) (game.getCurrentMaxZ() - game.getCurrentMinZ());

        // Use modern Paper Adventure components for the MOTD
        Component mapSizeComponent = Component.text(" | Map: ", NamedTextColor.GRAY)
                .append(Component.text(width + "x" + depth, NamedTextColor.WHITE));

        Component motd = Component.text(game.getCurrentMode().toString() + " - Overtime", NamedTextColor.RED)
                .append(Component.newline())
                .append(Component.text("Disaster: ", NamedTextColor.GOLD))
                .append(Component.text(disaster.getName(), NamedTextColor.YELLOW))
                .append(mapSizeComponent);

        event.motd(motd);
    }

    @Override
    protected StateListenerProvider getListenerProvider() {
        return null;
    }
}