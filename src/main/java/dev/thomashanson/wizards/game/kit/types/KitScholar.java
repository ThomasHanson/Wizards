package dev.thomashanson.wizards.game.kit.types;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;

import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.kit.WizardsKit;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.SpellManager;

/**
 * The Scholar Kit.
 * <p>
 * This kit starts with a set of basic spells and has the unique
 * ability to "over-level" these specific spells by one additional level.
 */
public class KitScholar extends WizardsKit {

    private final Wizards game;

    /**
     * Creates a new instance of the Scholar kit.
     *
     * @param game The active {@link Wizards} game instance.
     * @param data The configuration data for this kit from the database.
     */
    public KitScholar(Wizards game, Map<String, Object> data) {
        super(data);
        this.game = game;
    }

    @Override
    public void playSpellEffect(Player player, Location location) {}

    /**
     * Plays the introductory animation for the Scholar kit, featuring
     * a hopping white rabbit that disappears in a puff of smoke and fireworks.
     *
     * @param player The player to play the intro for.
     */
    @Override
    public void playIntro(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        Location playerSpawnLoc = player.getLocation();
        World world = playerSpawnLoc.getWorld();

        if (world == null) {
            return;
        }

        // 1. Pure white bunny hopping at player spawn
        Rabbit bunny = (Rabbit) world.spawnEntity(playerSpawnLoc, EntityType.RABBIT);
        bunny.setRabbitType(Rabbit.Type.WHITE);
        bunny.setAI(false); // Control its movement
        bunny.setSilent(true);
        bunny.setInvulnerable(true);
        
        player.playSound(playerSpawnLoc, Sound.ENTITY_RABBIT_AMBIENT, 0.7F, 1.5F);


        new BukkitRunnable() {
            int ticks = 0;
            final int duration = 60; // 3 seconds
            boolean goingUp = true;
            double hopHeight = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= duration) {
                    bunny.remove();
                    if (player.isOnline()) {
                        // Player appears in a puff of white smoke
                        player.setInvisible(false);
                        world.spawnParticle(Particle.SNOWBALL, player.getLocation().add(0, 1, 0), 30, 0.3, 0.3, 0.3, 0.1);
                        world.spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
                        player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1F, 1.2F);


                        // Two small firework bursts (white and gold)
                        Location fireworkLoc = player.getLocation().add(0, 1.5, 0);
                        spawnFirework(fireworkLoc, org.bukkit.Color.WHITE, org.bukkit.Color.SILVER);
                        game.getPlugin().getServer().getScheduler().runTaskLater(game.getPlugin(), () -> {
                            if(player.isOnline()) spawnFirework(fireworkLoc.clone().add(Math.random()*0.5-0.25, 0, Math.random()*0.5-0.25), org.bukkit.Color.YELLOW, org.bukkit.Color.ORANGE);
                        }, 5L); // Slightly delayed second firework
                         player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1F, 1F);

                    }
                    this.cancel();
                    return;
                }

                // Bunny hopping logic
                if (bunny.isValid()) {
                    if (goingUp) {
                        hopHeight += 0.1;
                        if (hopHeight >= 0.5) {
                            goingUp = false;
                            player.playSound(bunny.getLocation(), Sound.ENTITY_RABBIT_JUMP, 0.5F, 1.8F);
                        }
                    } else {
                        hopHeight -= 0.1;
                        if (hopHeight <= 0) {
                            hopHeight = 0;
                            goingUp = true;
                        }
                    }
                    bunny.teleport(playerSpawnLoc.clone().add(0, hopHeight, 0));
                }
                
                if (ticks % 20 == 0 && bunny.isValid()) {
                     player.playSound(bunny.getLocation(), Sound.ENTITY_RABBIT_AMBIENT, 0.4F, (float) (1.5 + Math.random()*0.4));
                }


                ticks++;
            }
        }.runTaskTimer(game.getPlugin(), 0L, 1L);
    }

    /**
     * Spawns a small, custom firework for the intro animation.
     */
    private void spawnFirework(Location location, org.bukkit.Color primaryColor, org.bukkit.Color fadeColor) {
        if (location.getWorld() == null) return;
        Firework fw = location.getWorld().spawn(location, Firework.class);
        FireworkMeta fwm = fw.getFireworkMeta();
        fwm.addEffect(FireworkEffect.builder()
                .withColor(primaryColor)
                .withFade(fadeColor)
                .with(FireworkEffect.Type.BALL)
                .flicker(true)
                .build());
        fwm.setPower(0);
        fw.setFireworkMeta(fwm);
        game.getPlugin().getServer().getScheduler().runTaskLater(game.getPlugin(), fw::detonate, 1L);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Provides the description for the Scholar's over-leveling perk.
     */
    @Override
    public List<String> getLevelDescription(int level) {
        // Scholar has no upgrades, so it's the same for all levels.
        return Collections.singletonList(
            "<gray>Can overlevel spells by 1."
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

    /**
     * {@inheritDoc}
     * <p>
     * Applies the Scholar's cooldown reduction (note: this appears to be
     * copied from Mage and may be unintentional).
     */
    @Override
    public void applyModifiers(Wizard wizard, int kitLevel) {
        double reduction = 0.1 + (0.025 * (kitLevel- 1));
        wizard.setCooldownMultiplier((float) (1 - reduction), false);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Grants the Scholar their set of starting spells.
     */
    @Override
    public void applyInitialSpells(Wizard wizard) {
        // Get the SpellManager from your main game instance
        SpellManager spellManager = game.getPlugin().getSpellManager();

        // Look up each spell by its key and pass the object to learnSpell
        Spell manaBolt = spellManager.getSpell("MANA_BOLT");
        if (manaBolt != null) {
            wizard.learnSpell(manaBolt);
        }

        Spell fireball = spellManager.getSpell("FIREBALL");
        if (fireball != null) {
            wizard.learnSpell(fireball);
        }

        Spell heal = spellManager.getSpell("HEAL");
        if (heal != null) {
            wizard.learnSpell(heal);
        }

        Spell icePrison = spellManager.getSpell("ICE_PRISON");
        if (icePrison != null) {
            wizard.learnSpell(icePrison);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Allows the Scholar's starting spells to be leveled up one level
     * higher than the default maximum.
     */
    @Override
    public int getModifiedMaxSpellLevel(Spell spell, int currentDefaultMaxLevel) {
        // Switch on the spell's unique key (e.g., "MANA_BOLT")
        return switch (spell.getKey().toUpperCase()) {
            case "MANA_BOLT", "FIREBALL", "HEAL", "ICE_PRISON" -> currentDefaultMaxLevel + 1;
            default -> currentDefaultMaxLevel;
        }; // Scholar bonus
    }
}