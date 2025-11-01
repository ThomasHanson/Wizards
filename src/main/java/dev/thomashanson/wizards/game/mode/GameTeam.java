package dev.thomashanson.wizards.game.mode;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import dev.thomashanson.wizards.game.manager.TeamManager;
import dev.thomashanson.wizards.game.manager.WizardManager;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * A simple data class representing a team of players in a game.
 * <p>
 * This class holds the team's name and a set of its members' UUIDs.
 * It contains no complex game logic itself; all logic is handled
 * by the {@link TeamManager}.
 */
public class GameTeam {

    /**
     * Represents the relationship between two players or teams.
     */
    public enum TeamRelation {
        /** The players are on the same team. */
        ALLY(NamedTextColor.GREEN),
        /** The players are on opposing teams. */
        ENEMY(NamedTextColor.RED),
        /** The player is in a solo game (everyone is an enemy). */
        SOLO(NamedTextColor.YELLOW),
        /** The relationship could not be determined. */
        UNKNOWN(NamedTextColor.GRAY);

        private final NamedTextColor relationColor;

        TeamRelation(NamedTextColor relationColor) {
            this.relationColor = relationColor;
        }

        /**
         * @return The {@link NamedTextColor} associated with this relation
         * (e.g., green for ALLY, red for ENEMY).
         */
        public NamedTextColor getRelationColor() {
            return relationColor;
        }
    }

    private final String teamName;
    private final Set<UUID> teamMembers = new HashSet<>();

    /**
     * Creates a new GameTeam.
     *
     * @param teamName The display name of the team (e.g., "Team 1" or a player's name).
     */
    public GameTeam(String teamName) {
        this.teamName = teamName;
    }

    /**
     * Adds a player to this team.
     *
     * @param player The player to add.
     */
    public void addPlayer(Player player) {
        teamMembers.add(player.getUniqueId());
    }

    /**
     * Checks if a player is a member of this team.
     *
     * @param player The player to check.
     * @return True if the player's UUID is in the member set, false otherwise.
     */
    public boolean isOnTeam(Player player) {
        return teamMembers.contains(player.getUniqueId());
    }

    /**
     * Checks if any member of this team is still considered alive in the game.
     * A player is considered "alive" if they have a {@link dev.thomashanson.wizards.game.Wizard}
     * object associated with them in the {@link WizardManager}.
     *
     * @param wizardManager The manager responsible for tracking player status.
     * @return True if at least one team member is alive, false otherwise.
     */
    public boolean isTeamAlive(WizardManager wizardManager) {
        for (UUID memberId : teamMembers) {
            Player player = Bukkit.getPlayer(memberId);
            // Player is non-null (online) AND is still an active wizard (not eliminated)
            if (player != null && wizardManager.getWizard(player) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the set of currently online players on this team.
     *
     * @return A new, unmodifiable set of online {@link Player} objects on the team.
     */
    public Set<Player> getOnlinePlayers() {
        return teamMembers.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * @return The display name of the team.
     */
    public String getTeamName() {
        return teamName;
    }

    /**
     * @return An unmodifiable view of the set of {@link UUID}s for all team members.
     */
    public Set<UUID> getTeamMembers() {
        return Collections.unmodifiableSet(teamMembers);
    }

    /**
     * @return The total number of players assigned to this team.
     */
    public int getTeamSize() {
        return teamMembers.size();
    }
}