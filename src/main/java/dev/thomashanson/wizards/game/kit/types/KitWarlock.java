package dev.thomashanson.wizards.game.kit.types;

import dev.thomashanson.wizards.game.kit.WizardsKit;
import dev.thomashanson.wizards.game.spell.WandElement;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;

public class KitWarlock extends WizardsKit {

    public KitWarlock() {

        super (

                "Warlock", ChatColor.DARK_PURPLE, Color.fromRGB(170, 0, 170),

                Collections.singletonList (
                        "Increases max mana to 150-575."
                ),

                new ItemStack(Material.GOLDEN_APPLE),
                new ItemStack(WandElement.MANA.getMaterial())
        );
    }

    @Override
    public void playSpellEffect(Player player, Location location) {

    }

    @Override
    public void playIntro(Player player, Location location, int ticks) {

    }
}