package dev.thomashanson.wizards.damage.types;

import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.game.manager.DamageManager;
import dev.thomashanson.wizards.util.EntityUtil;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.time.Instant;

public class VoidDamageTick extends DamageTick {

    private final DamageTick previousTick;

    public VoidDamageTick(double damage, String name, DamageTick previousTick, Instant timestamp) {
        super(damage, EntityDamageEvent.DamageCause.VOID, name, timestamp);
        this.previousTick = previousTick;
    }

    @Override
    public boolean matches(DamageTick tick) {
        return tick instanceof VoidDamageTick;
    }

    @Override
    public String getDeathMessage(Player player) {

        String finalMessage = ChatColor.BOLD + "???";

        if (previousTick != null) {

            if (previousTick instanceof PlayerDamageTick) {

                finalMessage =
                        DamageManager.ACCENT_COLOR + player.getDisplayName() +
                                DamageManager.BASE_COLOR + "was knocked into the void by " +
                                DamageManager.ACCENT_COLOR + ((PlayerDamageTick) previousTick).getPlayer().getDisplayName();

            } else if (previousTick instanceof MonsterDamageTick) {

                MonsterDamageTick monsterDamageTick = (MonsterDamageTick) previousTick;
                Entity entity = monsterDamageTick.getEntity();

                finalMessage =
                        DamageManager.ACCENT_COLOR + player.getDisplayName() +
                                DamageManager.BASE_COLOR + "was knocked into the void by " +
                                DamageManager.ACCENT_COLOR + EntityUtil.getEntityName(entity);
            }

        } else {

            finalMessage =
                    DamageManager.ACCENT_COLOR + player.getDisplayName() +
                            DamageManager.BASE_COLOR + " fell into the void";
        }

        return finalMessage;
    }

    @Override
    public String getSingleLineSummary() {
        return DamageManager.BASE_COLOR + "Fell into the void";
    }
}