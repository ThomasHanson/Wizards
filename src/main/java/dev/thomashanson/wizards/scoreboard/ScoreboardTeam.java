package dev.thomashanson.wizards.scoreboard;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class ScoreboardTeam {

    private String name; // Internal Bukkit team name (max 16 chars)
    private String displayName; // Conceptual name for display purposes
    private final Set<UUID> members = ConcurrentHashMap.newKeySet(); // Thread-safe set for members

    private final WizardsScoreboard scoreboardManager; // Reference to the main scoreboard controller

    // Team options
    private String prefix = "";
    private String suffix = "";
    private boolean allowFriendlyFire = false;
    private boolean canSeeFriendlyInvisibles = true;


    ScoreboardTeam(String name, String displayName, NamedTextColor teamColor, WizardsScoreboard scoreboardManager) {
        this.name = name.length() > 16 ? name.substring(0, 16) : name; // Ensure <= 16 chars
        this.displayName = displayName;
        this.scoreboardManager = scoreboardManager;
        // The Bukkit API requires a legacy string, so we serialize the color.
        this.prefix = LegacyComponentSerializer.legacySection().serialize(Component.text("", teamColor));
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        String oldName = this.name;
        this.name = name.length() > 16 ? name.substring(0, 16) : name;
        if (!oldName.equals(this.name)) {
            // Unregister old team name from all scoreboards if name changes
            scoreboardManager.getAllPlayerBukkitScoreboards().forEach(sb -> {
                Team t = sb.getTeam(oldName);
                if (t != null) t.unregister();
            });
            refreshAll(); // Re-register with new name and apply properties
        }
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        // Consider if display name change should alter prefix/suffix, then call refreshAll()
        // For now, prefix/suffix are set separately
    }

    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; refreshAll(); }

    public String getSuffix() { return suffix; }
    public void setSuffix(String suffix) { this.suffix = suffix; refreshAll(); }

    public boolean isAllowFriendlyFire() { return allowFriendlyFire; }
    public void setAllowFriendlyFire(boolean allowFriendlyFire) { this.allowFriendlyFire = allowFriendlyFire; refreshAll(); }

    public boolean canSeeFriendlyInvisibles() { return canSeeFriendlyInvisibles; }
    public void setCanSeeFriendlyInvisibles(boolean canSeeFriendlyInvisibles) { this.canSeeFriendlyInvisibles = canSeeFriendlyInvisibles; refreshAll(); }


    /**
     * Refreshes this team's representation on all player scoreboards.
     */
    public void refreshAll() {
        scoreboardManager.getAllPlayerBukkitScoreboards().forEach(this::refresh);
    }

    /**
     * Refreshes this team's representation on a specific Bukkit scoreboard.
     * This is for nametags, player list coloring, etc. for members of THIS team.
     */
    void refresh(Scoreboard individualPlayerScoreboard) {
        if (individualPlayerScoreboard == null) return;

        Team bukkitTeam = individualPlayerScoreboard.getTeam(this.name);
        if (bukkitTeam == null) {
            bukkitTeam = individualPlayerScoreboard.registerNewTeam(this.name);
        }

        // Apply team properties
        if (!bukkitTeam.getPrefix().equals(this.prefix)) bukkitTeam.setPrefix(this.prefix);
        if (!bukkitTeam.getSuffix().equals(this.suffix)) bukkitTeam.setSuffix(this.suffix);
        if (bukkitTeam.allowFriendlyFire() != this.allowFriendlyFire) bukkitTeam.setAllowFriendlyFire(this.allowFriendlyFire);
        if (bukkitTeam.canSeeFriendlyInvisibles() != this.canSeeFriendlyInvisibles) bukkitTeam.setCanSeeFriendlyInvisibles(this.canSeeFriendlyInvisibles);

        // Synchronize members
        Set<String> bukkitTeamEntries = new HashSet<>(bukkitTeam.getEntries());
        Set<String> currentMemberEntries = new HashSet<>();

        for (UUID memberUuid : members) {
            Player memberPlayer = Bukkit.getPlayer(memberUuid);
            String entry = (memberPlayer != null && memberPlayer.isOnline()) ? memberPlayer.getName() : memberUuid.toString(); // Use name if online
            currentMemberEntries.add(entry);
            if (!bukkitTeamEntries.contains(entry)) {
                bukkitTeam.addEntry(entry);
            }
        }

        // Remove entries from Bukkit team that are no longer in this ScoreboardTeam
        bukkitTeamEntries.removeAll(currentMemberEntries);
        bukkitTeamEntries.forEach(bukkitTeam::removeEntry);
    }

    public void addPlayer(Player player) {
        addMember(player.getUniqueId());
    }

    public void addMember(UUID uuid) {
        if (members.add(uuid)) { // Returns true if added (was not already present)
            refreshAll(); // Update all scoreboards
        }
    }

    public void removePlayer(Player player) {
        removeMember(player.getUniqueId());
    }

    public void removeMember(UUID uuid) {
        if (members.remove(uuid)) { // Returns true if removed
            // Also remove their name from the Bukkit teams explicitly
            Player player = Bukkit.getPlayer(uuid); // May be offline
            String entryToRemove = (player != null) ? player.getName() : uuid.toString();
            scoreboardManager.getAllPlayerBukkitScoreboards().forEach(sb -> {
                Team t = sb.getTeam(this.name);
                if (t != null && t.hasEntry(entryToRemove)) {
                    t.removeEntry(entryToRemove);
                }
            });
             // refreshAll(); // Not strictly needed if explicit removal is done, but good for consistency
        }
    }

    /**
     * Unregisters this team from all provided Bukkit scoreboards.
     * Called when the team is disbanded or the scoreboard system is destroyed.
     */
    void destroyOnAllScoreboards(Collection<Scoreboard> scoreboards) {
        for (Scoreboard sb : scoreboards) {
            Team t = sb.getTeam(this.name);
            if (t != null) {
                t.unregister();
            }
        }
        members.clear();
    }

    public boolean isOnTeam(UUID uuid) {
        return members.contains(uuid);
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    // Auto-generated
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScoreboardTeam that = (ScoreboardTeam) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}