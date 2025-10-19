package dev.thomashanson.wizards.hologram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Represents a multi-line hologram at a specific location.
 * This class manages a collection of individual HologramLine entities.
 */
public class Hologram {

    private static final double LINE_SPACING = 0.3;

    private final UUID hologramId = UUID.randomUUID();
    private final List<HologramLine> lines = new ArrayList<>();
    private Location location;

    public Hologram(Location location, List<Component> textLines) {
        this.location = location.clone();
        setLines(textLines);
    }

    // Manually added getters
    public UUID getHologramId() {
        return this.hologramId;
    }

    public List<HologramLine> getLines() {
        return this.lines;
    }

    public Location getLocation() {
        return this.location;
    }

    public void setLines(List<Component> textLines) {
        lines.clear();
        for (int i = 0; i < textLines.size(); i++) {
            Location lineLocation = location.clone().subtract(0, i * LINE_SPACING, 0);
            lines.add(new HologramLine(lineLocation, textLines.get(i)));
        }
    }

    public void teleport(Location newLocation) {
        this.location = newLocation.clone();
        for (int i = 0; i < lines.size(); i++) {
            lines.get(i).teleport(location.clone().subtract(0, i * LINE_SPACING, 0));
        }
    }

    /**
     * Represents a single line of a hologram, which is a single Armor Stand entity.
     */
    protected static class HologramLine {
        private final int entityId;
        private Location location;
        private final Component text;

        public HologramLine(Location location, Component text) {
            this.entityId = ThreadLocalRandom.current().nextInt();
            this.location = location;
            this.text = text;
        }

        // Manually added getters
        public int getEntityId() {
            return this.entityId;
        }

        public Location getLocation() {
            return this.location;
        }

        public Component getText() {
            return this.text;
        }

        public void teleport(Location newLocation) {
            this.location = newLocation;
        }

        public PacketContainer createSpawnPacket() {
            PacketContainer packet = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY);
            packet.getIntegers().write(0, entityId);
            packet.getUUIDs().write(0, UUID.randomUUID());
            packet.getEntityTypeModifier().write(0, EntityType.ARMOR_STAND);
            packet.getDoubles()
                .write(0, location.getX())
                .write(1, location.getY())
                .write(2, location.getZ());
            return packet;
        }

        public PacketContainer createMetadataPacket() {
            PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().write(0, entityId);

            String legacyText = LegacyComponentSerializer.legacySection().serialize(text);

            List<WrappedDataValue> dataValues = List.of(
                new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class), (byte) 0x20), // Invisible
                new WrappedDataValue(2, WrappedDataWatcher.Registry.getChatComponentSerializer(), Optional.of(WrappedChatComponent.fromLegacyText(legacyText).getHandle())),
                new WrappedDataValue(3, WrappedDataWatcher.Registry.get(Boolean.class), true), // Custom Name Visible
                new WrappedDataValue(15, WrappedDataWatcher.Registry.get(Byte.class), (byte) 0x10) // Marker Armor Stand
            );
            packet.getDataValueCollectionModifier().write(0, dataValues);
            return packet;
        }

        public PacketContainer createTeleportPacket() {
            PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_TELEPORT);
            packet.getIntegers().write(0, entityId);
            packet.getDoubles()
                .write(0, location.getX())
                .write(1, location.getY())
                .write(2, location.getZ());
            return packet;
        }

        public PacketContainer createDestroyPacket() {
            PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
            packet.getIntLists().write(0, Collections.singletonList(entityId));
            return packet;
        }
    }
}