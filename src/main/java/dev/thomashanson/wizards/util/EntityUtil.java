package dev.thomashanson.wizards.util;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.lang.WordUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class EntityUtil {

    public static String getEntityName(Entity entity) {

        if (entity.getCustomName() != null) {
            return entity.getCustomName();

        } else {

            String name = entity.getType().name();
            name = name.replace("_", " ");
            return WordUtils.capitalizeFully(name);
        }
    }

    public static Map<LivingEntity, Double> getInRadius(Location location, double radius) {

        Map<LivingEntity, Double> entities = new HashMap<>();

        for (Entity entity : Objects.requireNonNull(location.getWorld()).getEntities()) {

            if (!(entity instanceof LivingEntity))
                continue;

            if (entity instanceof Player)
                if (((Player) entity).getGameMode() == GameMode.SPECTATOR)
                    continue;

            LivingEntity livingEntity = (LivingEntity) entity;

            double offset = location.distance(livingEntity.getLocation());

            if (offset < radius)
                entities.put(livingEntity, 1 - (offset / radius));
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

    public static void displayProgress(Player player, String prefix, double amount, String suffix) {

        int bars = 24;
        StringBuilder progressBar = new StringBuilder(ChatColor.GREEN + "");
        boolean colorChange = false;

        for (int i = 0; i < bars; i++) {

            if (!colorChange && (float) i / (float) bars >= amount) {
                progressBar.append(ChatColor.RED);
                colorChange = true;
            }

            progressBar.append("\u258c");
        }

        player.spigot().sendMessage (

                ChatMessageType.ACTION_BAR,

                new TextComponent(
                        (prefix == null ? "" : prefix + ChatColor.RESET + " ") + progressBar +
                                (suffix == null ? "" : org.bukkit.ChatColor.RESET + " " + suffix)
                )
        );
    }

    public static void resetPlayer(Player player, GameMode gameMode) {

        player.setGameMode(gameMode);
        player.setAllowFlight(false);
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

        player.saveData();
    }

    public static Entity getEntityInSight (
            Player player, int rangeToScan, boolean avoidNonLiving,
            boolean lineOfSight, float expandBoxesPercentage
    ) {

        Location observerPos = player.getEyeLocation();

        Vector observerDir = observerPos.getDirection();
        Vector observerStart = observerPos.toVector();
        Vector observerEnd = observerStart.add(observerDir.multiply(rangeToScan));

        Entity hit = null;

        for (Entity entity : player.getNearbyEntities(rangeToScan, rangeToScan, rangeToScan)) {

            if (entity == player)
                continue;

            if (avoidNonLiving && !(entity instanceof LivingEntity))
                continue;

            if (entity instanceof Player && ((Player) entity).getGameMode() == GameMode.SPECTATOR)
                continue;

            double distance = player.getEyeLocation().distance(entity.getLocation());

            if (
                    lineOfSight
                            && player.getLastTwoTargetBlocks(BlockUtil.getNonSolidBlocks(), (int) Math.ceil(distance)).get(0)
                            .getLocation().distance(player.getEyeLocation()) + 1 < distance
            )
                continue;

            Vector targetPos = entity.getLocation().toVector();

            double width = (entity.getBoundingBox().getWidthX() / 1.8F) * expandBoxesPercentage;
            double height = (entity.getBoundingBox().getHeight() * expandBoxesPercentage);

            Vector minimum = targetPos.add(new Vector(-width, -0.1 / expandBoxesPercentage, -width));
            Vector maximum = targetPos.add(new Vector(width, height, width));

            BoundingBox entityBoundingBox = BoundingBox.of(minimum, maximum);
            BoundingBox observerBoundingBox = BoundingBox.of(observerStart, observerEnd);

            if (observerBoundingBox.overlaps(entityBoundingBox))
                if (hit == null || hit.getLocation().distanceSquared(observerPos) > entity.getLocation().distanceSquared(observerPos))
                    hit = entity;
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

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("Damage: " + damage);
        Bukkit.broadcastMessage("Armor Points: " + points);
        Bukkit.broadcastMessage("Toughness: " + toughness);
        Bukkit.broadcastMessage("Resistance: " + resistance);
        Bukkit.broadcastMessage("EPF: " + epf);
        Bukkit.broadcastMessage("");

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