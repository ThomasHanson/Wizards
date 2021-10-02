package dev.thomashanson.wizards.game.spell.types;

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

    private List<FallingBlock> fallingBlocks = new ArrayList<>();

    @Override
    public void castSpell(final Player player, int level) {

        final int radius = 4 + (level * 2); //(int) getValue(player, "Radius");

        player.getNearbyEntities(radius, radius * 3, radius).forEach(entity -> {

            if (!(entity instanceof Player))
                return;

            Player target = (Player) entity;

            player.getWorld().spawnParticle(Particle.SMOKE_LARGE, player.getLocation(), 1, 0, 0, 0, 0, 0);

            Location location = target.getLocation().clone().add(0, 15 + (level * 3), 0);
            int lowered = 0;

            while (lowered < 5 && location.getBlock().getType() != Material.AIR) {
                lowered++;
                location = location.subtract(0, 1, 0);
            }

            if (location.getBlock().getType() == Material.AIR) {

                FallingBlock anvil = target.getWorld().spawnFallingBlock (
                        location.getBlock().getLocation().add(0.5, 0.5, 0.5),
                        Bukkit.createBlockData(Material.ANVIL)
                );

                anvil.setMetadata("Wizard", new FixedMetadataValue(getGame().getPlugin(), player));
                anvil.setMetadata("SL", new FixedMetadataValue(getGame().getPlugin(), level));

                anvil.getWorld().playSound(anvil.getLocation(), Sound.BLOCK_ANVIL_USE, 1.9F, 0F);

                fallingBlocks.add(anvil);
            }
        });
    }

    private void createExplosion(Entity entity) {

        if (entity instanceof FallingBlock)
            fallingBlocks.remove(entity);

        int spellLevel = entity.getMetadata("SL").get(0).asInt();

        entity.getWorld().createExplosion(entity.getLocation(), 1 + (spellLevel / 2F));
        // TODO: 8/12/21 change to twice explosion power during earthquake disaster

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
    public void onPlace(EntityChangeBlockEvent event) {

        if (!(event.getEntity() instanceof FallingBlock))
            return;

        if (!fallingBlocks.contains(event.getEntity()))
            return;

        createExplosion(event.getEntity());
        event.setCancelled(true);
    }
}