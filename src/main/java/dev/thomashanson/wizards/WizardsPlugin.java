package dev.thomashanson.wizards;

import dev.thomashanson.wizards.game.manager.DamageManager;
import dev.thomashanson.wizards.game.manager.GameManager;
import dev.thomashanson.wizards.game.manager.MapManager;
import dev.thomashanson.wizards.game.manager.ProjectileManager;
import dev.thomashanson.wizards.game.state.types.SetupState;
import dev.thomashanson.wizards.map.LocalGameMap;
import dev.thomashanson.wizards.util.menu.listener.InventoryListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class WizardsPlugin extends JavaPlugin {

    private MapManager mapManager;
    private GameManager gameManager;
    private DamageManager damageManager;
    private ProjectileManager projectileManager;

    private InventoryListener inventoryListener;

    @Override
    public void onEnable() {

        saveDefaultConfig();
        getDataFolder().mkdirs();

        this.mapManager = new MapManager(this);
        this.gameManager = new GameManager(this);
        this.damageManager = new DamageManager(this);
        this.projectileManager = new ProjectileManager(this);
        this.inventoryListener = new InventoryListener(this);

        gameManager.setState(new SetupState());
    }

    @Override
    public void onDisable() {

        for (Player player : Bukkit.getOnlinePlayers())
            player.kickPlayer(ChatColor.RED + "Wizards is being updated. Server is restarting!");

        if (this.mapManager != null) {

            mapManager.handleListeners();

            LocalGameMap activeMap = mapManager.getActiveMap();

            if (activeMap != null && activeMap.isLoaded())
                activeMap.unload();
        }

        if (this.gameManager != null)
            gameManager.handleListeners();

        if (this.damageManager != null)
            damageManager.handleListeners();

        if (this.projectileManager != null)
            projectileManager.stopUpdates();
    }

    public MapManager getMapManager() {
        return mapManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public DamageManager getDamageManager() {
        return damageManager;
    }

    public ProjectileManager getProjectileManager() {
        return projectileManager;
    }

    public InventoryListener getInventoryListener() {
        return inventoryListener;
    }
}