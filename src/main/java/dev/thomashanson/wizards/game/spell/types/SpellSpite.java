package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.event.CustomDamageEvent;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.spell.Spell;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class SpellSpite extends Spell implements Tickable {

    private final List<SpiteInstance> activeAuras = new ArrayList<>();
    private final List<DebuffedPlayer> debuffedPlayers = new ArrayList<>();

    public SpellSpite(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        if (activeAuras.stream().anyMatch(aura -> aura.caster.getUniqueId().equals(player.getUniqueId()))) {
            return false;
        }
        activeAuras.add(new SpiteInstance(this, player, level));
        return true;
    }

    @Override
    public void tick(long gameTick) {
        activeAuras.removeIf(SpiteInstance::tick);
        debuffedPlayers.removeIf(DebuffedPlayer::isExpired);
    }
    
    public double getManaCostModifier(Player player) {
        return debuffedPlayers.stream()
                .filter(debuff -> debuff.playerUUID.equals(player.getUniqueId()))
                .mapToDouble(DebuffedPlayer::getExtraCost)
                .sum();
    }

    @EventHandler
    public void onDamage(CustomDamageEvent event) {
        if (!(event.getVictim() instanceof Player victim) || !(event.getDamageTick() instanceof CustomDamageTick tick)) return;
        
        Player attacker = tick.getPlayer();
        if (attacker == null || attacker.equals(victim)) return;

        activeAuras.stream()
            .filter(aura -> aura.caster.getUniqueId().equals(victim.getUniqueId()))
            .findFirst()
            .ifPresent(aura -> {
                aura.markForRemoval();
                
                long debuffMillis = (long) (getStat("debuff-duration-seconds", aura.level, 60.0) * 1000L);
                double extraCost = getStat("extra-mana-cost", aura.level, 8.0);

                debuffedPlayers.add(new DebuffedPlayer(attacker.getUniqueId(), Instant.now().plusMillis(debuffMillis), extraCost));
                
                attacker.sendMessage(languageManager.getTranslated(attacker, "wizards.spell.spite.applied.damager.mana_cost",
                    Placeholder.unparsed("cost", String.valueOf((int)extraCost)),
                    Placeholder.unparsed("duration", String.valueOf(debuffMillis / 1000L))
                ));
                victim.sendMessage(languageManager.getTranslated(victim, "wizards.spell.spite.applied.victim",
                    Placeholder.unparsed("damager_name", attacker.getName())
                ));
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_WITHER_HURT, 1F, 1.2F);
            });
    }

    @Override
    public void cleanup() {
        activeAuras.clear();
        debuffedPlayers.clear();
    }
    
    private static class SpiteInstance {
        final SpellSpite parent;
        final Player caster;
        final int level;
        final Instant expiry;
        
        final int particleCount;
        final double auraRadius;
        
        boolean remove = false;

        SpiteInstance(SpellSpite parent, Player caster, int level) {
            this.parent = parent;
            this.caster = caster;
            this.level = level;

            long durationMillis = (long) (parent.getStat("aura-duration-seconds", level, 3.0) * 1000L);
            this.expiry = Instant.now().plusMillis(durationMillis);
            
            this.particleCount = (int) parent.getStat("particle-count", level, 5.0);
            this.auraRadius = parent.getStat("aura-radius", level, 0.8);
            
            caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.7F, 0.5F);
        }

        boolean tick() {
            if (remove || !caster.isOnline() || Instant.now().isAfter(expiry)) {
                return true;
            }
            renderParticles();
            return false;
        }
        
        void renderParticles() {
            Location loc = caster.getLocation();
            for (int i = 0; i < particleCount; i++) {
                double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                double x = auraRadius * Math.cos(angle);
                double z = auraRadius * Math.sin(angle);
                double y = ThreadLocalRandom.current().nextDouble(0.2, 1.8);
                loc.getWorld().spawnParticle(Particle.SQUID_INK, loc.getX() + x, loc.getY() + y, loc.getZ() + z, 0, 0, 0, 0, 0.01);
            }
        }
        
        void markForRemoval() { this.remove = true; }
    }

    private record DebuffedPlayer(UUID playerUUID, Instant expiry, double extraCost) {
        boolean isExpired() { return Instant.now().isAfter(expiry); }
        double getExtraCost() { return isExpired() ? 0 : extraCost; }
    }
}