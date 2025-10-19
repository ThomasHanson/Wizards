package dev.thomashanson.wizards.game.kit.types;

import java.util.Arrays;
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

public class KitSorcerer extends WizardsKit {

    private final Wizards game;

    public KitSorcerer(Wizards game, Map<String, Object> data) {

        // super(
        //         "Sorcerer",

        //         "Along with 3 starting wands, can have 6 wands at the max. " +
        //                 "All players killed have a higher chance to drop a wand."

        //         // new ItemStack(Material.BLAZE_ROD),
        //         // new ItemStack(WandElement.FIRE.getMaterial())
        // );

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

        Location playerLoc = player.getLocation();
        World world = playerLoc.getWorld();

        if (world == null) {
            return;
        }

        // 1. Lightning warning particles for 1 second
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!player.isOnline() || ticks >= 20) { // 1 second
                    this.cancel();
                    return;
                }
                // Spawn warning particles around the player's spawn location
                for (int i = 0; i < 5; i++) {
                    double offsetX = (Math.random() - 0.5) * 2; // Spread around the player
                    double offsetZ = (Math.random() - 0.5) * 2;
                    world.spawnParticle(Particle.ELECTRIC_SPARK, playerLoc.clone().add(offsetX, Math.random() * 2, offsetZ), 3, 0.1, 0.1, 0.1, 0.01);
                }
                if (ticks % 5 == 0) { // Play sound intermittently
                     player.playSound(playerLoc, Sound.ENTITY_BEE_STING, 0.5F, 1.8F);
                }
                ticks++;
            }
        }.runTaskTimer(game.getPlugin(), 0L, 1L);

        // 2. Two lightning strikes over 2 seconds (after the 1-second warning)
        game.getPlugin().getServer().getScheduler().runTaskLater(game.getPlugin(), () -> {
            if (!player.isOnline()) return;
            world.strikeLightningEffect(playerLoc); // First strike
            player.playSound(playerLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1F, 1F);
        }, 20L); // After 1 second

        game.getPlugin().getServer().getScheduler().runTaskLater(game.getPlugin(), () -> {
            if (!player.isOnline()) return;
            world.strikeLightningEffect(playerLoc.clone().add(Math.random() * 2 - 1, 0, Math.random() * 2 - 1)); // Second strike, slightly offset
            player.playSound(playerLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1F, 1.2F);

            // 3. Player appears in a puff of smoke
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, playerLoc.clone().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.05);
            world.spawnParticle(Particle.EXPLOSION_NORMAL, playerLoc.clone().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.1);
            player.playSound(playerLoc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1F, 1.5F);
            player.playSound(playerLoc, Sound.BLOCK_LAVA_EXTINGUISH, 1F, 0.5F);


            // Make player visible
            if (player.isOnline()) {
                player.setInvisible(false);
            }
        }, 40L); // After 2 seconds (1s warning + 1s for first strike)
    }

    @Override
    public List<String> getLevelDescription(int level) {
        // Chance to drop wand = 0.1 + (0.0125 * (level - 1))
        double chance = 0.1 + (0.0125 * (Math.max(0, level - 1)));
        String formattedChance = String.format("%.1f%%", chance * 100);

        return Arrays.asList(
            "<gray>Starts with 3 wands (Max 6).",
            "<gray>Wand Drop Chance: <aqua>" + formattedChance
        );
    }

    @Override
    public float getInitialMaxMana(int kitLevel) {
        return 100;
    }

    @Override
    public int getInitialWands() {
        return 3;
    }

    @Override
    public int getInitialMaxWands(int kitLevel) {
        return 6;
    }

    @Override
    public float getBaseManaPerTick(int kitLevel) {
        return 2.5F / 20F;
    }

    @Override
    public void applyModifiers(Wizard wizard, int kitLevel) {
        double reduction = 0.1 + (0.025 * (kitLevel- 1));
        wizard.setCooldownMultiplier((float) (1 - reduction), false);
    }

    @Override
    public void applyInitialSpells(Wizard wizard) {
        wizard.setWandsOwned(3);
    }

    @Override
    public int getModifiedMaxSpellLevel(Spell spell, int currentDefaultMaxLevel) {
        return currentDefaultMaxLevel;
    }
}