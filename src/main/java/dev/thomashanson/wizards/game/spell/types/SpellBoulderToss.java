package dev.thomashanson.wizards.game.spell.types;

import com.google.common.collect.Sets;
import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.event.CustomDamageEvent;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.projectile.CustomProjectile;
import dev.thomashanson.wizards.projectile.ProjectileData;
import dev.thomashanson.wizards.util.LocationUtil;
import dev.thomashanson.wizards.util.MathUtil;
import dev.thomashanson.wizards.util.npc.NPC;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Instant;
import java.util.*;

public class SpellBoulderToss extends Spell implements CustomProjectile {

    private BukkitRunnable updateTask;
    private final Map<UUID, Set<ArmorStand>> stands = new HashMap<>();

    @Override
    public void castSpell(Player player, int level) {

        int boulders = level + 1;
        Location center = player.getEyeLocation();

        Set<ArmorStand> playerStands = stands.getOrDefault(player.getUniqueId(), Sets.newHashSet());

        for (int i = 0; i < boulders; i++) {

            ArmorStand stand = player.getWorld().spawn(player.getLocation(), ArmorStand.class, spawnedStand -> {

                spawnedStand.setVisible(false);
                spawnedStand.setSmall(true);
                spawnedStand.setBasePlate(false);
                spawnedStand.setArms(false);
                spawnedStand.setCollidable(false);

                spawnedStand.setMetadata("Wizard", new FixedMetadataValue(getGame().getPlugin(), player));

                Objects.requireNonNull(spawnedStand.getEquipment()).setHelmet(new ItemStack(Material.GRANITE));
            });

            playerStands.add(stand);
            stands.put(player.getUniqueId(), playerStands);
        }

        final float radius = 2F;

        // Number of seconds the boulders will circle the player
        double boulderLength = 3.5;

        // Make 2 rotations (720 degrees) over 3.5 second interval
        final float radiansPerTick = (float) ((720 * (Math.PI / 180)) / boulderLength);

        updateTask = new BukkitRunnable() {

            int tick = 0;

            @Override
            public void run() {

                if (++tick > boulderLength || stands.get(player.getUniqueId()).isEmpty()) {
                    launchBoulders(player);
                    cancel();
                }

                Location location = LocationUtil.getLocationAroundCircle(center, radius, radiansPerTick * tick);

                for (ArmorStand stand : stands.get(player.getUniqueId())) {

                    stand.setVelocity(new Vector(1, 0, 0));
                    stand.teleport(location);

                    stand.getNearbyEntities(0.5, 0.5, 0.5).forEach(nearbyEntity -> {

                        if (!(nearbyEntity instanceof LivingEntity))
                            return;

                        if (nearbyEntity.equals(player))
                            return;

                        CustomDamageTick damageTick = new CustomDamageTick (
                                2.0,
                                EntityDamageEvent.DamageCause.PROJECTILE,
                                "Rotating Boulder",
                                Instant.now(),
                                (Player) stand.getMetadata("Wizard").get(0).value()
                        );

                        damageTick.addKnockback(damageTick.getReason(), 3.0);

                        damage(player, damageTick);
                    });
                }
            }
        };

        updateTask.runTaskTimer(getGame().getPlugin(), 0L, 1L);
    }

    @Override
    public void onCollide(LivingEntity hitEntity, NPC hitNPC, Block hitBlock, ProjectileData data) {

        data.getEntity().remove();

        if (hitEntity != null) {

            if (data.getEntity().hasMetadata("Wizard")) {

                CustomDamageTick damageTick = new CustomDamageTick (
                        2.0,
                        EntityDamageEvent.DamageCause.PROJECTILE,
                        "Boulder",
                        Instant.now(),
                        (Player) data.getEntity().getMetadata("Wizard").get(0).value()
                );

                damage(hitEntity, damageTick);

                if (damageTick.getPlayer() != null)
                    if (getWizard(damageTick.getPlayer()) != null)
                        getWizard(damageTick.getPlayer()).addAccuracy(true);
            }
        }
    }

    @Override
    public void cleanup() {

        if (updateTask != null && !updateTask.isCancelled())
            updateTask.cancel();

        stands.clear();
    }

    @EventHandler
    public void onDamage(CustomDamageEvent event) {

        DamageTick tick = event.getDamageTick();

        if (tick.getReason().equals("Boulder"))
            tick.addKnockback(tick.getReason(), 3.0);
    }

    private void launchBoulders(Player player) {

        Set<ArmorStand> playerStands = stands.get(player.getUniqueId());

        if (playerStands == null || playerStands.isEmpty())
            return;

        for (ArmorStand stand : playerStands) {

            MathUtil.setVelocity(stand, player.getLocation().getDirection(), 2.0, false, 0, 0.2, 10, false);

            getGame().getPlugin().getProjectileManager().addThrow (

                    stand,

                    new ProjectileData (

                            getGame(),

                            stand, player,
                            this,
                            true, true,

                            null, Sound.ENTITY_MINECART_INSIDE, 1.0
                    )
            );
        }
    }
}