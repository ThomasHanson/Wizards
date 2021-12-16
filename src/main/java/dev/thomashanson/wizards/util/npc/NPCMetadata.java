package dev.thomashanson.wizards.util.npc;

import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import dev.thomashanson.wizards.util.npc.data.EntityState;

import java.util.List;

public class NPCMetadata {

    private final WrappedDataWatcher watcher = new WrappedDataWatcher();

    private final WrappedDataWatcher.Serializer byteSerializer = WrappedDataWatcher.Registry.get(Byte.class);
    private final WrappedDataWatcher.Serializer intSerializer = WrappedDataWatcher.Registry.get(Integer.class);
    private final WrappedDataWatcher.Serializer booleanSerializer = WrappedDataWatcher.Registry.get(Boolean.class);

    // Entity metadata
    private final WrappedDataWatcher.WrappedDataWatcherObject entityState = new WrappedDataWatcher.WrappedDataWatcherObject(0, byteSerializer);
    // silent, gravity
    //private final WrappedDataWatcher.WrappedDataWatcherObject pose = new WrappedDataWatcher.WrappedDataWatcherObject(6, objectSerializer);

    // LivingEntity metadata
    //private final WrappedWatchableObject handStatus = new WrappedWatchableObject(8, (byte) HandStatus.createMask(HandStatus.MAIN_HAND));
    //private final WrappedWatchableObject health = new WrappedWatchableObject(9, 1.0F);

    // Player metadata
    //private final WrappedWatchableObject skinStatus = new WrappedWatchableObject(15, (byte) SkinStatus.createMask(SkinStatus.ALL_ENABLED));

    NPCMetadata() {
        setEntityState(EntityState.DEFAULT);
        //setPose(EnumWrappers.EntityPose.STANDING);
    }

    public List<WrappedWatchableObject> getList() {
        return watcher.getWatchableObjects();
    }

    public EntityState[] getEntityState() {
        return EntityState.fromMask((Integer) entityState.getHandle());
    }

    private void setEntityState(EntityState... entityState) {
        watcher.setObject(this.entityState, (byte) EntityState.createMask(entityState));
    }

    //public EnumWrappers.EntityPose getPose() {
      //  return (EnumWrappers.EntityPose) this.pose.getHandle();
    //}

    //public void setPose(EnumWrappers.EntityPose pose) {
      //  watcher.setObject(this.pose, pose);
    //}

    /*
    public HandStatus[] getHandStatus() {
        return HandStatus.fromMask((Integer) handStatus.getValue());
    }

    public Float getHealth() {
        return (Float) health.getValue();
    }

    public SkinStatus[] getSkinStatus() {
        return SkinStatus.fromMask((Integer) skinStatus.getValue());
    }

    /*

    public void setNoGravity(Boolean noGravity) {
        this.noGravity.setValue(noGravity);
    }

    public void setHandStatus(HandStatus handStatus) {
        this.handStatus.setValue((byte) HandStatus.createMask(handStatus));
    }

    public void setHealth(Float health) {
        this.health.setValue(health);
    }

    public void setSkinStatus(SkinStatus... skinStatus) {
        this.skinStatus.setValue((byte) SkinStatus.createMask(skinStatus));
    }
     */
}
