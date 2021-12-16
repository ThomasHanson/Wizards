package dev.thomashanson.wizards.util;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.google.common.collect.MapMaker;
import dev.thomashanson.wizards.event.EquipmentSendingEvent;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import static com.comphenix.protocol.PacketType.Play.Server.ENTITY_EQUIPMENT;

/**
 * Modify player equipment.
 * @author Kristian
 */
public abstract class FakeEquipment {

    public enum EquipmentSlot {

        HELD(0),
        HELD_OFFHAND(1),
        BOOTS(2),
        LEGGINGS(3),
        CHESTPLATE(4),
        HELMET(5);

        private final int id;

        EquipmentSlot(int id) {
            this.id = id;
        }

        /**
         * Retrieve the entity's equipment in the current slot.
         * @param entity - the entity.
         * @return The equipment.
         */
        ItemStack getEquipment(LivingEntity entity) {

            if (entity.getEquipment() == null)
                return null;

            switch (this) {
                case HELD: return entity.getEquipment().getItemInMainHand();
                case BOOTS: return entity.getEquipment().getBoots();
                case LEGGINGS: return entity.getEquipment().getLeggings();
                case CHESTPLATE: return entity.getEquipment().getChestplate();
                case HELMET: return entity.getEquipment().getHelmet();
                default: throw new IllegalArgumentException("Unknown slot: " + this);
            }
        }

        /**
         * Determine if the entity has an equipment in the current slot.
         * @param entity - the entity.
         * @return True if it is empty, false otherwise.
         */
        public boolean isEmpty(LivingEntity entity) {
            ItemStack stack = getEquipment(entity);
            return stack != null && stack.getType() == Material.AIR;
        }

        /**
         * Retrieve the underlying equipment slot ID.
         * @return The ID.
         */
        int getId() {
            return id;
        }

        EnumWrappers.ItemSlot toWrapper() {

            switch (this) {

                case BOOTS: return EnumWrappers.ItemSlot.FEET;
                case LEGGINGS: return EnumWrappers.ItemSlot.LEGS;
                case CHESTPLATE: return EnumWrappers.ItemSlot.CHEST;
                case HELMET: return EnumWrappers.ItemSlot.HEAD;

                case HELD_OFFHAND: return EnumWrappers.ItemSlot.OFFHAND;
                default: return EnumWrappers.ItemSlot.MAINHAND;
            }
        }

        /**
         * Find the corresponding equipment slot.
         * @param id - the slot ID.
         * @return The equipment slot.
         */
        public static EquipmentSlot fromId(int id) {

            for (EquipmentSlot slot : values())
                if (slot.getId() == id)
                    return slot;

            throw new IllegalArgumentException("Cannot find slot id: " + id);
        }
    }

    // Necessary to detect duplicate
    private Map<Object, EquipmentSlot> processedPackets = new MapMaker().weakKeys().makeMap();

    private final Plugin plugin;
    private final ProtocolManager manager;

    private PacketListener listener;

    protected FakeEquipment(Plugin plugin) {

        this.plugin = plugin;
        this.manager = ProtocolLibrary.getProtocolManager();

        /*
        manager.addPacketListener (

                listener = new PacketAdapter(plugin, ENTITY_EQUIPMENT, NAMED_ENTITY_SPAWN) {

                    @Override
                    public void onPacketSending(PacketEvent event) {

                        PacketContainer packet = event.getPacket();
                        PacketType type = event.getPacketType();

                        // The entity that is being displayed on the player's screen
                        LivingEntity visibleEntity = (LivingEntity) packet.getEntityModifier(event).read(0);
                        Player observingPlayer = event.getPlayer();

                        if (ENTITY_EQUIPMENT.equals(type)) {

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

                        } else if (NAMED_ENTITY_SPAWN.equals(type)) {

                            // Trigger updates?
                            onEntitySpawn(observingPlayer, visibleEntity);

                        } else {
                            throw new IllegalArgumentException("Unknown packet type:" + type);
                        }
                    }
                });
         */
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
     * Invoked when the equipment or held item of an living entity is sent to a client.
     * <p>
     * This can be fully modified. Please return TRUE if you do, though.
     * @param equipmentEvent - the equipment event.
     * @return TRUE if the equipment was modified, FALSE otherwise.
     */
    protected abstract boolean onEquipmentSending(EquipmentSendingEvent equipmentEvent);

    /**
     * Update the given slot.
     * @param client - the observing client.
     * @param visibleEntity - the visible entity that will be updated.
     * @param slot - the equipment slot to update.
     */
    public void updateSlot(final Player client, LivingEntity visibleEntity, EquipmentSlot slot) {

        if (listener == null)
            throw new IllegalStateException("FakeEquipment has closed.");

        final PacketContainer equipmentPacket = new PacketContainer(ENTITY_EQUIPMENT);

        equipmentPacket.getIntegers().write(0, visibleEntity.getEntityId());

        equipmentPacket.getItemSlots().write(0, slot.toWrapper());
        equipmentPacket.getItemModifier().write(0, slot.getEquipment(visibleEntity));

        // We have to send the packet AFTER named entity spawn has been sent
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {

            try {
                ProtocolLibrary.getProtocolManager().sendServerPacket(client, equipmentPacket);

            } catch (InvocationTargetException e) {
                throw new RuntimeException("Unable to update slot.", e);
            }
        });
    }

    /**
     * Close the current equipment modifier.
     */
    public void close() {

        if (listener != null) {
            manager.removePacketListener(listener);
            listener = null;
        }
    }
}