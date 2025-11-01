package dev.thomashanson.wizards.game.spell.types;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;

public class SpellSpeedBoost extends Spell implements Tickable {

    private final List<SpeedInstance> activeBoosts = new ArrayList<>();

    public SpellSpeedBoost(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        StatContext context = StatContext.of(level);
        int durationTicks = (int) (getStat("duration-seconds", level) * 20);
        int amplifier = (int) getStat("speed-amplifier", level) - 1; // Potion effects are 0-indexed

        if (durationTicks <= 0) {
            return false;
        }

        // 1. Apply the potion effect
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, amplifier, false, true, true));

        // --- NEW: Activation Effects ---

        // 2. Play Sound
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0F, 1.9F);

        // 3. Play Visuals
        Location loc = player.getLocation().add(0, 0.5, 0);
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 3, 0.5, 0.5, 0.5, 0);
        player.getWorld().spawnParticle(Particle.CLOUD, loc, 15, 0.5, 0.2, 0.5, 0.01);
        
        // 4. Register the persistent effect instance
        activeBoosts.add(new SpeedInstance(player, durationTicks));
        
        return true;
    }

    @Override
    public void tick(long gameTick) {
        if (activeBoosts.isEmpty()) return;
        // Tick all active boosts and remove them if they are finished
        activeBoosts.removeIf(SpeedInstance::tick);
    }

    @Override
    public void cleanup() {
        activeBoosts.clear();
    }

    /**
     * Manages the persistent visual effects for an active speed boost.
     */
    private static class SpeedInstance {
        final Player player;
        final int durationTicks;
        int ticksLived = 0;

        SpeedInstance(Player player, int durationTicks) {
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
            
            // --- Persistent Visual ---
            // Spawn "wind" or "dust" particles at the player's feet
            if (ticksLived % 4 == 0) {
                Location loc = player.getLocation().add(0, 0.2, 0);
                player.getWorld().spawnParticle(Particle.CLOUD, loc, 1, 0.2, 0.1, 0.2, 0.0);
            }

            return false;
        }
    }
}