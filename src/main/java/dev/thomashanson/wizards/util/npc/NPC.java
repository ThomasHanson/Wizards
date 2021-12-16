package dev.thomashanson.wizards.util.npc;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.util.npc.data.Animation;
import dev.thomashanson.wizards.util.npc.data.Ping;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class NPC {

    private static final AtomicInteger ATOMIC_INTEGER = new AtomicInteger();

    private final int entityID;
    private WrappedGameProfile profile;
    private final NPCMetadata metadata = new NPCMetadata();
    private final Location location;
    private Ping ping = Ping.FIVE_BARS;
    private EnumWrappers.NativeGameMode gameMode = EnumWrappers.NativeGameMode.SURVIVAL;
    private String displayName;

    private NPC(UUID uuid, Location location, String displayName) {

        this.entityID = ATOMIC_INTEGER.incrementAndGet();

        this.profile = new WrappedGameProfile(uuid, displayName);
        this.location = location;
        this.displayName = displayName;
        String hideTeam = "hide-" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE));
    }

    public NPC(Location location, String displayName) {
        this(UUID.randomUUID(), location, displayName);
    }

    public void spawnNPC() {
        spawnNPC(Bukkit.getOnlinePlayers());
    }

    private void spawnNPC(Collection<? extends Player> players) {
        players.forEach(this::spawnNPC);
    }

    private void spawnNPC(Player player) {

        this.addToTabList(player);
        this.sendPacket(player, this.getEntitySpawnPacket());
        this.updateMetadata(player);
        this.removeFromTabList(player);

        Wizards.NPC_SET.add(this);
    }

    public void destroyNPC() {
        destroyNPC(Bukkit.getOnlinePlayers());
    }

    private void destroyNPC(Collection<? extends Player> players) {
        players.forEach(this::destroyNPC);
    }

    private void destroyNPC(Player player) {

        this.sendPacket(player, this.getPlayerInfoPacket(EnumWrappers.PlayerInfoAction.REMOVE_PLAYER));
        this.sendPacket(player, this.getEntityDestroyPacket());

        Wizards.NPC_SET.remove(this);
    }

    public void teleportNPC(Collection<Player> players, Location location, boolean onGround) {
        players.forEach(player -> this.teleportNPC(player, location, onGround));
    }

    private void teleportNPC(Player player, Location location, boolean onGround) {
        this.location.setX(location.getX());
        this.location.setY(location.getY());
        this.location.setZ(location.getZ());
        this.location.setPitch(location.getPitch());
        this.location.setYaw(location.getYaw());
        this.sendPacket(player, this.getEntityTeleportPacket(onGround));
        this.rotateHead(player, location.getPitch(), location.getYaw());
    }

    public void updateMetadata(Collection<Player> players) {
        players.forEach(this::updateMetadata);
    }

    private void updateMetadata(Player player) {
        this.sendPacket(player, this.getEntityMetadataPacket());
    }

    public void updateGameMode(Collection<Player> players) {
        players.forEach(this::updateGameMode);
    }

    private void updateGameMode(Player player) {
        this.sendPacket(player, this.getPlayerInfoPacket(EnumWrappers.PlayerInfoAction.UPDATE_GAME_MODE));
    }

    public void updatePing(Collection<Player> players) {
        players.forEach(this::updatePing);
    }

    private void updatePing(Player player) {
        this.sendPacket(player, this.getPlayerInfoPacket(EnumWrappers.PlayerInfoAction.UPDATE_LATENCY));
    }

    public void updateTabListName(Collection<Player> players) {
        players.forEach(this::updateTabListName);
    }

    private void updateTabListName(Player player) {
        this.sendPacket(player, this.getPlayerInfoPacket(EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME));
    }

    public void removeFromTabList(Collection<Player> players) {
        players.forEach(this::removeFromTabList);
    }

    private void removeFromTabList(Player player) {
        this.sendPacket(player, this.getPlayerInfoPacket(EnumWrappers.PlayerInfoAction.REMOVE_PLAYER));
    }

    public void addToTabList(Collection<Player> players) {
        players.forEach(this::addToTabList);
    }

    private void addToTabList(Player player) {
        this.sendPacket(player, this.getPlayerInfoPacket(EnumWrappers.PlayerInfoAction.ADD_PLAYER));
    }

    public void playAnimation(Animation animation) {
        playAnimation(Bukkit.getOnlinePlayers(), animation);
    }

    public void playAnimation(Collection<? extends Player> players, Animation animation) {
        players.forEach(player -> this.playAnimation(player, animation));
    }

    private void playAnimation(Player player, Animation animation) {
        this.sendPacket(player, this.getEntityAnimationPacket(animation));
    }

    public void lookAtPlayer(Collection<Player> players, Player target) {
        players.forEach(player -> this.lookAtPlayer(player, target));
    }

    private void lookAtPlayer(Player player, Player target) {
        this.lookAtPoint(player, target.getEyeLocation());
    }

    public void lookAtPoint(Collection<Player> players, Location location) {
        players.forEach(player -> this.lookAtPoint(player, location));
    }

    private void lookAtPoint(Player player, Location location) {

        Location eyeLocation = this.getEyeLocation();

        float yaw = (float) Math.toDegrees(Math.atan2(location.getZ() - eyeLocation.getZ(), location.getX() - eyeLocation.getX())) - 90;
        yaw = (float) (yaw + Math.ceil(-yaw / 360) * 360);

        float deltaXZ = (float) Math.sqrt(Math.pow(eyeLocation.getX() - location.getX(), 2) + Math.pow(eyeLocation.getZ() - location.getZ(), 2));
        float pitch = (float) Math.toDegrees(Math.atan2(deltaXZ, location.getY()-eyeLocation.getY())) - 90;

        pitch = (float) (pitch + Math.ceil(-pitch / 360) * 360);

        this.rotateHead(player, pitch, yaw);
    }

    public void rotateHead(Collection<Player> players, float pitch, float yaw) {
        players.forEach(player -> this.rotateHead(player, pitch, yaw));
    }

    private void rotateHead(Player player, float pitch, float yaw) {
        this.location.setPitch(pitch);
        this.location.setYaw(yaw);
        this.sendPacket(player, this.getEntityLookPacket());
        this.sendPacket(player, this.getEntityHeadRotatePacket());
    }

    public void setTabListName(String name) {
        this.displayName = name;
    }

    public void setEquipment(Collection<? extends Player> players, EnumWrappers.ItemSlot slot, ItemStack itemStack) {
        players.forEach(player -> this.setEquipment(player, slot, itemStack));
    }

    public void setEquipment(Player player, EnumWrappers.ItemSlot slot, ItemStack itemStack) {
        this.sendPacket(player, this.getEntityEquipmentPacket(slot, itemStack));
    }

    /*
    public void setPassenger(Collection<Player> players, int... entityIDs) {
        players.forEach(player -> this.setPassenger(player, entityIDs));
    }

    public void setPassenger(Player player, int... entityIDs) {
        this.sendPacket(player, getEntityAttachPacket(entityIDs));
    }
     */

    private void sendPacket(Player player, PacketContainer packet) {

        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);

        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    /*
    public void setNameTagVisibility(Collection<Player> players, boolean show) {
        players.forEach(player -> this.setNameTagVisibility(player, show));
    }

    public void setNameTagVisibility(Player player, boolean show) {
        ScoreboardTeam team = new ScoreboardTeam(new Scoreboard(), this.hideTeam);
        if(show) {
            PacketPlayOutScoreboardTeam leavePacket = PacketPlayOutScoreboardTeam.a(team, this.profile.getName(), PacketPlayOutScoreboardTeam.a.b);
            this.sendPacket(player, leavePacket);
        } else {
            team.setNameTagVisibility(ScoreboardTeamBase.EnumNameTagVisibility.b);
            PacketPlayOutScoreboardTeam createPacket = PacketPlayOutScoreboardTeam.a(team, true);
            PacketPlayOutScoreboardTeam joinPacket = PacketPlayOutScoreboardTeam.a(team, this.profile.getName(), PacketPlayOutScoreboardTeam.a.a);
            this.sendPacket(player, createPacket);
            this.sendPacket(player, joinPacket);
        }
    }
     */

    /*
    private PacketContainer getEntityAttachPacket(int[] entityIDs) {
        return this.createDataSerializer(data->{
            data.d(this.entityID);
            data.a(entityIDs);
            return new PacketPlayOutMount(data);
        });
    }
     */

    private PacketContainer getEntityLookPacket() {

        PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_LOOK);

        packet.getIntegers().write(0, entityID);

        packet.getBytes()
                .write(0, (byte) ((int) (location.getYaw() * 256.0F / 360.0F)))
                .write(1, (byte) ((int) (location.getPitch() * 256.0F / 360.0F)));

        packet.getBooleans().write(0, true);

        return packet;
    }

    private PacketContainer getEntityTeleportPacket(boolean onGround) {

        PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_TELEPORT);

        packet.getIntegers().write(0, entityID);

        packet.getDoubles()
                .write(0, location.getX())
                .write(1, location.getY())
                .write(2, location.getZ());

        packet.getBytes()
                .write(0, (byte) ((int) (location.getYaw() * 256.0F / 360.0F)))
                .write(1, (byte) ((int) (location.getPitch() * 256.0F / 360.0F)));

        packet.getBooleans().write(0, onGround);

        return packet;
    }

    private PacketContainer getEntityHeadRotatePacket() {

        PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);

        packet.getIntegers().write(0, entityID);
        packet.getBytes().write(0, (byte) ((int) (location.getYaw() * 256.0F / 360.0F)));

        return packet;
    }

    private PacketContainer getEntityEquipmentPacket(EnumWrappers.ItemSlot slot, ItemStack itemStack) {

        PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);

        packet.getIntegers().write(0, entityID);

        List<Pair<EnumWrappers.ItemSlot, ItemStack>> pairList = new ArrayList<>();
        pairList.add(new Pair<>(slot, itemStack));

        packet.getSlotStackPairLists().write(0, pairList);

        return packet;
    }

    private PacketContainer getEntityAnimationPacket(Animation animation) {

        PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ANIMATION);

        packet.getIntegers().write(0, entityID);
        packet.getBytes().write(0, (byte) animation.getType());

        return packet;
    }

    private PacketContainer getEntityDestroyPacket() {

        PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_DESTROY);

        packet.getIntegers().write(0, 1);
        packet.getIntegerArrays().write(1, new int[] { entityID });

        return packet;
    }

    private PacketContainer getEntityMetadataPacket() {

        PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_METADATA);

        packet.getIntegers().write(0, entityID);
        packet.getWatchableCollectionModifier().write(0, metadata.getList());

        return packet;
    }

    private PacketContainer getEntitySpawnPacket() {

        PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.NAMED_ENTITY_SPAWN);

        packet.getIntegers().write(0, entityID);
        packet.getUUIDs().write(0, profile.getUUID());

        packet.getDoubles()
                .write(0, location.getX())
                .write(1, location.getY())
                .write(2, location.getZ());

        packet.getBytes()
                .write(0, (byte) (location.getYaw() * 256.0F / 360.0F))
                .write(1, (byte) (location.getPitch() * 256.0F / 360.0F));

        return packet;
    }

    private PacketContainer getPlayerInfoPacket(EnumWrappers.PlayerInfoAction action) {

        PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.PLAYER_INFO);

        packet.getPlayerInfoAction().write(0, action);

        packet.getPlayerInfoDataLists().write (

                0,

                Collections.singletonList (

                        new PlayerInfoData (
                                profile,
                                ping.getMilliseconds(),
                                gameMode,
                                WrappedChatComponent.fromText(displayName)
                        )
                )
        );

        return packet;
    }

    public int getEntityID() {
        return entityID;
    }

    public WrappedGameProfile getProfile() {
        return profile;
    }

    public NPCMetadata getMetadata() {
        return metadata;
    }

    public Location getLocation() {
        return location;
    }

    private Location getEyeLocation() {
        return location.clone().add(0, /*EntityTypes.bi.m().b * 0.85F*/ 0, 0);
    }

    public Ping getPing() {
        return ping;
    }

    public EnumWrappers.NativeGameMode getGameMode() {
        return gameMode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setPing(Ping ping) {
        this.ping = ping;
    }

    public void setGameMode(EnumWrappers.NativeGameMode gameMode) {
        this.gameMode = gameMode;
    }

    public void setDisplayName(String displayName) {

        this.displayName = displayName;

        WrappedGameProfile swapProfile = new WrappedGameProfile(this.profile.getUUID(), displayName);
        swapProfile.getProperties().putAll(this.profile.getProperties());

        this.profile = swapProfile;
    }
}