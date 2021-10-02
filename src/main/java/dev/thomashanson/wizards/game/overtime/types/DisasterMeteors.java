package dev.thomashanson.wizards.game.overtime.types;

import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.overtime.Disaster;
import dev.thomashanson.wizards.game.spell.SpellType;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DisasterMeteors extends Disaster {

    private float speed = 0.05F;

    public DisasterMeteors(Wizards game) {

        super (
                game,

                "Meteors",

                Stream.of (

                        SpellType.FIREBALL,
                        SpellType.NAPALM,
                        SpellType.SPECTRAL_ARROW

                ).collect(Collectors.toSet()),

                Arrays.asList (
                        "Broken is the cage, the skies scream with rage!",
                        "The ground trembles with fear, your doom is here!",
                        "Where the wizards stand, meteors strike the land!"
                )
        );
    }

    private void createMeteor() {

        speed += (getSize() >= 10) ? 0.002 : 0.04;

        Location location = getNextLocation();

        if (location == null)
            return;

        Vector vector = new Vector(
                ThreadLocalRandom.current().nextDouble() - 0.5D,
                0.8,
                ThreadLocalRandom.current().nextDouble() - 0.5D
        ).normalize();

        vector.multiply(40);

        location.add (
                (ThreadLocalRandom.current().nextDouble() - 0.5) * 7,
                0,
                (ThreadLocalRandom.current().nextDouble() - 0.5) * 7
        );

        location.add(vector);

        final Fireball fireball = (Fireball) Objects.requireNonNull(location.getWorld()).spawnEntity(location, EntityType.FIREBALL);

        //fireball.setMetadata("Meteor", new FixedMetadataValue(getGame().getPlugin(), 1.5F * getSize()));

        // TODO: 2020-06-02 play particle

        vector.normalize().multiply(-(0.04 + ((speed - 0.05) / 2)));

        // TODO: 2020-06-02 set vector

        fireball.setBounce(false);
        fireball.setYield(0);
        fireball.setIsIncendiary(true);
        fireball.setFireTicks(Integer.MAX_VALUE);
    }

    @Override
    public void strike() {
        createMeteor();
    }

    @Override
    public void update() {

    }
}