package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
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

public class SpellHyperDash extends Spell implements Tickable {

    private static final Map<UUID, DashInstance> ACTIVE_DASHES = new ConcurrentHashMap<>();

    public SpellHyperDash(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        if (ACTIVE_DASHES.containsKey(player.getUniqueId())) {
            return false;
        }
        ACTIVE_DASHES.put(player.getUniqueId(), new DashInstance(this, player, level));
        return true;
    }

    @Override
    public void tick(long gameTick) {
        if (ACTIVE_DASHES.isEmpty()) return;
        ACTIVE_DASHES.values().removeIf(DashInstance::tick);
    }

    @Override
    public int getTickInterval() {
        return 1; // Requires precise, per-tick movement
    }

    @Override
    public void cleanup() {
        ACTIVE_DASHES.values().forEach(DashInstance::cleanup);
        ACTIVE_DASHES.clear();
    }
    
    // Static accessor for other parts of the plugin if needed (e.g., to prevent other movement)
    public static boolean isPlayerDashing(Player player) {
        return ACTIVE_DASHES.containsKey(player.getUniqueId());
    }

    private static class DashInstance {
        enum Phase { DASHING, RECOVERY }

        final SpellHyperDash parent;
        final Player caster;
        final Vector direction;
        final float initialYaw;
        final float initialPitch;
        
        // Configurable stats
        final double speedPerTick;
        final int dashDurationTicks;
        final double damage;
        final double launchPower;
        
        private Phase phase = Phase.DASHING;
        private int ticksLived = 0;
        private int recoveryTicks = 0;
        private int blocksBroken = 0;
        private final Set<UUID> hitPlayers = new HashSet<>();

        DashInstance(SpellHyperDash parent, Player caster, int level) {
            this.parent = parent;
            this.caster = caster;
            
            Location loc = caster.getLocation();
            this.direction = loc.getDirection().normalize();
            this.initialYaw = loc.getYaw();
            this.initialPitch = loc.getPitch();

            StatContext context = StatContext.of(level);
            double speedBPS = parent.getStat("speed-bps", level);
            double distance = parent.getStat("distance", level);
            this.speedPerTick = speedBPS / 20.0;
            this.dashDurationTicks = (int) ((distance / speedBPS) * 20.0);
            this.damage = parent.getStat("damage", level);
            this.launchPower = parent.getStat("launch-power", level);
        }

        /** @return true if this instance should be removed */
        boolean tick() {
            ticksLived++;
            if (!caster.isOnline() || caster.isDead()) {
                return true;
            }
            
            // Force player to look forward
            Location currentLoc = caster.getLocation();
            currentLoc.setYaw(initialYaw);
            currentLoc.setPitch(initialPitch);
            caster.teleport(currentLoc);

            if (phase == Phase.DASHING) {
                return tickDash();
            } else {
                return tickRecovery();
            }
        }
        
        private boolean tickDash() {
            if (ticksLived > dashDurationTicks || caster.isSneaking()) {
                startRecovery();
                return false; // Don't remove yet, transition to recovery
            }

            caster.setVelocity(direction.clone().multiply(speedPerTick));
            caster.setFallDistance(0);
            
            checkPlayerCollision();
            checkBlockCollision();
            
            caster.getWorld().spawnParticle(Particle.FLAME, caster.getEyeLocation().add(direction), 5, 0.5, 0.5, 0.5, 0);
            return false;
        }

        private void checkPlayerCollision() {
            Collection<Entity> targets = caster.getWorld().getNearbyEntities(
                caster.getBoundingBox().expand(1.0), // The bounding box to search
                entity -> { // The predicate
                    return (entity instanceof LivingEntity)   // Must be a LivingEntity
                        && !entity.equals(caster)           // Cannot be the caster
                        && !hitPlayers.contains(entity.getUniqueId()); // Not already hit
                }
            );

            // This loop is now much cleaner and runs fewer times, as the list is pre-filtered.
            for (Entity entity : targets) {
                LivingEntity target = (LivingEntity) entity; // Safe cast because of our predicate
                
                parent.damage(target, new CustomDamageTick(damage, EntityDamageEvent.DamageCause.ENTITY_ATTACK, parent.getKey(), Instant.now(), caster, null));
                target.setVelocity(new Vector(0, launchPower, 0));
                hitPlayers.add(target.getUniqueId());
            }
        }
        
        private void checkBlockCollision() {
            Block blockInPath = caster.getEyeLocation().add(direction).getBlock();
            if (blockInPath.getType().isSolid() && blockInPath.getType().getHardness() < 50) {
                blockInPath.breakNaturally();
                blocksBroken++;
            }
        }

        private void startRecovery() {
            this.phase = Phase.RECOVERY;
            caster.setVelocity(new Vector(0, 0, 0));
            caster.playSound(caster.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0F, 1.0F);
        }
        
        private boolean tickRecovery() {
            recoveryTicks++;
            double recoveryDuration = parent.getStat("recovery-base-ticks", 0)
                                    + (blocksBroken * parent.getStat("recovery-per-block-ticks", 0));
            
            if (recoveryTicks > recoveryDuration) {
                cleanup();
                return true; // Finished, remove instance
            }

            caster.setVelocity(new Vector(0, 0, 0));
            caster.getWorld().spawnParticle(Particle.SMOKE_NORMAL, caster.getLocation(), 2, 0.5, 1, 0.5, 0);
            return false;
        }

        void cleanup() {
            // Any final effects or state resets
        }
    }
}
