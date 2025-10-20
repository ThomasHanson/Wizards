package dev.thomashanson.wizards.game.spell.types;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;
import dev.thomashanson.wizards.util.ExplosionUtil;

public class SpellDroom extends Spell {

    private final NamespacedKey droomKey;
    private final NamespacedKey casterKey;
    private final NamespacedKey levelKey;

    public SpellDroom(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
        this.droomKey = new NamespacedKey(plugin, "droom_anvil");
        this.casterKey = new NamespacedKey(plugin, "droom_caster");
        this.levelKey = new NamespacedKey(plugin, "droom_level");
    }

    @Override
    public boolean cast(Player player, int level) {
        StatContext context = StatContext.of(level);
        double radius = getStat("radius", level);
        double spawnHeight = getStat("spawn-height", level);

        List<Player> targets = new ArrayList<>();
        targets.add(player);

        player.getNearbyEntities(radius, radius * 3, radius).forEach(entity -> {
            if (entity instanceof Player && getWizard((Player) entity).isPresent())
                targets.add((Player) entity);
        });

        if (targets.isEmpty()) return false;

        for (Player target : targets) {
            Location loc = target.getLocation().clone().add(0, spawnHeight, 0);
            
            // Ensure we don't spawn inside a block
            while (loc.getBlock().getType() != Material.AIR && loc.getY() < player.getWorld().getMaxHeight()) {
                loc.add(0, 1, 0);
            }
            
            FallingBlock anvil = target.getWorld().spawnFallingBlock(loc, Bukkit.createBlockData(Material.ANVIL));
            
            // Tag the entity with modern PersistentDataContainer
            PersistentDataContainer pdc = anvil.getPersistentDataContainer();
            pdc.set(droomKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(casterKey, PersistentDataType.STRING, player.getUniqueId().toString());
            pdc.set(levelKey, PersistentDataType.INTEGER, level);
            
            anvil.getWorld().playSound(anvil.getLocation(), Sound.BLOCK_ANVIL_USE, 1.9F, 0F);
        }

        return true;
    }

    @EventHandler
    public void onAnvilLand(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock)) return;

        PersistentDataContainer pdc = event.getEntity().getPersistentDataContainer();
        if (!pdc.has(droomKey, PersistentDataType.BYTE)) return;

        event.setCancelled(true); // Prevent the anvil block from actually forming

        String casterUUIDString = pdc.get(casterKey, PersistentDataType.STRING);
        Integer level = pdc.get(levelKey, PersistentDataType.INTEGER);
        if (casterUUIDString == null || level == null) return;

        Player caster = Bukkit.getPlayer(UUID.fromString(casterUUIDString));
        if (caster == null) return;

        StatContext context = StatContext.of(level);
        float explosionPower = (float) getStat("explosion-power", level);

        // UPDATED: ExplosionUtil now uses a configuration record.
        ExplosionUtil.ExplosionConfig config = new ExplosionUtil.ExplosionConfig(
            false,  // regenerateBlocks
            100L,   // regenerationDelayTicks
            60,     // debrisLifespanTicks
            0.3,    // debrisChance
            0.6,    // velocityStrength
            0.5,    // velocityYAward
            0.5     // itemVelocityModifier
        );

        ExplosionUtil.createExplosion(plugin, event.getEntity().getLocation(), explosionPower, config, false);
        event.getEntity().remove();
    }
}
