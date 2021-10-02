package dev.thomashanson.wizards.game.kit.types;

import dev.thomashanson.wizards.game.kit.WizardsKit;
import dev.thomashanson.wizards.game.spell.WandElement;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;

public class KitMystic extends WizardsKit {

    public KitMystic() {

        super (
                "Mystic", ChatColor.AQUA,

                Collections.singletonList (
                        "Mana regeneration increased by 10%."
                ),

                new ItemStack(Material.FEATHER),
                new ItemStack(WandElement.LIFE.getMaterial())
        );
    }

    @Override
    public void playSpellEffect(Player player, Location location) {

    }

    @Override
    public void playIntro(Player player, Location location) {

    }
}