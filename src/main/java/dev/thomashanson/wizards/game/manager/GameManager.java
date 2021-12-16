package dev.thomashanson.wizards.game.manager;

import com.comphenix.protocol.wrappers.Pair;
import dev.jcsoftware.jscoreboards.JPerPlayerMethodBasedScoreboard;
import dev.jcsoftware.jscoreboards.JPerPlayerScoreboard;
import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.kit.WizardsKit;
import dev.thomashanson.wizards.game.mode.WizardsMode;
import dev.thomashanson.wizards.game.state.GameState;
import dev.thomashanson.wizards.game.state.types.PrepareState;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class GameManager implements Listener {

    private final WizardsPlugin plugin;

    private Wizards activeGame;
    private GameState state;

    private final List<WizardsKit> wizardsKits = new ArrayList<>();
    private final Map<UUID, WizardsKit> playerKitMap = new HashMap<>();

    private final Map<UUID, JPerPlayerScoreboard> scoreboards = new HashMap<>();

    private boolean forceStart = false;
    public static final String SPECTATING_KEY = "spectating";

    private static final int LINE_LENGTH = 35;
    private final String LINE_BREAK = ChatColor.GREEN + ChatColor.STRIKETHROUGH.toString() + ChatColor.BOLD + "=".repeat(LINE_LENGTH);

    public GameManager(WizardsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void handleListeners() {
        HandlerList.unregisterAll(this);
    }

    public void setState(GameState state) {

        if (this.state != null)
            this.state.onDisable();

        this.state = state;
        this.state.onEnable(plugin);

        //for (JPerPlayerScoreboard scoreboard : scoreboards.values())
          //  scoreboard.updateScoreboard();
    }

    public void gameAnnounce(Player player, boolean start, String... messages) {

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 2F, 1F);

        for (int i = 0; i < 6 - messages.length; i++)
            player.sendMessage("");

        player.sendMessage(LINE_BREAK);

        player.sendMessage(ChatColor.WHITE.toString() + ChatColor.BOLD + "Wizards");

        player.sendMessage("");
        Arrays.stream(messages).forEach(message -> player.sendMessage(!start ? message : ChatColor.YELLOW.toString() + ChatColor.BOLD + message));
        player.sendMessage("");

        player.sendMessage (

                ChatColor.GREEN.toString() + ChatColor.BOLD + "Map: " +
                        ChatColor.YELLOW.toString() + ChatColor.BOLD + activeGame.getActiveMap().getName() +

                        ChatColor.GRAY + " created by " +
                        ChatColor.YELLOW.toString() + ChatColor.BOLD + activeGame.getActiveMap().getAuthors()
        );

        player.sendMessage(LINE_BREAK);

        for (int i = 0; i < 6 - messages.length; i++)
            player.sendMessage("");
    }

    public boolean canStart() {

        boolean canStart = false;

        int participatingPlayers = 0;

        for (Player player : Bukkit.getOnlinePlayers())
            if (!player.hasMetadata(SPECTATING_KEY))
                participatingPlayers++;

        WizardsMode mode = activeGame.getCurrentMode();

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

            player.sendMessage(ChatColor.YELLOW.toString() + ChatColor.BOLD + message);
        }

        Bukkit.getLogger().info("[Announcement] " + message);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {

        Player player = event.getPlayer();

        JPerPlayerMethodBasedScoreboard scoreboard = new JPerPlayerMethodBasedScoreboard();
        scoreboards.put(player.getUniqueId(), scoreboard);

        scoreboard.setTitle(player, ChatColor.YELLOW.toString() + ChatColor.BOLD + "Wizards".toUpperCase());

        List<String> lines = new ArrayList<>();

        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("mm/dd/yy");

        lines.add(ChatColor.GRAY + format.format(date));
        lines.add("");

        if (state instanceof PrepareState || activeGame.isLive()) {

            Pair<String, Instant> nextEvent = activeGame.getNextEvent();
            Duration nextEventDuration = Duration.between(Instant.now(), nextEvent.getSecond());

            lines.add(ChatColor.RESET + "Next Event: ");
            lines.add(ChatColor.GREEN + nextEvent.getFirst() + " " + DurationFormatUtils.formatDuration(nextEventDuration.toMillis(), "mm:ss", true));
            lines.add("");
        }

        lines.addAll(state.getScoreboardLines());
        lines.add("");
        lines.add(ChatColor.RESET + "Map: " + activeGame.getActiveMap().getName());
        lines.add(ChatColor.RESET + "Mode: " + ChatColor.GREEN + activeGame.getCurrentMode().toString());
        lines.add("");
        lines.add(ChatColor.YELLOW + "www.thomashanson.dev");

        scoreboard.setLines(player, lines);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {

        Player player = event.getPlayer();
        JPerPlayerScoreboard scoreboard = scoreboards.get(player.getUniqueId());

        if (scoreboard != null) {
            scoreboard.removePlayer(player);
            scoreboard.destroy();
        }

        scoreboards.remove(player.getUniqueId());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE)
            event.setCancelled(true);
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