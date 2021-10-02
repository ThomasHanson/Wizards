package dev.thomashanson.wizards.game.overtime.types;

import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.overtime.Disaster;
import dev.thomashanson.wizards.game.spell.SpellType;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DisasterEarthquake extends Disaster {

    public DisasterEarthquake(Wizards game) {

        super (
                game,

                "Earthquake",

                Stream.of (

                        SpellType.RUMBLE,
                        SpellType.IMPLODE,
                        SpellType.DROOM

                ).collect(Collectors.toSet()),

                Arrays.asList (
                        "Message 1",
                        "Message 2"
                )
        );
    }

    @Override
    public void strike() {

        for (Player player : getGame().getPlayers(true))
            player.getWorld().strikeLightning(player.getLocation());
    }

    @Override
    public void update() {

    }
}