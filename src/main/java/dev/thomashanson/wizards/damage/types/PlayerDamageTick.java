package dev.thomashanson.wizards.damage.types;

import dev.thomashanson.wizards.game.manager.DamageManager;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.time.Instant;

public class PlayerDamageTick extends MonsterDamageTick {

    public PlayerDamageTick(double damage, String name, Instant timestamp, Player player) {
        super(damage, name, timestamp, player);
    }

    public PlayerDamageTick(double damage, String name, Instant timestamp, Player player, double distance) {
        super(damage, name, timestamp, player, distance);
    }

    public Player getPlayer() {
        return (Player) getEntity();
    }

    @Override
    public String getDeathMessage(Player player) {
        DecimalFormat decimalFormat = new DecimalFormat("#.#");
        return getDeathMessageTemplate(player).replace("{KILLER}", getPlayer().getDisplayName() + DamageManager.PUNCTUATION_COLOR + "(" + DamageManager.ACCENT_COLOR + decimalFormat.format(getPlayer().getHealth()) + "❤" + DamageManager.PUNCTUATION_COLOR + ")");
    }

    @Override
    public String getSingleLineSummary() {
        DecimalFormat decimalFormat = new DecimalFormat("#.#");
        return getMessageTemplate().replace("{ATTACKER}", getPlayer().getDisplayName() + DamageManager.PUNCTUATION_COLOR + "(" + DamageManager.ACCENT_COLOR + decimalFormat.format(getPlayer().getHealth()) + "❤" + DamageManager.PUNCTUATION_COLOR + ")");
    }
}