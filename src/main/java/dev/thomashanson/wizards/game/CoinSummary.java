package dev.thomashanson.wizards.game;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.entity.Player;

import dev.thomashanson.wizards.game.manager.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class CoinSummary {

    private final Map<String, Breakdown> coinBreakdown;
    private final int totalCoins;

    private CoinSummary(Builder builder) {
        this.coinBreakdown = builder.coinBreakdown;
        this.totalCoins = coinBreakdown.values().stream().mapToInt(b -> b.coins).sum();
    }

    public int getTotalCoins() {
        return totalCoins;
    }

    public boolean hasEarnedCoins() {
        return totalCoins > 0;
    }

    /**
     * Dynamically generates and sends the translated summary message.
     * @param player The player to send the message to.
     * @param lang The LanguageManager instance for translation.
     */
    public void sendBreakdownMessage(Player player, LanguageManager lang) {
        if (!hasEarnedCoins()) return;
        
        player.sendMessage(lang.getTranslated(player, "wizards.coins.summary.header"));

        coinBreakdown.forEach((sourceKey, breakdown) -> {
            Component sourceComponent = lang.getTranslated(player, sourceKey);
            
            if (breakdown.isFlatBonus) {
                player.sendMessage(lang.getTranslated(player, "wizards.coins.summary.line.bonus",
                    Placeholder.component("source", sourceComponent),
                    Placeholder.unparsed("coins", String.valueOf(breakdown.coins))
                ));
            } else {
                player.sendMessage(lang.getTranslated(player, "wizards.coins.summary.line.calculation",
                    Placeholder.component("source", sourceComponent),
                    Placeholder.unparsed("count", String.valueOf(breakdown.count)),
                    Placeholder.unparsed("per_unit", String.valueOf(breakdown.coins / breakdown.count)),
                    Placeholder.unparsed("coins", String.valueOf(breakdown.coins))
                ));
            }
        });
        
        player.sendMessage(Component.text(" "));
        player.sendMessage(lang.getTranslated(player, "wizards.coins.summary.total",
            Placeholder.unparsed("total", String.valueOf(totalCoins))
        ));
        player.sendMessage(lang.getTranslated(player, "wizards.coins.summary.footer"));
    }

    private static class Breakdown {
        final int count;
        final int coins;
        final boolean isFlatBonus; // To distinguish between "Winner: 150" and "Kills: 5 x 10"
        Breakdown(int count, int coins, boolean isFlatBonus) {
            this.count = count;
            this.coins = coins;
            this.isFlatBonus = isFlatBonus;
        }
    }

    public static class Builder {
        private final Map<String, Breakdown> coinBreakdown = new LinkedHashMap<>();

        public Builder withKills(int count, int coinsPer) {
            if (count > 0) {
                coinBreakdown.put("wizards.coins.source.kills", new Breakdown(count, count * coinsPer, false));
            }
            return this;
        }

        public Builder withAssists(int count, int coinsPer) {
            if (count > 0) {
                coinBreakdown.put("wizards.coins.source.assists", new Breakdown(count, count * coinsPer, false));
            }
            return this;
        }

        public Builder withTimePlayed(int minutes, int coinsPer) {
            if (minutes > 0) {
                coinBreakdown.put("wizards.coins.source.timeBonus", new Breakdown(minutes, minutes * coinsPer, false));
            }
            return this;
        }

        public Builder withPlacementBonus(String titleKey, int coins) {
            if (coins > 0) {
                // Flat bonuses use a count of 1 and are marked as such
                coinBreakdown.put(titleKey, new Breakdown(1, coins, true));
            }
            return this;
        }

        public CoinSummary build() {
            return new CoinSummary(this);
        }
    }
}