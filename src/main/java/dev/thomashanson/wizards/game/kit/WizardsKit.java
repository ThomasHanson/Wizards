package dev.thomashanson.wizards.game.kit;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public abstract class WizardsKit implements Listener {

    private final String name;
    private final ChatColor color;
    private final List<String> description;
    private final ItemStack menuItem;
    private final ItemStack itemInHand;
    private final int cost;

    private WizardsKit(String name, ChatColor color, List<String> description, ItemStack menuItem, ItemStack itemInHand, int cost) {
        this.name = name;
        this.color = color;
        this.description = description;
        this.menuItem = menuItem;
        this.itemInHand = itemInHand;
        this.cost = cost;
    }

    protected WizardsKit(String name, ChatColor color, List<String> description, ItemStack menuItem, ItemStack itemInHand) {
        this(name, color, description, menuItem, itemInHand, 0);
    }

    public abstract void playSpellEffect(Player player, Location location);

    public void playIntro(Player player, Location location) {

    }

    public String getFormattedName() {
        return color.toString() + ChatColor.BOLD + name;
    }

    public String getName() {
        return name;
    }

    List<String> getDescription() {
        List<String> formattedDesc = new ArrayList<>();
        description.forEach(line -> formattedDesc.add(ChatColor.GRAY + line));
        return formattedDesc;
    }

    public ItemStack getMenuItem() {
        return menuItem;
    }

    public ItemStack getItemInHand() {
        return itemInHand;
    }

    public int getCost() {
        return cost;
    }
}
