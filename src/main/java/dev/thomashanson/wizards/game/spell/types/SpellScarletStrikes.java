package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;

public class SpellScarletStrikes extends Spell implements Tickable {

    private final List<StrikeInstance> activeInstances = new ArrayList<>();

    // Defines the visual formation of the strikes relative to the player.
    private static final Vector[] STRIKE_FORMATION_OFFSETS = {
        new Vector(0, 0.5, 1.8),    // Center
        new Vector(1.0, 0.3, 1.5),  // Right
        new Vector(-1.0, 0.3, 1.5), // Left
        new Vector(1.5, 0.1, 1.0),  // Far Right
        new Vector(-1.5, 0.1, 1.0), // Far Left
        new Vector(0.7, 0.8, 1.2),  // Top Right
        new Vector(-0.7, 0.8, 1.2)  // Top Left
    };

    public SpellScarletStrikes(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        // Find an existing, un-launched instance for this player
        Optional<StrikeInstance> existingInstance = activeInstances.stream()
                .filter(inst -> inst.caster.getUniqueId().equals(player.getUniqueId()) && inst.phase != StrikeInstance.Phase.LAUNCHED)
                .findFirst();

        if (existingInstance.isPresent()) {
            existingInstance.get().launch();
        } else {
            activeInstances.add(new StrikeInstance(this, player, level));
        }
        return true;
    }

    @Override
    public void tick(long gameTick) {
        if (activeInstances.isEmpty()) return;
        activeInstances.removeIf(StrikeInstance::tick);
    }

    @Override
    public void cleanup() {
        activeInstances.forEach(StrikeInstance::cleanup);
        activeInstances.clear();
    }
    
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        activeInstances.stream()
            .filter(inst -> inst.caster.getUniqueId().equals(event.getPlayer().getUniqueId()))
            .findFirst().ifPresent(StrikeInstance::cleanup);
    }

    private static class StrikeInstance {
        enum Phase { SUMMONING, FOLLOWING, LAUNCHED, DONE }

        final SpellScarletStrikes parent;
        final Player caster;
        final int level;
        final List<ScarletProjectile> projectiles = new ArrayList<>();
        
        // Configurable stats
        final int maxStrikes;
        final int summonInterval;
        final int autoLaunchTicks;

        private Phase phase = Phase.SUMMONING;
        private int ticksLived = 0;
        private int summonTickCounter = 0;

        StrikeInstance(SpellScarletStrikes parent, Player caster, int level) {
            this.parent = parent;
            this.caster = caster;
            this.level = level;

            StatContext context = StatContext.of(level);
            this.maxStrikes = (int) parent.getStat("max-strikes", level);
            this.summonInterval = (int) parent.getStat("summon-interval-ticks", level);
            this.autoLaunchTicks = (int) parent.getStat("auto-launch-ticks", level);

            caster.playSound(caster.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.0F, 1.2F);
        }

        boolean tick() {
            ticksLived++;
            if (!caster.isOnline()) {
                cleanup();
                return true;
            }

            switch (phase) {
                case SUMMONING: tickSummoning(); break;
                case FOLLOWING: tickFollowing(); break;
                case LAUNCHED: tickLaunched(); break;
            }
            
            return phase == Phase.DONE;
        }

        void tickSummoning() {
            summonTickCounter++;
            if (summonTickCounter >= summonInterval || projectiles.isEmpty()) {
                if (projectiles.size() < maxStrikes) {
                    spawnStrike();
                    summonTickCounter = 0;
                } else {
                    phase = Phase.FOLLOWING; // All strikes summoned
                }
            }
            updatePositions();
        }

        void tickFollowing() {
            if (ticksLived >= autoLaunchTicks) {
                launch();
                return;
            }
            updatePositions();
        }

        void tickLaunched() {
            projectiles.removeIf(ScarletProjectile::tick);
            if (projectiles.isEmpty()) {
                phase = Phase.DONE;
            }
        }

        void spawnStrike() {
            int index = projectiles.size();
            Vector offset = STRIKE_FORMATION_OFFSETS[index % STRIKE_FORMATION_OFFSETS.length];
            projectiles.add(new ScarletProjectile(this, offset));
            caster.playSound(caster.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 0.7F, 1.5F + (0.1F * index));
        }

        void updatePositions() {
            projectiles.forEach(p -> p.updateFollowingPosition());
        }

        void launch() {
            if (phase == Phase.LAUNCHED) return;
            this.phase = Phase.LAUNCHED;
            
            caster.playSound(caster.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.0F, 1.2F);
            projectiles.forEach(p -> p.launch(caster.getEyeLocation().getDirection()));
        }

        void cleanup() {
            projectiles.forEach(ScarletProjectile::remove);
            projectiles.clear();
            phase = Phase.DONE;
        }
    }

    private static class ScarletProjectile {
        final StrikeInstance parent;
        final ArmorStand armorStand;
        final Vector relativeOffset;

        // Configurable stats
        final double damage;
        final int stunTicks;
        final double hitboxSize;
        final double speedReduction;
        final double baseSpeed;
        final double rangeMultiplier;

        private Vector velocity;
        private Location startLocation;
        private final Set<UUID> hitEntities = new HashSet<>();

        ScarletProjectile(StrikeInstance parent, Vector offset) {
            this.parent = parent;
            this.relativeOffset = offset;
            
            // Load stats from parent spell
            StatContext context = StatContext.of(parent.level);
            this.damage = parent.parent.getStat("damage", 0);
            this.stunTicks = (int) parent.parent.getStat("stun-ticks", 0);
            this.hitboxSize = parent.parent.getStat("hitbox-size", 0);

            // Special stats for dynamic calculations
            this.baseSpeed = parent.parent.getStat("speed-base-bps", 0);
            this.speedReduction = parent.parent.getStat("speed-reduction-per-strike", 0);
            this.rangeMultiplier = parent.parent.getStat("range-multiplier", 0);

            this.armorStand = parent.caster.getWorld().spawn(parent.caster.getEyeLocation(), ArmorStand.class, as -> {
                as.setInvisible(true);
                as.setGravity(false);
                as.setMarker(true);
                as.setSmall(true);
                as.setInvulnerable(true);
            });
            updateFollowingPosition();
        }

        boolean tick() {
            if (!armorStand.isValid()) return true;

            double maxRange = (parent.level + parent.maxStrikes) * rangeMultiplier;
            if (armorStand.getLocation().distanceSquared(startLocation) > maxRange * maxRange) {
                remove();
                return true;
            }

            Location nextPos = armorStand.getLocation().add(velocity);
            if (nextPos.getBlock().getType().isSolid()) {
                remove();
                return true;
            }

            armorStand.teleport(nextPos);
            armorStand.getWorld().spawnParticle(Particle.REDSTONE, armorStand.getLocation(), 2, 0, 0, 0, 0, new Particle.DustOptions(Color.RED, 0.8F));

            for (LivingEntity target : armorStand.getWorld().getNearbyLivingEntities(armorStand.getLocation(), hitboxSize)) {
                if (target.equals(parent.caster) || hitEntities.contains(target.getUniqueId())) continue;

                parent.parent.damage(target, new CustomDamageTick(damage, EntityDamageEvent.DamageCause.MAGIC, parent.parent.getKey(), Instant.now(), parent.caster, null));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, stunTicks, 5, false, false));
                hitEntities.add(target.getUniqueId());

                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0F, 0.8F);
                remove();
                return true;
            }
            return false;
        }

        void updateFollowingPosition() {
            Location playerEyeLoc = parent.caster.getEyeLocation();
            float yaw = (float) Math.toRadians(playerEyeLoc.getYaw());
            Vector rotatedOffset = relativeOffset.clone().rotateAroundY(yaw);
            armorStand.teleport(playerEyeLoc.add(rotatedOffset));
            armorStand.getWorld().spawnParticle(Particle.REDSTONE, armorStand.getLocation().add(0, 0.2, 0), 2, 0.05, 0.05, 0.05, 0, new Particle.DustOptions(Color.RED, 1.0F));
        }

        void launch(Vector direction) {
            this.startLocation = armorStand.getLocation();
            this.armorStand.setInvulnerable(false);

            double strikes = parent.projectiles.size();
            double speedBPS = Math.max(1.0, baseSpeed - (strikes * speedReduction));
            double speedBPT = speedBPS / 20.0;
            this.velocity = direction.clone().normalize().multiply(speedBPT);
        }

        void remove() {
            if (armorStand.isValid()) {
                armorStand.getWorld().spawnParticle(Particle.SMOKE_NORMAL, armorStand.getLocation(), 10, 0.2, 0.2, 0.2, 0.05);
                armorStand.remove();
            }
        }
    }
}