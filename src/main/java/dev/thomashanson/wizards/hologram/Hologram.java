package dev.thomashanson.wizards.hologram;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a single, client-side hologram object managed by the {@link HologramManager}.
 * This class provides the primary API for interacting with a hologram, such as updating
 * its text, changing its location, or attaching it to an entity.
 */
public final class Hologram {

    // Unique entity ID generator to prevent collisions
    private static final AtomicInteger ID_COUNTER = new AtomicInteger(Integer.MAX_VALUE - 100_000);

    private final HologramManager manager;
    private final int entityId;
    private final UUID entityUuid;
    private final HologramProperties properties;

    private Location location;
    private List<Component> defaultText;
    private final Map<UUID, List<Component>> perPlayerText = new ConcurrentHashMap<>();

    private UUID attachedEntityUuid;
    private Vector attachmentOffset;

    /**
     * Internal constructor. Holograms should be created via {@link HologramManager#createHologram}.
     */
    Hologram(@NotNull HologramManager manager, @NotNull Location location, @NotNull List<Component> initialText, @NotNull HologramProperties properties) {
        this.manager = manager;
        this.entityId = ID_COUNTER.decrementAndGet();
        this.entityUuid = UUID.randomUUID();
        this.location = location.clone();
        this.defaultText = List.copyOf(initialText);
        this.properties = properties;
        this.attachmentOffset = properties.attachmentOffset();
    }

    /**
     * Shows this hologram to a specific player.
     * If the hologram is public, this will override any range-based hiding.
     *
     * @param player The player to show the hologram to.
     */
    public void showTo(@NotNull Player player) {
        manager.showHologram(player, this);
    }

    /**
     * Hides this hologram from a specific player.
     * If the hologram is public, this will prevent the player from seeing it, even if they are in range.
     *
     * @param player The player to hide the hologram from.
     */
    public void hideFrom(@NotNull Player player) {
        manager.hideHologram(player, this);
    }

    /**
     * Updates the default text for this hologram.
     * This update will be broadcast to all players currently viewing it.
     *
     * @param newText The new list of {@link Component} lines.
     */
    public void updateText(@NotNull List<Component> newText) {
        this.defaultText = List.copyOf(newText);
        manager.updateHologramContent(this);
    }

    /**
     * Updates the text for a single player, overriding the default text.
     * This is highly efficient and suitable for frequent updates like scoreboards or countdowns.
     *
     * @param player  The player to receive the custom text.
     * @param newText The new list of {@link Component} lines for this player.
     * To remove the override and revert to default text, pass null or an empty list.
     */
    public void updateText(@NotNull Player player, @Nullable List<Component> newText) {
        if (newText == null || newText.isEmpty()) {
            if (perPlayerText.remove(player.getUniqueId()) != null) {
                // If an override was removed, force an update to show the default text
                manager.updateHologramContent(player, this);
            }
        } else {
            perPlayerText.put(player.getUniqueId(), List.copyOf(newText));
            manager.updateHologramContent(player, this);
        }
    }

    /**
     * Teleports the hologram to a new location.
     *
     * @param newLocation The target {@link Location}.
     */
    public void teleport(@NotNull Location newLocation) {
        if (isAttached()) {
            // Cannot manually teleport an attached hologram
            return;
        }
        if (newLocation.equals(this.location)) {
            return;
        }
        this.location = newLocation.clone();
        manager.teleportHologram(this);
    }

    /**
     * Attaches the hologram to an entity, causing it to follow the entity's movements.
     * The offset is taken from the {@link HologramProperties} provided at creation.
     *
     * @param entity The entity to attach to.
     */
    public void attachTo(@NotNull Entity entity) {
        attachTo(entity, this.properties.attachmentOffset());
    }

    /**
     * Attaches the hologram to an entity with a custom runtime offset.
     *
     * @param entity The entity to attach to.
     * @param offset The positional {@link Vector} offset from the entity's location.
     */
    public void attachTo(@NotNull Entity entity, @NotNull Vector offset) {
        this.attachedEntityUuid = entity.getUniqueId();
        this.attachmentOffset = offset;
        manager.addEntityTracking(this);
    }

    /**
     * Detaches the hologram from any entity it's currently following.
     * It will remain at its last known location.
     */
    public void detach() {
        if (!isAttached()) {
            return;
        }
        manager.removeEntityTracking(this);
        this.attachedEntityUuid = null;
    }

    /**
     * Permanently deletes this hologram, sending destroy packets to all viewers.
     * A deleted hologram cannot be reused.
     */
    public void delete() {
        manager.deleteHologram(this);
    }

    // Internal getters used by the manager
    int getEntityId() { return entityId; }
    UUID getEntityUuid() { return entityUuid; }
    Location getLocation() { return location; }
    HologramProperties getProperties() { return properties; }
    UUID getAttachedEntityUuid() { return attachedEntityUuid; }
    Vector getAttachmentOffset() { return attachmentOffset; }
    boolean isAttached() { return attachedEntityUuid != null; }

    /**
     * Gets the lines of text this hologram should display for a specific player.
     * Returns the player-specific override if it exists, otherwise returns the default text.
     *
     * @param player The player to get the text for.
     * @return The list of {@link Component} lines for the player.
     */
    @NotNull
    List<Component> getLinesFor(@NotNull Player player) {
        return perPlayerText.getOrDefault(player.getUniqueId(), defaultText);
    }

    /**
     * Sets the hologram's location internally. Called by the manager during entity tracking.
     *
     * @param location The new location.
     */
    void setLocationInternal(@NotNull Location location) {
        this.location = location;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Hologram hologram = (Hologram) o;
        return entityId == hologram.entityId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId);
    }
}