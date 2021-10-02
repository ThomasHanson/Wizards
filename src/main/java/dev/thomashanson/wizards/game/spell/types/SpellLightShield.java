package dev.thomashanson.wizards.game.spell.types;

import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.SpellType;
import dev.thomashanson.wizards.util.BlockUtil;
import dev.thomashanson.wizards.util.EntityUtil;
import dev.thomashanson.wizards.util.MathUtil;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SpellLightShield extends Spell {

    private static class ShieldData {

        private double health;
        private Location location;

        private final ArmorStand stand;
        private BlockFace face;

        ShieldData(Location location) {

            this.health = 10.0;
            this.location = location;

            Validate.notNull(location.getWorld());

            this.stand = location.getWorld().spawn(location, ArmorStand.class, spawnedStand -> {
                spawnedStand.setVisible(false);
                spawnedStand.setBasePlate(false);
                spawnedStand.setArms(false);
            });
        }

        double getHealth() {
            return health;
        }

        void setHealth(double health) {
            this.health = health;
        }

        Location getLocation() {
            return location;
        }

        public void setLocation(Location location) {
            this.location = location;
        }

        ArmorStand getStand() {
            return stand;
        }

        public BlockFace getFace() {
            return face;
        }

        public void setFace(BlockFace face) {
            this.face = face;
        }
    }

    private static final Set<SpellType> DEFLECTED_SPELLS = Stream.of (

            SpellType.MANA_BOLT,
            SpellType.FIREBALL,
            SpellType.RAINBOW_BEAM,
            SpellType.ICE_SHARDS,
            SpellType.SPECTRAL_ARROW,
            SpellType.NAPALM,
            SpellType.ICE_PRISON,
            SpellType.MANA_BOMB,
            SpellType.BOULDER_TOSS,
            SpellType.SCARLET_STRIKES,
            SpellType.GRAPPLING_BEAM,
            SpellType.FROSTBITE,
            SpellType.TORNADO

    ).collect(Collectors.toSet());

    private final Map<UUID, ShieldData> shields = new HashMap<>();

    /*
     * Deflected spells cannot be further deflected.
     */

    @Override
    public void castSpell(Player player, int level) {

        shields.put(player.getUniqueId(), new ShieldData(player.getLocation()));

        Bukkit.getScheduler().scheduleSyncRepeatingTask(getGame().getPlugin(), () -> updateParticles(player), 0L, 1L);
    }

    private void damageShield(Player player, double damage) {

        if (!shields.containsKey(player.getUniqueId()))
            return;

        ShieldData data = shields.get(player.getUniqueId());

        Validate.notNull(data.getLocation().getWorld());
        data.getLocation().getWorld().playSound(data.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1F, 1F);

        /*
         * Calculating the damage based off armor.
         * The shield can block up to 6 damage and
         * has the defense equivalent of full gold.
         * We use this function to calculate how much
         * damage it should take with full armor.
         *
         * Gold armor has 11 armor points.
         * It does not have any toughness.
         * There is no resistance calculated.
         * There is no EPF calculated.
         *
         * Source: MC Wiki
         */
        double properDamage = EntityUtil.calculateDamage(damage, 11.0, 0, 0, 0);
        double newHealth = data.getHealth() - properDamage;

        if (newHealth > 0) {
            data.setHealth(data.getHealth() - properDamage);

        } else {
            data.getStand().remove();
            shields.remove(player.getUniqueId());
        }

        updateDisplay(player);
    }

    private void updateParticles(Player player) {

        if (!shields.containsKey(player.getUniqueId()))
            return;

        ShieldData data = shields.get(player.getUniqueId());
        ArmorStand stand = data.getStand();

        if (stand == null || !stand.isValid())
            return;

        int ticksLived = stand.getTicksLived();
        int maxTime = getSpellLevel(player) * 3; //(int) getValue(player, "Length (secs)");

        if ((ticksLived / 20) >= maxTime) {
            stand.remove();
            shields.remove(player.getUniqueId());
            return;
        }

        BlockFace facing = BlockUtil.getFace(player.getEyeLocation().getYaw());

        if (Arrays.stream(BlockUtil.AXIS).noneMatch(x -> x.equals(facing)))
            return;

        double circumference = 2 * Math.PI;
        double division = circumference / 100;
        double radius = 2;

        Location standEyeLocation = stand.getEyeLocation();
        Vector vector = standEyeLocation.getDirection().normalize();

        double nx = radius * vector.getX() + standEyeLocation.getX();
        double ny = radius * vector.getY() + standEyeLocation.getY();
        double nz = radius * vector.getZ() + standEyeLocation.getZ();

        Vector ya = MathUtil.getPerpendicular(vector, new Vector(0, 1, 0)).normalize();
        Vector xa = ya.getCrossProduct(vector).normalize();

        for (double theta = 0; theta < circumference; theta += division) {

            double
                    xb = 0,
                    yb = 0;

            double xi = xa.getX() * xb + ya.getX() * yb;
            double yi = xa.getY() * xb + ya.getY() * yb;
            double zi = xa.getZ() * xb + ya.getZ() * yb;

            double x = xi + nx;
            double y = yi + ny;
            double z = zi + nz;

            player.spawnParticle(Particle.TOTEM, new Location(standEyeLocation.getWorld(), x, y, z), 1, 0, 0, 0, 0);
        }
    }

    private void updateDisplay(Player player) {

        // TODO: 8/18/21 might want to use packets for this too tbh

        player.setAbsorptionAmount (
                !shields.containsKey(player.getUniqueId()) ? 0 : shields.get(player.getUniqueId()).getHealth()
        );
    }
}