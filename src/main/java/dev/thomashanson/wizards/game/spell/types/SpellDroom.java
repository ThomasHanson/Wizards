package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.game.overtime.types.DisasterEarthquake;
import dev.thomashanson.wizards.game.spell.Spell;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SpellDroom extends Spell {

    private final List<FallingBlock> fallingBlocks = new ArrayList<>();

    @Override
    public void castSpell(final Player player, int level) {

        List<Player> targets = new ArrayList<>();

        // Spawn anvil above player's head
        targets.add(player);

        final int radius = 4 + (level * 2); //(int) getValue(player, "Radius");

        player.getNearbyEntities(radius, radius * 3, radius).forEach(entity -> {
            if (entity instanceof Player && getWizard(player) != null)
                targets.add((Player) entity);
        });

        List<FallingBlock> curFalling = new ArrayList<>();

        for (Player target : targets) {

            target.getWorld().spawnParticle(Particle.SMOKE_LARGE, player.getLocation(), 1, 0, 0, 0, 0, 0);

            Location location = target.getLocation().clone().add(0, 15 + (level * 3), 0);
            int lowered = 0;

            while (lowered++ < 5 && location.getBlock().getType() != Material.AIR)
                location = location.subtract(0, 1, 0);

            if (location.getBlock().getType() == Material.AIR) {

                FallingBlock anvil = target.getWorld().spawnFallingBlock (
                        location.getBlock().getLocation().add(0.5, 0.5, 0.5),
                        Bukkit.createBlockData(Material.ANVIL)
                );

                anvil.setMetadata("Wizard", new FixedMetadataValue(getGame().getPlugin(), player));
                anvil.setMetadata("SL", new FixedMetadataValue(getGame().getPlugin(), level));

                anvil.getWorld().playSound(anvil.getLocation(), Sound.BLOCK_ANVIL_USE, 1.9F, 0F);
                curFalling.add(anvil);
            }
        }

        if (!curFalling.isEmpty())
            fallingBlocks.addAll(curFalling);
    }

    private void createExplosion(Entity entity) {

        if (entity instanceof FallingBlock)
            fallingBlocks.remove(entity);

        int spellLevel = entity.getMetadata("SL").get(0).asInt();

        float explosionPower = 1 + (spellLevel / 2F);

        if (getGame().isOvertime())
            if (getGame().getDisaster() instanceof DisasterEarthquake)
                explosionPower *= 2;

        entity.getWorld().createExplosion(entity.getLocation(), explosionPower);

        /*
        CustomExplosion explosion = new CustomExplosion(Wizards.getArcadeManager().GetDamage(), Wizards.getArcadeManager().GetExplosion(), entity.getLocation(), 1 + (spellLevel / 2F), "Anvil Drop");

        explosion.setPlayer((Player) entity.getMetadata("Wizard").get(0).value(), true);

        explosion.setFallingBlockExplosion(true);

        explosion.setDropItems(false);

        explosion.setMaxDamage(6 + (spellLevel * 4));

        explosion.explode();
         */

        entity.remove();
    }

    @EventHandler
    public void onDrop(ItemSpawnEvent event) {

        Iterator<FallingBlock> iterator = fallingBlocks.iterator();
        FallingBlock fallingBlock = null;

        while (iterator.hasNext()) {

            FallingBlock block = iterator.next();

            if (block.isDead()) {
                fallingBlock = block;
                break;
            }
        }

        if (fallingBlock != null) {
            event.setCancelled(true);
            createExplosion(fallingBlock);
        }
    }

    @EventHandler
    public void onChange(EntityChangeBlockEvent event) {

        if (!(event.getEntity() instanceof FallingBlock))
            return;

        if (!fallingBlocks.contains(event.getEntity()))
            return;

        createExplosion(event.getEntity());
        event.setCancelled(true);
    }
}