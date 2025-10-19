package dev.thomashanson.wizards.game.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import dev.thomashanson.wizards.map.LocalGameMap;

/**
 * Manages which player is editing which GameMap.
 */
public class MapEditingManager {

    private final Map<UUID, LocalGameMap> editingSessions = new HashMap<>();

    public void startEditing(Player player, LocalGameMap gameMap) {
        editingSessions.put(player.getUniqueId(), gameMap);
    }

    @Nullable
    public LocalGameMap getEditingMap(Player player) {
        return editingSessions.get(player.getUniqueId());
    }

    public void finishEditing(Player player) {
        editingSessions.remove(player.getUniqueId());
    }
}