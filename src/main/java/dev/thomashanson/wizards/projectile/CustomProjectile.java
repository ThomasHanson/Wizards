package dev.thomashanson.wizards.projectile;

import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * A functional interface that serves as a callback for when a CustomProjectile
 * collides with an object or expires.
 */
@FunctionalInterface
public interface CustomProjectile {

    /**
     * Called when the projectile collides with an entity or a block, or expires.
     *
     * @param hitEntity The entity that was hit, or null.
     * @param hitBlock  The block that was hit, or null.
     * @param data      The projectile's data object for context.
     */
    void onCollide(@Nullable LivingEntity hitEntity, @Nullable Block hitBlock, ProjectileData data);
}