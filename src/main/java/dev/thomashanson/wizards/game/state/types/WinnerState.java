package dev.thomashanson.wizards.game.state.types;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.scheduler.BukkitRunnable;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.thomashanson.wizards.game.manager.PlayerStatsManager.StatType;
import dev.thomashanson.wizards.game.mode.GameTeam;
import dev.thomashanson.wizards.game.potion.Potion;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.state.GameState;
import dev.thomashanson.wizards.game.state.listener.StateListenerProvider;
import dev.thomashanson.wizards.map.MapBorder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class WinnerState extends GameState {

    private final List<GameTeam> finalRankings;

    public WinnerState(LinkedList<GameTeam> finalRankings) {
        this.finalRankings = new ArrayList<>(finalRankings);
    }

    @Override
    public void onEnable(WizardsPlugin plugin) {
        super.onEnable(plugin);

        // This method now handles the broadcast internally.
        buildAndAnnounceResults();

        plugin.getGameManager().stopGameLoop();
        Wizards activeGame = getGame();

        Instant startTime = activeGame.getGameStartTime();
        if (startTime != null) {
            long secondsPlayed = Duration.between(startTime, Instant.now()).getSeconds();
            for (Player player : activeGame.getPlayers(false)) {
                plugin.getStatsManager().setStat(player, StatType.TIME_PLAYED_SECONDS, secondsPlayed);
            }
        }
        
        plugin.getStatsManager().processEndGameRewards(activeGame, this.finalRankings);

        MapBorder mapBorder = activeGame.getMapBorder();
        if (mapBorder != null)
            mapBorder.handleEnd();

        // Cleanup...
        activeGame.getSpells().values().forEach(Spell::cleanup);
        activeGame.getSpells().values().forEach(HandlerList::unregisterAll);
        activeGame.getSpells().clear();
        activeGame.getPotions().values().forEach(Potion::cleanup);
        activeGame.getPotions().values().forEach(HandlerList::unregisterAll);
        activeGame.getPotions().clear();
        plugin.getGameManager().getKitManager().getAllKits().forEach(HandlerList::unregisterAll);
        plugin.getGameManager().getKitManager().getAllKits().clear();
        HandlerList.unregisterAll(activeGame);

        // REFACTORED: The broadcast is now done in buildAndAnnounceResults,
        // so this redundant loop is removed.

        new BukkitRunnable() {
            @Override
            public void run() {
                setState(new ResetState());
            }
        }.runTaskLater(plugin, 20L * 15L);
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    private void buildAndAnnounceResults() {
        LanguageManager lang = getPlugin().getLanguageManager();
        List<Component> announcementComponents = new ArrayList<>();

        if (!finalRankings.isEmpty()) {
            GameTeam winner = finalRankings.get(0);
            String winnerName = formatTeamNameForChat(winner);
            announcementComponents.add(lang.getTranslated(null, "wizards.game.announcement.winner.first",
                Placeholder.unparsed("winner_name", winnerName)
            ));
        }

        if (finalRankings.size() > 1) {
            GameTeam second = finalRankings.get(1);
            String secondName = formatTeamNameForChat(second);
            announcementComponents.add(lang.getTranslated(null, "wizards.game.announcement.winner.second",
                Placeholder.unparsed("winner_name", secondName)
            ));
        }

        if (finalRankings.size() > 2) {
            GameTeam third = finalRankings.get(2);
            String thirdName = formatTeamNameForChat(third);
            announcementComponents.add(lang.getTranslated(null, "wizards.game.announcement.winner.third",
                Placeholder.unparsed("winner_name", thirdName)
            ));
        }
        
        // The announcement message is the same for everyone, so we build it once.
        // Then we call gameAnnounce for each player to display it to them.
        Bukkit.getOnlinePlayers().forEach(player -> {
            getPlugin().getGameManager().gameAnnounce(player, false, announcementComponents);
        });
    }

    private String formatTeamNameForChat(GameTeam team) {
        if (!getGame().getCurrentMode().isTeamMode()) {
            return team.getTeamName();
        } else {
            String playerNames = team.getTeamMembers().stream()
                                 .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                                 .collect(Collectors.joining(", "));
            return team.getTeamName() + " (" + playerNames + ")";
        }
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        event.setCancelled(true);
    }

    @Override
    public List<Component> getScoreboardComponents(Player player) {
        LanguageManager lang = getPlugin().getLanguageManager();
        List<Component> components = new ArrayList<>();

        if (finalRankings == null || finalRankings.isEmpty()) {
            components.add(lang.getTranslated(player, "wizards.scoreboard.winner.gameOver"));
            return components;
        }

        boolean isTeamMode = getGame().getCurrentMode().isTeamMode();

        for (int i = 0; i < 3 && i < finalRankings.size(); i++) {
            if (i > 0) {
                components.add(Component.text(""));
            }

            GameTeam team = finalRankings.get(i);
            String titleKey;
            switch (i) {
                case 0 -> titleKey = "wizards.scoreboard.winner.firstPlace";
                case 1 -> titleKey = "wizards.scoreboard.winner.secondPlace";
                default -> titleKey = "wizards.scoreboard.winner.thirdPlace";
            }

            components.add(lang.getTranslated(player, titleKey));
            
            Component teamName = Component.text("  " + team.getTeamName());
            components.add(teamName);
            
            if (isTeamMode && i == 0) {
                team.getTeamMembers().forEach(memberUUID -> {
                    Player member = Bukkit.getPlayer(memberUUID); // Can be null if offline
                    String name = (member != null) ? member.getName() : Bukkit.getOfflinePlayer(memberUUID).getName();
                    
                    Component memberComp = Component.text("    - " + name).color(NamedTextColor.GRAY);

                    if (getGame().getWizardManager().getWizard(member) == null) {
                        memberComp = memberComp.decorate(TextDecoration.STRIKETHROUGH);
                    }
                    components.add(memberComp);
                });
            }
        }
        
        return components;
    }

    @Override
    protected StateListenerProvider getListenerProvider() {
        return null;
    }
}