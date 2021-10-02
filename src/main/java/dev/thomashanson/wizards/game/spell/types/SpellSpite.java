package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.event.CustomDamageEvent;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.SpellType;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

import java.time.Instant;
import java.util.*;

public class SpellSpite extends Spell {

    private final Set<UUID> active = new HashSet<>();
    private final Map<UUID, Instant> spited = new HashMap<>();

    @Override
    public void castSpell(Player player, int level) {
        active.add(player.getUniqueId());
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_DEATH, 1F, 1F);
    }

    @EventHandler
    public void onDamage(CustomDamageEvent event) {

        DamageTick damageTick = event.getDamageTick();

        if (!(damageTick instanceof CustomDamageTick))
            return;

        CustomDamageTick customDamageTick = (CustomDamageTick) damageTick;

        if (!(event.getVictim() instanceof Player))
            return;

        Player player = (Player) event.getVictim();

        if (!active.contains(player.getUniqueId()))
            return;

        Player damager = customDamageTick.getPlayer();
        Wizard damagerWizard = getWizard(damager);

        if (damagerWizard == null)
            return;

        String damageReason = damageTick.getReason();
        SpellType spell = SpellType.getSpell(damageReason);

        if (spell != null) {

            damagerWizard.setDisabledSpell(spell);

            damager.sendMessage("You disabled " + player.getName() + "'s " + spell.getSpellName() + " spell");
            player.sendMessage("Your " + spell.getSpellName() + " spell was disabled by " + damager.getName());
        }
    }

    boolean isActive(Player player) {
        return active.contains(player.getUniqueId());
    }

    public Map<UUID, Instant> getSpited() {
        return spited;
    }
}
