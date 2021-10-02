package dev.thomashanson.wizards.game.overtime.types;

import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.overtime.Disaster;
import dev.thomashanson.wizards.game.spell.SpellType;
import dev.thomashanson.wizards.util.BlockUtil;
import dev.thomashanson.wizards.util.EntityUtil;
import org.apache.commons.lang.Validate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DisasterLightning extends Disaster {

    public DisasterLightning(Wizards game) {

        super (
                game,

                "Lightning",

                Stream.of (

                        SpellType.LIGHTNING_STRIKE,
                        SpellType.GUST,
                        SpellType.SPECTRAL_ARROW

                ).collect(Collectors.toSet()),

                Arrays.asList (
                        "Storm rumbles through the sky, birds fly high!",
                        "Lightning strikes the earth, terror given birth!",
                        "Lightning flickering through the air, doom is here!"
                )
        );
    }

    @Override
    public void strike() {

        Location location = getNextLocation();

        Validate.notNull(location);
        Validate.notNull(location.getWorld());

        location.getWorld().spigot().strikeLightningEffect(location, true);

        location.getWorld().playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 5F, 0.8F + ThreadLocalRandom.current().nextFloat());
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 2F, 0.9F + (ThreadLocalRandom.current().nextFloat() / 3));

        //UtilBlock.getExplosionBlocks(loc, 3 * _endgameSize, false);

        List<Block> blocks = new ArrayList<>(BlockUtil.getInRadius(location, 3 * getSize(), false).keySet());
        Collections.shuffle(blocks);

        while (blocks.size() > 20)
            blocks.remove(0).setType(Material.AIR);

        // TODO: 2020-06-02 block explosion

        Map<LivingEntity, Double> inRadius = EntityUtil.getInRadius(location, 4 * getSize());

        double baseDamage = 6 * getSize();

        for (LivingEntity entity : inRadius.keySet()) {

            double damage = baseDamage * inRadius.get(entity);

            if (damage <= 0)
                continue;

            CustomDamageTick damageTick = new CustomDamageTick (
                    damage,
                    EntityDamageEvent.DamageCause.LIGHTNING,
                    "Lightning",
                    Instant.now(),
                    null
            );

            getGame().getPlugin().getDamageManager().damage(entity, damageTick);
        }
    }

    @Override
    public void update() {

    }
}