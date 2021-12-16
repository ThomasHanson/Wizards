package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.overtime.types.DisasterHail;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.projectile.CustomProjectile;
import dev.thomashanson.wizards.projectile.ProjectileData;
import dev.thomashanson.wizards.util.EntityUtil;
import dev.thomashanson.wizards.util.MathUtil;
import dev.thomashanson.wizards.util.npc.NPC;
import org.apache.commons.lang.Validate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Instant;

public class SpellIceShards extends Spell implements CustomProjectile, Spell.Deflectable, Spell.Cancellable {

    public SpellIceShards() {
        setCancelOnSwap();
    }

    @Override
    public void castSpell(Player player, int level) {

        shoot(player);

        for (int i = 1; i <= (level * (getGame().isOvertime() && getGame().getDisaster() instanceof DisasterHail ? 2 : 1)); i++) {

            int finalI = i;

            new BukkitRunnable() {

                @Override
                public void run() {

                    if (isCancelled()) {
                        cancel();
                        return;
                    }

                    shoot(player);
                    setProgress((float) finalI / level);
                }

            }.runTaskLater(getGame().getPlugin(), i * 5);
        }
    }

    @Override
    public void onCollide(LivingEntity hitEntity, NPC hitNPC, Block hitBlock, ProjectileData data) {

        if (hitEntity != null) {

            if (data.getEntity().hasMetadata("Wizard")) {

                CustomDamageTick damageTick = new CustomDamageTick(
                        4.0,
                        EntityDamageEvent.DamageCause.PROJECTILE,
                        "Ice Shard",
                        Instant.now(),
                        (Player) data.getEntity().getMetadata("Wizard").get(0).value()
                );

                damage(hitEntity, damageTick);

                if (damageTick.getPlayer() != null)
                    if (getWizard(damageTick.getPlayer()) != null)
                        getWizard(damageTick.getPlayer()).addAccuracy(true);

            } else if (isBoulder(hitEntity)) {
                hitEntity.remove();
            }
        }

        Location location = data.getEntity().getLocation();
        Validate.notNull(location.getWorld());

        location.getWorld().playSound(location, Sound.BLOCK_GLASS_BREAK, 1.2F, 1F);

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {

                    Block block = location.clone().add(x, y, z).getBlock();

                    if (block.getType() == Material.FIRE) {

                        block.setType(Material.AIR);
                        // achievement
                    }
                }
            }
        }
    }

    @Override
    public void cancelSpell(Player player) {}

    @Override
    public void deflectSpell(Player player, int level, Vector direction) {

    }

    private void shoot(Player player) {

        if (getWizard(player) == null)
            return;

        ArmorStand stand = EntityUtil.makeProjectile(player.getEyeLocation(), Material.GHAST_TEAR);

        stand.setMetadata("Wizard", new FixedMetadataValue(getGame().getPlugin(), player));
        MathUtil.setVelocity(stand, player.getLocation().getDirection(), 2.0, false, 0, 0.2, 10, false);

        getGame().getPlugin().getProjectileManager().addThrow (

                stand,

                new ProjectileData (

                        getGame(),

                        stand, player,
                        this,
                        true, true
                )
        );

        player.getWorld().playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.2F, 0.8F);
    }
}