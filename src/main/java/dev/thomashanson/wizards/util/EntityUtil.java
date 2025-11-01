package dev.thomashanson.wizards.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.RayTraceResult;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Utility class for entity-related operations, such as state management and spatial queries.
 */
public final class EntityUtil {

    private static final int ACTION_BAR_PROGRESS_BARS = 24;
    private static final String ACTION_BAR_PROGRESS_CHAR = "\u258c";

    private EntityUtil() {
        // Private constructor to prevent instantiation
    }
    
    /**
     * Gets the display name of an entity.
     * <p>
     * This returns the entity's custom name if it has one. For players, it
     * returns their username. For other mobs, it returns their default name
     * (e.g., "Zombie").
     *
     * @param entity The entity.
     * @return The name of the entity.
     */
    public static String getEntityName(Entity entity) {
        // entity.getName() correctly returns the player name for players,
        // the custom name for entities that have one, and the default
        // entity type name otherwise.
        return entity.getName();
    }

    /**
     * Gets a map of living entities within a given radius.
     * This method is highly optimized using the Paper API's entity search methods.
     *
     * @param location The center of the search area.
     * @param radius   The radius to search within.
     * @return A map of living entities to their distance-based offset (1.0 = closest, 0.0 = furthest).
     */
    public static Map<LivingEntity, Double> getEntitiesInRadius(Location location, double radius) {
        Map<LivingEntity, Double> entities = new HashMap<>();
        if (location.getWorld() == null) {
            return entities;
        }

        Collection<LivingEntity> nearbyEntities = location.getWorld().getNearbyLivingEntities(
                location, radius,
                // Predicate to filter out spectators
                entity -> !(entity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR)
        );

        for (LivingEntity entity : nearbyEntities) {
            double distance = location.distance(entity.getEyeLocation());
            if (distance < radius) {
                entities.put(entity, 1.0 - (distance / radius));
            }
        }

        return entities;
    }

    /**
     * Displays a progress bar on a player's action bar.
     *
     * @param player     The player to send the action bar to.
     * @param prefix     A text component to display before the bar.
     * @param percentage A value from 0.0 to 1.0 representing the progress.
     * @param suffix     A text component to display after the bar.
     */
    public static void displayProgress(Player player, Component prefix, double percentage, Component suffix) {
        int greenBars = (int) (ACTION_BAR_PROGRESS_BARS * Math.min(1.0, Math.max(0.0, percentage)));
        int redBars = ACTION_BAR_PROGRESS_BARS - greenBars;

        Component progressBar = Component.text()
                .append(Component.text(ACTION_BAR_PROGRESS_CHAR.repeat(greenBars), NamedTextColor.GREEN))
                .append(Component.text(ACTION_BAR_PROGRESS_CHAR.repeat(redBars), NamedTextColor.RED))
                .build();

        player.sendActionBar(prefix.append(progressBar).append(suffix));
    }

    /**
     * Resets a player to a clean, default state for a specific gamemode.
     *
     * @param player   The player to reset.
     * @param gameMode The gamemode to set after resetting.
     */
    public static void resetPlayer(Player player, GameMode gameMode) {
        player.setGameMode(gameMode);

        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(20.0);
            player.setHealth(maxHealth.getValue());
        }

        player.setFoodLevel(20);
        player.setSaturation(5F);
        player.setExhaustion(0F);

        player.setFireTicks(0);
        player.setFallDistance(0F);

        player.setLevel(0);
        player.setExp(0F);

        player.setAllowFlight(gameMode == GameMode.SPECTATOR || gameMode == GameMode.CREATIVE);
        player.setFlying(false);

        player.setWalkSpeed(0.2F);
        player.setFlySpeed(0.1F);

        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.setItemOnCursor(null);

        // Clear active potion effects without causing visual glitches
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
    }

    /**
     * Finds the first entity in a player's line of sight using an efficient ray-trace.
     *
     * @param player The player whose line of sight to use.
     * @param range  The maximum distance to check.
     * @param filter A predicate to filter which entities can be targeted.
     * @return The entity in sight, or null if no entity was found.
     */
    public static Entity getEntityInSight(Player player, int range, Predicate<Entity> filter) {
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                range,
                entity -> !entity.equals(player) && filter.test(entity)
        );

        return (result != null) ? result.getHitEntity() : null;
    }

    /**
     * Calculates the final damage taken by a player, accounting for armor and enchantments.
     * Formula is based on the vanilla Minecraft damage calculation.
     *
     * @param damage      The initial raw damage amount.
     * @param armorPoints The total armor points of the player.
     * @param toughness   The total armor toughness of the player.
     * @param epf         The total Enchantment Protection Factor (from Protection enchantments).
     * @return The final damage amount to be dealt.
     */
    public static double calculateDamage(double damage, double armorPoints, double toughness, int epf) {
        // Vanilla damage formula: damage * (1 - min(20, max(points / 5, points - damage / (2 + toughness / 4))) / 25)
        double withArmorAndToughness = damage * (1 - Math.min(20, Math.max(armorPoints / 5, armorPoints - damage / (2 + toughness / 4))) / 25);
        // EPF reduces remaining damage by 4% per level, capped at 80% (20 levels)
        return withArmorAndToughness * (1 - (Math.min(20.0, epf) / 25));
    }

    /**
     * Calculates the total Enchantment Protection Factor (EPF) from a player's armor.
     *
     * @param inventory The player's inventory.
     * @return The total EPF from all equipped armor pieces with the Protection enchantment.
     */
    public static int getEPF(PlayerInventory inventory) {
        int totalEPF = 0;
        for (ItemStack armorPiece : inventory.getArmorContents()) {
            if (armorPiece != null) {
                totalEPF += armorPiece.getEnchantmentLevel(Enchantment.PROTECTION_ENVIRONMENTAL);
            }
        }
        return totalEPF;
    }
}