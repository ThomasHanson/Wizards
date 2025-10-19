package dev.thomashanson.wizards.game.potion.types;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

import dev.thomashanson.wizards.event.CustomDamageEvent;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.potion.Potion;

public class PotionIron extends Potion {

    private final Map<UUID, Float> originalSpeeds = new HashMap<>();

    @Override
    public void onActivate(Wizard wizard) {

        Player player = wizard.getPlayer();

        if (player != null) {
            float originalSpeed = player.getWalkSpeed();
            originalSpeeds.put(wizard.getUniqueId(), originalSpeed);
            float newSpeed = player.getWalkSpeed() / 2F;
            player.setWalkSpeed(newSpeed); 
        }
    }

    @Override
    public void onDeactivate(Wizard wizard) {

        if (!originalSpeeds.containsKey(wizard.getUniqueId()))
            return;

        Player player = wizard.getPlayer();

        if (player != null) {
            float originalSpeed = originalSpeeds.get(wizard.getUniqueId());
            player.setWalkSpeed(originalSpeed);         
        }
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