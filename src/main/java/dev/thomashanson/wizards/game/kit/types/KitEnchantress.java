package dev.thomashanson.wizards.game.kit.types;

import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.event.CustomDamageEvent;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.kit.WizardsKit;
import dev.thomashanson.wizards.game.manager.DamageManager;
import dev.thomashanson.wizards.game.spell.WandElement;
import dev.thomashanson.wizards.util.BlockUtil;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

public class KitEnchantress extends WizardsKit {

    private final Wizards game;
    private Instant lastDamaged;

    //private Map<UUID, List<Block>> blocksChanged = new HashMap<>();

    public KitEnchantress(Wizards game) {

        super (
                "Enchantress", ChatColor.RED, Color.RED,

                Collections.singletonList (
                        "Duplicate spellbooks have a 20-25% chance to level up a random spell."
                ),

                new ItemStack(Material.EXPERIENCE_BOTTLE),
                new ItemStack(WandElement.ICE.getMaterial())
        );

        this.game = game;
    }

    @Override
    public void playSpellEffect(Player player, Location location) {

    }

    @Override
    public void playIntro(Player player, Location location, int ticks) {

    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {

        Player player = event.getPlayer();

        if (!(game.getKit(player) instanceof KitEnchantress))
            return;

        if (!((LivingEntity) player).isOnGround())
            return;

        Map<Block, Double> blocks = BlockUtil.getInRadius(player.getLocation().subtract(0, 1, 0), 3.0, true);

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
}