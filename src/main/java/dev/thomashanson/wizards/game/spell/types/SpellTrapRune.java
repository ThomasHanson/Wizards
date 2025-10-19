package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
            if (ticksLived % 15 == 0) renderRune();
            if (ticksLived >= armingTicks) {
                state = State.ACTIVE;
                location.getWorld().playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 0.5F);
            }
        }

        boolean tickActive() {
            for (Player player : parent.plugin.getServer().getOnlinePlayers()) {
                if (player.getWorld().equals(location.getWorld()) && location.distanceSquared(player.getLocation()) < size * size) {
                    explode();
                    return true;
                }
            }
            return false;
        }

        void explode() {
            location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.5F, 1.2F);
            location.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, location, 1);

            for (LivingEntity target : location.getWorld().getNearbyLivingEntities(location, size)) {
                double distance = target.getLocation().distance(location);
                double falloff = Math.max(0, 1.0 - (distance / size));
                
                parent.damage(target, new CustomDamageTick(damage * falloff, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, parent.getKey(), Instant.now(), owner, null));
                
                Vector direction = target.getLocation().toVector().subtract(location.toVector()).normalize();
                if (direction.lengthSquared() < 0.01) direction.setY(1);
                target.setVelocity(direction.multiply(knockback * falloff));
            }
            cleanup();
        }

        void renderRune() {
            for (Location point : getRuneCorners()) {
                location.getWorld().spawnParticle(Particle.TOWN_AURA, point, 1, 0, 0, 0, 0);
            }
        }

        List<Location> getRuneCorners() {
            List<Location> corners = new ArrayList<>();
            corners.add(location.clone().add(-size, 0, -size));
            corners.add(location.clone().add(size, 0, -size));
            corners.add(location.clone().add(size, 0, size));
            corners.add(location.clone().add(-size, 0, size));
            return corners;
        }

        void cleanup() {
            // Can add a fade-out particle effect here
        }
    }
}