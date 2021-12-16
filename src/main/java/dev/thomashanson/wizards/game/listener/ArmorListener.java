package dev.thomashanson.wizards.game.listener;

import dev.thomashanson.wizards.event.ArmorEquipEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseArmorEvent;

public class ArmorListener implements Listener {

    @EventHandler
    public void onDispenseArmor(BlockDispenseArmorEvent event) {

        if (!(event.getTargetEntity() instanceof Player))
            return;

        Player player = (Player) event.getTargetEntity();

        ArmorEquipEvent armorEquipEvent = new ArmorEquipEvent(player, ArmorEquipEvent.EquipMethod.DISPENSER, null, event.getItem());
        Bukkit.getServer().getPluginManager().callEvent(armorEquipEvent);

        if (armorEquipEvent.isCancelled())
            event.setCancelled(true);
    }
}