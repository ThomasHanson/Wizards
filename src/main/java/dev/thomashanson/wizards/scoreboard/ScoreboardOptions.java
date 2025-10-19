package dev.thomashanson.wizards.scoreboard;

public class ScoreboardOptions {

    public enum TabHealthStyle {
        NONE,
        HEARTS,
        NUMBER
    }

    private TabHealthStyle tabHealthStyle;
    private boolean showHealthUnderName;

    public ScoreboardOptions(TabHealthStyle tabHealthStyle, boolean showHealthUnderName) {
        this.tabHealthStyle = tabHealthStyle;
        this.showHealthUnderName = showHealthUnderName;
    }

    public static final ScoreboardOptions DEFAULT_OPTIONS = new ScoreboardOptions(TabHealthStyle.NUMBER, true);

    public TabHealthStyle getTabHealthStyle() {
        return tabHealthStyle;
    }

    public boolean shouldShowHealthUnderName() {
        return showHealthUnderName;
    }

    public void setShowHealthUnderName(boolean showHealthUnderName) {
        this.showHealthUnderName = showHealthUnderName;
        // Note: If this changes, existing scoreboards need to be updated.
        // The WizardsScoreboard should handle calling an update when options change.
    }

    public void setTabHealthStyle(TabHealthStyle tabHealthStyle) {
        this.tabHealthStyle = tabHealthStyle;
        // Similar to above, an update is needed.
    }
}