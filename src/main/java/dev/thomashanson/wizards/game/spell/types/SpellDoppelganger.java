package dev.thomashanson.wizards.game.spell.types;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import dev.thomashanson.wizards.game.loot.ChestLoot;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.SpellType;
import dev.thomashanson.wizards.game.spell.WandElement;
import dev.thomashanson.wizards.util.npc.NPC;
import dev.thomashanson.wizards.util.npc.data.Ping;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SpellDoppelganger extends Spell implements Spell.Cancellable {

    public enum FakeTask {

        /*
         * The entity will look for the nearest group
         * of chests, walk towards them, and open a
         * random chest.
         */
        LOOT_CHEST,

        /*
         * The entity will select a random wand & cast
         * a random spell (with animations).
         */
        FAKE_SPELL,

        /*
         * The entity will select a random food item from
         * the chest loot & consume it (with animations).
         */
        FAKE_EAT (1610),

        /*
         * The entity will run or walk towards the nearest
         * player. It will not run towards other allies.
         */
        RUN_TOWARDS_PLAYER;

        FakeTask() {
            this(-1);
        }

        FakeTask(long timeToComplete) {
            this.timeToComplete = timeToComplete;
        }

        FakeTask(long minTime, long maxTime) {
            this.timeToComplete = ThreadLocalRandom.current().nextLong(minTime, maxTime);
        }

        private final long timeToComplete;

        public long getTimeToComplete() {
            return timeToComplete;
        }
    }

    public SpellDoppelganger() {
        setCancelOnSwap();
    }

    private final Map<UUID, NPC> clones = new HashMap<>();

    @Override
    public void castSpell(Player player, int level) {

        if (isCancelled()) {
            handleNPC(player);
            return;
        }

        NPC fakePlayer = new NPC(player.getLocation(), player.getDisplayName());

        fakePlayer.spawnNPC();
        copy(player, fakePlayer);

        clones.put(player.getUniqueId(), fakePlayer);

        int numTasks = FakeTask.values().length;
        FakeTask fakeTask = FakeTask.values()[ThreadLocalRandom.current().nextInt(numTasks)];
        Bukkit.broadcastMessage(fakeTask.toString() + " selected as fake task");

        ItemStack randomItem = getRandomItem(player, fakeTask);

        if (randomItem != null)
            for (Player onlinePlayer : Bukkit.getOnlinePlayers())
                fakePlayer.setEquipment(onlinePlayer, EnumWrappers.ItemSlot.MAINHAND, randomItem);

        setProgress(1.0);
    }

    @Override
    public void cleanup() {
        clones.clear();
    }

    /*
     * Update every tick
     *
     * - Loop through clones
     * - If skeleton alive,
     *   - Drain 6-SL mana per second
     *   - Disable mana regen for owner
     */

    @Override
    public void cancelSpell(Player player) {
        handleNPC(player);
    }

    private void handleNPC(Player player) {

        if (!clones.containsKey(player.getUniqueId()))
            return;

        NPC fakePlayer = clones.remove(player.getUniqueId());

        if (fakePlayer == null)
            return;

        Location location = fakePlayer.getLocation();

        for (int i = 0 ; i < 2 ; i++)
            Objects.requireNonNull(location.getWorld()).playSound(location, Sound.BLOCK_FIRE_EXTINGUISH, 2F, 0.4F);

        fakePlayer.destroyNPC();

        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.sendMessage("You are no longer invisible.");
    }

    private void showChestAnimation(Player player, Block block, boolean open) {

        Location chestLoc = block.getLocation();
        PacketContainer blockAction = new PacketContainer(PacketType.Play.Server.BLOCK_ACTION);

        blockAction.getBlocks().write(0, Material.CHEST);
        blockAction.getBlockPositionModifier().write(0, new BlockPosition(chestLoc.getBlockX(), chestLoc.getBlockY(), chestLoc.getBlockZ()));

        blockAction.getIntegers().write(0, 1);
        blockAction.getIntegers().write(1, open ? 1 : 0);

        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, blockAction, true);

        } catch (InvocationTargetException ex) {
            throw new IllegalStateException("Unable to send packet " + blockAction, ex);
        }
    }

    private void copy(Player player, NPC npc) {

        npc.setPing(Ping.fromMilliseconds(player.getPing()));
        npc.setGameMode(EnumWrappers.NativeGameMode.fromBukkit(player.getGameMode()));

        PlayerInventory inventory = player.getInventory();

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            npc.setEquipment(onlinePlayer, EnumWrappers.ItemSlot.HEAD, inventory.getHelmet());
            npc.setEquipment(onlinePlayer, EnumWrappers.ItemSlot.CHEST, inventory.getChestplate());
            npc.setEquipment(onlinePlayer, EnumWrappers.ItemSlot.LEGS, inventory.getLeggings());
            npc.setEquipment(onlinePlayer, EnumWrappers.ItemSlot.FEET, inventory.getBoots());
        }
    }

    private ItemStack getRandomItem(Player player, FakeTask fakeTask) {

        ItemStack randomItem = null;

        if (fakeTask == FakeTask.LOOT_CHEST) {

            randomItem = player.getInventory().getItem(ThreadLocalRandom.current().nextInt(8));

            if (randomItem != null && getGame().getSpell(randomItem) != null)
                randomItem = new ItemStack(getGame().getSpell(randomItem).getWandElement().getMaterial());

        } else if (fakeTask == FakeTask.FAKE_SPELL) {

            List<WandElement> wandOptions = new ArrayList<>();

            for (SpellType spell : getWizard(player).getKnownSpells())
                if (!wandOptions.contains(spell.getWandElement()))
                    wandOptions.add(spell.getWandElement());

            int numElements = wandOptions.size();
            WandElement element = wandOptions.get(ThreadLocalRandom.current().nextInt(numElements));

            randomItem = new ItemStack(element.getMaterial());

        } else if (fakeTask == FakeTask.FAKE_EAT) {

            ChestLoot loot = getGame().getChestLoot();

            while (randomItem == null || (!randomItem.getType().isEdible() || randomItem.getType() == Material.CAKE))
                randomItem = loot.getLoot();
        }

        return randomItem;
    }

    public boolean isActive(Player player) {
        return clones.containsKey(player.getUniqueId());
    }

    public Map<UUID, NPC> getClones() {
        return clones;
    }
}