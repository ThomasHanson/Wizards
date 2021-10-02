package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.util.BlockUtil;
import net.md_5.bungee.api.chat.TranslatableComponent;
import org.apache.commons.lang.Validate;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.*;

public class SpellTrapRune extends Spell {

    private Map<UUID, List<TrapRune>> runes = new HashMap<>();

    @Override
    public void castSpell(Player player, int level) {

        List<Block> list = player.getLastTwoTargetBlocks(null, (level * 4) + 4);

        if (list.size() > 1) {

            Location location = list.get(0).getLocation().add(0.5, 0, 0.5);

            if (location.getBlock().getRelative(BlockFace.DOWN).getType() == Material.AIR)
                return;

            TrapRune newRune = new TrapRune(getGame(), player, location, level);

            if (newRune.isInvalid()) {

                TranslatableComponent invalidRune = new TranslatableComponent("wizards.invalidRune");
                player.spigot().sendMessage(invalidRune);

                return;
            }

            newRune.createParticles();

            List<TrapRune> existingRunes = runes.getOrDefault(player.getUniqueId(), new ArrayList<>());

            if (existingRunes.size() >= 3)
                existingRunes.remove(0);

            for (TrapRune existingRune : existingRunes) {

                if (existingRune.equals(newRune))
                    continue;

                if (newRune.location.distance(existingRune.location) <= newRune.size) {
                    newRune.explodeTrap();
                    existingRune.explodeTrap();
                    return;
                }
            }

            existingRunes.add(newRune);
            runes.put(player.getUniqueId(), existingRunes);
        }
    }

    public static class TrapRune {

        private Wizards game;

        private Location location;
        private float size;
        private Player owner;
        private int ticksLived;

        public boolean updateRune() {

            if (!owner.isOnline() || owner.getGameMode() == GameMode.SPECTATOR) {
                return true;

            } else if (ticksLived++ > 2000) {
                return true;

            } else {

                if (ticksLived <= 100) {

                    if (ticksLived % 15 == 0)
                        createParticles();

                    if (ticksLived == 100) {

                        //UtilParticle.PlayParticle(ParticleType.FIREWORKS_SPARK, location, 0, size / 4, 0, size / 4,
                        //      (int) (size * 10),
                        //    ViewDist.LONG, UtilServer.getPlayers());
                    }

                } else {

                    if (isInvalid()) {

                        explodeTrap();
                        return true;

                    } else {

                        for (Player player : game.getPlayers(true)) {

                            if (isInTrap(player.getLocation())) {
                                explodeTrap();
                                return true;
                            }
                        }
                    }
                }
            }

            return false;
        }

        TrapRune(Wizards game, Player owner, Location location, int spellLevel) {
            this.game = game;
            this.owner = owner;
            this.location = location;
            this.size = Math.max(1, spellLevel * 0.8F);
        }

        void createParticles() {

            for (Location location : getBox())
                for (double y = 0; y < 1; y += 0.2)
                    if (location.getWorld() != null)
                        location.getWorld().spawnParticle(Particle.SMOKE_NORMAL, location, 1);
        }

        List<Location> getBox() {

            List<Location> boxCorners = getBoxCorners();
            List<Location> returns = new ArrayList<>();

            for (int i = 0; i < boxCorners.size(); i++) {

                int a = i + 1 >= boxCorners.size() ? 0 : i + 1;

                returns.addAll(BlockUtil.getLinesDistancedPoints(boxCorners.get(i), boxCorners.get(a), 0.3));
                returns.add(boxCorners.get(i));
            }

            return returns;
        }

        List<Location> getBoxCorners() {

            List<Location> boxPoints = new ArrayList<>();

            boxPoints.add(location.clone().add(-size, 0, -size));
            boxPoints.add(location.clone().add(-size, 0, size));
            boxPoints.add(location.clone().add(size, 0, -size));
            boxPoints.add(location.clone().add(size, 0, size));

            return boxPoints;
        }

        boolean isInTrap(Location location) {

            if (location.getX() >= this.location.getX() - size && location.getX() <= this.location.getX() + size)
                if (location.getZ() >= this.location.getZ() - size && location.getZ() <= this.location.getZ() + size)
                    return location.getY() >= this.location.getY() - 0.1 && location.getY() <= this.location.getY() + 0.9;

            return false;
        }

        boolean isInvalid() {
            return !location.getBlock().getType().isSolid() &&
                    !location.getBlock().getRelative(BlockFace.DOWN).getType().isSolid();
        }

        void explodeTrap() {

            Validate.notNull(location.getWorld());

            location.getWorld().playSound(location, Sound.ENTITY_WITHER_SHOOT, 5, size * 2);

            /*
            CustomExplosion explosion = new CustomExplosion(game.getArcadeManager().GetDamage(), game.getArcadeManager()
                    .GetExplosion(), location.clone().add(0, 0.3, 0), (float) size * 1.2F, "Trap Rune");

            explosion.setPlayer(owner, true);

            explosion.setBlockExplosionSize((float) size * 2F);

            explosion.setFallingBlockExplosion(true);

            explosion.setDropItems(false);

            explosion.setMaxDamage((spellLevel * 4) + 6);

            explosion.explode();

             */

            for (Location location : getBox())
                for (double y = 0; y < 1; y += 0.2)
                    if (location.getWorld() != null)
                        location.getWorld().playEffect(location, Effect.SMOKE, 1, 30);
        }
    }
}