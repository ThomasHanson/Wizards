package dev.thomashanson.wizards.scoreboard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.manager.GameManager;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class WizardsScoreboard {

    public static final int MAX_LINES = 16;
    private static final char COLOR_CHAR = '§'; // The legacy color code character

    private final WizardsPlugin plugin;
    private final GameManager gameManager;
    private final LanguageManager languageManager;
    private final ScoreboardOptions options;
    private final ObjectiveWrapper objectiveWrapper;

    private final List<ScoreboardTeam> gameTeams = Collections.synchronizedList(new ArrayList<>());
    private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();

    private final Map<UUID, Scoreboard> playerScoreboardMap = new ConcurrentHashMap<>();
    private final Map<Scoreboard, List<Component>> previousLinesMap = new ConcurrentHashMap<>();

    private Function<Player, Component> titleGenerator;
    private Function<Player, List<Component>> lineGenerator;

    public WizardsScoreboard(WizardsPlugin plugin, GameManager gameManager, LanguageManager languageManager, ScoreboardOptions options) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.languageManager = languageManager;
        this.options = options;
        this.objectiveWrapper = new ObjectiveWrapper();

        this.titleGenerator = player -> languageManager.getTranslated(player, "wizards.scoreboard.title");
        this.lineGenerator = player -> Collections.emptyList();
    }

    public void addPlayer(Player player) {
        activePlayers.add(player.getUniqueId());
        Scoreboard playerScore = playerScoreboardMap.computeIfAbsent(player.getUniqueId(), k -> Bukkit.getScoreboardManager().getNewScoreboard());
        player.setScoreboard(playerScore);

        for (ScoreboardTeam gameTeam : gameTeams) {
            gameTeam.refresh(playerScore);
        }
        updateScoreboardForPlayer(player);
    }

    public void removePlayer(Player player) {
        activePlayers.remove(player.getUniqueId());
        playerScoreboardMap.remove(player.getUniqueId());

        if (player.isOnline()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public Scoreboard getBukkitScoreboard(Player player) {
        return playerScoreboardMap.get(player.getUniqueId());
    }

    public void updateScoreboardForPlayer(Player player) {
        if (player == null || !player.isOnline() || !activePlayers.contains(player.getUniqueId())) {
            if (player != null) {
                activePlayers.remove(player.getUniqueId());
                playerScoreboardMap.remove(player.getUniqueId());
            }
            return;
        }

        Scoreboard playerScore = playerScoreboardMap.get(player.getUniqueId());
        if (playerScore == null) {
            plugin.getLogger().warning(String.format("Scoreboard instance missing for player %s. Re-adding.", player.getName()));
            addPlayer(player);
            playerScore = playerScoreboardMap.get(player.getUniqueId());
            if (playerScore == null) return;
        }

        List<Component> lines = lineGenerator.apply(player);
        Component title = titleGenerator.apply(player);

        updateScoreboardContent(player, playerScore, title, lines == null ? Collections.emptyList() : lines);
    }

    public void updateAllScoreboards() {
        new ArrayList<>(activePlayers).forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                updateScoreboardForPlayer(p);
            } else {
                activePlayers.remove(uuid);
                playerScoreboardMap.remove(uuid);
            }
        });
    }

    private void updateScoreboardContent(Player viewer, Scoreboard scoreboard, Component title, List<Component> linesFromGenerator) {
        Objective objective = objectiveWrapper.getOrCreateSidebarObjective(scoreboard);

        String legacyTitle = LegacyComponentSerializer.legacySection().serialize(title);
        if (!objective.getDisplayName().equals(legacyTitle)) {
            objective.setDisplayName(legacyTitle);
        }

        List<Component> linesToDisplay = linesFromGenerator.size() > MAX_LINES ? linesFromGenerator.subList(0, MAX_LINES) : linesFromGenerator;
        List<Component> previousPlayerDisplayLines = previousLinesMap.getOrDefault(scoreboard, Collections.emptyList());

        if (previousPlayerDisplayLines.equals(linesToDisplay) && scoreboard.getEntries().size() == linesToDisplay.size()) {
            updateHealthAndGameTeams(viewer, scoreboard);
            return;
        }

        Set<String> oldEntries = new HashSet<>(scoreboard.getEntries());
        for (String entry : oldEntries) {
            if (objective.getScore(entry).isScoreSet() && !isHealthObjectiveEntry(entry, scoreboard)) {
                boolean wasLineEntry = false;
                for (Team team : scoreboard.getTeams()) {
                    if (team.getName().startsWith("sbLine_") && team.getEntries().contains(entry)) {
                        wasLineEntry = true;
                        break;
                    }
                }
                if (wasLineEntry) {
                    scoreboard.resetScores(entry);
                }
            }
        }

        scoreboard.getTeams().stream()
                  .filter(t -> t.getName().startsWith("sbLine_"))
                  .forEach(Team::unregister);

        previousLinesMap.put(scoreboard, new ArrayList<>(linesToDisplay));

        if (objective.getDisplaySlot() != DisplaySlot.SIDEBAR) {
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        updateHealthAndGameTeams(viewer, scoreboard);

        List<String> uniqueColorCodes = getUniqueInvisibleEntries(linesToDisplay.size());

        for (int i = 0; i < linesToDisplay.size(); i++) {
            Component lineComponent = linesToDisplay.get(i);
            int scoreValue = MAX_LINES - i;
            String uniqueEntry = uniqueColorCodes.get(i);
            Team lineTeam = scoreboard.getTeam("sbLine_" + i);
            if (lineTeam == null) {
                lineTeam = scoreboard.registerNewTeam("sbLine_" + i);
            }

            if (!lineTeam.getEntries().contains(uniqueEntry)) {
                new HashSet<>(lineTeam.getEntries()).forEach(lineTeam::removeEntry);
                lineTeam.addEntry(uniqueEntry);
            }

            String lineText = LegacyComponentSerializer.legacySection().serialize(lineComponent);
            setTeamPrefixSuffix(lineTeam, lineText, 64);
            objective.getScore(uniqueEntry).setScore(scoreValue);
        }
    }

    private boolean isHealthObjectiveEntry(String entry, Scoreboard scoreboard) {
        Objective nameHealth = scoreboard.getObjective(ObjectiveWrapper.NAME_HEALTH_OBJECTIVE);
        if (nameHealth != null && nameHealth.getScore(entry).isScoreSet()) return true;
        Objective tabHealth = scoreboard.getObjective(ObjectiveWrapper.TAB_HEALTH_OBJECTIVE);
        return tabHealth != null && tabHealth.getScore(entry).isScoreSet();
    }

    private void updateHealthAndGameTeams(Player viewer, Scoreboard scoreboard) {
        updateHealthObjectives(scoreboard);
        for (ScoreboardTeam gameTeam : this.gameTeams) {
            gameTeam.refresh(scoreboard);
        }
    }

    /**
     * Replicates the functionality of the legacy {@code ChatColor.getLastColors} method.
     * This is necessary to correctly split scoreboard lines that exceed the character limit
     * for prefixes, ensuring that color and formatting are carried over to the suffix.
     * This helper method keeps the logic self-contained and removes the dependency
     * on the deprecated ChatColor class.
     *
     * @param text The legacy-formatted string to parse.
     * @return A string containing the legacy codes for the last color and all active formatting.
     */
    private String findLastLegacyStyles(String text) {
        String color = "";
        StringBuilder formats = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == COLOR_CHAR && i + 1 < text.length()) {
                char code = text.charAt(i + 1);
                String validColorCodes = "0123456789abcdef";
                String validFormatCodes = "klmno";

                if (validColorCodes.indexOf(code) > -1) {
                    color = COLOR_CHAR + "" + code;
                    formats.setLength(0); // Color resets previous formats
                } else if (validFormatCodes.indexOf(code) > -1) {
                    if (formats.indexOf(String.valueOf(code)) == -1) {
                        formats.append(COLOR_CHAR).append(code);
                    }
                } else if (code == 'r') {
                    color = "";
                    formats.setLength(0); // Reset all
                }
            }
        }
        return color + formats;
    }

    /**
     * Sets the prefix and suffix for a team, automatically splitting long text.
     * This method serves as a bridge to the legacy Bukkit Scoreboard API, which
     * requires legacy-formatted strings.
     *
     * @param team The Bukkit team to modify.
     * @param text The full legacy text to set.
     * @param maxLengthPerPart The max length for the prefix (usually 64).
     */
    private void setTeamPrefixSuffix(Team team, String text, int maxLengthPerPart) {
        String prefix = text;
        String suffix = "";

        if (text.length() > maxLengthPerPart) {
            prefix = text.substring(0, maxLengthPerPart);
            String lastStyles = findLastLegacyStyles(prefix);
            suffix = lastStyles + text.substring(maxLengthPerPart);

            if (suffix.length() > maxLengthPerPart) {
                suffix = suffix.substring(0, maxLengthPerPart);
            }
        }

        if (!team.getPrefix().equals(prefix)) team.setPrefix(prefix);
        if (!team.getSuffix().equals(suffix)) team.setSuffix(suffix);
    }

    private void updateHealthObjectives(Scoreboard scoreboard) {
        if (options.getTabHealthStyle() != ScoreboardOptions.TabHealthStyle.NONE) {
            Objective tabHealthObjective = objectiveWrapper.getTabHealthObjective(options.getTabHealthStyle(), scoreboard);
            if (tabHealthObjective.getDisplaySlot() != DisplaySlot.PLAYER_LIST) {
                tabHealthObjective.setDisplaySlot(DisplaySlot.PLAYER_LIST);
            }
        } else {
            Objective currentTabHealth = scoreboard.getObjective(ObjectiveWrapper.TAB_HEALTH_OBJECTIVE);
            if (currentTabHealth != null && currentTabHealth.getDisplaySlot() == DisplaySlot.PLAYER_LIST) {
                scoreboard.clearSlot(DisplaySlot.PLAYER_LIST);
            }
        }

        if (options.shouldShowHealthUnderName()) {
            Objective nameHealthObjective = objectiveWrapper.getNameHealthObjective(scoreboard);
            if (nameHealthObjective.getDisplaySlot() != DisplaySlot.BELOW_NAME) {
                nameHealthObjective.setDisplaySlot(DisplaySlot.BELOW_NAME);
            }
        } else {
            Objective currentNameHealth = scoreboard.getObjective(ObjectiveWrapper.NAME_HEALTH_OBJECTIVE);
            if (currentNameHealth != null && currentNameHealth.getDisplaySlot() == DisplaySlot.BELOW_NAME) {
                scoreboard.clearSlot(DisplaySlot.BELOW_NAME);
            }
        }
    }
    
    /**
     * Generates a list of unique, invisible strings for use as scoreboard entries.
     * This is a standard practice for creating scoreboard lines that can be dynamically updated.
     * By using hardcoded '§' characters, we avoid depending on the ChatColor class.
     *
     * @param count The number of unique entries to generate.
     * @return A list of unique invisible strings.
     */
    private List<String> getUniqueInvisibleEntries(int count) {
        List<String> codes = new ArrayList<>();
        // Using a simple counter with color codes is sufficient and performant.
        for (int i = 0; i < count; i++) {
            codes.add(COLOR_CHAR + "" + (i / 10) + COLOR_CHAR + "" + (i % 10));
        }
        return codes;
    }

    private String stripLegacy(String legacyText) {
        Component component = LegacyComponentSerializer.legacySection().deserialize(legacyText);
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public Optional<ScoreboardTeam> findGameTeam(String name) {
        return gameTeams.stream()
                .filter(team -> stripLegacy(team.getDisplayName()).equalsIgnoreCase(stripLegacy(name)))
                .findAny();
    }

    public ScoreboardTeam createGameTeam(String name, String displayName, NamedTextColor teamColor) {
        if (name.length() > 16) {
            plugin.getLogger().warning(String.format("Team name '%s' is longer than 16 characters and may not work correctly.", name));
        }
        if (findGameTeam(name).isPresent()) {
            return findGameTeam(name).get();
        }
        ScoreboardTeam team = new ScoreboardTeam(name, displayName, teamColor, this);
        this.gameTeams.add(team);
        playerScoreboardMap.values().forEach(team::refresh);
        return team;
    }

    public void removeGameTeam(ScoreboardTeam team) {
        if (!this.gameTeams.contains(team)) return;
        team.destroyOnAllScoreboards(playerScoreboardMap.values());
        this.gameTeams.remove(team);
    }

    public List<ScoreboardTeam> getGameTeams() {
        return Collections.unmodifiableList(gameTeams);
    }

    public Optional<ScoreboardTeam> getGameTeamForPlayer(UUID playerUUID) {
        return gameTeams.stream().filter(team -> team.isOnTeam(playerUUID)).findFirst();
    }

    public void destroy() {
        for (UUID playerUUID : new ArrayList<>(activePlayers)) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        gameTeams.forEach(team -> team.destroyOnAllScoreboards(playerScoreboardMap.values()));
        gameTeams.clear();
        activePlayers.clear();
        playerScoreboardMap.clear();
        previousLinesMap.clear();
    }

    public void setTitleGenerator(Function<Player, Component> titleGenerator) {
        this.titleGenerator = Objects.requireNonNull(titleGenerator);
    }

    public void setLineGenerator(Function<Player, List<Component>> lineGenerator) {
        this.lineGenerator = Objects.requireNonNull(lineGenerator);
    }
    
    public ScoreboardOptions getOptions() {
        return options;
    }

    public Collection<Scoreboard> getAllPlayerBukkitScoreboards() {
        return playerScoreboardMap.values();
    }
}