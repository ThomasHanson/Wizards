package dev.thomashanson.wizards.game.potion.types;

import dev.thomashanson.wizards.event.CustomDamageEvent;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.potion.Potion;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PotionRegeneration extends Potion {

    private final Map<UUID, BukkitTask> tasks = new HashMap<>();

    @Override
    public void onActivate(Wizard wizard) {

        tasks.put(wizard.getUniqueId(), new BukkitRunnable() {

            @Override
            public void run() {

                Player player = wizard.getPlayer();

                // If the player dies in the middle of potion use
                if (getGame().getWizard(player) == null)
                    cancel();

                AttributeInstance healthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);

                if (healthAttribute != null)
                    player.setHealth(Math.min(player.getHealth() + 2.0, healthAttribute.getBaseValue()));
            }

        }.runTaskTimer(getGame().getPlugin(), 0L, 20L));
    }

    @Override
    public void onDeactivate(Wizard wizard) {

        BukkitTask task = tasks.get(wizard.getUniqueId());

        if (task != null)
            task.cancel();

        tasks.remove(wizard.getUniqueId());
    }

    @Override
    public void cleanup() {

        for (BukkitTask task : tasks.values())
            task.cancel();

        tasks.clear();
    }

    @EventHandler
    public void onRegain(EntityRegainHealthEvent event) {

        // Prevent 1 heart per second AND natural regeneration simultaneously
        if (tasks.containsKey(event.getEntity().getUniqueId()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onDamage(CustomDamageEvent event) {

        LivingEntity victim = event.getVictim();

        if (victim instanceof Player) {

            Player victimPlayer = (Player) victim;

            Wizard wizard = getGame().getWizard(victimPlayer);

            if (wizard != null && wizard.getActivePotion() == getPotion())
                event.setDamage(event.getDamage() * 2);
        }
    }
}
