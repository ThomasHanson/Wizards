package dev.thomashanson.wizards.map;

import org.bukkit.World;

interface GameMap {

    boolean load();
    void unload();
    boolean isLoaded();

    World getWorld();
}