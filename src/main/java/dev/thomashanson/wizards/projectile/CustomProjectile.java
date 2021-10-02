package dev.thomashanson.wizards.projectile;

import dev.thomashanson.wizards.util.npc.NPC;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;

public interface CustomProjectile {
    void onCollide(LivingEntity hitEntity, NPC hitNPC, Block hitBlock, ProjectileData data);
}