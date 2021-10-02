package dev.thomashanson.wizards.game.mode;

import dev.thomashanson.wizards.game.Wizards;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class GameTeam {

    public enum SpawnType {
        NEAR_ALLIES,
        NEAR_ENEMIES
    }

    public enum TeamRelation {

        /**
         * Represents a player that is on the
         * same team. If that player is an
         * ally, name tag will appear green.
         */
        ALLY (ChatColor.GREEN),

        /**
         * Represents a player that is on the
         * opposite team. If the player is an
         * enemy, name tag will appear red.
         */
        ENEMY (ChatColor.RED),

        /**
         * Represents a solo mode, in which
         * it is a free-for-all. Every players
         * name tag will remain yellow.
         */
        SOLO (ChatColor.YELLOW),

        /**
         * Represents a team relation that is
         * unknown. This occurs when the game
         * is active and trying to compare 2
         * teams, but 1 of the teams cannot
         * be found or identified.
         */
        UNKNOWN (ChatColor.GRAY);

        TeamRelation(ChatColor relationColor) {
            this.relationColor = relationColor;
        }

        private final ChatColor relationColor;

        public ChatColor getRelationColor() {
            return relationColor;
        }
    }

    private final Wizards game;

    private final UUID teamId;
    private final Set<Player> players = new HashSet<>();

    private final List<Location> spawns;

    public GameTeam(Wizards game, UUID teamId, List<Location> spawns) {
        this.game = game;
        this.teamId = teamId;
        this.spawns = spawns;
    }

    public boolean isAlive(Player player) {
        return game.getWizard(player) != null;
    }

    public boolean isTeamAlive() {

        boolean alive = false;

        for (Player player : players)
            if (isAlive(player))
                alive = true;

        return alive;
    }

    public boolean isOnTeam(Player player) {
        return players.contains(player);
    }

    /*

        //Keep allies together
        if (!game.isLive() && Host.SpawnNearAllies) {

            //Find Location Nearest Ally
            Location loc = UtilAlg.getLocationNearPlayers(spawns, GetPlayers(true), Host.GetPlayers(true));
            if (loc != null)
                return loc;

            //No allies existed spawned yet

            //Spawn near enemies (used for SG)
            if (Host.SpawnNearEnemies) {

                loc = UtilAlg.getLocationNearPlayers(spawns, Host.GetPlayers(true), Host.GetPlayers(true));

                if (loc != null)
                    return loc;

            } else {

                //Spawn away from enemies
                loc = UtilAlg.getLocationAwayFromPlayers(spawns, Host.GetPlayers(true));

                if (loc != null)
                    return loc;
            }

        } else {

            //Spawn near players
            if (Host.SpawnNearEnemies) {

                Location loc = UtilAlg.getLocationNearPlayers(spawns, Host.GetPlayers(true), Host.GetPlayers(true));

                if (loc != null)
                    return loc;

            } else {

                Location loc = UtilAlg.getLocationAwayFromPlayers(spawns, Host.GetPlayers(true));

                if (loc != null)
                    return loc;
            }
        }

        return spawns.get(UtilMath.r(spawns.size()));
         */

    public Wizards getGame() {
        return game;
    }

    public Set<Player> getPlayers() {
        return players;
    }

    public List<Location> getSpawns() {
        return spawns;
    }
}