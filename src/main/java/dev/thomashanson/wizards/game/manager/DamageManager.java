package dev.thomashanson.wizards.game.manager;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.DamageConfig;
import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.damage.KillAssist;
import dev.thomashanson.wizards.damage.types.PlayerDamageTick;
import dev.thomashanson.wizards.event.CustomDamageEvent;
import dev.thomashanson.wizards.game.listener.DamageListener;
import dev.thomashanson.wizards.game.listener.DeathListener;
import dev.thomashanson.wizards.game.manager.PlayerStatsManager.StatType;
import dev.thomashanson.wizards.util.EntityUtil;
import dev.thomashanson.wizards.util.MathUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class DamageManager {

    private final WizardsPlugin plugin;
    private final DamageConfig config;

    private final DamageListener damageListener;
    private final DeathListener deathListener;

    private static final DecimalFormat DAMAGE_FORMAT = new DecimalFormat("#.#");
    private final Map<UUID, List<DamageTick>> damageTicks = new HashMap<>();

    public DamageManager(WizardsPlugin plugin) {
        this.plugin = plugin;
        this.config = loadDamageConfig();

        this.damageListener = new DamageListener(this);
        this.deathListener = new DeathListener(this);

        plugin.getServer().getPluginManager().registerEvents(damageListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(deathListener, plugin);
    }

    private DamageConfig loadDamageConfig() {
        ConfigurationSection damageSection = plugin.getConfig().getConfigurationSection("damage");
        if (damageSection == null) {
            plugin.getLogger().severe("Missing 'damage' section in config.yml! Using default values.");
            damageSection = plugin.getConfig().createSection("damage");
        }

        // Load main settings
        long logTimeoutMillis = TimeUnit.SECONDS.toMillis(damageSection.getLong("log-timeout-seconds", 10));
        int assistThreshold = damageSection.getInt("assist-threshold-percentage", 20);
        long justNowMillis = (long) (damageSection.getDouble("just-now-threshold-seconds", 1.5) * 1000);
        double meleeRange = damageSection.getDouble("melee-range-threshold", 4.5);

        // Load armor hit sounds
        Map<String, DamageConfig.SoundConfig> armorSounds = new HashMap<>();
        ConfigurationSection soundSection = damageSection.getConfigurationSection("armor-hit-sounds");
        if (soundSection != null) {
            for (String key : soundSection.getKeys(false)) {
                try {
                    Sound sound = Sound.valueOf(soundSection.getString(key + ".sound", "minecraft:item.armor.equip_generic").replace("minecraft:", "").toUpperCase());
                    float volume = (float) soundSection.getDouble(key + ".volume", 1.0);
                    float pitch = (float) soundSection.getDouble(key + ".pitch", 1.0);
                    armorSounds.put(key.toUpperCase(), new DamageConfig.SoundConfig(sound, volume, pitch));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning(String.format("Invalid sound specified in config.yml under damage.armor-hit-sounds.%s", key));
                }
            }
        }

        // Load death messages
        ConfigurationSection msgSection = damageSection.getConfigurationSection("death-messages");
        if (msgSection == null) {
            plugin.getLogger().severe("Missing 'damage.death-messages' section in config.yml! Using default values.");
            msgSection = damageSection.createSection("death-messages");
        }

        // Create an effectively final variable to use in lambdas
        final ConfigurationSection finalMsgSection = msgSection;

        // Load messages by block
        Map<String, String> byBlock = new HashMap<>();
        Optional.ofNullable(finalMsgSection.getConfigurationSection("by-block"))
                .ifPresent(blockSection -> blockSection.getKeys(false)
                        .forEach(key -> byBlock.put(key, blockSection.getString(key))));

        // Load messages by cause
        Map<String, String> byCause = new HashMap<>();
        Optional.ofNullable(finalMsgSection.getConfigurationSection("by-cause"))
                .ifPresent(causeSection -> causeSection.getKeys(false)
                        .forEach(key -> byCause.put(key, causeSection.getString(key))));

        // Load PvP messages
        DamageConfig.PvpConfig pvpConfig = new DamageConfig.PvpConfig(
                new DamageConfig.MeleeConfig(
                        finalMsgSection.getStringList("pvp.melee.verbs"),
                        finalMsgSection.getString("pvp.melee.format", "wizards.death.player.melee")
                ),
                finalMsgSection.getString("pvp.ranged.format", "wizards.death.player.ranged")
        );

        // Load spell messages, including overrides
        Map<String, String> spellOverrides = new HashMap<>();
        Optional.ofNullable(finalMsgSection.getConfigurationSection("by-spell.overrides"))
                .ifPresent(overrideSection -> overrideSection.getKeys(false)
                        .forEach(key -> spellOverrides.put(key, overrideSection.getString(key))));

        DamageConfig.BySpellConfig spellConfig = new DamageConfig.BySpellConfig(
                spellOverrides,
                new DamageConfig.GenericSpellConfig(
                        finalMsgSection.getStringList("by-spell.generic.verbs"),
                        finalMsgSection.getString("by-spell.generic.format", "wizards.death.player_by_spell")
                ),
                finalMsgSection.getString("by-spell.suicide.format", "wizards.death.suicide_by_spell")
        );

        DamageConfig.DeathMessageConfig deathMessageConfig = new DamageConfig.DeathMessageConfig(
                byBlock,
                finalMsgSection.getString("by-block.default", "wizards.death.block.generic"),
                byCause,
                finalMsgSection.getString("fall", "wizards.death.fall.distance"),
                new DamageConfig.VoidConfig(
                        finalMsgSection.getString("void.knocked-by-player", "wizards.death.void.knocked_by_player"),
                        finalMsgSection.getString("void.knocked-by-monster", "wizards.death.void.knocked_by_monster"),
                        finalMsgSection.getString("void.fell", "wizards.death.void.fell")
                ),
                pvpConfig,
                new DamageConfig.MonsterConfig(
                        finalMsgSection.getString("monster.melee", "wizards.death.monster.melee"),
                        finalMsgSection.getString("monster.ranged", "wizards.death.monster.ranged")
                ),
                spellConfig,
                finalMsgSection.getString("default", "wizards.death.default")
        );

        return new DamageConfig(logTimeoutMillis, assistThreshold, justNowMillis, meleeRange, armorSounds, deathMessageConfig);
    }

    public void damage(LivingEntity entity, DamageTick damageTick) {
        if (entity == null || entity.isDead()) {
            return;
        }

        CustomDamageEvent event = new CustomDamageEvent(entity, damageTick);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }

        double rawDamageForCalc = damageTick.getFinalDamage();
        logTick(entity, damageTick);

        double finalDamageToApply = rawDamageForCalc;

        if (entity instanceof Player victimPlayer) {
            if (victimPlayer.getGameMode() == GameMode.CREATIVE) {
                return;
            }
            finalDamageToApply = calculatePlayerDamage(victimPlayer, rawDamageForCalc);
        }

        applyKnockback(entity, damageTick, rawDamageForCalc);

        if (finalDamageToApply > 0) {
            entity.playEffect(EntityEffect.HURT);
            trackStats(entity, damageTick, finalDamageToApply);
        }

        entity.setHealth(Math.max(0, entity.getHealth() - finalDamageToApply));
    }

    private double calculatePlayerDamage(Player victimPlayer, double rawDamage) {
        AttributeInstance armorAttribute = victimPlayer.getAttribute(Attribute.GENERIC_ARMOR);
        AttributeInstance toughnessAttribute = victimPlayer.getAttribute(Attribute.GENERIC_ARMOR_TOUGHNESS);
        PotionEffect resistanceEffect = victimPlayer.getPotionEffect(PotionEffectType.DAMAGE_RESISTANCE);

        double armorPoints = (armorAttribute != null) ? armorAttribute.getValue() : 0;
        double armorToughness = (toughnessAttribute != null) ? toughnessAttribute.getValue() : 0;
        int resistance = (resistanceEffect != null) ? resistanceEffect.getAmplifier() : 0;

        return EntityUtil.calculateDamage(
                rawDamage,
                armorPoints, armorToughness,
                resistance,
                EntityUtil.getEPF(victimPlayer.getInventory())
        );
    }
    
    private void applyKnockback(LivingEntity entity, DamageTick tick, double rawDamage) {
        if (tick.getKnockbackOrigin() == null && !(tick instanceof PlayerDamageTick) && tick.getKnockbackModifiers().isEmpty()) {
            return;
        }

        double kbStrength = Math.log10(Math.max(2, rawDamage));
        for (double mod : tick.getKnockbackModifiers().values()) {
            kbStrength *= mod;
        }

        Location origin = Optional.ofNullable(tick.getKnockbackOrigin())
                .orElseGet(() -> tick instanceof PlayerDamageTick pdt && pdt.getPlayer() != null ? pdt.getPlayer().getLocation() : entity.getLocation());

        // This logic is now cleaner. The trajectory is purely horizontal.
        // Vertical knockback is handled entirely by the MathUtil.setVelocity method.
        Vector trajectory = MathUtil.getTrajectory2D(origin, entity.getLocation());
        trajectory.multiply(0.6 * kbStrength);

        double velocity = 0.2 + trajectory.length() * 0.8;
        MathUtil.setVelocity(entity, trajectory, velocity, false, 0, Math.abs(0.2 * kbStrength), 0.4 + (0.04 * kbStrength), true);
    }
    
    private void trackStats(LivingEntity victim, DamageTick tick, double finalDamage) {
        if (victim instanceof Player victimPlayer) {
            plugin.getStatsManager().incrementStat(victimPlayer, StatType.DAMAGE_TAKEN, finalDamage);
        }

        if (tick instanceof PlayerDamageTick pdt) {
            Player attacker = pdt.getPlayer();
            if (attacker != null && !attacker.getUniqueId().equals(victim.getUniqueId())) {
                plugin.getStatsManager().incrementStat(attacker, StatType.DAMAGE_DEALT, finalDamage);
            }
        }
    }

    public void logTick(LivingEntity entity, DamageTick newTickToLog) {
        UUID uuid = entity.getUniqueId();
        List<DamageTick> playerSpecificTicks = damageTicks.computeIfAbsent(uuid, k -> new ArrayList<>());

        cleanupListInPlace(playerSpecificTicks);

        Optional<DamageTick> existingTickToMerge = playerSpecificTicks.stream()
                .filter(existingTick -> existingTick.matches(newTickToLog))
                .findFirst();

        if (existingTickToMerge.isPresent()) {
            DamageTick foundTick = existingTickToMerge.get();
            foundTick.setDamage(foundTick.getFinalDamage() + newTickToLog.getFinalDamage());
            foundTick.setTimestamp(Instant.now());
        } else {
            playerSpecificTicks.add(newTickToLog);
        }
        playerSpecificTicks.sort(Comparator.naturalOrder());
    }

    private void cleanupListInPlace(List<DamageTick> ticksToClean) {
        if (ticksToClean == null) return;
        ticksToClean.removeIf(tick -> Duration.between(tick.getTimestamp(), Instant.now()).toMillis() > config.logTimeoutMillis());
    }

    public List<KillAssist> getPossibleAssists(UUID victimUUID, List<DamageTick> allTicks) {
        if (allTicks.isEmpty()) {
            return Collections.emptyList();
        }

        UUID killerId = null;
        if (allTicks.get(allTicks.size() - 1) instanceof PlayerDamageTick killingTick) {
            killerId = killingTick.getAttackerId();
        }

        final UUID finalKillerId = killerId;
        Map<UUID, Double> damageByPlayer = new HashMap<>();

        allTicks.stream()
                .filter(PlayerDamageTick.class::isInstance)
                .map(PlayerDamageTick.class::cast)
                .filter(pdt -> !pdt.getAttackerId().equals(victimUUID) && !pdt.getAttackerId().equals(finalKillerId))
                .forEach(pdt -> damageByPlayer.merge(pdt.getAttackerId(), pdt.getFinalDamage(), Double::sum));

        if (damageByPlayer.isEmpty()) {
            return Collections.emptyList();
        }

        double totalAssistDamage = damageByPlayer.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalAssistDamage == 0) {
            return Collections.emptyList();
        }

        List<KillAssist> assists = new ArrayList<>();
        for (Map.Entry<UUID, Double> entry : damageByPlayer.entrySet()) {
            Player attacker = Bukkit.getPlayer(entry.getKey());
            if (attacker == null) continue;

            double damageDealt = entry.getValue();
            int percentage = (int) ((damageDealt / totalAssistDamage) * 100);

            if (percentage >= config.assistThresholdPercentage()) {
                assists.add(new KillAssist(attacker, damageDealt, percentage));
            }
        }

        assists.sort(Collections.reverseOrder());
        return assists;
    }
    
    public List<Component> getDamageSummary(Player viewer, List<DamageTick> ticksToSummarize) {
        LanguageManager lang = plugin.getLanguageManager();
        List<Component> components = new ArrayList<>();
        
        for (DamageTick tick : ticksToSummarize) {
            Component summaryComponent = tick.getSingleLineSummary(viewer, lang, this);
            String damageStr = DAMAGE_FORMAT.format(tick.getFinalDamage());
            Component timeDiffComponent = tick.getTimeDifferenceComponent(viewer, lang, this);

            components.add(
                lang.getTranslated(viewer, "wizards.damage.summary.line",
                    Placeholder.unparsed("damage", damageStr),
                    Placeholder.component("summary", summaryComponent),
                    Placeholder.component("time_diff", timeDiffComponent)
                )
            );
        }
        return components;
    }

    public DamageTick getLastLoggedTick(UUID uuid) {
        List<DamageTick> cleanedDamageTicks = getLoggedTicks(uuid);
        // Correctly get the last element from the cleaned list
        return cleanedDamageTicks.isEmpty() ? null : cleanedDamageTicks.get(cleanedDamageTicks.size() - 1);
    }

    public List<DamageTick> getLoggedTicks(UUID uuid) {
        List<DamageTick> actualTicks = damageTicks.get(uuid);
        if (actualTicks != null && !actualTicks.isEmpty()) {
            List<DamageTick> ticksCopy = new ArrayList<>(actualTicks);
            cleanupListInPlace(ticksCopy);
            return ticksCopy;
        }
        return Collections.emptyList();
    }

    public void dump(UUID uuid) {
        damageTicks.remove(uuid);
    }
    
    public WizardsPlugin getPlugin() { return plugin; }
    public DamageConfig getConfig() { return config; }
}