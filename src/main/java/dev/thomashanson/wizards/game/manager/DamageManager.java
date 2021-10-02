package dev.thomashanson.wizards.game.manager;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.damage.KillAssist;
import dev.thomashanson.wizards.damage.types.PlayerDamageTick;
import dev.thomashanson.wizards.event.CustomDamageEvent;
import dev.thomashanson.wizards.game.listener.DamageListener;
import dev.thomashanson.wizards.game.listener.DeathListener;
import dev.thomashanson.wizards.util.EntityUtil;
import dev.thomashanson.wizards.util.MathUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class DamageManager {

    private final WizardsPlugin plugin;

    private final DamageListener damageListener;
    private final DeathListener deathListener;

    private static final long DAMAGE_TIMEOUT = 10000;
    private static final int ASSIST_PERCENTAGE_THRESHOLD = 20;
    public static final ChatColor BASE_COLOR = ChatColor.GRAY;
    public static final ChatColor ACCENT_COLOR = ChatColor.GREEN;
    public static final ChatColor PUNCTUATION_COLOR = ChatColor.DARK_GRAY;

    private static final Map<UUID, List<DamageTick>> damageTicks = new HashMap<>();

    public DamageManager(WizardsPlugin plugin) {

        this.plugin = plugin;

        this.damageListener = new DamageListener(this);
        this.deathListener = new DeathListener(this);

        plugin.getServer().getPluginManager().registerEvents(damageListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(deathListener, plugin);
    }

    /*
    public void damage(NPC npc, DamageTick damageTick) {

        if (npc == null || npc.getMetadata() == null)
            return;

        NPCMetadata metadata = npc.getMetadata();

        if (metadata.getHealth() <= 0)
            return;

        metadata.setHealth((float) Math.max(0, metadata.getHealth() - damageTick.getDamage()));
        npc.playAnimation(Animation.TAKE_DAMAGE);
    }
     */

    public void damage(LivingEntity entity, DamageTick damageTick) {

        if (entity == null || entity.getHealth() <= 0)
            return;

        CustomDamageEvent event = new CustomDamageEvent(entity, damageTick);

        if (event.isCancelled())
            return;

        double damage = damageTick.getDamage();

        /*
         * Log the damage
         */
        logTick(entity, damageTick);

        if (damageTick instanceof PlayerDamageTick) {

            PlayerDamageTick playerDamageTick = (PlayerDamageTick) damageTick;
            Player player = playerDamageTick.getPlayer();

            /*
             * Calculate & handle damage
             */
            double armorPoints = 0, armorToughness = 0;

            AttributeInstance armorAttribute = player.getAttribute(Attribute.GENERIC_ARMOR);
            AttributeInstance toughnessAttribute = player.getAttribute(Attribute.GENERIC_ARMOR_TOUGHNESS);

            PotionEffect effect = player.getPotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
            int resistance = effect == null ? 0 : effect.getAmplifier();

            if (armorAttribute != null)
                armorPoints = armorAttribute.getValue();

            if (toughnessAttribute != null)
                armorToughness = toughnessAttribute.getValue();

            damage = EntityUtil.calculateDamage(
                    damageTick.getDamage(),
                    armorPoints, armorToughness,
                    resistance,
                    EntityUtil.getEPF(player.getInventory())
            );

            player.setLevel((int) damage);

            Bukkit.broadcastMessage("Final Damage: " + damage);
            Bukkit.broadcastMessage("Reason: " + damageTick.getReason());
            Bukkit.broadcastMessage("");

            /*
             * Handle knockback
             */
            double knockback = damageTick.getDamage();

            knockback = Math.max(2, knockback);
            knockback = Math.log10(knockback);

            for (double mod : damageTick.getKnockbackMods().values())
                knockback *= mod;

            Location origin = player.getLocation();

            if (damageTick.getKnockbackOrigin() != null)
                origin = damageTick.getKnockbackOrigin();

            Vector trajectory = MathUtil.getTrajectory2D(origin, entity.getLocation());

            trajectory.multiply(0.6 * knockback);
            trajectory.setY(Math.abs(trajectory.getY()));

            double velocity = 0.2 + trajectory.length() * 0.8;
            MathUtil.setVelocity(entity, trajectory, velocity, false, 0, Math.abs(0.2 * knockback), 0.4 + (0.04 * knockback), true);
        }

        entity.setHealth(Math.max(0, entity.getHealth() - damage));

        if (damage > 0)
            entity.playEffect(EntityEffect.HURT);
    }

    public void logTick(LivingEntity entity, DamageTick tick) {

        Optional<DamageTick> logged = getLoggedTick(entity.getUniqueId(), tick);

        if (logged.isPresent()) {

            logged.get().setDamage(logged.get().getDamage() + tick.getDamage());
            logged.get().setTimestamp(Instant.now());

        } else {

            List<DamageTick> ticks = getLoggedTicks(entity.getUniqueId());
            ticks.add(tick);

            damageTicks.put(entity.getUniqueId(), ticks);
        }
    }

    public List<KillAssist> getPossibleAssists(List<DamageTick> ticks) {

        if (ticks.isEmpty())
            return new ArrayList<>();

        List<KillAssist> assists = new ArrayList<>();
        List<PlayerDamageTick> playerDamage = new ArrayList<>();

        PlayerDamageTick killingTick = null;

        DamageTick lastTick = ticks.get(ticks.size() - 1);

        if (lastTick instanceof PlayerDamageTick)
            killingTick = (PlayerDamageTick) lastTick;

        for (DamageTick tick : ticks) {
            if ((tick instanceof PlayerDamageTick)) {

                PlayerDamageTick damageTick = (PlayerDamageTick) tick;

                if (killingTick != null && damageTick.getPlayer().getUniqueId().equals(killingTick.getPlayer().getUniqueId()))
                    continue;

                playerDamage.add(damageTick);
            }
        }

        double totalDamage = 0;

        for(PlayerDamageTick tick : playerDamage)
            totalDamage += tick.getDamage();

        for(PlayerDamageTick tick : playerDamage) {

            double damage = tick.getDamage();

            int percentage = (int) ((damage / totalDamage) * 100);

            if (percentage >= DamageManager.ASSIST_PERCENTAGE_THRESHOLD)
                assists.add(new KillAssist(tick.getPlayer(), damage, percentage));
        }

        Collections.sort(assists);
        return assists;
    }

    public List<String> getDamageSummary(List<DamageTick> ticks) {

        List<String> messages = new ArrayList<>();
        DecimalFormat decimalFormat = new DecimalFormat("#.#");

        ticks.forEach(tick -> messages.add (
                PUNCTUATION_COLOR + " - " + ACCENT_COLOR + ChatColor.BOLD + decimalFormat.format(tick.getDamage()) + " damage" +
                PUNCTUATION_COLOR + ": " + tick.getSingleLineSummary() + PUNCTUATION_COLOR + " (" + tick.timeDiff() + ")"
        ));

        return messages;
    }

    private Optional<DamageTick> getLoggedTick(UUID uuid, DamageTick newTick) {

        return
                getLoggedTicks(uuid)
                        .stream()
                        .filter(tick -> tick.getCause() == newTick.getCause() && tick.matches(newTick))
                        .findFirst();
    }

    public List<DamageTick> getLoggedTicks(UUID uuid) {
        return damageTicks.containsKey(uuid) ?
                cleanup(damageTicks.get(uuid)) :
                new ArrayList<>();
    }

    public DamageTick getLastLoggedTick(UUID uuid) {

        if (getLoggedTicks(uuid).isEmpty())
            return null;

        List<DamageTick> loggedTicks = getLoggedTicks(uuid);
        int numTicks = loggedTicks.size();

        return loggedTicks.get(numTicks - 1);
    }

    public void dump(UUID uuid) {
        damageTicks.remove(uuid);
    }

    private List<DamageTick> cleanup(List<DamageTick> ticks) {

        for(int i = 0; i < ticks.size(); i++) {

            DamageTick tick = ticks.get(i);

            if (Duration.between(Instant.now(), tick.getTimestamp()).toMillis() > DAMAGE_TIMEOUT)
                ticks.remove(tick);
        }

        Collections.sort(ticks);
        return ticks;
    }

    public void handleListeners() {

        //previousLevels.clear();

        HandlerList.unregisterAll(damageListener);
        HandlerList.unregisterAll(deathListener);
    }

    public WizardsPlugin getPlugin() {
        return plugin;
    }
}