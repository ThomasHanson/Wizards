package dev.thomashanson.wizards.game.state.listener;

import dev.thomashanson.wizards.WizardsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

public abstract class StateListenerProvider implements Listener {

    public void onEnable(WizardsPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getLogger().info("Registering listener from StateListenerProvider");
    }

    public void onDisable() {
        HandlerList.unregisterAll(this);
    }
}