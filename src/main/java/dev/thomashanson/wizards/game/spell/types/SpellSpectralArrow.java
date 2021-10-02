package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.overtime.Disaster;
import dev.thomashanson.wizards.game.overtime.types.DisasterHail;
import dev.thomashanson.wizards.game.overtime.types.DisasterLightning;
import dev.thomashanson.wizards.game.overtime.types.DisasterMeteors;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.util.BlockUtil;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Instant;
import java.util.*;

public class SpellSpectralArrow extends Spell implements Spell.Deflectable {

    private final Map<Arrow, List<Location>> arrows = new HashMap<>();

    @Override
    public void castSpell(Player player, int level) {

        Arrow arrow = player.launchProjectile(Arrow.class);

        arrow.setMetadata("SL", new FixedMetadataValue(getGame().getPlugin(), level));
        arrow.setVelocity(arrow.getVelocity().multiply(1));
        arrow.setShooter(player);

        if (getGame().isOvertime() && getGame().getDisaster() instanceof DisasterMeteors)
            arrow.setFireTicks(Integer.MAX_VALUE);

        arrows.put(arrow, Arrays.asList(player.getLocation(), player.getLocation()));

        final Particle particle =

                (getGame().isOvertime() && getGame().getDisaster() instanceof DisasterHail) ?
                        Particle.SNOW_SHOVEL :
                        Particle.FIREWORKS_SPARK;

        new BukkitRunnable() {

            @Override
            public void run() {

                // Remove any ground or invalid arrows
                arrows.keySet().removeIf(entity -> entity.isOnGround() || !entity.isValid());

                // Play particles
                for (Map.Entry<Arrow, List<Location>> entry : arrows.entrySet()) {

                    for (Location location : BlockUtil.getLinesDistancedPoints(entry.getValue().get(1), entry.getKey().getLocation(), 0.7))
                        if (location.getWorld() != null)
                            location.getWorld().spawnParticle(particle, location, 0, 0, 0, 1);

                    entry.getValue().set(1, entry.getKey().getLocation());
                }

            }

        }.runTaskTimer(getGame().getPlugin(), 0L, 1L);
    }

    @Override
    public void deflectSpell(Player player, int level, Vector vector) {

        // TODO: 2020-06-18 add metadata. example:
        //Arrow arrow = null;
        //arrow.setMetadata("Deflected", new FixedMetadataValue(getGame().getPlugin(), true));
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {

        if (!(event.getDamager() instanceof Arrow))
            return;

        if (!event.getDamager().hasMetadata("SL"))
            return;

        if (!(event.getEntity() instanceof Player))
            return;

        Arrow arrow = (Arrow) event.getDamager();
        List<Location> locations = arrows.remove(event.getDamager());

        if (locations == null || locations.isEmpty())
            return;

        Player player = (Player) event.getEntity();

        int spellLevel = arrow.getMetadata("SL").get(0).asInt();

        double distance = locations.get(0).distance(event.getEntity().getLocation());
        double damage = 6 + distance / (7.0 - spellLevel);

        event.setDamage(damage);

        // TODO: 4/14/21 stat tracking & sniper achievement

        CustomDamageTick customTick = new CustomDamageTick (
                damage,
                EntityDamageEvent.DamageCause.PROJECTILE,
                getSpell().getSpellName(),
                Instant.now(),
                (Player) arrow.getShooter()
        );

        getGame().getPlugin().getDamageManager().logTick(player, customTick);

        Wizard shooter = getWizard((Player) arrow.getShooter());
        shooter.addAccuracy(true, false);

        if (getGame().isOvertime()) {

            Disaster disaster = getGame().getDisaster();

            if (disaster instanceof DisasterLightning) {

                LightningStrike strike = player.getWorld().strikeLightning(player.getLocation());
                strike.setMetadata("OT", new FixedMetadataValue(getGame().getPlugin(), shooter));

            } else if (disaster instanceof DisasterHail) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 30, Integer.MAX_VALUE));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 30, 250));
            }
        }

        Bukkit.broadcastMessage (

                ChatColor.GREEN.toString() + ChatColor.BOLD + ((Player) event.getEntity()).getDisplayName() +
                        ChatColor.GRAY + " was hit by " +
                        ChatColor.GREEN + ChatColor.BOLD + getSpell().getSpellName() +
                        ChatColor.GRAY + " from " +
                        ChatColor.GREEN + "" + ChatColor.BOLD + distance +
                        ChatColor.GRAY + " blocks away for " +
                        ChatColor.GREEN + ChatColor.BOLD + event.getDamage() +
                        ChatColor.GRAY + " damage!"
        );

        //int damage = getValue(player, "Damage", distance);
    }
}