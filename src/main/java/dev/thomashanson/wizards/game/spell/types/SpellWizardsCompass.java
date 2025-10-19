package dev.thomashanson.wizards.game.spell.types;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.mode.GameTeam;
import dev.thomashanson.wizards.game.spell.Spell;

public class SpellWizardsCompass extends Spell implements Tickable {

    private final List<CompassInstance> activeCompasses = new ArrayList<>();

    public SpellWizardsCompass(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        activeCompasses.add(new CompassInstance(this, player, level));
        player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.5F, 1F);
        return true;
    }

    @Override
    public void tick(long gameTick) {
        if (activeCompasses.isEmpty()) return;
        activeCompasses.removeIf(CompassInstance::tick);
    }

    @Override
    public void cleanup() {
        activeCompasses.clear();
    }

    private static class CompassInstance {
        final SpellWizardsCompass parent;
        final Player caster;
        final int level;
        final List<ParticleStrand> strands = new ArrayList<>();

        final int particleLifespan;

        CompassInstance(SpellWizardsCompass parent, Player caster, int level) {
            this.parent = parent;
            this.caster = caster;
            this.level = level;

            this.particleLifespan = (int) parent.getStat("particle-lifespan-ticks", level, 50.0);

            initializeStrands();
        }

        void initializeStrands() {
            parent.getGame().ifPresent(game -> game.getPlayers(true).stream()
                    .filter(target -> game.getRelation(caster, target) == GameTeam.TeamRelation.ENEMY)
                    .forEach(target -> {
                        Color color = Color.fromBGR(ThreadLocalRandom.current().nextInt(256), ThreadLocalRandom.current().nextInt(256), ThreadLocalRandom.current().nextInt(256));
                        strands.add(new ParticleStrand(parent, caster, target, color, level));
                    }));
        }

        boolean tick() {
            if (!caster.isOnline() || strands.isEmpty()) {
                return true;
            }
            strands.forEach(strand -> strand.tick(particleLifespan));
            strands.removeIf(ParticleStrand::isDone);
            return strands.isEmpty();
        }
    }

    private static class ParticleStrand {
        final SpellWizardsCompass parent;
        final Player caster;
        final Player target;
        final Color color;
        final int level; // Pass level down
        final List<ParticlePoint> points = new ArrayList<>();
        boolean done = false;

        ParticleStrand(SpellWizardsCompass parent, Player caster, Player target, Color color, int level) {
            this.parent = parent;
            this.caster = caster;
            this.target = target;
            this.color = color;
            this.level = level;
        }

        void tick(int maxAge) {
            if (!target.isOnline() || !target.getWorld().equals(caster.getWorld())) {
                done = true;
                return;
            }

            double speed = parent.getStat("particle-speed", level, 0.2);
            Vector trajectory = target.getEyeLocation().toVector().subtract(caster.getEyeLocation().toVector()).normalize().multiply(speed);

            Location newPointLoc = caster.getEyeLocation().add(trajectory);
            points.add(new ParticlePoint(newPointLoc));

            points.forEach(point -> point.tick(color));
            points.removeIf(point -> point.isExpired(maxAge));

            if (caster.getLocation().distanceSquared(target.getLocation()) < 4 && points.isEmpty()) {
                done = true;
            }
        }

        boolean isDone() {
            return done;
        }
    }

    private static class ParticlePoint {
        final Location location;
        int age = 0;
        ParticlePoint(Location location) { this.location = location; }
        void tick(Color color) {
            age++;
            location.getWorld().spawnParticle(Particle.REDSTONE, location, 1, 0, 0, 0, 0, new Particle.DustOptions(color, 1.0F));
        }
        boolean isExpired(int maxAge) { return age > maxAge; }
    }
}