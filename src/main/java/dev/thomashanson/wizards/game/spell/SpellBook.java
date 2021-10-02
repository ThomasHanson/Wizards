package dev.thomashanson.wizards.game.spell;

import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.util.menu.InventoryMenuBuilder;
import dev.thomashanson.wizards.util.menu.ItemBuilder;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.*;

public class SpellBook {

    private final Wizards game;

    public SpellBook(Wizards game) {
        this.game = game;
    }

    public void showBook(Player player, SpellComparator comparator) {

        InventoryMenuBuilder menuBuilder = new InventoryMenuBuilder(game.getPlugin(), "Spell Menu", 54);

        ItemBuilder itemBuilder;

        Wizard wizard = game.getWizard(player);
        Set<Integer> usedSlots = new HashSet<>();

        for (SpellElement element : SpellElement.values()) {

            itemBuilder = new ItemBuilder(Material.GUNPOWDER)
                    .withCustomModelData(element.getData())
                    .withName(element.getColor() + "" + ChatColor.BOLD + element.getName());
                    //.wrapLoreText(ChatColor.GRAY + "- " + element.getDescription());

            menuBuilder.withItem(element.getSlot(), itemBuilder.get());

            for (int i = element.getFirstSlot(); i <= element.getSecondSlot(); i++)
                usedSlots.add(i);
        }

        List<SpellType> spells;

        for (int i = 0; i < menuBuilder.getInventory().getSize(); i++) {

            SpellType spell = null;

            spells = new ArrayList<>(Arrays.asList(SpellType.values()));

            for (SpellType type : spells) {

                if (type.getSlot() == i) {
                    spell = type;
                    break;
                }
            }

            if (usedSlots.contains(i % 9) && spell != null) {

                int spellLevel = wizard == null ? 1 : wizard.getLevel(spell);
                List<String> spellLore = new ArrayList<>();

                if (spellLevel > 0) {

                    itemBuilder = new ItemBuilder(spell.getIcon())
                            .withAmount(spellLevel)
                            .withCustomModelData(1)
                            .withName(spell.getSpellElement().getColor().toString() + ChatColor.BOLD + spell.getSpellName());

                    spellLore.add(wizard == null ?
                            ChatColor.YELLOW.toString() + ChatColor.BOLD + "Max Level: " + ChatColor.WHITE + spell.getMaxLevel() :
                            ChatColor.YELLOW.toString() + ChatColor.BOLD + "Spell Level: " + ChatColor.WHITE + spellLevel);

                    spellLore.add(ChatColor.YELLOW.toString() + ChatColor.BOLD + "Mana Cost: " + ChatColor.WHITE +
                            (wizard == null ?
                            (int) spell.getMana() + (spell.getManaChange() == 0 ? "" : " (" + (spell.getManaChange() > 0 ? "+" : "") + (int) spell.getManaChange() + " per level)") :
                            (int) wizard.getManaCost(spell)));

                    spellLore.add(ChatColor.YELLOW.toString() + ChatColor.BOLD + "Cooldown: " + ChatColor.WHITE +
                            (wizard == null ?
                            spell.getCooldown() + (spell.getCooldownChange() == 0 ? "" : " (" + (spell.getCooldownChange() > 0 ? "+" : "") + spell.getCooldownChange() + ")") :
                            wizard.getSpellCooldown(spell)) + " seconds");

                    spellLore.add("");

                    for (Map.Entry<String, Object> entry : spell.getStats().entrySet()) {

                        spellLore.add (
                                ChatColor.YELLOW + "" + ChatColor.BOLD + entry.getKey() + ": " + ChatColor.WHITE + entry.getValue().toString()
                                .replaceAll("SL", "Spell Level")
                                .replaceAll("BPS", "Blocks Per Second")
                                .replaceAll("D", "Distance")
                                .replaceAll("\\*", "x")
                        );
                    }

                    if (!spell.getStats().isEmpty())
                        spellLore.add("");

                    for (String description : spell.getDescription())
                        spellLore.add(ChatColor.GRAY + description);

                    if (wizard == null) {
                        menuBuilder.withItem(i, itemBuilder.withLore(spellLore).get());

                    } else {

                        spellLore.add("");
                        spellLore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Left-Click" + ChatColor.WHITE + " Bind to Wand");
                        spellLore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Right-Click" + ChatColor.WHITE + " Quickcast Spell");

                        SpellType finalSpell = spell;

                        menuBuilder.withItem(i, itemBuilder.withLore(spellLore).hideAttributes().get(), (viewer, clickType, itemStack) -> {

                            if (!game.isLive())
                                return;

                            int slot = player.getInventory().getHeldItemSlot();

                            if (slot >= wizard.getWandsOwned())
                                return;

                            if (clickType.isLeftClick()) {

                                wizard.setSpell(slot, finalSpell);

                                TranslatableComponent boundWand = new TranslatableComponent("wizards.boundSpell");
                                boundWand.setColor(ChatColor.GREEN);

                                TextComponent boundSpell = new TextComponent(finalSpell.getSpellName());
                                boundSpell.setColor(ChatColor.GOLD);
                                boundSpell.setBold(true);

                                boundWand.addWith(boundSpell);
                                player.spigot().sendMessage(boundWand);

                                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 10F, 1F);

                            } else {
                                game.castSpell(player, wizard, finalSpell, null, true);
                            }

                            game.updateWandIcon(player, -1, player.getInventory().getHeldItemSlot());
                            game.updateWandTitle(player);

                            player.closeInventory();

                        }, ClickType.LEFT, ClickType.RIGHT);
                    }

                } else {

                    menuBuilder.withItem(i,

                            new ItemBuilder(Material.GRAY_DYE)
                                    .withName(ChatColor.RED.toString() + ChatColor.BOLD + "Unknown Spell")
                                    .withLore("", ChatColor.GRAY + "Loot chests to find spells")
                                    .get(),

                            (clicker, clickType, itemStack) -> {}, ClickType.LEFT);
                }

            } else if (!usedSlots.contains(i % 9)) {

                menuBuilder.withItem(i,

                        new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                                .withName(" ")
                                .get(),

                        (clicker, clickType, itemStack) -> {}, ClickType.LEFT);
            }
        }

        menuBuilder.show(player);
    }
}