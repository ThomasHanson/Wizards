package dev.thomashanson.wizards.game.potion.types;

import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.damage.types.PlayerDamageTick;
import dev.thomashanson.wizards.event.CustomDamageEvent;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.potion.Potion;
import dev.thomashanson.wizards.game.spell.SpellElement;
import dev.thomashanson.wizards.game.spell.SpellType;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;

import java.util.Objects;

public class PotionVolatile extends Potion {

    @Override
    public void activate(Wizard wizard) {

    }

    @Override
    public void deactivate(Wizard wizard) {

    }

    @EventHandler
    public void onDamage(CustomDamageEvent event) {

        DamageTick damageTick = event.getDamageTick();
        String reason = damageTick.getReason();

        SpellType spell = SpellType.getSpell(reason);

        if (spell == null)
            return;

        Location explosionLocation = null;

        if (damageTick instanceof PlayerDamageTick)
            explosionLocation = ((PlayerDamageTick) damageTick).getEntity().getLocation();

        if (spell.getSpellElement() == SpellElement.ATTACK) {

            if (explosionLocation != null)
                Objects.requireNonNull(explosionLocation.getWorld()).createExplosion(explosionLocation, 5F);
        }
    }
}
