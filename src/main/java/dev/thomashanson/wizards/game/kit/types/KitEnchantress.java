package dev.thomashanson.wizards.game.kit.types;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.event.CustomDamageEvent;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.kit.WizardsKit;
import dev.thomashanson.wizards.game.manager.DamageManager;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.util.BlockUtil;

public class KitEnchantress extends WizardsKit {

    private final Wizards game;
    private Instant lastDamaged;

    private final int PARTICLE_DELAY = 5; // Particle delay in ticks (adjust as needed)
    private final int FIREWORK_DELAY = 60; // Firework delay in ticks (adjust as needed)

    public KitEnchantress(Wizards game, Map<String, Object> data) {
        super(data);
        this.game = game;
    }

    @Override
    public List<String> getLevelDescription(int level) {
        // Chance to level up = 0.2 + (0.0125 * (level - 1))
        double chance = 0.2 + (0.0125 * (Math.max(0, level - 1)));
        String formattedChance = String.format("%.1f%%", chance * 100);

        return Collections.singletonList(
            "<gray>Duplicate Spellbook Upgrade Chance: <aqua>" + formattedChance
        );
    }

    //             new ItemStack(Material.EXPERIENCE_BOTTLE),
    //             new ItemStack(WandElement.ICE.getMaterial())

    @Override
    public void playSpellEffect(Player player, Location location) {

    }

    @Override
    public void playIntro(Player player) {

        // Store the player's initial location
        Location initialLocation = player.getLocation();

        // Set the player to be invisible
        player.setInvisible(true);

        // Play the rising hot pink particles
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                count++;

                // Calculate the position for the rising particles
                double offsetX = Math.sin(count / 10.0) * 0.5;
                double offsetY = count / 20.0;
                double offsetZ = Math.cos(count / 10.0) * 0.5;

                // Spawn the particles at the calculated position
                initialLocation.getWorld().spawnParticle(Particle.REDSTONE, initialLocation.getX() + offsetX,
                        initialLocation.getY() + offsetY, initialLocation.getZ() + offsetZ, 0,
                        new Particle.DustOptions(Color.FUCHSIA, 1));

                if (count >= 60) {
                    // Stop the particle effect after 3 seconds (60 ticks)
                    cancel();

                    // Play the burst of hot pink fireworks
                    spawnFirework(initialLocation, Color.FUCHSIA);
                }
            }
        }.runTaskTimer(WizardsPlugin.getPlugin(WizardsPlugin.class), 0, PARTICLE_DELAY);

        // Schedule the player to become visible again after the effect
        new BukkitRunnable() {
            @Override
            public void run() {
                // Make the player visible again
                player.setInvisible(false);
            }
        }.runTaskLater(WizardsPlugin.getPlugin(WizardsPlugin.class), FIREWORK_DELAY);
    }

    private void spawnFirework(Location location, Color color) {
        Firework firework = (Firework) location.getWorld().spawnEntity(location, EntityType.FIREWORK);
        FireworkMeta fireworkMeta = firework.getFireworkMeta();
        fireworkMeta.addEffect(FireworkEffect.builder().withColor(color).with(FireworkEffect.Type.BURST).build());
        fireworkMeta.setPower(0);
        firework.setFireworkMeta(fireworkMeta);

        // Schedule the firework to explode and disappear
        Bukkit.getScheduler().scheduleSyncDelayedTask(WizardsPlugin.getPlugin(WizardsPlugin.class), firework::detonate,
                FIREWORK_DELAY);
        Bukkit.getScheduler().scheduleSyncDelayedTask(WizardsPlugin.getPlugin(WizardsPlugin.class), firework::remove,
                FIREWORK_DELAY + 20); // Remove firework 1 second after explosion
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {

        Player player = event.getPlayer();

        if (!(game.getKit(player) instanceof KitEnchantress))
            return;

        if (!((LivingEntity) player).isOnGround())
            return;

        Map<Block, Double> blocks = BlockUtil.getBlocksInRadius(player.getLocation().subtract(0, 1, 0), 3.0);

        blocks.forEach((block, distance) -> {

            if (block.getType() == Material.AIR || block.getType() == Material.CHEST)
                return;

            if (block.getRelative(BlockFace.UP).getType().isSolid())
                return;

            //List<Block> alreadyChanged = blocksChanged.getOrDefault(player.getUniqueId(), new ArrayList<>());
            //alreadyChanged.add(block);

            //blocksChanged.put(player.getUniqueId(), alreadyChanged);
            player.sendBlockChange(block.getLocation(), Material.GLASS.createBlockData());
        });
    }

    @EventHandler
    public void onDamage(CustomDamageEvent event) {

        LivingEntity entity = event.getVictim();

        if (!(entity instanceof Player))
            return;

        Player player = (Player) event.getVictim();

        if (!(game.getKit(player) instanceof KitEnchantress))
            return;

        DamageTick tick = event.getDamageTick();
        String reason = tick.getReason();

        DamageManager damageManager = game.getPlugin().getDamageManager();
        DamageTick lastTick = damageManager.getLastLoggedTick(player.getUniqueId());

        lastDamaged = lastTick.getTimestamp();

        if (reason.equalsIgnoreCase("Rumble"))
            if (lastDamaged == null || lastDamaged.plusSeconds(5).isBefore(Instant.now()))
                event.setCancelled(true);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {

        Entity entity = event.getEntity();

        if (!(entity instanceof Player))
            return;

        Player player = (Player) entity;

        if (!(game.getKit(player) instanceof KitEnchantress))
            return;

        if (game.getWizard(player) == null)
            return;

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {

            if (lastDamaged == null || lastDamaged.plusSeconds(5).isBefore(Instant.now()))
                event.setCancelled(true);

        } else {

            DamageManager damageManager = game.getPlugin().getDamageManager();
            DamageTick lastTick = damageManager.getLastLoggedTick(player.getUniqueId());

            lastDamaged = lastTick.getTimestamp();
        }
    }

    @Override
    public float getInitialMaxMana(int kitLevel) {
        return 100;
    }

    @Override
    public int getInitialWands() {
        return 2;
    }

    @Override
    public int getInitialMaxWands(int kitLevel) {
        return 5;
    }

    @Override
    public float getBaseManaPerTick(int kitLevel) {
        return 2.5F / 20F;
    }

    @Override
    public void applyModifiers(Wizard wizard, int kitLevel) { }

    @Override
    public void applyInitialSpells(Wizard wizard) { }

    @Override
    public int getModifiedMaxSpellLevel(Spell spell, int currentDefaultMaxLevel) {
        return currentDefaultMaxLevel;
    }
}