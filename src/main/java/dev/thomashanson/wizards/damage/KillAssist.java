package dev.thomashanson.wizards.damage;

import org.bukkit.entity.Player;

public class KillAssist implements Comparable<KillAssist> {

    private final Player attacker;
    private final double damage;
    private final int percentage;

    public KillAssist(Player attacker, double damage, int percentage) {
        this.attacker = attacker;
        this.damage = damage;
        this.percentage = percentage;
    }

    @Override
    public int compareTo(KillAssist assist) {
        return Integer.compare(getPercentage(), assist.getPercentage());
    }

    public Player getAttacker() {
        return attacker;
    }

    private int getPercentage() {
        return percentage;
    }
}