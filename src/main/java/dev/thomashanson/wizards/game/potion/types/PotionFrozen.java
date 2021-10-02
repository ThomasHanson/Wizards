package dev.thomashanson.wizards.game.potion.types;

import dev.thomashanson.wizards.damage.types.PlayerDamageTick;
import dev.thomashanson.wizards.event.CustomDamageEvent;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.potion.Potion;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class PotionFrozen extends Potion {

    @Override
    public void activate(Wizard wizard) {}

    @EventHandler
    public void onDamage(CustomDamageEvent event) {

        if (!(event.getDamageTick() instanceof PlayerDamageTick))
            return;

        Player player = ((PlayerDamageTick) event.getDamageTick()).getPlayer();
        Wizard wizard = getGame().getWizard(player);

        if (wizard == null || wizard.getActivePotion() != getPotion())
            return;

        LivingEntity victim = event.getVictim();

        if (player.getUniqueId().equals(victim.getUniqueId()))
            return;

        if (Math.random() >= 0.25)
            return;

        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 3));
        victim.sendMessage("You were hit by " + player.getName() + "'s Frozen Potion!");
    }
}
