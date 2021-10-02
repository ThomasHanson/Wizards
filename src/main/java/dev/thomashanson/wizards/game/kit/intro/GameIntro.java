package dev.thomashanson.wizards.game.kit.intro;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface GameIntro {

    void start(Player player, Location location);

    void tick();

    void end(Player player, Location location);
}