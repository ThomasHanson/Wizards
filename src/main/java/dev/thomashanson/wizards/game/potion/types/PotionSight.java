package dev.thomashanson.wizards.game.potion.types;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.potion.Potion;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;

public class PotionSight extends Potion {

    @Override
    public void activate(Wizard wizard) {

    }

    @Override
    public void deactivate(Wizard wizard) {

        super.deactivate(wizard);
    }

    public void setGlow(Entity entity, Player target, boolean glow) {

        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        PacketContainer packet = pm.createPacket(PacketType.Play.Server.ENTITY_METADATA);

        packet.getIntegers().write(0, entity.getEntityId());

        WrappedDataWatcher watcher = new WrappedDataWatcher();
        WrappedDataWatcher.Serializer serializer = WrappedDataWatcher.Registry.get(Byte.class);

        watcher.setEntity(entity);
        watcher.setObject(0, serializer, (byte) (glow ? 0x40 : 0x0));

        packet.getWatchableCollectionModifier().write(0, watcher.getWatchableObjects());

        try {
            pm.sendServerPacket(target, packet);

        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}
