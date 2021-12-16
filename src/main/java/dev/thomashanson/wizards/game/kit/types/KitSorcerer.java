package dev.thomashanson.wizards.game.kit.types;

import dev.thomashanson.wizards.game.kit.WizardsKit;
import dev.thomashanson.wizards.game.spell.WandElement;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

public class KitSorcerer extends WizardsKit {

    public KitSorcerer() {

        super (
                "Sorcerer", ChatColor.YELLOW, Color.YELLOW,

                Arrays.asList (
                        "Along with 3 starting wands, can have 6 wands at the max.",
                        "All players killed have a higher chance to drop a wand."
                ),

                new ItemStack(Material.BLAZE_ROD),
                new ItemStack(WandElement.FIRE.getMaterial())
        );
    }

    @Override
    public void playSpellEffect(Player player, Location location) {

    }

    @Override
    public void playIntro(Player player, Location location, int ticks) {

    }
}