package dev.thomashanson.wizards.game.mode;

/**
 * Defines the available game modes for Wizards, controlling team size,
 * player counts, and match duration parameters.
 */
public enum WizardsMode {

    /**
     * Standard solo mode with 16 players.
     */
    SOLO_NORMAL(1, 16, 10),

    /**
     * A larger, more chaotic solo mode with 24 players.
     */
    SOLO_BRAWL(1, 24, 15),

    /**
     * A large-scale solo mode for 40 players.
     */
    SOLO_TOURNEY(1, 40, 10),

    /**
     * Standard 8-team doubles mode (16 players total).
     */
    DOUBLES_NORMAL(8, 2, 10),

    /**
     * A larger, 12-team doubles mode (24 players total).
     */
    DOUBLES_BRAWL(12, 2, 15),

    /**
     * A large-scale, 20-team doubles mode (40 players total).
     */
    DOUBLES_TOURNEY(20, 2, 10);

    private final int numTeams;
    private final int numPlayersPerTeam;
    private final int preparationSecs;

    /**
     * Creates a new WizardsMode configuration.
     *
     * @param numTeams          The number of teams (1 for solo modes).
     * @param numPlayersPerTeam The number of players on each team.
     * @param preparationSecs   The duration of the {@link dev.thomashanson.wizards.game.state.types.PrepareState} in seconds.
     */
    WizardsMode(int numTeams, int numPlayersPerTeam, int preparationSecs) {
        this.numTeams = numTeams;
        this.numPlayersPerTeam = numPlayersPerTeam;
        this.preparationSecs = preparationSecs;
    }

    /**
     * @return The duration of the preparation (freeze) state in seconds.
     */
    public int getPreparationSecs() {
        return preparationSecs;
    }

    /**
     * @return True if this is a team-based mode (e.g., Doubles), false for solo.
     */
    public boolean isTeamMode() {
        return numTeams > 1;
    }

    /**
     * @return True if this is a "Brawl" variant, which may have different game rules.
     */
    public boolean isBrawl() {
        return this == SOLO_BRAWL || this == DOUBLES_BRAWL;
    }

    /**
     * @return The minimum number of players required to start a game (75% of max).
     */
    public int getMinPlayers() {
        return (int) (0.75 * numTeams * numPlayersPerTeam);
    }

    /**
     * @return The absolute maximum number of players for this mode.
     */
    public int getMaxPlayers() {
        return numTeams * numPlayersPerTeam;
    }

    /**
     * @return The number of teams in this mode (1 for solo).
     */
    public int getNumTeams() {
        return numTeams;
    }

    /**
     * @return The number of players per team.
     */
    public int getNumPlayersPerTeam() {
        return numPlayersPerTeam;
    }
}