package dev.thomashanson.wizards.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class EntityUtil {

    public static String getEntityName(Entity entity) {

        return entity.getName();

        // } else {

        //     String name = entity.getType().name();
        //     name = name.replace("_", " ");
        //     return name;
        // }
    }

    public static Map<LivingEntity, Double> getInRadius(Location location, double radius) {
        Map<LivingEntity, Double> entities = new HashMap<>();
        World world = location.getWorld();
        if (world == null) return entities; // Safety check

        for (Entity entity : world.getEntities()) {
            // Use a positive check with the pattern
            if (entity instanceof LivingEntity livingEntity) {
                // Check for the spectator case using another pattern
                if (livingEntity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) {
                    continue; // Skip spectators
                }

                // No more casting is needed here
                double offset = location.distance(livingEntity.getLocation());
                if (offset < radius) {
                    entities.put(livingEntity, 1 - (offset / radius));
                }
            }
        }
        return entities;
    }

    public static ArmorStand makeProjectile(Location location, Material material) {

        return Objects.requireNonNull(location.getWorld()).spawn(location.subtract(0, 1, 0), ArmorStand.class, spawnedStand -> {

            spawnedStand.setInvisible(true);
            spawnedStand.setBasePlate(false);
            spawnedStand.setSmall(true);

            Objects.requireNonNull(spawnedStand.getEquipment()).setHelmet(new ItemStack(material));
        });
    }

    public static Firework launchFirework(Location location, FireworkEffect effect, Vector vector, int power) {

        if (location.getWorld() == null)
            return null;

        Firework firework = location.getWorld().spawn(location, Firework.class);
        FireworkMeta data = firework.getFireworkMeta();

        data.clearEffects();
        data.setPower(power);
        data.addEffect(effect);

        firework.setFireworkMeta(data);

        if (vector != null)
            firework.setVelocity(vector);

        return firework;
    }

    public static void displayProgress(Player player, String prefix, double amount, String suffix) {

        final int bars = 24;
        int greenBars = (int) (bars * Math.min(1.0, amount)); // Ensure amount is not > 1
        String greenSection = "\u258c".repeat(greenBars);
        String redSection = "\u258c".repeat(bars - greenBars);

        Component progressBar = MiniMessage.miniMessage().deserialize("<green>" + greenSection + "<red>" + redSection);

        Component prefixComponent = prefix == null ? Component.empty() : Component.text(prefix + " ");
        Component suffixComponent = suffix == null ? Component.empty() : Component.text(" " + suffix);

        player.sendActionBar(prefixComponent.append(progressBar).append(suffixComponent));
    }

    public static void resetPlayer(Player player, GameMode gameMode) {

        player.setGameMode(gameMode);
        player.setAllowFlight(gameMode == GameMode.SPECTATOR);
        player.setWalkSpeed(0.2F);
        player.setFlySpeed(0.1F);

        PlayerInventory inventory = player.getInventory();

        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        player.setItemOnCursor(null);

        player.setSprinting(false);
        player.setSneaking(false);

        player.setFoodLevel(20);
        player.setSaturation(3F);
        player.setExhaustion(0F);

        AttributeInstance healthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);

        if (healthAttribute != null) {
            healthAttribute.setBaseValue(20.0);
            player.setHealth(healthAttribute.getBaseValue());
        }

        player.setFireTicks(0);
        player.setFallDistance(0F);

        player.eject();
        player.leaveVehicle();

        player.setLevel(0);
        player.setExp(0F);

        for (PotionEffect potion : player.getActivePotionEffects())
            player.removePotionEffect(potion.getType());

        player.setInvisible(false);

        player.saveData();
    }

    public static Entity getEntityInSight(
            Player player, int rangeToScan, boolean avoidNonLiving,
            boolean lineOfSight, float expandBoxesPercentage
    ) {
        Location observerPos = player.getEyeLocation();
        Vector observerDir = observerPos.getDirection();
        Vector observerEnd = observerPos.toVector().add(observerDir.multiply(rangeToScan));

        Entity hit = null;
        double minDistanceSquared = Double.POSITIVE_INFINITY;

        for (Entity entity : player.getNearbyEntities(observerEnd.getX(), observerEnd.getY(), observerEnd.getZ())) {
            if (entity == player || (avoidNonLiving && !(entity instanceof LivingEntity)) || (entity instanceof Player && ((Player) entity).getGameMode() == GameMode.SPECTATOR)) {
                continue;
            }

            double distanceSquared = entity.getLocation().distanceSquared(observerPos);
            if (distanceSquared > rangeToScan * rangeToScan) {
                continue;
            }

            if (lineOfSight) {
                double lastBlockDistance = player.getLastTwoTargetBlocks(BlockUtil.getNonSolidBlocks(), (int) Math.ceil(Math.sqrt(distanceSquared))).get(0).getLocation().distance(observerPos);
                if (lastBlockDistance + 1 < Math.sqrt(distanceSquared)) {
                    continue;
                }
            }

            Vector targetPos = entity.getLocation().toVector();
            double width = entity.getBoundingBox().getWidthX() * expandBoxesPercentage / 1.8;
            double height = entity.getBoundingBox().getHeight() * expandBoxesPercentage;

            Vector minimum = targetPos.clone().subtract(new Vector(width, 0.1 / expandBoxesPercentage, width));
            Vector maximum = targetPos.clone().add(new Vector(width, height, width));
            BoundingBox entityBoundingBox = new BoundingBox(minimum.getX(), minimum.getY(), minimum.getZ(), maximum.getX(), maximum.getY(), maximum.getZ());

            if (entityBoundingBox.contains(observerPos.toVector())) {
                double distanceSquaredToEntity = entity.getLocation().distanceSquared(observerPos);
                if (distanceSquaredToEntity < minDistanceSquared) {
                    hit = entity;
                    minDistanceSquared = distanceSquaredToEntity;
                }
            }
        }

        return hit;
    }

    /**
     * Calculates the damage taken by a player manually.
     * This includes armor, toughness, resistance, and
     * enchantments.
     * @param damage The amount of damage before.
     * @param points The amount of armor points.
     * @param toughness The armor toughness.
     * @param resistance Resistance effect level.
     * @param epf The enchantment protection factor.
     * @return The final amount of damage with all factors.
     */
    public static double calculateDamage(double damage, double points, double toughness, int resistance, int epf) {

        Component debugMessage = MiniMessage.miniMessage().deserialize(
                "<br><white>Damage: <gold><damage></gold>" +
                "<br><white>Armor Points: <gold><points></gold>" +
                "<br><white>Toughness: <gold><toughness></gold>" +
                "<br><white>Resistance: <gold><resistance></gold>" +
                "<br><white>EPF: <gold><epf></gold><br>",
                Placeholder.unparsed("damage", String.valueOf(damage)),
                Placeholder.unparsed("points", String.valueOf(points)),
                Placeholder.unparsed("toughness", String.valueOf(toughness)),
                Placeholder.unparsed("resistance", String.valueOf(resistance)),
                Placeholder.unparsed("epf", String.valueOf(epf))
        );

        Bukkit.broadcast(debugMessage);

        double withArmorAndToughness = damage * (1 - Math.min(20, Math.max(points / 5, points - damage / (2 + toughness / 4))) / 25);
        double withResistance = withArmorAndToughness * (1 - (resistance * 0.2));

        return withResistance * (1 - (Math.min(20.0, epf) / 25));
    }

    /**
     * Gets the enchantment protection factor from
     * a given player inventory.
     * @param inventory The inventory of the player.
     * @return The final protection factor.
     */
    public static int getEPF(PlayerInventory inventory) {

        ItemStack helmet = inventory.getHelmet();
        ItemStack chestplate = inventory.getChestplate();
        ItemStack leggings = inventory.getLeggings();
        ItemStack boots = inventory.getBoots();

        return
                (helmet != null ? helmet.getEnchantmentLevel(Enchantment.DAMAGE_ALL) : 0) +
                        (chestplate != null ? chestplate.getEnchantmentLevel(Enchantment.DAMAGE_ALL) : 0) +
                        (leggings != null ? leggings.getEnchantmentLevel(Enchantment.DAMAGE_ALL) : 0) +
                        (boots != null ? boots.getEnchantmentLevel(Enchantment.DAMAGE_ALL) : 0);
    }
}