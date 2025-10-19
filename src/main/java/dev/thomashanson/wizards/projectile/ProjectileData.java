package dev.thomashanson.wizards.projectile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;

import dev.thomashanson.wizards.game.Wizards;

/**
 * A data container holding all state and configuration for a custom projectile.
 * This object is created using its inner Builder class.
 */
public class ProjectileData {

    private final Wizards game;
    private final Item itemEntity;
    private final LivingEntity thrower;
    private final CustomProjectile callback;

    private final boolean hitPlayer;
    private final boolean hitBlock;
    private final Set<UUID> ignoredEntities;

    private final Particle trailParticle;
    private final Sound impactSound;
    private final float soundVolume;
    private final float soundPitch;

    private final double hitboxExpansion;
    private final int maxTicksLived;
    private int ticksLived = 0;

    private final Map<String, Object> customDataMap;

    private ProjectileData(Builder builder) {
        this.game = builder.game;
        this.itemEntity = builder.itemEntity;
        this.thrower = builder.thrower;
        this.callback = builder.callback;
        this.hitPlayer = builder.hitPlayer;
        this.hitBlock = builder.hitBlock;
        this.ignoredEntities = builder.ignoredEntities;
        this.trailParticle = builder.trailParticle;
        this.impactSound = builder.impactSound;
        this.soundVolume = builder.soundVolume;
        this.soundPitch = builder.soundPitch;
        this.hitboxExpansion = builder.hitboxExpansion;
        this.maxTicksLived = builder.maxTicksLived;
        this.customDataMap = builder.customDataMap;

        // The thrower should always ignore their own projectile by default.
        this.ignoredEntities.add(thrower.getUniqueId());
    }

    // Getters
    public Wizards getGame() { return game; }
    public Item getItemEntity() { return itemEntity; }
    public LivingEntity getThrower() { return thrower; }
    public CustomProjectile getCallback() { return callback; }
    public boolean canHitPlayer() { return hitPlayer; }
    public boolean canHitBlock() { return hitBlock; }
    public Set<UUID> getIgnoredEntities() { return ignoredEntities; }
    public Particle getTrailParticle() { return trailParticle; }
    public Sound getImpactSound() { return impactSound; }
    public float getSoundVolume() { return soundVolume; }
    public float getSoundPitch() { return soundPitch; }
    public double getHitboxExpansion() { return hitboxExpansion; }
    public int getMaxTicksLived() { return maxTicksLived; }
    public int getTicksLived() { return ticksLived; }

    public void incrementTicks() {
        this.ticksLived++;
    }

    public <T> T getCustomData(String key, Class<T> type) {
        Object value = customDataMap.get(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    public static class Builder {
        private final Wizards game;
        private final LivingEntity thrower;
        private final CustomProjectile callback;
        private Item itemEntity;

        private boolean hitPlayer = true;
        private boolean hitBlock = true;
        private final Set<UUID> ignoredEntities = new HashSet<>();

        private Particle trailParticle = null;
        private Sound impactSound = Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK;
        private float soundVolume = 0.6F;
        private float soundPitch = 1.2F;
        private double hitboxExpansion = 0.2;
        private int maxTicksLived = 100;
        private final Map<String, Object> customDataMap = new HashMap<>();

        public Builder(Wizards game, LivingEntity thrower, CustomProjectile callback) {
            this.game = Objects.requireNonNull(game, "Game cannot be null");
            this.thrower = Objects.requireNonNull(thrower, "Thrower cannot be null");
            this.callback = Objects.requireNonNull(callback, "Callback cannot be null");
        }

        public Builder itemEntity(Item itemEntity) {
            this.itemEntity = Objects.requireNonNull(itemEntity, "Item entity cannot be null");
            return this;
        }

        public Builder hitPlayer(boolean hitPlayer) {
            this.hitPlayer = hitPlayer;
            return this;
        }

        public Builder hitBlock(boolean hitBlock) {
            this.hitBlock = hitBlock;
            return this;
        }

        public Builder ignoreEntity(LivingEntity entity) {
            this.ignoredEntities.add(entity.getUniqueId());
            return this;
        }

        public Builder trailParticle(Particle trailParticle) {
            this.trailParticle = trailParticle;
            return this;
        }

        public Builder impactSound(Sound impactSound, float volume, float pitch) {
            this.impactSound = impactSound;
            this.soundVolume = volume;
            this.soundPitch = pitch;
            return this;
        }

        public Builder hitboxExpansion(double hitboxExpansion) {
            this.hitboxExpansion = hitboxExpansion;
            return this;
        }

        public Builder maxTicksLived(int maxTicksLived) {
            this.maxTicksLived = maxTicksLived;
            return this;
        }

        public Builder customData(String key, Object value) {
            this.customDataMap.put(key, value);
            return this;
        }

        public ProjectileData build() {
            Objects.requireNonNull(this.itemEntity, "Item entity must be set before building");
            this.itemEntity.setOwner(this.thrower.getUniqueId());
            this.itemEntity.setThrower(this.thrower.getUniqueId());
            this.itemEntity.setPickupDelay(Integer.MAX_VALUE);

            // Directly call the Paper API method. No reflection needed.
            this.itemEntity.setCanPlayerPickup(false);
            this.itemEntity.setCanMobPickup(false);

            return new ProjectileData(this);
        }
    }
}