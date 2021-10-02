package dev.thomashanson.wizards.game.mode;

public enum WizardsMode {

    SOLO_NORMAL (1, 16),
    SOLO_BRAWL (1, 24),
    SOLO_TOURNEY (1, 40),

    DOUBLES_NORMAL (8, 2),
    DOUBLES_BRAWL (12, 2),
    DOUBLES_TOURNEY (20, 2);

    WizardsMode(int numTeams, int numPlayers) {
        this.numTeams = numTeams;
        this.numPlayers = numPlayers;
    }

    private final int numTeams;
    private final int numPlayers;

    public boolean isTeamMode() {
        return numTeams > 1;
    }

    public boolean isBrawl() {
        return this == SOLO_BRAWL || this == DOUBLES_BRAWL;
    }

    public boolean isTourney() {
        return this == SOLO_TOURNEY || this == DOUBLES_TOURNEY;
    }

    public int getMaxPlayers() {
        return numTeams * numPlayers;
    }

    public int getNumTeams() {
        return numTeams;
    }

    public int getNumPlayers() {
        return numPlayers;
    }

    public int getPreparationSecs() {
        return isBrawl() ? 15 : 10;
    }
}