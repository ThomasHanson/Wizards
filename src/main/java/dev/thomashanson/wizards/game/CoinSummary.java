package dev.thomashanson.wizards.game;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.entity.Player;

import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.thomashanson.wizards.game.manager.PlayerStatsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/**
 * A data-centric class that holds a detailed breakdown of coins earned by a player
 * at the end of a game.
 * <p>
 * This class is created using its {@link Builder} and provides a method to send
 * a formatted, localized summary message to the player.
 *
 * @see PlayerStatsManager#processEndGameRewards(Wizards, List)
 */
public class CoinSummary {

    private final Map<String, Breakdown> coinBreakdown;
    private final int totalCoins;

    /**
     * Private constructor to be used by the {@link Builder}.
     * @param builder The builder instance containing the summary data.
     */
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

    /**
     * Internal data record holding the details for a single line item
     * in the coin summary.
     *
     * @param count The number of units (e.g., 5 kills).
     * @param coins The total coins from this source.
     * @param isFlatBonus True if this is a flat bonus (e.g., "Winner: 150"),
     * false if it's a calculation (e.g., "Kills: 5 x 10").
     */
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

    /**
     * A fluent builder for constructing a {@link CoinSummary} instance.
     */
    public static class Builder {
        private final Map<String, Breakdown> coinBreakdown = new LinkedHashMap<>();

        /**
         * Adds kill-based earnings to the summary.
         *
         * @param count    The number of kills.
         * @param coinsPer The coins awarded per kill.
         * @return This builder instance for chaining.
         */
        public Builder withKills(int count, int coinsPer) {
            if (count > 0) {
                coinBreakdown.put("wizards.coins.source.kills", new Breakdown(count, count * coinsPer, false));
            }
            return this;
        }

        /**
         * Adds assist-based earnings to the summary.
         *
         * @param count    The number of assists.
         * @param coinsPer The coins awarded per assist.
         * @return This builder instance for chaining.
         */
        public Builder withAssists(int count, int coinsPer) {
            if (count > 0) {
                coinBreakdown.put("wizards.coins.source.assists", new Breakdown(count, count * coinsPer, false));
            }
            return this;
        }

        /**
         * Adds time-based earnings to the summary.
         *
         * @param minutes  The number of minutes played.
         * @param coinsPer The coins awarded per minute.
         * @return This builder instance for chaining.
         */
        public Builder withTimePlayed(int minutes, int coinsPer) {
            if (minutes > 0) {
                coinBreakdown.put("wizards.coins.source.timeBonus", new Breakdown(minutes, minutes * coinsPer, false));
            }
            return this;
        }

        /**
         * Adds a flat placement bonus to the summary (e.g., "1st Place").
         *
         * @param titleKey The localization key for the bonus (e.g., "wizards.coins.placement.winner").
         * @param coins    The total flat coins awarded.
         * @return This builder instance for chaining.
         */
        public Builder withPlacementBonus(String titleKey, int coins) {
            if (coins > 0) {
                // Flat bonuses use a count of 1 and are marked as such
                coinBreakdown.put(titleKey, new Breakdown(1, coins, true));
            }
            return this;
        }

        /**
         * Builds the final, immutable {@link CoinSummary} object.
         *
         * @return A new {@link CoinSummary} instance.
         */
        public CoinSummary build() {
            return new CoinSummary(this);
        }
    }
}