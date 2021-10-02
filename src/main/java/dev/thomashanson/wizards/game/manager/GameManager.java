package dev.thomashanson.wizards.game.manager;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.kit.WizardsKit;
import dev.thomashanson.wizards.game.mode.WizardsMode;
import dev.thomashanson.wizards.game.state.GameState;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.*;

public class GameManager implements Listener {

    private final WizardsPlugin plugin;

    private Wizards activeGame;
    private GameState state;

    private boolean forceStart = false;
    public static final String SPECTATING_KEY = "spectating";

    private final List<WizardsKit> wizardsKits = new ArrayList<>();
    private final Map<UUID, WizardsKit> playerKitMap = new HashMap<>();

    public GameManager(WizardsPlugin plugin) {

        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        if (activeGame != null && activeGame.isLive()) {

            plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {

                // update scoreboard
                // update player info & game state

            }, 0L, 1L);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE)
            event.setCancelled(true);
    }

    public void handleListeners() {
        HandlerList.unregisterAll(this);
    }

    public void setState(GameState state) {

        if (this.state != null)
            this.state.onDisable();

        this.state = state;
        this.state.onEnable(plugin);
    }

    public boolean canStart() {

        boolean canStart = false;

        Wizards game = getActiveGame();
        int participatingPlayers = 0;

        for (Player player : Bukkit.getOnlinePlayers())
            if (!player.hasMetadata(SPECTATING_KEY))
                participatingPlayers++;

        WizardsMode mode = game.getCurrentMode();

        int maxPlayers = mode.getMaxPlayers();
        double minRequirement = 0.75;

        if ((double) (participatingPlayers / maxPlayers) >= minRequirement)
            canStart = true;

        return canStart || forceStart;
    }

    public void announce(String message) {
        announce(message, false);
    }

    public void announce(String message, boolean withSound) {

        if (message == null || message.isEmpty())
            return;

        for (Player player : Bukkit.getOnlinePlayers()) {

            if (withSound)
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1F, 1F);

            player.sendMessage(message);
        }

        Bukkit.getLogger().info("[Announcement] " + message);
    }

    public void setForceStart(boolean forceStart) {
        this.forceStart = forceStart;
    }

    public void addKits(WizardsKit... kits) {
        wizardsKits.addAll(Arrays.asList(kits));
    }

    public WizardsKit getKit(Player player) {
        return playerKitMap.get(player.getUniqueId());
    }

    public void setKit(Player player, WizardsKit kit) {
        playerKitMap.put(player.getUniqueId(), kit);
    }

    public List<WizardsKit> getWizardsKits() {
        return wizardsKits;
    }

    public Wizards getActiveGame() {
        return activeGame;
    }

    public void setActiveGame(Wizards activeGame) {
        this.activeGame = activeGame;
    }

    public GameState getState() {
        return state;
    }
}