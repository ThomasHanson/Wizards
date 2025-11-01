package dev.thomashanson.wizards.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;

import dev.thomashanson.wizards.event.EquipmentSendingEvent;

/**
 * An abstract utility class for intercepting and modifying entity equipment packets
 * sent to players. This allows for client-side customization of what a player sees
 * other entities wearing, without changing the server-side reality.
 * <p>
 * This class relies on ProtocolLib to listen to {@link PacketType.Play.Server#ENTITY_EQUIPMENT}
 * and {@link PacketType.Play.Server#NAMED_ENTITY_SPAWN} packets.
 * <p>
 * Implementations must override {@link #onEquipmentSending(EquipmentSendingEvent)}
 * to define the custom logic for modifying equipment.
 *
 * @author Kristian
 */
public abstract class FakeEquipment {

    private Map<Object, EnumWrappers.ItemSlot> processedPackets = new WeakHashMap<>();

    private final Plugin plugin;
    private final ProtocolManager manager;

    private PacketListener listener;

    public FakeEquipment(Plugin plugin) {

        this.plugin = plugin;
        this.manager = ProtocolLibrary.getProtocolManager();

        manager.addPacketListener (

                listener = new PacketAdapter(plugin, PacketType.Play.Server.ENTITY_EQUIPMENT, PacketType.Play.Server.NAMED_ENTITY_SPAWN) {

                    @Override
                    public void onPacketSending(PacketEvent event) {

                        PacketContainer packet = event.getPacket();
                        PacketType type = event.getPacketType();

                        LivingEntity visibleEntity = (LivingEntity) packet.getEntityModifier(event).read(0);

                        Player observingPlayer = event.getPlayer();

                        if (type == PacketType.Play.Server.ENTITY_EQUIPMENT) {

                            Pair<EnumWrappers.ItemSlot, ItemStack> pair = packet.getSlotStackPairLists().read(0).get(0);

                            EnumWrappers.ItemSlot slot = pair.getFirst();
                            ItemStack equipment = pair.getSecond();

                            EquipmentSendingEvent sendingEvent = new EquipmentSendingEvent(observingPlayer, visibleEntity, slot, equipment);

                            if (sendingEvent.isCancelled())
                                return;

                            EnumWrappers.ItemSlot previous = processedPackets.get(packet.getHandle());

                            if (previous != null) {

                                // Clone it - otherwise, we'll lose the old modification
                                packet = event.getPacket().deepClone();
                                sendingEvent.setSlot(previous);

                                sendingEvent.setEquipment(Objects.requireNonNull(visibleEntity.getEquipment()).getItem(EquipmentSlot.HAND));
                            }

                            if (onEquipmentSending(sendingEvent))
                                processedPackets.put(packet.getHandle(), previous != null ? previous : slot);

                            // Save changes
                            if (slot != sendingEvent.getSlot()) {

                                List<Pair<EnumWrappers.ItemSlot, ItemStack>> pairList = new ArrayList<>();
                                pairList.add(new Pair<>(slot, Objects.requireNonNull(visibleEntity.getEquipment()).getItem(getSlot(slot))));

                                packet.getSlotStackPairLists().write(0, pairList);
                            }

                            if (equipment != sendingEvent.getEquipment()) {

                                List<Pair<EnumWrappers.ItemSlot, ItemStack>> pairList = new ArrayList<>();
                                pairList.add(new Pair<>(sendingEvent.getSlot(), sendingEvent.getEquipment()));

                                packet.getSlotStackPairLists().write(0, pairList);
                            }
                        }

                        /*
                        if (type == PacketType.Play.Server.ENTITY_EQUIPMENT) {

                            EquipmentSlot slot = EquipmentSlot.fromId(packet.getIntegers().read(1));
                            ItemStack equipment = packet.getItemModifier().read(0);

                            EquipmentSendingEvent sendingEvent = new EquipmentSendingEvent(observingPlayer, visibleEntity, slot, equipment);

                            if (sendingEvent.isCancelled())
                                return;

                            // Assume we process all packets - the overhead isn't that bad
                            EquipmentSlot previous = processedPackets.get(packet.getHandle());

                            // See if this packet instance has already been processed
                            if (previous != null) {
                                // Clone it - otherwise, we'll lose the old modification
                                packet = event.getPacket().deepClone();
                                sendingEvent.setSlot(previous);
                                sendingEvent.setEquipment(Objects.requireNonNull(previous.getEquipment(visibleEntity)).clone());
                            }

                            if (onEquipmentSending(sendingEvent))
                                processedPackets.put(packet.getHandle(), previous != null ? previous : slot);

                            // Save changes
                            if (slot != sendingEvent.getSlot())
                                packet.getIntegers().write(1, slot.getId());

                            if (equipment != sendingEvent.getEquipment())
                                packet.getItemModifier().write(0, sendingEvent.getEquipment());

                        } else {

                            // Trigger updates?
                            onEntitySpawn(observingPlayer, visibleEntity);
                        }
                        */
                    }

                    @Override
                    public void onPacketReceiving(PacketEvent event) {
                        DebugUtil.debugMessage("Hey", event.getPlayer());
                    }
                }
        );
    }

    /**
     * Invoked when a living entity has been spawned on the given client.
     * @param client - the client.
     * @param visibleEntity - the visibleEntity.
     */
    protected void onEntitySpawn(Player client, LivingEntity visibleEntity) {
        // Update all the slots?
    }

    /**
     * Invoked when the equipment or held item of a living entity is sent to a client.
     * <p>
     * This method can be used to modify the {@link EquipmentSendingEvent} to change
     * the {@link ItemStack} or {@link EnumWrappers.ItemSlot} being sent.
     *
     * @param equipmentEvent The event containing packet and entity data.
     * @return {@code true} if the equipment was modified, {@code false} otherwise.
     */
    protected abstract boolean onEquipmentSending(EquipmentSendingEvent equipmentEvent);

    /**
     * Converts a ProtocolLib {@link EnumWrappers.ItemSlot} to a Bukkit {@link EquipmentSlot}.
     *
     * @param slot The ProtocolLib slot.
     * @return The corresponding Bukkit slot.
     */
    public EquipmentSlot getSlot(EnumWrappers.ItemSlot slot) {

        switch (slot) {

            case MAINHAND: return EquipmentSlot.HAND;
            case OFFHAND: return EquipmentSlot.OFF_HAND;
            case FEET: return EquipmentSlot.FEET;
            case LEGS: return EquipmentSlot.LEGS;
            case CHEST: return EquipmentSlot.CHEST;
            default: return EquipmentSlot.HEAD;
        }
    }

    /**
     * Forcibly sends an update packet for a specific slot to a client.
     *
     * @param client        The observing client.
     * @param visibleEntity The entity that will be updated.
     * @param slot          The equipment slot to update.
     */
    public void updateSlot(final Player client, LivingEntity visibleEntity, EquipmentSlot slot) {

        if (listener == null)
            throw new IllegalStateException("FakeEquipment has closed.");

        final PacketContainer equipmentPacket = new PacketContainer(PacketType.Play.Server.ENTITY_EQUIPMENT);

        equipmentPacket.getIntegers().write(0, visibleEntity.getEntityId());

        /*
        equipmentPacket.getItemSlots().write(0, slot.toWrapper());
        equipmentPacket.getItemModifier().write(0, slot.getEquipment(visibleEntity));
         */

        // We have to send the packet AFTER named entity spawn has been sent
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            ProtocolLibrary.getProtocolManager().sendServerPacket(client, equipmentPacket);
        });
    }

    /**
     * Disposes of this listener, unregistering it from ProtocolLib.
     * This must be called when the plugin is disabled to prevent memory leaks.
     */
    public void close() {

        if (listener != null) {
            manager.removePacketListener(listener);
            listener = null;
        }
    }

    public PacketListener getListener() {
        return listener;
    }
}