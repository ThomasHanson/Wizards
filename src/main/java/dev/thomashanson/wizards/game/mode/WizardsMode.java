package dev.thomashanson.wizards.game.mode;

public enum WizardsMode {

    // Added a 3rd parameter for preparation time in seconds
    SOLO_NORMAL(1, 16, 10),
    SOLO_BRAWL(1, 24, 15),
    SOLO_TOURNEY(1, 40, 10),

    DOUBLES_NORMAL(8, 2, 10),
    DOUBLES_BRAWL(12, 2, 15),
    DOUBLES_TOURNEY(20, 2, 10);

    private final int numTeams;
    private final int numPlayersPerTeam;
    private final int preparationSecs;

    WizardsMode(int numTeams, int numPlayersPerTeam, int preparationSecs) {
        this.numTeams = numTeams;
        this.numPlayersPerTeam = numPlayersPerTeam;
        this.preparationSecs = preparationSecs; // Set the new field
    }

    // This method is now just a simple getter, with no hard-coded logic.
    public int getPreparationSecs() {
        return preparationSecs;
    }

    public boolean isTeamMode() {
        return numTeams > 1;
    }

    public boolean isBrawl() {
        return this == SOLO_BRAWL || this == DOUBLES_BRAWL;
    }

    public int getMinPlayers() {
        return (int) (0.75 * numTeams * numPlayersPerTeam);
    }

    public int getMaxPlayers() {
        return numTeams * numPlayersPerTeam;
    }

    public int getNumTeams() {
        return numTeams;
    }

    public int getNumPlayersPerTeam() {
        return numPlayersPerTeam;
    }
}