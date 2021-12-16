package dev.thomashanson.wizards.util.menu;

import org.bukkit.entity.HumanEntity;

public abstract class MenuBuilder<T> {

    MenuBuilder() {
    }

    /**
     * Shows the Menu to the viewers
     */
    public abstract MenuBuilder show(HumanEntity... viewers);

    /**
     * Refreshes the content of the menu
     */
    public abstract MenuBuilder refreshContent();

    /**
     * Builds the menu
     */
    public abstract T build();

    public abstract void dispose();
}