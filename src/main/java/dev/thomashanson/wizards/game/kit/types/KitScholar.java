package dev.thomashanson.wizards.game.kit.types;

import dev.thomashanson.wizards.game.kit.WizardsKit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

public class KitScholar extends WizardsKit {

    public KitScholar() {

        super (
                "Scholar", ChatColor.LIGHT_PURPLE,

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
    public void playIntro(Player player, Location location) {

    }
}