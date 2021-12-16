package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.event.EquipmentSendingEvent;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.SpellType;
import dev.thomashanson.wizards.projectile.CustomProjectile;
import dev.thomashanson.wizards.projectile.ProjectileData;
import dev.thomashanson.wizards.util.FakeEquipment;
import dev.thomashanson.wizards.util.MathUtil;
import dev.thomashanson.wizards.util.npc.NPC;
import dev.thomashanson.wizards.util.npc.data.Animation;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

public class SpellGrapplingBeam extends Spell {

    // https://pastebin.com/694Ke5yy old code

    @Override
    public void castSpell(Player player, int level) {

        player.sendMessage(player.getInventory().getItemInMainHand().getType().toString());

        //Fish fishHook = player.getWorld().spawn(player.getEyeLocation(), Fish.class);
        //player.getWorld().spawnEntity(player.getEyeLocation(), EntityType.LIN);
        //fishHook.setVelocity(player.getLocation().getDirection().multiply(2));
    }

    @EventHandler
    public void onSwap(PlayerItemHeldEvent event) {

        Player player = event.getPlayer();
        Wizard wizard = getWizard(player);

        if (wizard == null)
            return;

        /*
         * We swapped to the slot with the Grappling
         * Beam spell. When we move to this slot, our
         * game class will automatically try to update
         * to an Iron Hoe until it hits our corner case.
         *
         * In order to cast a real fishing rod, we need
         * to change this item to look like an Iron Hoe,
         * but really be a fishing rod. When the player
         * casts the spell, they will be holding a Fishing
         * Rod.
         */
        new FakeEquipment(getGame().getPlugin()) {

            @Override
            protected boolean onEquipmentSending(EquipmentSendingEvent equipmentEvent) {

                int newSlot = event.getNewSlot();

                if (newSlot <= wizard.getWandsOwned()) {

                    SpellType spellType = wizard.getSpell(newSlot);

                    if (spellType == getSpell()) {
                        equipmentEvent.setEquipment(new ItemStack(getSpell().getWandElement().getMaterial()));
                        return true;
                    }
                }

                return false;
            }
        };
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {

        if (!(event.getEntity() instanceof FishHook))
            return;

        FishHook hook = (FishHook) event.getEntity();

        ProjectileSource source = hook.getShooter();

        if (!(source instanceof Player))
            return;

        Player player = (Player) source;

        if (player.getGameMode() == GameMode.SPECTATOR)
            return;

        Entity hitEntity = event.getHitEntity();
        Block hitBlock = event.getHitBlock();

        if (hitEntity != null) {
            pullEntity(player, hitEntity);

        } else if (hitBlock != null) {

            if (!player.isSneaking())
                pullTowards(player, hitBlock);
            else
                pullBlock(player, hitBlock);
        }
    }

    /**
     * Pulls an entity towards a player.
     * @param player The player who cast the spell.
     * @param target The entity that the beam caught.
     */
    private void pullEntity(Player player, Entity target) {

    }

    /**
     * Pulls the player towards a specific block.
     * @param player The player who cast the spell.
     * @param block The block that the beam caught.
     */
    private void pullTowards(Player player, Block block) {

        Vector vector = MathUtil.getTrajectory(player.getLocation(), block.getLocation());
        double multiplier = vector.length();

        vector.multiply(0.4 + (multiplier * 2));
        vector.setY(Math.min((vector.getY() + (0.6 * multiplier * 2) + (((LivingEntity) player).isOnGround() ? 0.2 : 0)), 1.2 * multiplier * 2));

        player.setFallDistance(0);
        player.setVelocity(vector);
    }

    /**
     * Pulls a specific block towards a player.
     * @param player The player who cast the spell.
     * @param block The block that the beam caught.
     */
    private void pullBlock(Player player, Block block) {

        BlockData data = block.getBlockData();
        block.setType(Material.AIR);

        FallingBlock fallingBlock = block.getWorld().spawnFallingBlock(block.getLocation(), data);
        fallingBlock.setMetadata("SL", new FixedMetadataValue(getGame().getPlugin(), getSpellLevel(player)));

        Vector vector = MathUtil.getTrajectory(fallingBlock.getLocation(), player.getLocation());
        vector.setY(Math.min(vector.getY(), 1.4));

        getGame().getPlugin().getProjectileManager().addThrow (

                fallingBlock,

                new ProjectileData (

                        getGame(),

                        fallingBlock, player,
                        new ThrowableBlock(getWizard(player)),
                        true, true,

                        null, Sound.ENTITY_SLIME_SQUISH, 1F, 1F
                )
        );

        fallingBlock.setVelocity(vector);
    }

    public static class ThrowableBlock implements CustomProjectile {

        private final Wizard wizard;

        ThrowableBlock(Wizard wizard) {
            this.wizard = wizard;
        }

        @Override
        public void onCollide(LivingEntity hitEntity, NPC hitNPC, Block hitBlock, ProjectileData data) {

            Player player = wizard.getPlayer();
            FallingBlock fallingBlock = (FallingBlock) data.getEntity();

            int spellLevel = fallingBlock.getMetadata("SL").get(0).asInt();

            if (hitEntity != null) {

                wizard.addAccuracy(true);

                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1F, 1.2F);
                player.getWorld().playEffect(player.getLocation(), Effect.STEP_SOUND, Material.OAK_WOOD);

                hitEntity.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, (spellLevel + 1) * 20, 0));
                hitEntity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, (spellLevel + 1) * 20, 0));
            }

            if (hitNPC != null)
                hitNPC.playAnimation(Bukkit.getOnlinePlayers(), Animation.TAKE_DAMAGE);

            if (hitBlock != null)
                hitBlock.setBlockData(fallingBlock.getBlockData());
        }
    }
}