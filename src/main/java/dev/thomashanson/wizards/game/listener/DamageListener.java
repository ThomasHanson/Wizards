package dev.thomashanson.wizards.game.listener;

import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.damage.types.*;
import dev.thomashanson.wizards.game.manager.DamageManager;
import org.apache.commons.lang.Validate;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;

public class DamageListener implements Listener {

    private final DamageManager damageManager;

    public DamageListener(DamageManager damageManager) {
        this.damageManager = damageManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {

        if (!(event.getEntity() instanceof LivingEntity))
            return;

        LivingEntity entity = (LivingEntity) event.getEntity();

        double damage = event.getDamage();

        if (event.isCancelled())
            return;

        Sound sound = Sound.ENTITY_PLAYER_HURT;
        float volume = 1F, pitch = 1F;

        if ((entity instanceof Player)) {

            Player player = (Player) entity;

            EntityDamageEvent.DamageCause cause = event.getCause();

            if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK && cause != EntityDamageEvent.DamageCause.PROJECTILE)
                return;

            double percentage = Math.random();

            ItemStack armorPiece =
                    percentage > 0.5 ? player.getInventory().getChestplate() :
                            percentage > 0.25 ? player.getInventory().getLeggings() :
                                    percentage > 0.1 ? player.getInventory().getHelmet() :
                                            player.getInventory().getHelmet();

            if (armorPiece != null) {

                Material material = armorPiece.getType();

                if (material.toString().startsWith("LEATHER")) {

                    sound = Sound.ENTITY_ARROW_SHOOT;
                    pitch = 2F;

                } else if (material.toString().startsWith("GOLDEN")) {

                    sound = Sound.ENTITY_ITEM_BREAK;
                    pitch = 1.4F;

                } else if (material.toString().startsWith("CHAINMAIL")) {

                    sound = Sound.ENTITY_ITEM_BREAK;
                    pitch = 1.8F;

                } else if (material.toString().startsWith("IRON")) {

                    sound = Sound.ENTITY_BLAZE_HURT;
                    pitch = 0.7F;

                } else if (
                        material.toString().startsWith("DIAMOND") ||
                                material.toString().startsWith("NETHERITE")
                ) {

                    sound = Sound.ENTITY_BLAZE_HURT;
                    pitch = 0.9F;
                }

                /*
                PacketListener listener = new PacketAdapter(damageManager.getPlugin(), ListenerPriority.NORMAL, PacketType.Play.Server.NAMED_SOUND_EFFECT) {

                    @Override
                    public void onPacketSending(PacketEvent event) {

                        if (event.getPacketType() == PacketType.Play.Server.NAMED_SOUND_EFFECT) {

                            if (event.getPacket().getSoundEffects().read(0) == Sound.ENTITY_PLAYER_HURT) {
                                event.setCancelled(true);
                            }
                        }
                    }
                };

                ProtocolLibrary.getProtocolManager().addPacketListener(listener);
                 */

                // Custom damage sounds
                player.getWorld().playSound(player.getLocation(), sound, volume, pitch);
            }

            if (damage == 0)
                return;

            DamageTick damageTick = null;

            if ((event instanceof EntityDamageByEntityEvent)) {

                EntityDamageByEntityEvent entityDamageByEntityEvent = (EntityDamageByEntityEvent) event;
                Entity damager = entityDamageByEntityEvent.getDamager();

                LivingEntity attacker = null;
                double distance = 0;

                if ((damager instanceof LivingEntity)) {
                    attacker = (LivingEntity) damager;

                } else if ((damager instanceof Projectile)) {

                    Projectile projectile = (Projectile) damager;

                    if ((projectile.getShooter() instanceof LivingEntity)) {
                        attacker = (LivingEntity) projectile.getShooter();
                        distance = attacker.getLocation().distance(entity.getLocation());
                    }

                } else if ((damager instanceof TNTPrimed)) {

                    damageTick = new BlockDamageTick (
                            damage,
                            EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
                            "TNT",
                            Instant.now(),
                            Material.TNT,
                            damager.getLocation()
                    );
                }

                if (attacker != null) {

                    if ((attacker instanceof Player)) {

                        if (distance > 0)
                            damageTick = new PlayerDamageTick(damage, "Ranged Combat", Instant.now(), (Player) attacker, distance);
                        else
                            damageTick = new PlayerDamageTick(damage, "Combat", Instant.now(), (Player) attacker);

                    } else {

                        if (distance > 0)
                            damageTick = new MonsterDamageTick(damage, "Entity Damage", Instant.now(), attacker, distance);
                        else
                            damageTick = new MonsterDamageTick(damage, "Entity Damage", Instant.now(), attacker);
                    }
                }

            } else if ((event instanceof EntityDamageByBlockEvent)) {

                EntityDamageByBlockEvent entityDamageByBlockEvent = (EntityDamageByBlockEvent) event;
                Validate.notNull(entityDamageByBlockEvent.getDamager());

                damageTick = new BlockDamageTick (
                        damage,
                        EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
                        "Block",
                        Instant.now(),
                        entityDamageByBlockEvent.getDamager().getType(),
                        entityDamageByBlockEvent.getDamager().getLocation()
                );

            } else {

                if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    damageTick = new FallDamageTick(damage, "Fall", Instant.now(), entity.getFallDistance());

                } else {
                    String name = event.getCause().name();
                    damageTick = new OtherDamageTick(damage, event.getCause(), name, Instant.now());
                }
            }

            if (damageTick != null)
                damageManager.damage(player, damageTick);
        }
    }
}