package dev.thomashanson.wizards.game.listener;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import dev.thomashanson.wizards.damage.DamageConfig;
import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.damage.types.BlockDamageTick;
import dev.thomashanson.wizards.damage.types.FallDamageTick;
import dev.thomashanson.wizards.damage.types.MonsterDamageTick;
import dev.thomashanson.wizards.damage.types.OtherDamageTick;
import dev.thomashanson.wizards.damage.types.PlayerDamageTick;
import dev.thomashanson.wizards.game.manager.DamageManager;

public class DamageListener implements Listener {

    private final DamageManager damageManager;
    private final DamageConfig config;
    private final Random random = new Random();

    // Internal reasons remain constant, their display messages are configured.
    private static final String REASON_MELEE_COMBAT = "Melee Combat";
    private static final String REASON_RANGED_COMBAT = "Ranged Combat";
    private static final String REASON_MONSTER_MELEE = "Monster Melee";
    private static final String REASON_MONSTER_RANGED = "Monster Ranged";
    private static final String REASON_TNT_EXPLOSION = "TNT Explosion";
    private static final String REASON_BLOCK_DAMAGE = "Block Hazard";
    private static final String REASON_ENVIRONMENTAL = "Environmental Hazard";

    public DamageListener(DamageManager damageManager) {
        this.damageManager = damageManager;
        this.config = damageManager.getConfig();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        double initialDamage = event.getDamage();
        if (initialDamage <= 0) {
            return;
        }

        playArmorHitSound(victim);

        DamageTick damageTick = createDamageTick(event, victim, initialDamage);

        if (damageTick != null) {
            event.setDamage(0D);
            damageManager.damage(victim, damageTick);
        }
    }

    private void playArmorHitSound(Player player) {
        List<ItemStack> equippedArmor = Arrays.stream(player.getInventory().getArmorContents())
                .filter(item -> item != null && item.getType() != Material.AIR)
                .collect(Collectors.toList());

        if (equippedArmor.isEmpty()) {
            return;
        }

        ItemStack armorPiece = equippedArmor.get(random.nextInt(equippedArmor.size()));
        String materialName = armorPiece.getType().toString();

        DamageConfig.SoundConfig soundConfig = config.armorHitSounds().get("default");

        for (Map.Entry<String, DamageConfig.SoundConfig> entry : config.armorHitSounds().entrySet()) {
            if (materialName.contains(entry.getKey())) {
                soundConfig = entry.getValue();
                break;
            }
        }

        if (soundConfig != null) {
            player.getWorld().playSound(player.getLocation(), soundConfig.sound(), soundConfig.volume(), soundConfig.pitch());
        }
    }

    private DamageTick createDamageTick(EntityDamageEvent event, Player victim, double damage) {
        Instant now = Instant.now();
        EntityDamageEvent.DamageCause cause = event.getCause();

        if (event instanceof EntityDamageByEntityEvent edbe) {
            return createEntityDamageTick(edbe, victim, damage, now, cause);
        }

        if (event instanceof EntityDamageByBlockEvent edbb) {
            Block damager = edbb.getDamager();
            if (damager != null) {
                return new BlockDamageTick(damage, REASON_BLOCK_DAMAGE, now, damager.getType(), damager.getLocation());
            }
        }

        return switch (cause) {
            case FALL -> new FallDamageTick(damage, now, victim.getFallDistance());
            case LAVA -> new BlockDamageTick(damage, REASON_ENVIRONMENTAL, now, Material.LAVA, victim.getLocation());
            // Void damage is handled separately to instantly kill.
            case VOID, SUICIDE -> null;
            default -> {
                String reasonName = cause.name().toLowerCase().replace("_", " ");
                reasonName = Character.toUpperCase(reasonName.charAt(0)) + reasonName.substring(1);
                yield new OtherDamageTick(damage, cause, reasonName, now);
            }
        };
    }

    private DamageTick createEntityDamageTick(EntityDamageByEntityEvent event, Player victim, double damage, Instant now, EntityDamageEvent.DamageCause cause) {
        Entity damagerEntity = event.getDamager();

        if (damagerEntity instanceof TNTPrimed tnt) {
            return createTntDamageTick(damage, now, tnt);
        }

        LivingEntity attacker = getAttackerFromEvent(damagerEntity);
        if (attacker != null) {
            double distance = attacker.getLocation().distance(victim.getLocation());
            boolean isRanged = (damagerEntity instanceof Projectile) || (distance > config.meleeRangeThreshold());

            if (attacker instanceof Player playerAttacker) {
                String reason = isRanged ? REASON_RANGED_COMBAT : REASON_MELEE_COMBAT;
                return new PlayerDamageTick(damage, cause, reason, now, playerAttacker, isRanged ? distance : null);
            } else {
                String reason = isRanged ? REASON_MONSTER_RANGED : REASON_MONSTER_MELEE;
                return new MonsterDamageTick(damage, cause, reason, now, attacker, isRanged ? distance : null);
            }
        }
        return null;
    }

    private LivingEntity getAttackerFromEvent(Entity damager) {
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        return null;
    }

    private DamageTick createTntDamageTick(double damage, Instant now, TNTPrimed tnt) {
        Entity source = tnt.getSource();
        if (source instanceof Player playerAttacker) {
            return new PlayerDamageTick(damage, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, REASON_TNT_EXPLOSION, now, playerAttacker, null);
        } else if (source instanceof LivingEntity entityAttacker) {
            return new MonsterDamageTick(damage, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, REASON_TNT_EXPLOSION, now, entityAttacker, null);
        }
        return new BlockDamageTick(damage, REASON_TNT_EXPLOSION, now, Material.TNT, tnt.getLocation());
    }
}