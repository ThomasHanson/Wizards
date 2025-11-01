package dev.thomashanson.wizards.game.kit.types;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.kit.WizardsKit;
import dev.thomashanson.wizards.game.spell.Spell;

/**
 * The Warlock Kit.
 * <p>
 * This kit focuses on a greatly expanded maximum mana pool.
 */
public class KitWarlock extends WizardsKit {

    private final Wizards game;

    /**
     * Creates a new instance of the Warlock kit.
     *
     * @param game The active {@link Wizards} game instance.
     * @param data The configuration data for this kit from the database.
     */
    public KitWarlock(Wizards game, Map<String, Object> data) {
        super(data);
        this.game = game;
    }

    @Override
    public void playSpellEffect(Player player, Location location) {

    }

    /**
     * Plays the introductory animation for the Warlock kit, featuring
     * a swirling purple and black portal effect.
     *
     * @param player The player to play the intro for.
     */
    @Override
    public void playIntro(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        Location playerSpawnCenter = player.getLocation().clone().add(0, 1.0, 0); // Centered vertically
        World world = playerSpawnCenter.getWorld();

        if (world == null) {
            return;
        }

        Particle.DustOptions purpleDust = new Particle.DustOptions(org.bukkit.Color.PURPLE, 1.2F);
        Particle.DustOptions blackDust = new Particle.DustOptions(org.bukkit.Color.BLACK, 1.2F);

        new BukkitRunnable() {
            int ticks = 0;
            final int formationDuration = 40; // 2 seconds to form
            final int hangDuration = 20; // 1 second to hang
            final int totalDuration = formationDuration + hangDuration;
            final double maxRadius = 1.5;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= totalDuration) {
                    // Disappear abruptly to reveal the player
                    if (player.isOnline()) {
                        player.setInvisible(false);
                        player.playSound(playerSpawnCenter, Sound.ENTITY_ENDERMAN_TELEPORT, 1F, 0.5F);
                        world.spawnParticle(Particle.PORTAL, playerSpawnCenter, 50, 0.5, 0.5, 0.5, 0.5); // Final burst
                    }
                    this.cancel();
                    return;
                }

                double currentRadius;
                if (ticks < formationDuration) {
                    currentRadius = (maxRadius / formationDuration) * ticks;
                } else {
                    currentRadius = maxRadius;
                }

                // Expanding 2D vertical circle
                for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 24) { // Increased particle density
                    // Main purple circle
                    double y = currentRadius * Math.sin(angle);
                    double x = currentRadius * Math.cos(angle); // Assuming portal faces along Z axis, change x to z for X axis
                    
                    // Rotate the circle to be vertical. This spawns it in X-Y plane.
                    // To make it face player or a specific direction, adjust vector math.
                    // For simplicity, let's make it an X-Y plane circle.
                    Location particleLoc = playerSpawnCenter.clone().add(x, y, 0);
                    world.spawnParticle(Particle.REDSTONE, particleLoc, 1, purpleDust);

                    // Black particle outline (slightly offset or larger)
                    double outlineOffset = 0.1;
                    double xOutline = (currentRadius + outlineOffset) * Math.cos(angle);
                    double yOutline = (currentRadius + outlineOffset) * Math.sin(angle);
                    world.spawnParticle(Particle.REDSTONE, playerSpawnCenter.clone().add(xOutline, yOutline, 0),1, blackDust);

                }

                // Purple portal particles sucked into the "portal"
                if (ticks % 2 == 0 && currentRadius > 0.2) { // Only if portal has some size
                    for (int i = 0; i < 3; i++) { // Number of particles being sucked in
                        Vector randomDir = new Vector(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5).normalize().multiply(currentRadius * 1.5);
                        Location startPos = playerSpawnCenter.clone().add(randomDir);
                        // Make particles move towards the center of the circle (playerSpawnCenter)
                        // For simplicity, we'll just spawn portal particles near the edge moving inwards
                        // True "sucking" requires moving existing particles or more complex logic.
                        // Here, we spawn them as if they are moving towards the center.
                        world.spawnParticle(Particle.PORTAL, startPos, 1, 
                                            (playerSpawnCenter.getX() - startPos.getX()) * 0.1,
                                            (playerSpawnCenter.getY() - startPos.getY()) * 0.1,
                                            (playerSpawnCenter.getZ() - startPos.getZ()) * 0.1, // Velocity towards center
                                            0.2); // Speed of particles
                    }
                }
                
                if (ticks == 0) {
                     player.playSound(playerSpawnCenter, Sound.BLOCK_PORTAL_AMBIENT, 0.5F, 0.5F);
                }
                if (ticks % 10 == 0) {
                    player.playSound(playerSpawnCenter, Sound.BLOCK_PORTAL_TRAVEL, 0.3F, (float) (0.5 + currentRadius / maxRadius));
                }


                ticks++;
            }
        }.runTaskTimer(game.getPlugin(), 0L, 1L);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Provides the description for the Warlock's increased max mana at a specific level.
     */
    @Override
    public List<String> getLevelDescription(int level) {
        // Max mana (rounded) = 150 + (6.25 * (level - 1))
        int maxMana = 150 + (int) Math.round(6.25 * (Math.max(0, level - 1)));

        return Collections.singletonList(
            "<gray>Max Mana: <aqua>" + maxMana
        );
    }

    /**
     * {@inheritDoc}
     * <p>
     * Warlock's max mana is set based on its level-scaling formula.
     */
    @Override
    public float getInitialMaxMana(int kitLevel) {
        return (float) (150 + (6.25 * (kitLevel - 1)));
    }

    @Override
    public int getInitialWands() {
        return 3;
    }

    @Override
    public int getInitialMaxWands(int kitLevel) {
        return 5;
    }

    @Override
    public float getBaseManaPerTick(int kitLevel) {
        return 2.5F / 20F;
    }

    @Override
    public void applyModifiers(Wizard wizard, int kitLevel) {}

    @Override
    public void applyInitialSpells(Wizard wizard) { }

    @Override
    public int getModifiedMaxSpellLevel(Spell spell, int currentDefaultMaxLevel) {
        return currentDefaultMaxLevel;
    }
}