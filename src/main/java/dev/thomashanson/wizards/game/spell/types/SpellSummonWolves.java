package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.MonsterDamageTick;
import dev.thomashanson.wizards.event.CustomDamageEvent;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.mode.GameTeam;
import dev.thomashanson.wizards.game.spell.Spell;

public class SpellSummonWolves extends Spell implements Tickable {

    private final List<SummonedWolf> activeWolves = new ArrayList<>();

    public SpellSummonWolves(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        int wolfCount = (int) getStat("wolf-count", level, 3.0);
        long durationMillis = (long) (getStat("duration-seconds", level, 30.0) * 1000L);

        for (int i = 0; i < wolfCount; i++) {
            Location spawnLoc = player.getLocation().add(
                    ThreadLocalRandom.current().nextDouble(-1, 1), 0.5,
                    ThreadLocalRandom.current().nextDouble(-1, 1)
            );

            Wolf wolf = player.getWorld().spawn(spawnLoc, Wolf.class, newWolf -> configureWolf(newWolf, player, level));
            activeWolves.add(new SummonedWolf(wolf, Instant.now().plusMillis(durationMillis)));
        }

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WOLF_HOWL, 1.2F, 1.0F);
        return true;
    }

    private void configureWolf(Wolf wolf, Player owner, int level) {
        wolf.setOwner(owner);
        wolf.setTamed(true);
        wolf.setSitting(false);
        wolf.setBreed(false);

        AttributeInstance healthAttr = Objects.requireNonNull(wolf.getAttribute(Attribute.GENERIC_MAX_HEALTH));
        healthAttr.setBaseValue(getStat("wolf-health", level, 4.0));
        wolf.setHealth(healthAttr.getBaseValue());

        int speedAmplifier = (int) getStat("wolf-speed-amplifier", level, 1.0) - 1;
        int durationTicks = (int) (getStat("duration-seconds", level, 30.0) * 20);
        if (speedAmplifier >= 0) {
            wolf.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, speedAmplifier));
        }
    }

    @Override
    public void tick(long gameTick) {
        activeWolves.removeIf(summon -> {
            if (!summon.wolf.isValid() || Instant.now().isAfter(summon.expiry)) {
                if (summon.wolf.isValid()) summon.wolf.remove();
                return true;
            }
            return false;
        });
    }

    @Override
    public void cleanup() {
        activeWolves.forEach(summon -> {
            if (summon.wolf.isValid()) summon.wolf.remove();
        });
        activeWolves.clear();
    }

    @EventHandler
    public void onDamage(CustomDamageEvent event) {
        if (!(event.getDamageTick() instanceof MonsterDamageTick tick) || !(tick.getEntity() instanceof Wolf wolf)) return;
        
        activeWolves.stream().filter(summon -> summon.wolf.equals(wolf)).findFirst().ifPresent(summon -> {
            tick.addDamageModifier("Summoned Wolf", getStat("damage-modifier", 0, 0.3));
            if (wolf.getOwner() instanceof Player owner) {
                tick.setEntity(owner);
            }
        });
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Wolf wolf) || !(event.getTarget() instanceof Player target)) return;
        if (!(wolf.getOwner() instanceof Player owner)) return;

        getGame().ifPresent(game -> {
            if (game.getRelation(owner, target) == GameTeam.TeamRelation.ALLY) {
                event.setCancelled(true);
            }
        });
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Wolf) {
            activeWolves.removeIf(summon -> summon.wolf.equals(event.getEntity()));
        }
    }

    private record SummonedWolf(Wolf wolf, Instant expiry) {}
}