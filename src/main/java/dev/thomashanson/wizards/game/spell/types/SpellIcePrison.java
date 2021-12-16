package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.overtime.types.DisasterHail;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.SpellType;
import dev.thomashanson.wizards.projectile.CustomProjectile;
import dev.thomashanson.wizards.projectile.ProjectileData;
import dev.thomashanson.wizards.util.BlockUtil;
import dev.thomashanson.wizards.util.EntityUtil;
import dev.thomashanson.wizards.util.MathUtil;
import dev.thomashanson.wizards.util.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SpellIcePrison extends Spell implements CustomProjectile {

    private BukkitTask updateTask;
    private final Map<Block, Instant> iceTracking = new HashMap<>();

    @Override
    public void castSpell(Player player, int level) {

        if (updateTask == null)
            startUpdates();

        ArmorStand stand = EntityUtil.makeProjectile(player.getEyeLocation(), Material.ICE);

        stand.setMetadata("Radius", new FixedMetadataValue(getGame().getPlugin(), 3 + level));
        stand.setMetadata("Wizard", new FixedMetadataValue(getGame().getPlugin(), getWizard(player)));

        MathUtil.setVelocity(stand, player.getLocation().getDirection(), 1.7, false, 0, 0.2, 10, false);

        getGame().getPlugin().getProjectileManager().addThrow (

                stand,

                new ProjectileData (

                        getGame(),

                        stand, player, this,
                        true, true,
                        null,

                        Sound.ENTITY_SILVERFISH_HURT, 2F, 1F
                )
        );

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.2F, 0.8F);
    }

    @Override
    public void cleanup() {
        updateTask.cancel();
        iceTracking.clear();
    }

    @Override
    public void onCollide(LivingEntity hitEntity, NPC hitNPC, Block hitBlock, ProjectileData data) {

        Location location = data.getEntity().getLocation();
        data.getEntity().remove();

        double radius = data.getEntity().getMetadata("Radius").get(0).asDouble();
        Wizard originalWizard = (Wizard) data.getEntity().getMetadata("Wizard").get(0).value();

        int multiplier = 1;

        if (getGame().isOvertime())
            if (getGame().getDisaster() instanceof DisasterHail)
                multiplier = 2;

        Map<Block, Double> blocks = BlockUtil.getInRadius(location.getBlock(), radius, true);

        for (Block block : blocks.keySet()) {

            if (iceTracking.containsKey(block) || !block.getType().isSolid()) {
                block.setType(Material.ICE);
                iceTracking.put(block, Instant.now().plusSeconds(((20 * multiplier) + ThreadLocalRandom.current().nextInt((10 * multiplier)))));
            }
        }

        Collection<Entity> captured = Objects.requireNonNull(location.getWorld()).getNearbyEntities(location, radius, radius, radius);

        captured.removeIf(entity -> !(entity instanceof Player));

        if (originalWizard != null) {
            captured.remove(Bukkit.getEntity(originalWizard.getUniqueId())); // To prevent stat exploiting
            originalWizard.addAccuracy(!captured.isEmpty()); // If other players are captured
        }

        if (captured.isEmpty())
            return;

        for (Entity entity : captured) {

            Player playerInRadius = (Player) entity;
            Wizard wizardInRadius = getWizard(playerInRadius);

            if (wizardInRadius == null)
                continue;

            if (wizardInRadius.getLevel(SpellType.SPITE) == 0)
                continue;

            SpellSpite spiteSpell = (SpellSpite) getGame().getSpells().get(SpellType.SPITE);

            if (!spiteSpell.isActive(playerInRadius))
                continue;

            if (originalWizard == null || !getGame().getWizards().contains(originalWizard))
                continue;

            originalWizard.setDisabledSpell(this.getSpell());
        }
    }

    private void startUpdates() {

        this.updateTask = new BukkitRunnable() {

            @Override
            public void run() {

                Iterator<Map.Entry<Block, Instant>> iterator = iceTracking.entrySet().iterator();

                while (iterator.hasNext()) {

                    Map.Entry<Block, Instant> entry = iterator.next();

                    if (entry.getValue().isBefore(Instant.now())) {

                        iterator.remove();

                        if (entry.getKey().getType() == Material.ICE)
                            entry.getKey().setType(Material.AIR);
                    }
                }
            }

        }.runTaskTimer(getGame().getPlugin(), 0L, 1L);
    }

    @EventHandler
    public void onMelt(BlockFadeEvent event) {

        Block block = event.getBlock();

        if (!iceTracking.containsKey(block))
            return;

        event.setCancelled(true);

        iceTracking.remove(block);
        block.setType(Material.AIR);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {

        Block block = event.getBlock();

        if (!iceTracking.containsKey(block))
            return;

        event.setCancelled(true);

        iceTracking.remove(block);
        block.setType(Material.AIR);
    }
}