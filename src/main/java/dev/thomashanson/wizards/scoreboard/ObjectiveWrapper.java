package dev.thomashanson.wizards.scoreboard;

import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

final class ObjectiveWrapper {

    public static final String SIDEBAR_OBJECTIVE_NAME = "WizSidebar";
    public static final String NAME_HEALTH_OBJECTIVE = "WizHealthNM";
    public static final String TAB_HEALTH_OBJECTIVE = "WizHealthTab";

    private static final Component SIDEBAR_TITLE = Component.text("WIZARDS", NamedTextColor.GOLD, TextDecoration.BOLD);
    private static final Component NAME_HEALTH_DISPLAY = Component.text("❤", NamedTextColor.RED);

    Objective getOrCreateSidebarObjective(Scoreboard scoreboard) {
        Objective objective = scoreboard.getObjective(SIDEBAR_OBJECTIVE_NAME);
        if (objective == null) {
            String legacyTitle = LegacyComponentSerializer.legacySection().serialize(SIDEBAR_TITLE);
            objective = scoreboard.registerNewObjective(SIDEBAR_OBJECTIVE_NAME, Criteria.DUMMY, legacyTitle);
        }
        return objective;
    }

    Objective getNameHealthObjective(Scoreboard scoreboard) {
        Objective healthObjective = scoreboard.getObjective(NAME_HEALTH_OBJECTIVE);
        if (healthObjective == null) {
            String legacyDisplayName = LegacyComponentSerializer.legacySection().serialize(NAME_HEALTH_DISPLAY);
            healthObjective = scoreboard.registerNewObjective(
                    NAME_HEALTH_OBJECTIVE,
                    Criteria.HEALTH,
                    legacyDisplayName,
                    RenderType.INTEGER
            );
        }
        return healthObjective;
    }

    Objective getTabHealthObjective(ScoreboardOptions.TabHealthStyle healthStyle, Scoreboard scoreboard) {
        Objective healthObjective = scoreboard.getObjective(TAB_HEALTH_OBJECTIVE);
        RenderType renderType = healthStyle == ScoreboardOptions.TabHealthStyle.HEARTS ? RenderType.HEARTS : RenderType.INTEGER;

        if (healthObjective == null) {
            healthObjective = scoreboard.registerNewObjective(
                    TAB_HEALTH_OBJECTIVE,
                    Criteria.HEALTH,
                    Component.text("Player Health"), // Display name is often not shown in player list
                    renderType
            );
        } else {
            if (healthObjective.getRenderType() != renderType) {
                healthObjective.unregister();
                healthObjective = scoreboard.registerNewObjective(TAB_HEALTH_OBJECTIVE, Criteria.HEALTH, Component.text("Player Health"), renderType);
            }
        }
        return healthObjective;
    }
}