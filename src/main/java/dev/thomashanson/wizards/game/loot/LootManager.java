package dev.thomashanson.wizards.game.loot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;

import com.google.common.base.Preconditions;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.manager.WandManager;
import dev.thomashanson.wizards.game.mode.WizardsMode;
import dev.thomashanson.wizards.game.potion.PotionType;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.SpellManager;
import dev.thomashanson.wizards.game.spell.SpellRarity;
import dev.thomashanson.wizards.map.LocalGameMap;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import net.kyori.adventure.text.Component;

/**
 * Manages all loot table loading, parsing, and chest population for the game.
 * <p>
 * This manager is responsible for:
 * <ul>
 * <li>Loading and parsing all loot tables from {@code loot.yml} on startup.</li>
 * <li>Caching spells by rarity for weighted random selection.</li>
 * <li>Registering custom item suppliers (e.g., for wands).</li>
 * <li>Populating all pre-placed chests in a map with loot.</li>
 * <li>Spawning and populating new, randomized chests in a map.</li>
 * </ul>
 * It uses a system of "guarantees" and a "master pool" to ensure a balanced
 * loot distribution as defined in the configuration.
 */
public class LootManager {

    /**
     * Internal record holding the parsed loot settings for a specific game mode.
     *
     * @param randomChestsToSpawn The number of new chests to randomly spawn on the map.
     * @param minItems            The minimum number of items to place in a chest.
     * @param maxItems            The maximum number of items to place in a chest.
     * @param guarantees          A list of specific loot tables to *always* pull from.
     */
    private record LootSettings(int randomChestsToSpawn, int minItems, int maxItems, List<LootGuarantee> guarantees) {}

    /**
     * Internal record representing a guaranteed item drop from a specific table.
     *
     * @param tableName The key of the loot table to pull from (e.g., "spells").
     * @param amount    The number of items to pull from this table.
     */
    private record LootGuarantee(String tableName, int amount) {}

    private final WizardsPlugin plugin;
    private final Logger logger;
    private final SpellManager spellManager;

    /** Caches all parsed loot tables from loot.yml, keyed by their name (e.g., "master_pool"). */
    private final Map<String, ChestLoot> lootTables = new HashMap<>();

    /** Caches the specific {@link LootSettings} for each {@link WizardsMode}. */
    private final Map<WizardsMode, LootSettings> modeSettings = new EnumMap<>(WizardsMode.class);

    /** A registry for dynamically creating special items, like wands or custom food. */
    private final Map<String, Supplier<ItemStack>> customItemRegistry = new HashMap<>();

    /** A pre-cached map of spells grouped by their rarity for efficient weighted lookups. */
    private final Map<SpellRarity, List<Spell>> spellsByRarity = new EnumMap<>(SpellRarity.class);

    /** The fallback {@link LootSettings} if a specific game mode is not defined in the config. */
    private LootSettings defaultSettings;

    /** A set of materials (like leaves and air) to ignore when spawning random chests. */
    private Set<Material> nonGroundMaterials;

    /**
     * Creates a new LootManager.
     *
     * @param plugin The main plugin instance.
     */
    public LootManager(@NotNull WizardsPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.spellManager = plugin.getSpellManager();
    }

    /**
     * Loads and parses all configuration from the {@code loot.yml} file.
     * This method populates the loot tables, mode settings, and custom item registries.
     *
     * @param config      The {@link ConfigurationSection} of {@code loot.yml}.
     * @param wandManager The {@link WandManager} instance, used to register the wand item supplier.
     */
    public void load(@NotNull ConfigurationSection config, @NotNull WandManager wandManager) {
        lootTables.clear();
        modeSettings.clear();
        customItemRegistry.clear();
        spellsByRarity.clear();

        cacheSpellsByRarity();
        registerCustomItems(wandManager);

        loadLootTables(config.getConfigurationSection("tables"));
        loadModeSettings(config.getConfigurationSection("modes"));

        this.nonGroundMaterials = config.getStringList("global.non-ground-materials").stream()
                .map(Material::getMaterial)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());

        logger.info("Successfully loaded %d loot tables and %d game mode settings.".formatted(lootTables.size(), modeSettings.size()));
    }

    /**
     * Registers suppliers for custom items defined in the loot tables.
     *
     * @param wandManager The {@link WandManager} to get the lootable wand item from.
     */
    private void registerCustomItems(WandManager wandManager) {
        customItemRegistry.put("lootable_wand", wandManager::createLootableWandItem);
        customItemRegistry.put("cheese", () -> ItemBuilder.from(Material.COOKED_CHICKEN)
                .name(Component.translatable("wizards.item.food.cheese.name"))
                .build());
    }

    /**
     * The main entry point to populate a game map with all loot.
     * This method fills both pre-placed chests and spawns new random chests.
     *
     * @param gameMap The map to populate.
     * @param mode    The current {@link WizardsMode}, used to determine loot settings.
     */
    public void populateMapWithLoot(@NotNull LocalGameMap gameMap, @NotNull WizardsMode mode) {
        final LootSettings settings = getSettings(mode);
        fillPrePlacedChests(gameMap, mode);
        spawnRandomChests(gameMap, mode, settings.randomChestsToSpawn());
    }

    /**
     * Scans the active map's chunks for all pre-existing chests and
     * fills them with loot according to the game mode's settings.
     *
     * @param gameMap The map to scan.
     * @param mode    The current {@link WizardsMode}.
     */
    private void fillPrePlacedChests(@NotNull LocalGameMap gameMap, @NotNull WizardsMode mode) {
        World world = gameMap.getWorld();
        BoundingBox bounds = gameMap.getBounds();
        int chestCount = 0;
        int minChunkX = (int) bounds.getMinX() >> 4;
        int maxChunkX = (int) bounds.getMaxX() >> 4;
        int minChunkZ = (int) bounds.getMinZ() >> 4;
        int maxChunkZ = (int) bounds.getMaxZ() >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                Chunk chunk = world.getChunkAt(cx, cz);
                for (BlockState tileEntity : chunk.getTileEntities()) {
                    if (tileEntity.getBlock().getLocation().toVector().isInAABB(bounds.getMin(), bounds.getMax()) && tileEntity instanceof Chest) {
                        fillChest(tileEntity.getBlock(), mode);
                        chestCount++;
                    }
                }
            }
        }
        logger.info("[Loot] Filled %d pre-placed chests for %s mode.".formatted(chestCount, mode.name()));
    }
    
    /**
     * Spawns a configured number of new chests at random, valid locations on the map
     * and fills them with loot.
     *
     * @param gameMap    The map to spawn chests on.
     * @param mode       The current {@link WizardsMode}.
     * @param chestCount The number of chests to attempt to spawn.
     */
    private void spawnRandomChests(@NotNull LocalGameMap gameMap, @NotNull WizardsMode mode, int chestCount) {
        if (chestCount <= 0) return;
        World world = gameMap.getWorld();
        BoundingBox bounds = gameMap.getBounds();
        int chestsSpawned = 0;
        for (int i = 0; i < chestCount * 3 && chestsSpawned < chestCount; i++) {
            int randomX = ThreadLocalRandom.current().nextInt((int) bounds.getMinX(), (int) bounds.getMaxX() + 1);
            int randomZ = ThreadLocalRandom.current().nextInt((int) bounds.getMinZ(), (int) bounds.getMaxZ() + 1);
            Block groundBlock = world.getHighestBlockAt(randomX, randomZ, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (nonGroundMaterials.contains(groundBlock.getType())) continue;
            
            Block chestBlock = groundBlock.getRelative(BlockFace.UP);
            if (chestBlock.getY() < bounds.getMaxY() && chestBlock.getType().isAir()) {
                chestBlock.setType(Material.CHEST);
                fillChest(chestBlock, mode);
                chestsSpawned++;
            }
        }
        logger.info("[Loot] Spawned %d/%d randomized chests for %s mode.".formatted(chestsSpawned, chestCount, mode.name()));
    }

    /**
     * A convenience wrapper to fill a chest's inventory from its {@link Block} state.
     *
     * @param block The chest block.
     * @param mode  The current {@link WizardsMode}.
     */
    private void fillChest(@NotNull Block block, @NotNull WizardsMode mode) {
        if (block.getState() instanceof Chest chest) {
            fillChest(chest.getBlockInventory(), mode);
        }
    }

    /**
     * The core loot generation logic.
     * This method fills a given inventory by first processing all "guarantees"
     * (like one guaranteed spell) and then filling the remaining slots with
     * random items from the "master_pool" table.
     *
     * @param inventory The chest inventory to fill.
     * @param mode      The current {@link WizardsMode} to get settings for.
     */
    private void fillChest(@NotNull Inventory inventory, @NotNull WizardsMode mode) {
        inventory.clear();

        final LootSettings settings = getSettings(mode);
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        
        // Get the master loot table for random items.
        ChestLoot masterLootTable = lootTables.get("master_pool");
        if (masterLootTable == null) {
            logger.severe("FATAL: Could not find the 'master_pool' loot table in loot.yml!");
            return;
        }

        // Get a list of available slots and shuffle them.
        List<Integer> availableSlots = new ArrayList<>(inventory.getSize());
        for (int i = 0; i < inventory.getSize(); i++) {
            availableSlots.add(i);
        }
        Collections.shuffle(availableSlots, random);

        // 1. Process all guaranteed items first.
        for (LootGuarantee guarantee : settings.guarantees()) {
            ChestLoot guaranteeTable = lootTables.get(guarantee.tableName());
            if (guaranteeTable == null) {
                logger.warning("Invalid guarantee table '%s' for mode %s.".formatted(guarantee.tableName(), mode.name()));
                continue;
            }
            for (int i = 0; i < guarantee.amount(); i++) {
                if (availableSlots.isEmpty()) break;
                placeItemInRandomSlot(inventory, availableSlots, guaranteeTable.getLoot());
            }
        }

        // 2. Determine how many more random items to place.
        int totalItemsToPlace = random.nextInt(settings.minItems(), settings.maxItems() + 1);
        int itemsAlreadyPlaced = inventory.getSize() - availableSlots.size();
        int remainingItemsToPlace = totalItemsToPlace - itemsAlreadyPlaced;

        // 3. Fill the rest of the slots with random items from the master pool.
        for (int i = 0; i < remainingItemsToPlace; i++) {
            if (availableSlots.isEmpty()) break;
            placeItemInRandomSlot(inventory, availableSlots, masterLootTable.getLoot());
        }
    }

    /**
     * Places a single item into a random, available slot in a chest's inventory.
     * This method modifies the {@code availableSlots} list by removing the slot it used.
     *
     * @param inventory      The chest inventory.
     * @param availableSlots A mutable list of available slot indices.
     * @param item           The {@link ItemStack} to place.
     */
    private void placeItemInRandomSlot(Inventory inventory, List<Integer> availableSlots, ItemStack item) {
        if (availableSlots.isEmpty() || item == null) return;
        if (spellManager.getSpell(item) != null) {
            item = ItemBuilder.from(item).glow().build();
        }
        int slot = availableSlots.remove(0);
        inventory.setItem(slot, item);
    }
    
    /**
     * Selects a random {@link Spell} from the cache based on the given rarity.
     *
     * @param rarity The {@link SpellRarity} to select from.
     * @return A random {@link Spell}, or null if no spells of that rarity exist.
     */
    private Spell getRandomSpell(SpellRarity rarity) {
        List<Spell> spellList = spellsByRarity.get(rarity);
        if (spellList == null || spellList.isEmpty()) return null;
        return spellList.get(ThreadLocalRandom.current().nextInt(spellList.size()));
    }

    /**
     * Populates the {@link #spellsByRarity} map for fast, weighted lookups.
     */
    private void cacheSpellsByRarity() {
        for (Spell spell : spellManager.getAllSpells().values()) {
            spellsByRarity.computeIfAbsent(spell.getRarity(), k -> new ArrayList<>()).add(spell);
        }
    }

    /**
     * Gets the {@link LootSettings} for the given mode, or the default
     * settings if no specific configuration exists.
     *
     * @param mode The game mode.
     * @return The corresponding {@link LootSettings}.
     */
    private LootSettings getSettings(WizardsMode mode) {
        return modeSettings.getOrDefault(mode, defaultSettings);
    }
    
    /**
     * Loads and parses all item definitions from the "tables" section of {@code loot.yml}.
     * This method handles different item types (ITEM, SPELL_BY_RARITY, CUSTOM_ITEM, etc.)
     * and resolves TABLE_INCLUDE directives.
     *
     * @param tablesConfig The "tables" {@link ConfigurationSection}.
     */
    private void loadLootTables(ConfigurationSection tablesConfig) {
        Preconditions.checkNotNull(tablesConfig, "Missing 'tables' section in loot.yml!");

        List<String> tableOrder = new ArrayList<>(tablesConfig.getKeys(false));
        Collections.sort(tableOrder, (a, b) -> (a.equals("master_pool") ? 1 : b.equals("master_pool") ? -1 : 0)); // Ensure master_pool is last

        for (String key : tableOrder) {
            ChestLoot chestLoot = new ChestLoot();
            List<Map<?, ?>> items = tablesConfig.getMapList(key);
            for (Map<?, ?> itemData : items) {
                String type = ((String) itemData.get("type")).toUpperCase();
                int weight = (int) itemData.get("weight");
                ItemStack prototype = switch (type) {
                    case "ITEM" -> new ItemStack(Objects.requireNonNull(Material.matchMaterial((String) itemData.get("material"))));
                    case "SPELL_BY_RARITY" -> {
                        SpellRarity rarity = SpellRarity.valueOf((String) itemData.get("rarity"));
                        Spell randomSpell = getRandomSpell(rarity);
                        yield randomSpell != null ? randomSpell.createItemStack(null, 1, 1) : null;
                    }
                    case "POTION" -> {
                        PotionType potionType = PotionType.valueOf((String) itemData.get("potion-type"));
                        yield potionType.createPotion();
                    }
                    case "CUSTOM_ITEM" -> {
                        String itemKey = (String) itemData.get("key");
                        Supplier<ItemStack> itemSupplier = customItemRegistry.get(itemKey);
                        yield itemSupplier != null ? itemSupplier.get() : null;
                    }
                    case "TABLE_INCLUDE" -> {
                        String tableName = (String) itemData.get("table");
                        ChestLoot includedTable = lootTables.get(tableName);
                        if (includedTable != null) {
                            chestLoot.addLoot(includedTable, weight);
                        } else {
                            logger.warning(String.format("Tried to include loot table '" + tableName + "' before it was loaded. Check order in loot.yml."));
                        }
                        yield null;
                    }
                    default -> {
                        logger.warning("Unknown loot item type '%s' in table '%s'".formatted(type, key));
                        yield null;
                    }
                };
                if (prototype != null) {
                    Object rawAmount = itemData.get("amount");
                    if (rawAmount == null) rawAmount = "1";
                    String amountRange = String.valueOf(rawAmount);
                    String[] amounts = amountRange.split("-");
                    int min = Integer.parseInt(amounts[0].trim());
                    int max = (amounts.length > 1) ? Integer.parseInt(amounts[1].trim()) : min;
                    chestLoot.addLoot(prototype, weight, min, max);
                }
            }
            lootTables.put(key, chestLoot);
        }
    }

    /**
     * Loads and parses all game mode-specific settings from the "modes" section of {@code loot.yml}.
     *
     * @param modesConfig The "modes" {@link ConfigurationSection}.
     */
    private void loadModeSettings(ConfigurationSection modesConfig) {
        if (modesConfig == null) {
            logger.severe("Could not find 'modes' section in loot.yml. Loot settings will not be loaded.");
            return;
        }
        for (String key : modesConfig.getKeys(false)) {
            ConfigurationSection modeConfig = modesConfig.getConfigurationSection(key);
            if (modeConfig == null) continue;

            LootSettings settings = parseSettings(modeConfig);
            if (key.equalsIgnoreCase("default")) {
                this.defaultSettings = settings;
            } else {
                try {
                    WizardsMode mode = WizardsMode.valueOf(key.toUpperCase());
                    modeSettings.put(mode, settings);
                } catch (IllegalArgumentException e) {
                    logger.warning("Unknown WizardsMode '%s' in loot.yml".formatted(key));
                }
            }
        }
    }
    
    /**
     * Parses a single game mode's configuration section into a {@link LootSettings} record.
     *
     * @param config The {@link ConfigurationSection} for a specific mode (e.g., "default" or "SOLO_BRAWL").
     * @return The parsed {@link LootSettings} object.
     */
    private LootSettings parseSettings(ConfigurationSection config) {
        int randomChests = config.getInt("random-chests-to-spawn");
        int minItems = config.getInt("items-per-chest.min");
        int maxItems = config.getInt("items-per-chest.max");

        List<LootGuarantee> guarantees = new ArrayList<>();
        List<Map<?, ?>> guaranteeList = config.getMapList("guarantees");
        for (Map<?, ?> guaranteeData : guaranteeList) {
            String table = (String) guaranteeData.get("table");
            
            Object rawAmount = guaranteeData.get("amount");
            int amount = 1; // Default to 1 if not specified

            if (rawAmount instanceof Number) {
                amount = ((Number) rawAmount).intValue();
            }
            
            guarantees.add(new LootGuarantee(table, amount));
        }

        return new LootSettings(randomChests, minItems, maxItems, guarantees);
    }
}