package dev.thomashanson.wizards.game.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map; // Import your utility
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.mode.GameTeam;
import dev.thomashanson.wizards.game.mode.WizardsMode;
import dev.thomashanson.wizards.game.state.types.WinnerState;
import dev.thomashanson.wizards.map.LocalGameMap;
import dev.thomashanson.wizards.util.LocationUtil;

/**
 * Manages all team-related logic for a single game instance, including
 * team creation, player assignment, eliminations, and win conditions.
 *
 * @see Wizards
 */
public class TeamManager {

    private final Wizards game;
    private final WizardManager wizardManager;

    /** Tracks all teams that are still alive and in the game. */
    private final List<GameTeam> activeTeams = new ArrayList<>();
    
    /** A map for O(1) lookups of a player's {@link GameTeam}. */
    private final Map<UUID, GameTeam> playerTeamMap = new HashMap<>();

    /**
     * Stores teams in the order they are eliminated (LIFO).
     * The last team in is 2nd place, the first team in is last place.
     * The winner is the last team remaining in {@link #activeTeams}.
     */
    private final LinkedList<GameTeam> placementRankings = new LinkedList<>();

    /**
     * Creates a new TeamManager for a specific game instance.
     *
     * @param game The active {@link Wizards} game.
     */
    public TeamManager(Wizards game) {
        this.game = game;
        this.wizardManager = game.getWizardManager();
    }

    /**
     * Creates the initial team structures based on the game mode.
     * For solo, this will be run as players are assigned.
     */
    public void setupTeams() {
        WizardsMode mode = game.getCurrentMode();
        if (mode.isTeamMode()) {
            for (int i = 0; i < mode.getNumTeams(); i++) {
                activeTeams.add(new GameTeam("Team " + (i + 1)));
            }
        }
    }

    /**
     * Assigns a list of shuffled players to teams.
     * In solo mode, creates a new 1-player {@link GameTeam} for each player.
     *
     * @param players The list of players to assign.
     */
    public void assignTeams(List<Player> players) {
        Collections.shuffle(players);
        WizardsMode mode = game.getCurrentMode();

        if (mode.isTeamMode()) {
            int teamIndex = 0;
            for (Player player : players) {
                GameTeam team = activeTeams.get(teamIndex);
                team.addPlayer(player);
                playerTeamMap.put(player.getUniqueId(), team);
                teamIndex = (teamIndex + 1) % activeTeams.size();
            }
        } else { // Solo mode
            for (Player player : players) {
                GameTeam soloTeam = new GameTeam(player.getName());
                soloTeam.addPlayer(player);
                activeTeams.add(soloTeam);
                playerTeamMap.put(player.getUniqueId(), soloTeam);
            }
        }
    }

    /**
     * Central logic to run when a player dies or quits.
     * This method checks if the player's team is now fully eliminated
     * and then checks if a game-ending condition has been met.
     *
     * @param player The player who died or quit.
     */
    public void handlePlayerDeath(Player player) {
        GameTeam team = getTeam(player);
        if (team == null) return;

        // A team is eliminated if no members are considered "alive" by the WizardManager.
        if (!team.isTeamAlive(wizardManager)) {
            eliminateTeam(team);
        }

        checkEndGameCondition();
    }

    /**
     * Finds an appropriate spawn location for a player based on game mode,
     * ally locations, and enemy locations.
     *
     * @param player The player to find a spawn for.
     * @return A suitable spawn location.
     */
    public Location findSpawnForPlayer(Player player) {
        LocalGameMap map = game.getActiveMap();
        List<Location> allSpawns = map.getSpawnLocations();
        
        if (allSpawns.isEmpty()) {
            return map.getSpectatorLocation(); // Failsafe
        }

        List<Player> allLivingPlayers = game.getPlayers(true);
        GameTeam playerTeam = getTeam(player);

        // Try to spawn near allies in team games
        if (game.getCurrentMode().isTeamMode() && playerTeam != null) {
            List<Player> allies = playerTeam.getOnlinePlayers().stream()
                .filter(p -> !p.equals(player) && allLivingPlayers.contains(p))
                .collect(Collectors.toList());

            if (!allies.isEmpty()) {
                Location location = LocationUtil.getClosestLocation(allSpawns, allies, allLivingPlayers).orElse(null);
                if (location != null) return location;
            }
        }

        // Default: Spawn away from enemies
        List<Player> enemies = allLivingPlayers.stream()
            .filter(p -> getTeam(p) != playerTeam)
            .collect(Collectors.toList());

        if (!enemies.isEmpty()) {
            Location location = LocationUtil.getFurthestLocation(allSpawns, enemies).orElse(null);
            if (location != null) return location;
        }

        // If all else fails, return a random spawn point
        return allSpawns.get(ThreadLocalRandom.current().nextInt(allSpawns.size()));
    }

    /**
     * Resets all team data, clearing all lists and maps for a new game.
     */
    public void reset() {
        activeTeams.clear();
        playerTeamMap.clear();
        placementRankings.clear();
    }

    /**
     * Handles the elimination of a team, removing it from active play
     * and adding it to the front of the {@link #placementRankings} list.
     *
     * @param team The team that has been eliminated.
     */
    private void eliminateTeam(GameTeam team) {
        if (!activeTeams.contains(team)) return;

        activeTeams.remove(team);
        placementRankings.addFirst(team); // e.g., 3rd place, then 2nd, etc.
    }

    /**
     * Checks if only one or zero teams remain. If so, it finalizes the
     * rankings and transitions the game to the {@link WinnerState}.
     */
    private void checkEndGameCondition() {
        if (activeTeams.size() <= 1) {
            if (!activeTeams.isEmpty()) {
                eliminateTeam(activeTeams.get(0)); // The last remaining team is the winner
            }
            game.getGameManager().setState(new WinnerState(placementRankings));
        }
    }

    /**
     * @param player The player.
     * @return The {@link GameTeam} the player is on, or null if they are not on a team.
     */
    public GameTeam getTeam(Player player) {
        return playerTeamMap.get(player.getUniqueId());
    }

    /**
     * @return An unmodifiable list of all teams still alive in the game.
     */
    public List<GameTeam> getActiveTeams() {
        return Collections.unmodifiableList(activeTeams);
    }
    
    /**
     * @return An unmodifiable list of the final team placements, from 1st to last.
     */
    public List<GameTeam> getFinalRankings() {
        return Collections.unmodifiableList(placementRankings);
    }
}