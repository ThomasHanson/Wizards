package dev.thomashanson.wizards.game.kit.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.kit.WizardsKit;
import dev.thomashanson.wizards.game.spell.Spell;

public class KitMystic extends WizardsKit {

    private final Wizards game;

    public KitMystic(Wizards game, Map<String, Object> data) {
        super(data);
        this.game = game;
    }

    @Override
    public void playSpellEffect(Player player, Location location) {

    }

    @Override
    public void playIntro(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        Location playerBaseLoc = player.getLocation().clone(); // Ground level
        World world = playerBaseLoc.getWorld();

        if (world == null) {
            return;
        }

        Particle.DustOptions blueDust = new Particle.DustOptions(org.bukkit.Color.BLUE, 1.5F);
        List<Location> eyeParticles = new ArrayList<>();

        // 1. Blue eye insignia appears on ground (Simplified eye shape)
        // Outer ellipse
        double aOuter = 1.5; // horizontal radius
        double bOuter = 0.8; // vertical radius
        for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 16) {
            double x = aOuter * Math.cos(angle);
            double z = bOuter * Math.sin(angle);
            Location particleLoc = playerBaseLoc.clone().add(x, 0.1, z);
            eyeParticles.add(particleLoc);
            world.spawnParticle(Particle.REDSTONE, particleLoc, 1, blueDust);
        }
        // Inner circle (pupil)
        double pupilRadius = 0.3;
        for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
            double x = pupilRadius * Math.cos(angle);
            double z = pupilRadius * Math.sin(angle);
            Location particleLoc = playerBaseLoc.clone().add(x, 0.1, z);
            eyeParticles.add(particleLoc);
            world.spawnParticle(Particle.REDSTONE, particleLoc, 1, blueDust);
        }
        player.playSound(playerBaseLoc, Sound.BLOCK_BEACON_ACTIVATE, 0.7F, 1.8F);


        // 2. After 2 seconds, particles rise 2 blocks into the air, creating pillars
        new BukkitRunnable() {
            int ticks = 0;
            final int riseDuration = 20; // 1 second to rise
            final double riseHeight = 2.0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= riseDuration) {
                    // 3. After a second (of rising), all particles disappear, revealing the player
                    if (ticks >= riseDuration) { // Ensure this runs once after rising
                        // Clear particles (they fade naturally, but an abrupt sound helps)
                        player.playSound(playerBaseLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1F, 1.5F);
                        player.playSound(playerBaseLoc, Sound.BLOCK_GLASS_BREAK, 0.5F, 1.0F);


                        if (player.isOnline()) {
                            player.setInvisible(false);
                            // Optional: small effect on reveal
                            world.spawnParticle(Particle.ENCHANTMENT_TABLE, player.getLocation().add(0,1,0), 30, 0.5, 0.5, 0.5, 0.1);
                        }
                    }
                    this.cancel();
                    return;
                }

                for (Location basePLoc : eyeParticles) {
                    double currentHeight = (riseHeight / riseDuration) * ticks;
                    world.spawnParticle(Particle.REDSTONE, basePLoc.clone().add(0, currentHeight, 0), 1, blueDust);
                    // Optionally, make the pillar thicker
                    world.spawnParticle(Particle.REDSTONE, basePLoc.clone().add(0, currentHeight + 0.1, 0), 1, blueDust);
                }
                
                if (ticks % 5 == 0) {
                    player.playSound(playerBaseLoc, Sound.BLOCK_CONDUIT_AMBIENT_SHORT, 0.3F, 1.5F + (float)ticks / riseDuration);
                }

                ticks++;
            }
        }.runTaskTimer(game.getPlugin(), 40L, 1L); // Start after 2 seconds (40 ticks)
    }

    @Override
    public List<String> getLevelDescription(int level) {
        // Mana per second = 0.1 + (0.0125 * (level - 1))
        double manaPerSec = 0.1 + (0.0125 * (Math.max(0, level - 1)));
        String formattedMana = String.format("%.1f%%", manaPerSec * 100);

        return Collections.singletonList(
            "<gray>Regen <aqua>" + formattedMana + "</aqua> of current mana/sec."
        );
    }

    @Override
    public float getInitialMaxMana(int kitLevel) {
        return 100;
    }

    @Override
    public int getInitialWands() {
        return 2;
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
    public void applyModifiers(Wizard wizard, int kitLevel) {
        double multiplier = 0.1 + (0.0125 * (kitLevel - 1));
        wizard.setManaRegenMultiplier((float) (1 + multiplier), false);
    }

    @Override
    public void applyInitialSpells(Wizard wizard) { }

    @Override
    public int getModifiedMaxSpellLevel(Spell spell, int currentDefaultMaxLevel) {
        return currentDefaultMaxLevel;
    }
}