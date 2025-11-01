package dev.thomashanson.wizards.game.potion.types;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;

import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.potion.Potion;

public class PotionSight extends Potion {

    @Override
    public void onActivate(Wizard wizard) {}

    @Override
    public void onDeactivate(Wizard wizard) {}

    public void setGlow(Entity entity, Player target, boolean glow) {
        try {
            ProtocolManager pm = ProtocolLibrary.getProtocolManager();
            PacketContainer packet = pm.createPacket(PacketType.Play.Server.ENTITY_METADATA);

            packet.getIntegers().write(0, entity.getEntityId());

            // --- START OF FIX ---
            // Don't use WrappedDataWatcher.setEntity(). Build the list manually.
            
            List<WrappedDataValue> dataValues = new ArrayList<>();

            // We need to read the entity's current bitmask first
            WrappedDataWatcher existingWatcher = new WrappedDataWatcher(entity);
            byte existingBitmask = existingWatcher.getByte(0);
            
            byte newBitmask;
            if (glow) {
                newBitmask = (byte) (existingBitmask | 0x40); // Add glow flag
            } else {
                newBitmask = (byte) (existingBitmask & ~0x40); // Remove glow flag
            }

            // Only send the packet if the value actually changed
            if (newBitmask == existingBitmask) {
                return; 
            }

            dataValues.add(new WrappedDataValue(
                    0, // Index 0: Bitmask
                    WrappedDataWatcher.Registry.get(Byte.class),
                    newBitmask
            ));

            packet.getDataValueCollectionModifier().write(0, dataValues);
            // --- END OF FIX ---

            pm.sendServerPacket(target, packet);

        } catch (Exception e) {
            // Log the error but don't crash
            e.printStackTrace();
        }
    }
}