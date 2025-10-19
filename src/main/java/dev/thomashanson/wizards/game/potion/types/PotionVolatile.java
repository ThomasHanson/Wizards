package dev.thomashanson.wizards.game.potion.types;

import java.time.Duration;
import java.time.Instant;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;

import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.damage.types.PlayerDamageTick;
import dev.thomashanson.wizards.event.CustomDamageEvent;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.manager.DamageManager;
import dev.thomashanson.wizards.game.potion.Potion;
import dev.thomashanson.wizards.game.spell.SpellElement;
import dev.thomashanson.wizards.game.spell.SpellType;

public class PotionVolatile extends Potion {

    @Override
    public void onActivate(Wizard wizard) {}

    @Override
    public void onDeactivate(Wizard wizard) {}

    @EventHandler
    public void onDamage(CustomDamageEvent event) {
        DamageTick damageTick = event.getDamageTick();
        String reason = damageTick.getReason();

        SpellType spell = SpellType.getSpell(reason);
        if (spell == null || spell.getSpellElement() == SpellElement.ATTACK) {
            return;
        }

        if (damageTick instanceof PlayerDamageTick playerDamageTick) {
            if (playerDamageTick.getEntity() instanceof Player damager) {

                if (damager.getGameMode() == GameMode.SPECTATOR) return;
                if (event.getVictim() instanceof Player && ((Player) event.getVictim()).getGameMode() == GameMode.SPECTATOR) return;

                double damage = event.getDamage();
                DamageManager damageManager = getGame().getPlugin().getDamageManager();
                CustomDamageTick explosionTick;

                Duration victimDuration = getGame().getWizardManager().getPotionDuration((Player) event.getVictim(), getPotion());
                Duration damagerDuration = getGame().getWizardManager().getPotionDuration(damager, getPotion());

                if (victimDuration.toMillis() > 0) {
                    // Victim has Volatile active
                    explosionTick = new CustomDamageTick(
                        damage * (1.0 / 3.0), // 33% additional damage
                        EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, "Volatile Explosion", Instant.now(),
                        (Player) event.getVictim(), null // UPDATED: Added null for distance
                    );
                    damageManager.damage(event.getVictim(), explosionTick);

                } else if (damagerDuration.toMillis() > 0) {
                    // Attacker has Volatile active
                    explosionTick = new CustomDamageTick(
                        damage * 0.25, // 25% additional damage
                        EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, "Volatile Explosion", Instant.now(),
                        damager, null // UPDATED: Added null for distance
                    );
                    damageManager.damage(event.getVictim(), explosionTick);
                }
            }
        }
    }
}
