package dev.thomashanson.wizards.game.spell.types;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.event.CustomDamageEvent;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.spell.Spell;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class SpellFocus extends Spell implements Tickable {

    private final Map<UUID, FocusInstance> activeFocuses = new ConcurrentHashMap<>();

    public SpellFocus(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        if (activeFocuses.containsKey(player.getUniqueId())) {
            player.sendMessage(languageManager.getTranslated(player, "wizards.spell.focus.alreadyActive"));
            return false;
        }
        activeFocuses.put(player.getUniqueId(), new FocusInstance(this, player, level));
        return true;
    }

    @Override
    public void tick(long gameTick) {
        if (activeFocuses.isEmpty()) return;
        activeFocuses.values().removeIf(FocusInstance::tick);
    }

    @Override
    public int getTickInterval() {
        return 10; // Run twice per second
    }

    @Override
    public void cleanup() {
        activeFocuses.values().forEach(focus -> focus.endFocus(false, true));
        activeFocuses.clear();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onCustomDamage(CustomDamageEvent event) {
        // --- Attacker Logic ---
        if (event.getDamageTick() instanceof CustomDamageTick customTick) {
            Player attacker = customTick.getPlayer();
            if (attacker != null) {
                FocusInstance instance = activeFocuses.remove(attacker.getUniqueId());
                if (instance != null) {
                    double multiplier = instance.getPowerMultiplier();
                    event.setDamage(event.getDamage() * multiplier);
                    attacker.sendMessage(languageManager.getTranslated(attacker, "wizards.spell.focus.unleashed",
                        Placeholder.unparsed("power", String.format("%.1fx", multiplier))
                    ));
                    instance.endFocus(false, false);
                }
            }
        }

        // --- Victim Logic ---
        if (event.getVictim() instanceof Player victim) {
            FocusInstance instance = activeFocuses.get(victim.getUniqueId());
            if (instance != null && event.getDamageTick() instanceof CustomDamageTick) {
                victim.sendMessage(languageManager.getTranslated(victim, "wizards.spell.focus.shattered"));
                instance.endFocus(true, false);
                activeFocuses.remove(victim.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        FocusInstance instance = activeFocuses.remove(event.getPlayer().getUniqueId());
        if (instance != null) {
            instance.endFocus(false, true);
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (activeFocuses.containsKey(event.getPlayer().getUniqueId())) {
            event.getPlayer().sendMessage(languageManager.getTranslated(event.getPlayer(), "wizards.spell.focus.cannotConsume"));
            event.setCancelled(true);
        }
    }

    private static class FocusInstance {
        final SpellFocus parent;
        final Player player;
        final Wizard wizard;
        final Instant startTime;
        final int originalHunger;
        final int level;

        final long maxDurationMillis;
        final int stunDurationTicks;
        final int stunSlownessAmplifier;

        FocusInstance(SpellFocus parent, Player player, int level) {
            this.parent = parent;
            this.player = player;
            this.wizard = parent.getWizard(player).orElse(null);
            this.startTime = Instant.now();
            this.originalHunger = player.getFoodLevel();
            this.level = level;

            this.maxDurationMillis = (long) (parent.getStat("max-duration-seconds", level, 10.0) * 1000);
            this.stunDurationTicks = (int) parent.getStat("stun-duration-ticks", level, 60.0);
            this.stunSlownessAmplifier = (int) parent.getStat("stun-slowness-amplifier", level, 3.0) - 1;

            if (wizard != null) {
                wizard.setManaRegenMultiplier((float) parent.getStat("mana-regen-multiplier", level, 3.0), true);
            }
            player.setFoodLevel(6);
            player.sendMessage(parent.languageManager.getTranslated(player, "wizards.spell.focus.activated"));
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0F, 1.3F);
        }

        boolean tick() {
            if (!player.isOnline() || Duration.between(startTime, Instant.now()).toMillis() > maxDurationMillis) {
                if (player.isOnline()) {
                    player.sendMessage(parent.languageManager.getTranslated(player, "wizards.spell.focus.faded"));
                }
                endFocus(false, false);
                return true;
            }

            Location center = player.getLocation().add(0, 1.2, 0);
            center.getWorld().spawnParticle(Particle.REDSTONE, center, 15, 0.8, 0.8, 0.8, new Particle.DustOptions(Color.YELLOW, 1F));
            center.getWorld().playSound(center, Sound.BLOCK_CONDUIT_AMBIENT_SHORT, 0.7F, 1.5F);
            
            return false;
        }

        void endFocus(boolean applyStun, boolean isCleanup) {
            if (wizard != null) wizard.revert();
            if (player.isOnline()) player.setFoodLevel(originalHunger);

            if (applyStun) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, stunDurationTicks, stunSlownessAmplifier));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0F, 0.8F);
            } else if (!isCleanup) {
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8F, 1.2F);
            }
        }
        
        double getPowerMultiplier() {
            long seconds = Duration.between(startTime, Instant.now()).getSeconds();
            
            // CORRECTED LOGIC: The semicolon is removed.
            if (seconds >= parent.getStat("tier-3-seconds", level, 6.0))
                return parent.getStat("tier-3-multiplier", level, 2.5);
            if (seconds >= parent.getStat("tier-2-seconds", level, 4.0))
                return parent.getStat("tier-2-multiplier", level, 2.0);
            if (seconds >= parent.getStat("tier-1-seconds", level, 2.0)) 
                return parent.getStat("tier-1-multiplier", level, 1.5);
            
            return 1.0;
        }
    }
}