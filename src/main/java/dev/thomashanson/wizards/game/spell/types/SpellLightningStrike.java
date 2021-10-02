package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.game.overtime.types.DisasterLightning;
import dev.thomashanson.wizards.game.spell.Spell;
import org.apache.commons.lang.Validate;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class SpellLightningStrike extends Spell {

    @Override
    public void castSpell(Player player, int level) {

        double currentRange = 0;
        long delay = 25L / (getGame().isOvertime() && getGame().getDisaster() instanceof DisasterLightning ? 2 : 1);

        while (currentRange <= 150) {

            Location newTarget = player.getEyeLocation()
                    .add(new Vector(0, 0.2, 0))
                    .add(player.getLocation().getDirection().multiply(currentRange));

            if (newTarget.getBlock().getType().isSolid() || newTarget.getBlock().getRelative(BlockFace.UP).getType().isSolid())
                break;

            currentRange += 0.02;

            if (currentRange < 2)
                return;

            final Location location = player.getLocation().add(player.getLocation().getDirection().multiply(currentRange).add(new Vector(0, 0.4, 0)));

            while (location.getBlock().getRelative(BlockFace.UP).getType().isSolid())
                location.add(0, 1, 0);

            Validate.notNull(location.getWorld());

            location.getWorld().spawnParticle(Particle.VILLAGER_ANGRY, location.clone().add(0, 1.3, 0), 7, 0.5, 0.3, 0.5, 0);
            location.getWorld().playSound(location, Sound.ENTITY_CAT_HISS, 1F, 1F);

            Bukkit.getScheduler().scheduleSyncDelayedTask(getGame().getPlugin(), () -> {

                LightningStrike lightning = player.getWorld().strikeLightning(location);
                lightning.setMetadata("Wizard", new FixedMetadataValue(getGame().getPlugin(), getWizard(player)));

                Block block = location.getWorld().getHighestBlockAt(location);
                block = block.getRelative(BlockFace.DOWN);

                Set<Block> fire = new HashSet<>(),
                        explosion = new HashSet<>();

                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {

                            if (x == 0 || (Math.abs(x) != Math.abs(z) || ThreadLocalRandom.current().nextInt(3) == 0)) {

                                Block relative = block.getRelative(x, y, z);

                                if ((y == 0 || (x == 0 && z == 0)) && relative.getType() != Material.AIR && relative.getType() != Material.BEDROCK) {

                                    if (y == 0 || ThreadLocalRandom.current().nextBoolean()) {
                                        explosion.add(relative);
                                        fire.add(relative);
                                    }

                                } else if (relative.getType() == Material.AIR) {
                                    fire.add(relative);
                                }
                            }
                        }
                    }
                }

                //Wizards.getArcadeManager().GetExplosion().BlockExplosion(toExplode, b.getLocation(), false);

                for (Block relative : fire)
                    if (ThreadLocalRandom.current().nextBoolean())
                        relative.setType(Material.FIRE);

            }, delay);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {

        if (!(event.getDamager() instanceof LightningStrike))
            return;

        if (!(event.getEntity() instanceof LivingEntity))
            return;

        LightningStrike lightning = (LightningStrike) event.getDamager();
        LivingEntity entity = (LivingEntity) event.getEntity();

        if (!lightning.hasMetadata("Wizard"))
            return;

        event.setCancelled(true); // TODO: 2020-05-12 set custom damage
        entity.setFireTicks(80); // change
    }
}