package dev.thomashanson.wizards.game.mode;

import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.util.LocationUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

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

    private final Set<Player> players = new HashSet<>();

    private String teamName;
    private final List<Location> spawns;

    public GameTeam(Wizards game, String teamName, List<Location> spawns) {
        this.game = game;
        this.spawns = spawns;
    }

    public Location getSpawn() {

        Location location = LocationUtil.getLocationNearPlayers(spawns, getPlayers(true), game.getPlayers(true));

        if (location != null)
            return location;

        if (game.getCurrentMode().isTourney()) {

            // Spawn near other enemies
            location = LocationUtil.getLocationNearPlayers(spawns, game.getPlayers(true), game.getPlayers(true));

            if (location != null)
                return location;
        }

        return spawns.get(ThreadLocalRandom.current().nextInt(spawns.size()));
    }

    public void addPlayer(Player player) {
        players.add(player);
    }

    public List<Player> getPlayers(boolean alive) {

        List<Player> players = new ArrayList<>();

        for (Player player : this.players)
            if (!alive || (isAlive(player) && player.isOnline()))
                players.add(player);

        return players;
    }

    private boolean isAlive(Player player) {
        return game.getWizard(player) != null;
    }

    public boolean isTeamAlive() {

        for (Player player : players)
            if (isAlive(player))
                return true;

        return false;
    }

    public boolean isOnTeam(Player player) {
        return players.contains(player);
    }

    public Wizards getGame() {
        return game;
    }

    public int getTeamSize() {
        return players.size();
    }

    public Set<Player> getPlayers() {
        return players;
    }

    public String getTeamName() {
        return teamName;
    }
}