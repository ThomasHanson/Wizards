package dev.thomashanson.wizards.game.potion.types;

import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.damage.types.PlayerDamageTick;
import dev.thomashanson.wizards.event.CustomDamageEvent;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.manager.DamageManager;
import dev.thomashanson.wizards.game.potion.Potion;
import dev.thomashanson.wizards.game.spell.SpellElement;
import dev.thomashanson.wizards.game.spell.SpellType;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;

import java.time.Duration;
import java.time.Instant;

public class PotionVolatile extends Potion {

    @Override
    public void activate(Wizard wizard) {

    }

    @Override
    public void deactivate(Wizard wizard) {
        super.deactivate(wizard);
    }

    @EventHandler
    public void onDamage(CustomDamageEvent event) {

        DamageTick damageTick = event.getDamageTick();
        String reason = damageTick.getReason();

        SpellType spell = SpellType.getSpell(reason);

        if (spell == null || spell.getSpellElement() == SpellElement.ATTACK)
            return;

        if (damageTick instanceof PlayerDamageTick) {

            LivingEntity entity = ((PlayerDamageTick) damageTick).getEntity();

            // entity.getWorld().createExplosion(entity.getLocation(), 5F);

            if (entity instanceof Player) {

                if (((Player) entity).getGameMode() == GameMode.SPECTATOR)
                    return;

                Player player = (Player) entity;
                double damage = event.getDamage();
                DamageManager damageManager = getGame().getPlugin().getDamageManager();

                CustomDamageTick explosionTick;
                Duration duration = getGame().getPotionDuration(player, getPotion());

                // Player who drank the potion got hit
                if (duration.toMillis() > 0) {

                    // Create explosion
                    explosionTick = new CustomDamageTick (
                            damage * (1 + (double) (1 / 3)), // 33% additional damage
                            EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
                            "Volatile Explosion",
                            Instant.now(),
                            player
                    );

                    damageManager.damage(player, explosionTick);
                    player.sendMessage("Volatile Potion dealt an additional 33% damage");

                    // TODO: 12/13/21 damage other players nearby within specified radius

                } else {

                    // Another player got hit with an offensive spell
                    explosionTick = new CustomDamageTick (
                            damage * (1 + (double) (1 / 4)), // 33% additional damage
                            EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
                            "Volatile Explosion",
                            Instant.now(),
                            player
                    );

                    damageManager.damage(event.getVictim(), explosionTick);
                    event.getVictim().sendMessage("Volatile Potion dealt an additional 25% damage");
                }
            }
        }
    }
}
