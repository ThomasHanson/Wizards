package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.game.spell.Spell;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class SpellTornado extends Spell {

    private static final int MAX_HEIGHT = 15;
    private static final double MAX_RADIUS = 10;
    private static final int LINES = 4;
    private static final double HEIGHT_INCREASE = 0.375;
    private static final double RADIUS_INCREASE = MAX_RADIUS / MAX_HEIGHT;

    @Override
    public void castSpell(Player player, int level) {

        Location location = player.getLocation();
        player.getWorld().playSound(location, Sound.ENTITY_BAT_TAKEOFF, 1F, 0.7F);

        new BukkitRunnable() {

            int angle = 0;

            @Override
            public void run() {

                for (int i = 0; i < LINES; i++) {

                    for (double y = 0; y < MAX_HEIGHT; y+= HEIGHT_INCREASE) {

                        double radius = y * RADIUS_INCREASE;

                        double x = Math.cos(Math.toRadians((double) (360 / LINES) * i + y * 25 - angle)) * radius;
                        double z = Math.sin(Math.toRadians((double) (360 / LINES) * i + y * 25 - angle)) * radius;

                        Location tornadoLocation = new Location(player.getWorld(), x, y, z);

                        if (tornadoLocation.getBlock().getType().isSolid()) {
                            // destroy
                            return;
                        }

                        player.getWorld().spawnParticle(Particle.CLOUD, location.clone().add(x, y, z), 1, 0, 0, 0, 1);
                    }
                }

                angle++;
            }

        }.runTaskTimer(getGame().getPlugin(), 0L, 1L);
    }
}