package dev.thomashanson.wizards.game.potion.types;

import dev.thomashanson.wizards.event.CustomDamageEvent;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.potion.Potion;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PotionIron extends Potion {

    private Map<UUID, Float> originalSpeeds = new HashMap<>();

    @Override
    public void activate(Wizard wizard) {

        Player player = wizard.getPlayer();

        if (player != null) {
            originalSpeeds.put(wizard.getUniqueId(), player.getWalkSpeed());
            player.setWalkSpeed(player.getWalkSpeed() / 2F);
        }
    }

    @Override
    public void deactivate(Wizard wizard) {

        if (!originalSpeeds.containsKey(wizard.getUniqueId()))
            return;

        Player player = wizard.getPlayer();

        if (player != null)
            player.setWalkSpeed(originalSpeeds.get(wizard.getUniqueId()));
    }

    @Override
    public void cleanup() {
        originalSpeeds.clear();
    }

    @EventHandler
    public void onDamage(CustomDamageEvent event) {

        if (originalSpeeds.containsKey(event.getVictim().getUniqueId()))
            event.setDamage(event.getDamage() * (1.0 / 3.0));
    }
}