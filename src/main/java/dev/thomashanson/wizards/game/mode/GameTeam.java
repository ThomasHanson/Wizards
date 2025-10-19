package dev.thomashanson.wizards.game.mode;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import dev.thomashanson.wizards.game.manager.WizardManager;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * A simple data class representing a team of players. It holds the team's
 * name and a set of its members' UUIDs. It contains no complex game logic.
 */
public class GameTeam {

    public enum TeamRelation {
        ALLY(NamedTextColor.GREEN),
        ENEMY(NamedTextColor.RED),
        SOLO(NamedTextColor.YELLOW),
        UNKNOWN(NamedTextColor.GRAY);

        private final NamedTextColor relationColor;

        TeamRelation(NamedTextColor relationColor) {
            this.relationColor = relationColor;
        }

        public NamedTextColor getRelationColor() {
            return relationColor;
        }
    }

    private final String teamName;
    private final Set<UUID> teamMembers = new HashSet<>();

    public GameTeam(String teamName) {
        this.teamName = teamName;
    }

    public void addPlayer(Player player) {
        teamMembers.add(player.getUniqueId());
    }

    public boolean isOnTeam(Player player) {
        return teamMembers.contains(player.getUniqueId());
    }

    /**
     * Checks if any member of this team is still considered alive in the game.
     * @param wizardManager The manager responsible for tracking player status.
     * @return True if at least one team member is alive.
     */
    public boolean isTeamAlive(WizardManager wizardManager) {
        for (UUID memberId : teamMembers) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null && wizardManager.getWizard(player) != null) {
                return true; // Found an alive member
            }
        }
        return false;
    }

    /**
     * Safely gets the set of currently online players on this team.
     * This method is safe from modification issues as it returns a new set.
     * @return A new, unmodifiable set of online players on the team.
     */
    public Set<Player> getOnlinePlayers() {
        return teamMembers.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    public String getTeamName() {
        return teamName;
    }

    public Set<UUID> getTeamMembers() {
        return Collections.unmodifiableSet(teamMembers);
    }

    public int getTeamSize() {
        return teamMembers.size();
    }
}