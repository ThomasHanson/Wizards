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

public class KitMage extends WizardsKit {

    public KitMage() {

        super (
                "Mage", ChatColor.DARK_GREEN, Color.GREEN,

                Collections.singletonList (
                        "10-20% cooldown decrease on all spells."
                ),

                new ItemStack(Material.REDSTONE),
                new ItemStack(WandElement.EARTH.getMaterial())
        );
    }

    @Override
    public void playSpellEffect(Player player, Location location) {

    }

    @Override
    public void playIntro(Player player, Location location, int ticks) {

    }
}
