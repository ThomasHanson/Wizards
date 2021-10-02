package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.event.CustomDamageEvent;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.SpellType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class SpellFocus extends Spell {

    private BukkitTask updateTask;
    private final Map<Wizard, Instant> focused = new HashMap<>();

    @Override
    public void castSpell(Player player, int level) {

        if (updateTask == null)
            startUpdates();

        focused.put(getWizard(player), Instant.now());

        player.setMetadata("Hunger", new FixedMetadataValue(getGame().getPlugin(), player.getFoodLevel()));
        player.setFoodLevel(6);
    }

    @Override
    public void cleanup() {

        updateTask.cancel();
        updateTask = null;

        focused.clear();
    }

    private void startUpdates() {

        updateTask = new BukkitRunnable() {

            @Override
            public void run() {

                for (Wizard wizard : focused.keySet()) {

                    Instant start = focused.get(wizard);
                    Duration between = Duration.between(start, Instant.now());

                    if (between.toSeconds() >= 30) {
                        endFocus(wizard);
                        return;
                    }
                }

                // update particles
            }
        }.runTaskTimer(getGame().getPlugin(), 0L, 1L);
    }

    @EventHandler
    public void onDamage(CustomDamageEvent event) {

        if (!(event.getDamageTick() instanceof CustomDamageTick))
            return;

        CustomDamageTick customDamageTick = (CustomDamageTick) event.getDamageTick();

        LivingEntity victim = event.getVictim();
        Player damager = customDamageTick.getPlayer();

        /*
         * Check if the user has hit another player
         * with a spell while Focus is active.
         */
        if (getWizard(damager) != null) {

            Wizard damagerWizard = getWizard(damager);

            if (!focused.containsKey(damagerWizard))
                return;

            double damageMultiplier = getMultiplier(focused.get(damagerWizard));
            customDamageTick.setDamage(customDamageTick.getDamage() * damageMultiplier);
        }

        /*
         * Check if the user has been hit with a spell
         * while Focus is active.
         */
        if (victim instanceof Player) {

            Player victimPlayer = (Player) victim;
            Wizard wizard = getWizard(victimPlayer);

            if (!focused.containsKey(wizard))
                return;

            boolean hitBySpell = false;

            for (SpellType spell : SpellType.values()) {

                if (customDamageTick.getReason().startsWith(spell.getSpellName())) {
                    hitBySpell = true;
                    break;
                }
            }

            if (!hitBySpell)
                return;

            endFocus(wizard);
            victimPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 2));
        }
    }

    /*
     * If player is active, we disable their sprint.
     * In doing so, we cannot let them eat food or update
     * their hunger. So we will disable this event.
     */
    @EventHandler
    public void onChange(FoodLevelChangeEvent event) {

        HumanEntity humanEntity = event.getEntity();
        Player player = (Player) humanEntity;

        Wizard wizard = getWizard(player);

        if (wizard == null || !focused.containsKey(wizard))
            return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {

        Player player = event.getPlayer();
        Wizard wizard = getWizard(player);

        if (wizard == null || !focused.containsKey(wizard))
            return;

        ItemStack item = event.getItem();

        if (item.getType().isEdible())
            event.setCancelled(true);
    }

    private double getMultiplier(Instant from) {
        long betweenSecs = Duration.between(from, Instant.now()).toSeconds();
        return betweenSecs >= 10 ? 2 : betweenSecs >= 5 ? 1.5 : betweenSecs >= 2 ? 1.2 : 0;
    }

    private void endFocus(Wizard wizard) {

        focused.remove(wizard);
        updateTask.cancel();

        Player player = wizard.getPlayer();

        player.setFoodLevel(player.getMetadata("Hunger").get(0).asInt());
        player.removeMetadata("Hunger", getGame().getPlugin());

        player.sendMessage("Your focus ended!");
    }
}