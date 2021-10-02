package dev.thomashanson.wizards.game.kit.types;

import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.event.CustomDamageEvent;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.kit.WizardsKit;
import dev.thomashanson.wizards.game.spell.WandElement;
import dev.thomashanson.wizards.util.menu.ItemBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Objects;

public class KitLich extends WizardsKit {

    private final Wizards game;

    private static final ItemStack SKULL = new ItemBuilder(Material.SKELETON_SKULL)
            .withEnchantment(Enchantment.BINDING_CURSE, 1)
            .get();

    public KitLich(Wizards game) {

        super (
                "Lich", ChatColor.BLACK,

                Arrays.asList (
                        "Gain 10 mana per heart dealt.",
                        "Gets double mana regeneration with souls, but starts with 1.5 mana per second.",
                        "Permanently wears a skeleton skull."
                ),

                new ItemStack(Material.SKELETON_SKULL),
                new ItemStack(WandElement.DARK.getMaterial())
        );

        this.game = game;
    }

    @Override
    public void playSpellEffect(Player player, Location location) {

    }

    @Override
    public void playIntro(Player player, Location location) {
        Objects.requireNonNull(player.getEquipment()).setHelmet(SKULL);
    }

    @EventHandler
    public void onDamage(CustomDamageEvent event) {

        DamageTick damageTick = event.getDamageTick();
        double heartsDmg = damageTick.getDamage() / 2;

        Player damager = null;

        if (damageTick instanceof CustomDamageTick)
            damager = ((CustomDamageTick) damageTick).getPlayer();

        if (damager == null || !(game.getKit(damager) instanceof KitLich))
            return;

        Wizard wizard = game.getWizard(damager);
        float gainedMana = getManaGain(1);

        for (int i = 0; i < heartsDmg; i++)
            wizard.addMana(gainedMana);
    }

    private float getManaGain(int level) {
        return (float) (10 + (1.25 * (level - 1)));
    }
}