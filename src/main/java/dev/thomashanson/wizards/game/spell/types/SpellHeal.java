package dev.thomashanson.wizards.game.spell.types;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.spell.Spell;

public class SpellHeal extends Spell implements Tickable {

    private final List<HealingInstance> activeHeals = new ArrayList<>();

    public SpellHeal(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        AttributeInstance healthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);

        if (healthAttribute != null && player.getHealth() >= healthAttribute.getValue()) {
            return false;
        }

        int durationTicks = (int) (getStat("duration", level) * 20);
        int amplifier = (int) getStat("regeneration-level", level) - 1; // Potion effects are 0-indexed

        if (durationTicks <= 0) {
            return false;
        }

        // 1. Apply the potion effect
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, durationTicks, amplifier, false, true, true));

        // 2. Play the initial "burst" effect
        int particleCount = (int) getStat("particle-count", level);
        player.getWorld().spawnParticle(Particle.HEART, player.getEyeLocation(), particleCount, 0.8F, 0.4F, 0.8F, 0);

        // 3. Register the persistent effect instance
        activeHeals.add(new HealingInstance(player, durationTicks));

        return true;
    }

    @Override
    public void tick(long gameTick) {
        if (activeHeals.isEmpty()) return;
        // Tick all active heals and remove them if they are finished
        activeHeals.removeIf(HealingInstance::tick);
    }

    @Override
    public void cleanup() {
        activeHeals.clear();
    }

    /**
     * Manages the persistent visual and audio effects for an active heal.
     */
    private static class HealingInstance {
        final Player player;
        final int durationTicks;
        int ticksLived = 0;
        double angle = 0; // Stores the current angle for the spiral

        HealingInstance(Player player, int durationTicks) {
            this.player = player;
            this.durationTicks = durationTicks;
        }

        /**
         * Ticks the effect.
         * @return true if the effect is finished and should be removed.
         */
        boolean tick() {
            ticksLived++;
            // Stop if the player logs off or the duration ends
            if (!player.isOnline() || ticksLived > durationTicks) {
                return true;
            }

            // --- Persistent Audio ---
            if (ticksLived % 30 == 0) { // Play a soft "hum" every 1.5 seconds
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.2F, 1.8F);
            }

            // --- Persistent Visual ---
            // Only spawn particles every 4 ticks to keep it subtle
            if (ticksLived % 4 == 0) {
                // This creates a flat spiral at the player's feet
                angle += Math.PI / 8; // Adjusts the tightness of the spiral
                double radius = 0.8;
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;

                Location loc = player.getLocation().add(x, 0.3, z);
                
                // --- PARTICLE CHANGED ---
                player.getWorld().spawnParticle(Particle.HEART, loc, 1, 0, 0, 0, 0);
            }

            return false;
        }
    }
}