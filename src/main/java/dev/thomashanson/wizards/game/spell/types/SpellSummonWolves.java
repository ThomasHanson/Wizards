package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.MonsterDamageTick;
import dev.thomashanson.wizards.event.CustomDamageEvent;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.mode.GameTeam;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.util.effects.ParticleConfig;
import dev.thomashanson.wizards.util.effects.ParticleUtil;

public class SpellSummonWolves extends Spell implements Tickable {

    private final List<SummonedWolf> activeWolves = new ArrayList<>();

    // Particle config for the summoning circle
    private static final ParticleConfig RITUAL_PARTICLE = new ParticleConfig(
            Particle.SOUL, 1, 0.1, 0, 0, 0, null
    );

    public SpellSummonWolves(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        int wolfCount = (int) getStat("wolf-count", level, 3.0);
        long durationMillis = (long) (getStat("duration", level, 30.0) * 1000L);

        for (int i = 0; i < wolfCount; i++) {
            Location spawnLoc = player.getLocation().add(
                    ThreadLocalRandom.current().nextDouble(-2.5, 2.5), // Widen the spawn area
                    0.5,
                    ThreadLocalRandom.current().nextDouble(-2.5, 2.5)
            );
            
            // Find the ground
            spawnLoc = spawnLoc.getWorld().getHighestBlockAt(spawnLoc).getLocation().add(0, 1.2, 0);

            // Start a new ritual for each wolf
            new SummoningRitual(player, level, spawnLoc, durationMillis).runTaskTimer(plugin, 0L, 1L);
        }
        
        // Play an initial "cast" sound
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1.0F, 1.0F);
        return true;
    }

    private void configureWolf(Wolf wolf, Player owner, int level) {
        wolf.setOwner(owner);
        wolf.setTamed(true);
        wolf.setSitting(false);
        wolf.setBreed(false);

        AttributeInstance healthAttr = Objects.requireNonNull(wolf.getAttribute(Attribute.GENERIC_MAX_HEALTH));
        healthAttr.setBaseValue(getStat("wolf-health", level, 2.0));
        wolf.setHealth(healthAttr.getBaseValue());

        int speedAmplifier = (int) getStat("wolf-speed", level, 1.0) - 1;
        int durationTicks = (int) (getStat("duration", level, 30.0) * 20);
        if (speedAmplifier >= 0) {
            wolf.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, speedAmplifier));
        }
    }

    @Override
    public void tick(long gameTick) {
        activeWolves.removeIf(summon -> {
            if (!summon.wolf.isValid() || Instant.now().isAfter(summon.expiry)) {
                if (summon.wolf.isValid()) summon.wolf.remove();
                return true;
            }
            return false;
        });
    }

    @Override
    public void cleanup() {
        activeWolves.forEach(summon -> {
            if (summon.wolf.isValid()) summon.wolf.remove();
        });
        activeWolves.clear();
    }

    @EventHandler
    public void onDamage(CustomDamageEvent event) {
        if (!(event.getDamageTick() instanceof MonsterDamageTick tick) || !(tick.getEntity() instanceof Wolf wolf)) return;
        
        activeWolves.stream().filter(summon -> summon.wolf.equals(wolf)).findFirst().ifPresent(summon -> {
            tick.addDamageModifier("Summoned Wolf", getStat("damage-modifier", 0, 0.3));
            if (wolf.getOwner() instanceof Player owner) {
                tick.setEntity(owner);
            }
        });
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Wolf wolf) || !(event.getTarget() instanceof Player target)) return;
        if (!(wolf.getOwner() instanceof Player owner)) return;

        getGame().ifPresent(game -> {
            if (game.getRelation(owner, target) == GameTeam.TeamRelation.ALLY) {
                event.setCancelled(true);
            }
        });
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Wolf) {
            activeWolves.removeIf(summon -> summon.wolf.equals(event.getEntity()));
        }
    }

    private record SummonedWolf(Wolf wolf, Instant expiry) {}

    /**
     * Manages the staged summoning effect for a single wolf.
     */
    private class SummoningRitual extends BukkitRunnable {

        private static final int SUMMON_DELAY_TICKS = 15; // 0.75 seconds

        private final Player owner;
        private final int level;
        private final Location spawnLoc;
        private final long durationMillis;
        private int ticksLived = 0;

        SummoningRitual(Player owner, int level, Location spawnLoc, long durationMillis) {
            this.owner = owner;
            this.level = level;
            this.spawnLoc = spawnLoc;
            this.durationMillis = durationMillis;
        }

        @Override
        public void run() {
            ticksLived++;

            if (ticksLived > SUMMON_DELAY_TICKS) {
                cancel();
                spawnWolf();
                return;
            }

            // --- Ritual Visuals ---
            // Create an expanding shockwave circle on the ground
            double radius = (ticksLived / (double) SUMMON_DELAY_TICKS) * 1.5; // 0 to 1.5 block radius
            ParticleUtil.createShockwave(spawnLoc.clone().subtract(0, 0.8, 0), radius, RITUAL_PARTICLE);

            // --- Ritual Audio ---
            if (ticksLived % 4 == 0) {
                spawnLoc.getWorld().playSound(spawnLoc, Sound.BLOCK_PORTAL_AMBIENT, 0.3F, 1.8F);
            }
        }

        private void spawnWolf() {
            if (!owner.isOnline()) return;

            // --- Arrival Sounds ---
            spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0F, 1.0F);
            spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_WOLF_HOWL, 1.2F, 1.0F);
            
            // --- Arrival Visual ---
            spawnLoc.getWorld().spawnParticle(Particle.SOUL, spawnLoc, 30, 0.5, 0.5, 0.5, 0.1);

            // --- Spawn Wolf ---
            Wolf wolf = spawnLoc.getWorld().spawn(spawnLoc, Wolf.class, newWolf -> 
                configureWolf(newWolf, owner, level)
            );

            activeWolves.add(new SummonedWolf(wolf, Instant.now().plusMillis(durationMillis)));
        }
    }
}