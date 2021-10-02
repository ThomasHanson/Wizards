package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.damage.types.MonsterDamageTick;
import dev.thomashanson.wizards.event.CustomDamageEvent;
import dev.thomashanson.wizards.game.mode.GameTeam;
import dev.thomashanson.wizards.game.spell.Spell;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

public class SpellSummonWolves extends Spell implements Spell.SpellBlock {

    private static final int SPELL_LENGTH = 30;

    @Override
    public void castSpell(Player player, int level) {

        Block block = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
    }

    @Override
    public void castSpell(Player player, Block block, int level) {

        Location location = block.getLocation().add(0.5, 0.5, 0.5);

        for (int i = 0; i < level + 2; i++) {

            Wolf wolf = player.getWorld().spawn (

                    location.clone().add (
                            ThreadLocalRandom.current().nextFloat() - 0.5F,
                            0.5,
                            ThreadLocalRandom.current().nextFloat() - 0.5F
                    ),

                    Wolf.class, spawnedWolf -> {

                spawnedWolf.setCustomName(player.getName() + "'s Wolf");

                spawnedWolf.setTamed(true);
                spawnedWolf.setOwner(player);

                AttributeInstance healthAttribute = spawnedWolf.getAttribute(Attribute.GENERIC_MAX_HEALTH);

                if (healthAttribute != null) {
                    healthAttribute.setBaseValue(2.0);
                    spawnedWolf.setHealth(healthAttribute.getBaseValue());
                }

                spawnedWolf.setBreed(false);
                spawnedWolf.setRemoveWhenFarAway(false);
            });

            wolf.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * SPELL_LENGTH, level - 1));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.2F, 1);

            Bukkit.getScheduler().scheduleSyncDelayedTask(getGame().getPlugin(), wolf::remove, SPELL_LENGTH * 20);

            /*
             * Change the color of the wolf's collar based
             * off the team each player is on
             */

            /*
            PacketAdapter adapter = new PacketAdapter(getGame().getPlugin(), ListenerPriority.NORMAL, PacketType.Play.Server.ENTITY_METADATA) {

                @Override
                public void onPacketSending(PacketEvent event) {

                    PacketContainer packet = event.getPacket();

                    Player receiver = event.getPlayer();
                    Entity entity = packet.getEntityModifier(receiver.getWorld()).read(0);

                    if (!(entity instanceof Wolf))
                        return;

                    Wolf wolf = (Wolf) entity;

                    if (wolf.getOwner() == null || (!(wolf.getOwner() instanceof Player)))
                        return;

                    packet = event.getPacket().deepClone();
                    event.setPacket(packet);

                    WrappedDataWatcher watcher = WrappedDataWatcher.getEntityWatcher(wolf);

                    WrappedDataWatcher.Serializer serializer = WrappedDataWatcher.Registry.get(Integer.class);
                    WrappedDataWatcher.WrappedDataWatcherObject object = new WrappedDataWatcher.WrappedDataWatcherObject(19, serializer);

                    // Color collar
                    watcher.setObject(object, receiver.getUniqueId().equals(wolf.getOwner().getUniqueId()) ? DyeColor.GREEN.getDyeData() : DyeColor.RED.getDyeData());

                    // Entity name

                    serializer = WrappedDataWatcher.Registry.getChatComponentSerializer(true);
                    object = new WrappedDataWatcher.WrappedDataWatcherObject(2, serializer);

                    Optional<?> optional = Optional.of (
                            WrappedChatComponent.fromChatMessage (

                                    receiver.getUniqueId().equals(wolf.getOwner().getUniqueId()) ?
                                            ChatColor.GREEN + "Your Wolf" :
                                            ChatColor.RED + wolf.getName()

                            )[0].getHandle()
                    );

                    watcher.setObject(object, optional);
                }
            };

            ProtocolLibrary.getProtocolManager().addPacketListener(adapter);
             */
        }
    }

    @EventHandler
    public void onDamage(CustomDamageEvent event) {

        DamageTick tick = event.getDamageTick();

        if (!(tick instanceof MonsterDamageTick))
            return;

        MonsterDamageTick monsterDamageTick = (MonsterDamageTick) tick;
        LivingEntity entity = monsterDamageTick.getEntity();

        if (entity instanceof Wolf) {

            Wolf wolf = (Wolf) entity;
            monsterDamageTick.addDamage("Summoned Wolf", 0.3);

            AnimalTamer tamer = wolf.getOwner();

            if (tamer instanceof Player) {
                monsterDamageTick.setEntity((Player) tamer);
                monsterDamageTick.setKnockbackOrigin(wolf.getLocation());
            }
        }
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent event) {

        if (!(event.getEntity() instanceof Wolf))
            return;

        Wolf wolf = (Wolf) event.getEntity();

        if (!(event.getTarget() instanceof Player))
            return;

        Player target = (Player) event.getTarget();

        if (!getGame().getCurrentMode().isTeamMode())
            return;

        Player owner = (Player) wolf.getOwner();

        if (getGame().getRelation(owner, target) == GameTeam.TeamRelation.ALLY)
            event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {

        // Disable standard death message on wolf death

        if (event.getEntity() instanceof Wolf) {
            event.getEntity().remove();
        }
    }
}