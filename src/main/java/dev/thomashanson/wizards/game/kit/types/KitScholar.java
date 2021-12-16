package dev.thomashanson.wizards.game.kit.types;

import dev.thomashanson.wizards.game.kit.WizardsKit;
import dev.thomashanson.wizards.util.EntityUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

public class KitScholar extends WizardsKit {

    public KitScholar() {

        super (
                "Scholar", ChatColor.LIGHT_PURPLE, Color.fromRGB(255, 85, 255),

                Arrays.asList (
                        "Starts with Mana Bolt, Heal, Ice Prison, and Wizard Compass.",
                        "It can over-level each spell by 1."
                ),

                new ItemStack(Material.BOOK),
                new ItemStack(Material.BLAZE_ROD)
        );
    }

    @Override
    public void playSpellEffect(Player player, Location location) {}

    @Override
    public void playIntro(Player player, Location location, int ticks) {

        if (ticks % 20 == 3) {

            for (int i = 0; i < 2; i++) {

                FireworkEffect effect = FireworkEffect.builder()
                        .flicker(false)
                        .withColor(i == 0 ? Color.WHITE : Color.YELLOW)
                        .with(FireworkEffect.Type.BALL)
                        .trail(false)
                        .build();

                EntityUtil.launchFirework(player.getLocation(), effect, null, 3);
            }
        }
    }
}