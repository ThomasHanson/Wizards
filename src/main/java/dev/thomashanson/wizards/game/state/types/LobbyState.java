package dev.thomashanson.wizards.game.state.types;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.manager.GameManager;
import dev.thomashanson.wizards.game.state.GameState;
import dev.thomashanson.wizards.game.state.listener.LobbyListener;
import dev.thomashanson.wizards.game.state.listener.StateListenerProvider;
import dev.thomashanson.wizards.map.LocalGameMap;
import dev.thomashanson.wizards.util.BlockUtil;
import dev.thomashanson.wizards.util.EntityUtil;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class LobbyState extends GameState {

    private LobbyListener listener;
    private BukkitTask updateLobbyTask;

    private final List<TextComponent> gameTips = Arrays.asList (
            new TextComponent("If you level up a spell to its maximum, obtaining more of the same spell will grant you additional mana."),
            new TextComponent("\"Quick-Cast\" a spell by right-clicking it in your spellbook! This allows you to use a spell without using a wand slot."),
            new TextComponent("Using multiple spells in combination can lead to devastating results... unless you miss."),
            new TextComponent("On death, players will drop their soul. Picking one up will increase your mana regeneration rate!"),
            new TextComponent("Careful management of both your mana and spell cooldowns is the key to success."),
            new TextComponent("Bend the map to your whim! You can freely place or break blocks in Wizards."),
            new TextComponent("Looking for other wizards? Use Wizard's Compass to track their locations."),
            new TextComponent("Expand your magical arsenal by looting spells and wands from chests!"),
            new TextComponent("Do not take too long as you eliminate your foes. At a certain point, Overtime will kick in, triggering death from above!"),
            new TextComponent("The more wands a player has, the higher chance they'll drop one on death.")
    );

    private Instant lastTip;
    private int tipIndex = 0;
    private ChatColor tipColor = ChatColor.YELLOW;

    private boolean starting = false;
    private int timeUntilStart = 10;

    @Override
    public void onEnable(WizardsPlugin plugin) {

        this.listener = new LobbyListener(plugin);
        super.onEnable(plugin);

        updateLobbyTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {

            if (lastTip == null || Duration.between(lastTip, Instant.now()).toSeconds() >= 20) {

                tipColor = tipColor == ChatColor.YELLOW ? ChatColor.GOLD : ChatColor.YELLOW;
                lastTip = Instant.now();

                String message = ChatColor.WHITE.toString() + ChatColor.BOLD + "TIP> " + ChatColor.RESET + tipColor + gameTips.get(tipIndex).getText();

                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1F, 1F);
                    player.sendMessage(message);
                }

                tipIndex = (tipIndex + 1) % gameTips.size();
            }

            if (!plugin.getGameManager().canStart()) {
                starting = false;
                timeUntilStart = 10;
                return;
            }

            if (timeUntilStart <= 0) {
                plugin.getGameManager().setState(new PrepareState());

            } else {

                if (timeUntilStart == 10 || timeUntilStart <= 5) {

                    Bukkit.broadcastMessage(ChatColor.GREEN + "Starting in " + timeUntilStart + "...");

                    for (Player player : Bukkit.getOnlinePlayers())
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1F, 1F);
                }
            }

            starting = true;
            timeUntilStart--;

        }, 0L, 20L);
    }

    @Override
    public void onDisable() {

        super.onDisable();

        if (updateLobbyTask != null && !updateLobbyTask.isCancelled())
            updateLobbyTask.cancel();

        for (Player player : Bukkit.getOnlinePlayers())
            EntityUtil.resetPlayer(player, !player.hasMetadata(GameManager.SPECTATING_KEY) ? GameMode.ADVENTURE : GameMode.SPECTATOR);

        //createRandomChests(getGame().getActiveMap());
    }

    private void createRandomChests(LocalGameMap gameMap) {

        Set<Material> ignore = new HashSet<>(

                Arrays.asList (
                        Material.ACACIA_LEAVES,
                        Material.BIRCH_LEAVES,
                        Material.DARK_OAK_LEAVES,
                        Material.JUNGLE_LEAVES,
                        Material.OAK_LEAVES,
                        Material.SPRUCE_LEAVES
                )
        );

        int xDiff = (int) (gameMap.getMaxX() - gameMap.getMinX());
        int zDiff = (int) (gameMap.getMaxZ() - gameMap.getMinZ());

        int done = 0;

        while (done < 40) {

            Block block = gameMap.getWorld().getHighestBlockAt (
                    (int) (gameMap.getMinX() + ThreadLocalRandom.current().nextInt(xDiff)),
                    (int) (gameMap.getMinZ() + ThreadLocalRandom.current().nextInt(zDiff))
            );

            while (block.getY() > 0 && (!block.getType().isSolid() || (ignore.contains(block.getType()))))
                block = block.getRelative(BlockFace.DOWN);

            block = block.getRelative(BlockFace.UP);
            block.setType(Material.CHEST);

            int numValues = BlockUtil.AXIS.length;
            BlockFace randomDirection = BlockUtil.AXIS[ThreadLocalRandom.current().nextInt(numValues)];

            ((Directional) block.getBlockData()).setFacing(randomDirection);

            getGame().fillChest(block);
            done++;
        }
    }

    @Override
    protected StateListenerProvider getListenerProvider() {
        return listener;
    }

    public boolean isStarting() {
        return starting;
    }

    public int getTimeUntilStart() {
        return timeUntilStart;
    }
}
