package dev.thomashanson.wizards.game.overtime.types;

import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.overtime.Disaster;
import dev.thomashanson.wizards.game.spell.SpellType;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DisasterManaStorm extends Disaster {

    public DisasterManaStorm(Wizards game) {

        super (
                game,

                "Mana Storm",

                Stream.of (

                        SpellType.MANA_BOLT,
                        SpellType.MANA_BOMB

                ).collect(Collectors.toSet()),

                Arrays.asList (
                        "Message 1",
                        "Message 2"
                )
        );
    }

    @Override
    public void strike() {

        for (Player player : getGame().getActiveMap().getWorld().getPlayers())
            if (player.getGameMode() != GameMode.SPECTATOR)
                player.getWorld().strikeLightning(player.getLocation());
    }

    @Override
    public void update() {

    }
}