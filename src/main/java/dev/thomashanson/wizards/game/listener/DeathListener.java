package dev.thomashanson.wizards.game.listener;

import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.damage.KillAssist;
import dev.thomashanson.wizards.event.CustomDeathEvent;
import dev.thomashanson.wizards.game.manager.DamageManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.ArrayList;
import java.util.List;

public class DeathListener implements Listener {

    private final DamageManager damageManager;

    public DeathListener(DamageManager damageManager) {
        this.damageManager = damageManager;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {

        LivingEntity entity = event.getEntity();

        if ((event instanceof PlayerDeathEvent)) {

            Player player = (Player) entity;

            List<DamageTick> ticks = damageManager.getLoggedTicks(player.getUniqueId());
            List<String> summary = damageManager.getDamageSummary(ticks);

            ((PlayerDeathEvent) event).setDeathMessage(ticks.isEmpty() ? null : ticks.get(ticks.size() - 1).getDeathMessage(player));

            if (summary.size() > 0) {

                player.sendMessage(DamageManager.PUNCTUATION_COLOR + "-----------[ " + DamageManager.ACCENT_COLOR + ChatColor.BOLD + " Death Summary " + DamageManager.PUNCTUATION_COLOR + "]-----------");

                for(String message : summary)
                    player.sendMessage(message);

                player.sendMessage("");

                int more = ticks.size() - 1;
                List<KillAssist> assists = damageManager.getPossibleAssists(ticks);

                if (assists.size() > 0) {

                    String assistText = DamageManager.BASE_COLOR + ", assisted by ";

                    List<String> names = new ArrayList<>();
                    int morePlayers = 0;

                    for (KillAssist assist : assists) {

                        if (names.size() >= 3) {
                            morePlayers++;
                            continue;
                        }

                        names.add(DamageManager.ACCENT_COLOR + assist.getAttacker().getDisplayName() + DamageManager.BASE_COLOR);
                    }

                    assistText += names.toString().replace("[", "").replace("]", "");

                    if (morePlayers > 0)
                        assistText += " + " + morePlayers + " other player" + (morePlayers != 1 ? "s" : "");

                    ((PlayerDeathEvent) event).setDeathMessage(((PlayerDeathEvent) event).getDeathMessage() + assistText);

                } else if (more > 0) {
                    ((PlayerDeathEvent) event).setDeathMessage(((PlayerDeathEvent) event).getDeathMessage() + DamageManager.BASE_COLOR + " (+" + more + " more)");
                }
            }

            CustomDeathEvent deathEvent = new CustomDeathEvent(entity, damageManager.getLastLoggedTick(player.getUniqueId()));
            Bukkit.getServer().getPluginManager().callEvent(deathEvent);
        }

        damageManager.dump(entity.getUniqueId());
    }
}