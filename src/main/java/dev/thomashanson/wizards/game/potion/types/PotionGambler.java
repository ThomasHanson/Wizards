package dev.thomashanson.wizards.game.potion.types;

import dev.thomashanson.wizards.damage.types.PlayerDamageTick;
import dev.thomashanson.wizards.event.CustomDamageEvent;
import dev.thomashanson.wizards.event.SpellCastEvent;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.potion.Potion;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class PotionGambler extends Potion {

    public enum GamblerResult {

        NO_MANA,
        DOUBLE_MANA,

        NO_DAMAGE,
        DOUBLE_DAMAGE
    }

    private final Map<UUID, GamblerResult> results = new HashMap<>();

    @Override
    public void activate(Wizard wizard) {

        int numResults = GamblerResult.values().length;
        GamblerResult result = GamblerResult.values()[ThreadLocalRandom.current().nextInt(numResults - 1)];

        results.put(wizard.getUniqueId(), result);
    }

    @Override
    public void deactivate(Wizard wizard) {
        results.remove(wizard.getUniqueId());
        super.deactivate(wizard);
    }

    @Override
    public void cleanup() {
        results.clear();
    }

    @EventHandler
    public void onSpellCast(SpellCastEvent event) {

        Player player = event.getPlayer();

        GamblerResult result = results.get(player.getUniqueId());

        if (result == null)
            return;

        event.setManaMultiplier (
                result == GamblerResult.NO_MANA ? 0 : // All spells cast will cost no mana
                        result == GamblerResult.DOUBLE_MANA ? 2 : // All spells cast will cost double mana
                                1 // Neither result was selected - mana costs will be defaulted
        );
    }

    @EventHandler
    public void onDamage(CustomDamageEvent event) {

        if (!(event.getDamageTick() instanceof PlayerDamageTick))
            return;

        Player player = ((PlayerDamageTick) event.getDamageTick()).getPlayer();
        GamblerResult result = results.get(player.getUniqueId());

        if (result == null)
            return;

        event.setDamage (
                result == GamblerResult.NO_DAMAGE ? 0 : // Every spell will deal no damage
                        result == GamblerResult.DOUBLE_DAMAGE ? event.getDamage() * 2 : // Every spell will deal double damage
                                event.getDamage() // Neither result was selected - damages will be defaulted
        );
    }
}