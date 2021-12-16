package dev.thomashanson.wizards.game.overtime.types;

import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.manager.DamageManager;
import dev.thomashanson.wizards.game.overtime.Disaster;
import dev.thomashanson.wizards.game.spell.SpellType;
import dev.thomashanson.wizards.util.BlockUtil;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
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
    public void update() {

        for (Player player : getGame().getPlayers(true)) {

            player.playSound(player.getLocation(), Sound.ENTITY_MINECART_RIDING, 0.2F, 0.2F);

            if (((Entity) player).isOnGround()) {

                DamageManager damageManager = getGame().getPlugin().getDamageManager();

                List<DamageTick> damageTicks = damageManager.getLoggedTicks(player.getUniqueId());
                Duration lastEarthquake = null;

                for (DamageTick tick : damageTicks)
                    if (tick.getReason().equals(getName()))
                        lastEarthquake = Duration.between(Instant.now(), tick.getTimestamp());

                CustomDamageTick damageTick = new CustomDamageTick (
                        (2 + 2 * Math.random()),
                        EntityDamageEvent.DamageCause.PROJECTILE,
                        getName(),
                        Instant.now(),
                        null
                );

                if (lastEarthquake != null && lastEarthquake.toSeconds() > 5)
                    damage(player, damageTick);
            }

            for (Block block : BlockUtil.getInRadius(player.getLocation(), 5, false).keySet()) {

                if (Math.random() < 0.98)
                    continue;

                if (!block.getType().isSolid() || !block.getRelative(BlockFace.UP).getType().isSolid())
                    continue;

                player.playEffect(block.getLocation(), Effect.STEP_SOUND, block.getType());

                Block lowestBlock = block;

                // Shuffle down until lowest block can be found
                while (lowestBlock.getRelative(BlockFace.DOWN).getType() != Material.AIR)
                    lowestBlock = lowestBlock.getRelative(BlockFace.DOWN);

                if (lowestBlock.getType() != Material.AIR) {

                    if (Math.random() > 0.75)
                        lowestBlock.getWorld().spawnFallingBlock(lowestBlock.getLocation().add(0.5, 0.5, 0.5), Bukkit.createBlockData(lowestBlock.getType()));

                    lowestBlock.setType(Material.AIR);
                }
            }
        }
    }
}