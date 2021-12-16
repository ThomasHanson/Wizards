package dev.thomashanson.wizards.projectile;

import dev.thomashanson.wizards.game.Wizards;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.Objects;

public class ProjectileData {

    private final Wizards game;

    private final Entity entity;
    private final LivingEntity thrower;

    private final CustomProjectile callback;

    private final boolean
            hitPlayer,
            hitBlock;

    private final Particle particle;
    private final Sound sound;

    private final float
            volume,
            pitch;

    private final double hitboxExpansion;

    public ProjectileData (

            Wizards game,

            Entity entity,
            LivingEntity thrower,

            CustomProjectile callback,

            boolean hitPlayer,
            boolean hitBlock
    ) {
        this(game, entity, thrower, callback, hitPlayer, hitBlock, null, null, 0);
    }

    public ProjectileData (

            Wizards game,

            Entity entity,
            LivingEntity thrower,

            CustomProjectile callback,

            boolean hitPlayer,
            boolean hitBlock,

            double hitboxExpansion
    ) {
        this(game, entity, thrower, callback, hitPlayer, hitBlock, null, null, hitboxExpansion);
    }

    public ProjectileData (

            Wizards game,

            Entity entity,
            LivingEntity thrower,

            CustomProjectile callback,

            boolean hitPlayer,
            boolean hitBlock,

            Particle particle,
            Sound sound,

            double hitboxExpansion
    ) {
        this(game, entity, thrower, callback, hitPlayer, hitBlock, particle, sound, 1F, 1F, hitboxExpansion);
    }

    public ProjectileData (

            Wizards game,

            Entity entity,
            LivingEntity thrower,

            CustomProjectile callback,

            boolean hitPlayer,
            boolean hitBlock,

            Particle particle,
            Sound sound,

            float volume,
            float pitch
    ) {
        this(game, entity, thrower, callback, hitPlayer, hitBlock, particle, sound, volume, pitch, 0);
    }

    private ProjectileData(

            Wizards game,

            Entity entity,
            LivingEntity thrower,

            CustomProjectile callback,

            boolean hitPlayer,
            boolean hitBlock,

            Particle particle,
            Sound sound,

            float volume,
            float pitch,

            double hitboxExpansion
    ) {

        this.game = game;
        this.entity = entity;
        this.thrower = thrower;
        this.callback = callback;
        this.hitPlayer = hitPlayer;
        this.hitBlock = hitBlock;
        this.particle = particle;
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
        this.hitboxExpansion = hitboxExpansion;
    }

    public void playEffect() {

        if (sound != null)
            thrower.getWorld().playSound(entity.getLocation(), sound, volume, pitch);

        if (particle != null)
            thrower.getWorld().spawnParticle(particle, entity.getLocation(), 1, 0, 0, 0);
    }

    public boolean hasCollided() {

        boolean collision = false;

        if (hitPlayer) {

            /*
             * Check for fake players
            Location location = entity.getLocation();
            NPC npc = game.getNPC(location);

            if (npc != null) {
                callback.onCollide(null, npc, null, this);
                collision = true;
            }
             */

            /*
             * Check for real players
             */
            for (Entity worldEntity : thrower.getWorld().getEntities()) {

                if (!(worldEntity instanceof LivingEntity))
                    continue;

                if (worldEntity.equals(entity) || worldEntity.equals(thrower))
                    continue;

                if (worldEntity instanceof Player)
                    if (((Player) worldEntity).getGameMode() == GameMode.SPECTATOR)
                        continue;

                if (worldEntity.getBoundingBox().overlaps(entity.getBoundingBox().expand(hitboxExpansion))) {
                    callback.onCollide((LivingEntity) worldEntity, null, null, this);
                    collision = true;
                }
            }
        }

        /*
         * Check for block collision (or when it hits ground)
         */
        if (hitBlock) {

            Location location = entity.getLocation();

            RayTraceResult rayTrace = entity.getWorld().rayTraceBlocks(location, location.getDirection(), 0.5, FluidCollisionMode.SOURCE_ONLY);

            collision = entity.isOnGround() || rayTrace != null;

            if (collision)
                callback.onCollide(null, null, entity.isOnGround() ? entity.getLocation().getBlock() : Objects.requireNonNull(rayTrace).getHitBlock(), this);
        }

        if (collision)
            entity.remove();

        return collision;
    }

    public Entity getEntity() {
        return entity;
    }

    public LivingEntity getThrower() {
        return thrower;
    }

    public Wizards getGame() {
        return game;
    }
}