package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.util.MathUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class SpellWizardsCompass extends Spell {

    @Override
    public void castSpell(Player player, int level) {

        Location castLocation = player.getEyeLocation().subtract(0, 1, 0);
        final List<Integer[]> colors = new ArrayList<>();

        for (int x = -1; x <= 1; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++)
                    colors.add(new Integer[] { x, y, z });

        Collections.shuffle(colors);

        for (Player target : player.getWorld().getPlayers()) {

            if (player.equals(target))
                continue;

            if (getWizard(target) == null)
                continue;

            final double playerDistance = Math.min(7, player.getLocation().distance(target.getLocation()));
            Vector trajectory = MathUtil.getTrajectory(player.getLocation(), target.getEyeLocation()).multiply(0.1);

            final Location location = castLocation.clone();
            final Integer[] integers = colors.remove(0);

            new BukkitRunnable() {

                int distance, tick;
                final Map<Location, Integer> locations = new HashMap<>();

                public void run() {

                    tick++;

                    Iterator<Map.Entry<Location, Integer>> iterator = locations.entrySet().iterator();

                    while (iterator.hasNext()) {

                        Map.Entry<Location, Integer> entry = iterator.next();

                        if ((entry.getValue() + tick) % 3 == 0) {

                            player.getWorld().spawnParticle(
                                    Particle.REDSTONE, entry.getKey(), 0,
                                    integers[0], integers[1], integers[2],
                                    new Particle.DustOptions(Color.RED, 1)
                            );
                        }

                        if (entry.getValue() < tick)
                            iterator.remove();
                    }

                    if (distance <= playerDistance * 10) {

                        for (int a = 0; a < 2; a++) {

                            player.getWorld().spawnParticle (
                                    Particle.REDSTONE, location, 0,
                                    integers[0], integers[1], integers[2],
                                    new Particle.DustOptions(Color.RED, 1)
                            );

                            locations.put(location.clone(), tick + 50);

                            location.add(trajectory);
                            distance++;
                        }

                    } else if (locations.isEmpty()) {
                        //hologram.stop();
                        cancel();
                    }
                }
            }.runTaskTimer(getGame().getPlugin(), 0L, 0L);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.5F, 1F);
    }
}