package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;
import dev.thomashanson.wizards.util.ExplosionUtil;
import dev.thomashanson.wizards.util.MathUtil;
import dev.thomashanson.wizards.util.effects.ParticleConfig;
import dev.thomashanson.wizards.util.effects.ParticleUtil;

public class SpellTrapRune extends Spell implements Tickable {

    private final List<TrapRune> activeRunes = new ArrayList<>();

    public SpellTrapRune(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        StatContext context = StatContext.of(level);
        int range = (int) getStat("range", level);
        List<Block> targetBlocks = player.getLastTwoTargetBlocks(null, range);

        // We need two blocks: the one before the target (air) and the target (solid).
        if (targetBlocks.size() < 2 || !targetBlocks.get(1).getType().isSolid()) {
            return false;
        }

        Block targetBlock = targetBlocks.get(1); // The solid block the player is looking at.
        Block adjacentBlock = targetBlocks.get(0); // The air block just in front of the target.

        // Correctly determine the face of the solid block that was hit.
        BlockFace hitFace = targetBlock.getFace(adjacentBlock);

        // The rune should be placed in the air block adjacent to the face that was hit.
        // We add a small 0.1 Y-offset to place it just above the ground.
        Location location = targetBlock.getRelative(hitFace).getLocation().add(0.5, 0.1, 0.5);

        // Enforce rune limit
        int maxRunes = (int) getStat("max-runes", level);
        List<TrapRune> playerRunes = activeRunes.stream()
                .filter(rune -> rune.owner.equals(player))
                .collect(Collectors.toList());

        if (playerRunes.size() >= maxRunes) {
            playerRunes.get(0).cleanup();
            activeRunes.remove(playerRunes.get(0));
        }

        activeRunes.add(new TrapRune(this, player, location, level));
        return true;
    }

    @Override
    public void tick(long gameTick) {
        if (activeRunes.isEmpty()) return;
        activeRunes.removeIf(TrapRune::tick);
    }

    @Override
    public void cleanup() {
        activeRunes.forEach(TrapRune::cleanup);
        activeRunes.clear();
    }

    private static class TrapRune {
        enum State { ARMING, ACTIVE }

        // Particle configuration for the rune visual
        private static final ParticleConfig RUNE_PARTICLE_CONFIG = new ParticleConfig(
                Particle.REDSTONE, 1, 0, 0, 0, 0, new DustOptions(Color.RED, 1.0F)
        );

        // Particle configuration for the shockwave effect
        private static final ParticleConfig SHOCKWAVE_PARTICLE_CONFIG = new ParticleConfig(
                Particle.CRIT, 1, 0.1, 0, 0, 0, null
        );

        final SpellTrapRune parent;
        final Player owner;
        final Location location;
        final int level;

        // Configurable stats
        final double size;
        final int armingTicks;
        final int lifespanTicks;
        final double damage;
        final double knockback;

        private State state = State.ARMING;
        private int ticksLived = 0;

        TrapRune(SpellTrapRune parent, Player owner, Location location, int level) {
            this.parent = parent;
            this.owner = owner;
            this.location = location;
            this.level = level;

            StatContext context = StatContext.of(level);
            this.size = parent.getStat("rune-size", level);
            this.armingTicks = (int) parent.getStat("arming-ticks", level);
            this.lifespanTicks = (int) parent.getStat("lifespan-ticks", level);
            this.damage = parent.getStat("damage", level);
            this.knockback = parent.getStat("knockback-strength", level);
        }

        boolean tick() {
            ticksLived++;
            if (!owner.isOnline() || ticksLived > lifespanTicks) {
                cleanup();
                return true;
            }

            if (state == State.ARMING) {
                tickArming();
            } else {
                return tickActive();
            }
            return false;
        }

        void tickArming() {
            if (ticksLived % 15 == 0) {
                renderRune();
            }
            if (ticksLived >= armingTicks) {
                state = State.ACTIVE;
                location.getWorld().playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 0.5F);
            }
        }

        boolean tickActive() {
            // Check for any player (not just enemies, as this is a minigame)
            for (Player player : parent.plugin.getServer().getOnlinePlayers()) {
                if (player.getWorld().equals(location.getWorld()) && location.distanceSquared(player.getLocation()) < size * size) {
                    // Check to ensure rune doesn't trigger on its owner immediately
                    if (player.equals(owner) && ticksLived < (armingTicks + 10)) {
                        continue; // Give owner a 0.5-second grace period to get away
                    }
                    explode();
                    return true;
                }
            }
            return false;
        }

        void explode() {
            World world = location.getWorld();
            if (world == null) return;

            // --- 1. Sound Effects ---
            // Unchanged, this combo is solid.
            world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.5F, 1.2F);
            world.playSound(location, Sound.BLOCK_STONE_BREAK, 1.0F, 1.0F);

            // --- 2. Block Debris (More Aggressive) ---
            // We'll increase the debris chance and velocity.
            ExplosionUtil.ExplosionConfig debrisConfig = new ExplosionUtil.ExplosionConfig(
                    true,  // regenerateBlocks
                    100,   // regenerationDelayTicks
                    60,    // debrisLifespanTicks (3 seconds)
                    0.85,  // debrisChance (UP from 0.5)
                    1.5,   // velocityStrength (UP from 1.2)
                    0.4,   // velocityYAward (Slightly more Y-pop for blocks)
                    0.8    // itemVelocityModifier
            );
            // We pass 'false' for playSound, as we are handling sounds manually
            ExplosionUtil.createExplosion(parent.plugin, location, (float) this.size, debrisConfig, false);

            // --- 3. Particle Effects ---
            // Unchanged, these are good.
            ParticleUtil.createShockwave(location, size, SHOCKWAVE_PARTICLE_CONFIG);
            world.spawnParticle(Particle.EXPLOSION_LARGE, location, 5, 0.5, 0.5, 0.5, 0.5);
            world.spawnParticle(Particle.LAVA, location, 10, 0.5, 0.5, 0.5, 0.1);


            // --- 4. Damage & Knockback (New Decoupled Logic) ---
            Collection<LivingEntity> targets = world.getNearbyLivingEntities(location, size);
            for (LivingEntity target : targets) {
                double distance = target.getLocation().distance(location);

                // Calculate falloff manually
                double falloff = Math.max(0, 1.0 - (distance / size));

                // Apply damage (with falloff)
                parent.damage(target, new CustomDamageTick(damage * falloff, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, parent.getKey(), Instant.now(), owner, null));

                // --- This is the new knockback logic ---
                
                // 1. Horizontal strength scales with falloff.
                double horizontalStrength = this.knockback * falloff;

                // 2. Vertical strength is consistent. It has a base pop + scales slightly.
                //    This ensures players *always* get "popped up" even at the edge.
                double verticalStrength = (this.knockback * 0.4) + 0.3; 

                // 3. Call our function, but tell it NOT to apply its own falloff.
                Vector knockbackVector = MathUtil.calculateExplosiveKnockbackVector(
                        target,
                        location,
                        horizontalStrength,     // Pass in our manually-scaled horizontal strength
                        verticalStrength,       // Pass in our consistent vertical strength
                        this.size,
                        false                   // IMPORTANT: Disable internal falloff
                );

                target.setVelocity(knockbackVector);
            }
            
            cleanup();
        }

        void renderRune() {
            // Use the new ParticleUtil to draw the square
            ParticleUtil.drawParticleSquare(this.location, this.size, RUNE_PARTICLE_CONFIG);
        }

        void cleanup() {
            // Can add a fade-out particle effect here
        }
    }
}