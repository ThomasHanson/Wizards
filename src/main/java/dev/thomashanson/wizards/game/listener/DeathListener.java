package dev.thomashanson.wizards.game.listener;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.damage.KillAssist;
import dev.thomashanson.wizards.damage.types.PlayerDamageTick;
import dev.thomashanson.wizards.event.CustomDeathEvent;
import dev.thomashanson.wizards.game.manager.DamageManager;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.thomashanson.wizards.game.manager.PlayerStatsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DeathListener implements Listener {

    private final DamageManager damageManager;

    public DeathListener(DamageManager damageManager) {
        this.damageManager = damageManager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();

        WizardsPlugin plugin = damageManager.getPlugin();
        LanguageManager lang = plugin.getLanguageManager();

        getStatsManager().incrementStat(player, PlayerStatsManager.StatType.DEATHS, 1);
        List<DamageTick> allTicks = damageManager.getLoggedTicks(player.getUniqueId());

        if (allTicks.isEmpty()) {
            player.sendMessage(lang.getTranslated(player, "wizards.death.summary.noDamage"));
            event.deathMessage(null); // Use modern Paper API
            damageManager.dump(player.getUniqueId());
            return;
        }

        DamageTick lastTick = allTicks.get(allTicks.size() - 1);

        if (lastTick instanceof PlayerDamageTick pdt) {
            Player killer = pdt.getPlayer();
            if (killer != null && !killer.getUniqueId().equals(player.getUniqueId())) {
                getStatsManager().incrementStat(killer, PlayerStatsManager.StatType.KILLS, 1);
            }
        }
        
        List<KillAssist> assists = damageManager.getPossibleAssists(player.getUniqueId(), allTicks);
        if (!assists.isEmpty()) {
            for (KillAssist assist : assists) {
                Player assister = assist.getAttacker();
                if (assister != null) {
                    getStatsManager().incrementStat(assister, PlayerStatsManager.StatType.ASSISTS, 1);
                }
            }
        }
        
        Component deathMessage = lastTick.getDeathMessage(player, lang, damageManager);
        deathMessage = appendAssistMessage(player, deathMessage, assists, lang);

        // Modern Paper API to set death message
        event.deathMessage(deathMessage);

        sendDeathSummary(player, allTicks, lang);

        Bukkit.getServer().getPluginManager().callEvent(new CustomDeathEvent(player, lastTick));
        damageManager.dump(player.getUniqueId());
    }
    
    private Component appendAssistMessage(Player viewer, Component deathMessage, List<KillAssist> assists, LanguageManager lang) {
        if (assists.isEmpty()) {
            return deathMessage.append(Component.text("."));
        }
        
        List<Component> assisterNames = assists.stream()
            .map(assist -> Component.text(assist.getAttacker().getName(), NamedTextColor.RED))
            .collect(Collectors.toList());

        JoinConfiguration joinConfig = JoinConfiguration.builder()
            .separator(lang.getTranslated(viewer, "wizards.death.assist.separator"))
            .lastSeparator(lang.getTranslated(viewer, "wizards.death.assist.and"))
            .build();
            
        return deathMessage
            .append(lang.getTranslated(viewer, "wizards.death.assist.header"))
            .append(Component.join(joinConfig, assisterNames))
            .append(Component.text("."));
    }

    private void sendDeathSummary(Player player, List<DamageTick> allTicks, LanguageManager lang) {
        List<DamageTick> displayOrderTicks = new ArrayList<>(allTicks);
        Collections.reverse(displayOrderTicks);
        
        List<Component> summaryComponents = damageManager.getDamageSummary(player, displayOrderTicks);
        
        player.sendMessage(lang.getTranslated(player, "wizards.death.summary.header"));
        summaryComponents.forEach(player::sendMessage);
        player.sendMessage(lang.getTranslated(player, "wizards.death.summary.footer"));
    }

    private PlayerStatsManager getStatsManager() {
        return damageManager.getPlugin().getStatsManager();
    }
}