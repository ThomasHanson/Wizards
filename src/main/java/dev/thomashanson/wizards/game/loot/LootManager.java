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

public class LootManager {

    // MODIFIED: Re-added guarantees to the records.
    private record LootSettings(int randomChestsToSpawn, int minItems, int maxItems, List<LootGuarantee> guarantees) {}
    private record LootGuarantee(String tableName, int amount) {}

    private final WizardsPlugin plugin;
    private final Logger logger;
    private final SpellManager spellManager;

    private final Map<String, ChestLoot> lootTables = new HashMap<>();
    private final Map<WizardsMode, LootSettings> modeSettings = new EnumMap<>(WizardsMode.class);
    private final Map<String, Supplier<ItemStack>> customItemRegistry = new HashMap<>();
    private final Map<SpellRarity, List<Spell>> spellsByRarity = new EnumMap<>(SpellRarity.class);

    private LootSettings defaultSettings;
    private Set<Material> nonGroundMaterials;

    public LootManager(@NotNull WizardsPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.spellManager = plugin.getSpellManager();
    }

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

    private void registerCustomItems(WandManager wandManager) {
        customItemRegistry.put("lootable_wand", wandManager::createLootableWandItem);
        customItemRegistry.put("cheese", () -> ItemBuilder.from(Material.COOKED_CHICKEN)
                .name(Component.translatable("wizards.item.food.cheese.name"))
                .build());
    }

    public void populateMapWithLoot(@NotNull LocalGameMap gameMap, @NotNull WizardsMode mode) {
        final LootSettings settings = getSettings(mode);
        fillPrePlacedChests(gameMap, mode);
        spawnRandomChests(gameMap, mode, settings.randomChestsToSpawn());
    }

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

    private void fillChest(@NotNull Block block, @NotNull WizardsMode mode) {
        if (block.getState() instanceof Chest chest) {
            fillChest(chest.getBlockInventory(), mode);
        }
    }

    /**
     * REWRITTEN AGAIN: This method now handles guarantees first, then fills the rest randomly.
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

    private void placeItemInRandomSlot(Inventory inventory, List<Integer> availableSlots, ItemStack item) {
        if (availableSlots.isEmpty() || item == null) return;
        if (spellManager.getSpell(item) != null) {
            item = ItemBuilder.from(item).glow().build();
        }
        int slot = availableSlots.remove(0);
        inventory.setItem(slot, item);
    }
    
    private Spell getRandomSpell(SpellRarity rarity) {
        List<Spell> spellList = spellsByRarity.get(rarity);
        if (spellList == null || spellList.isEmpty()) return null;
        return spellList.get(ThreadLocalRandom.current().nextInt(spellList.size()));
    }

    private void cacheSpellsByRarity() {
        for (Spell spell : spellManager.getAllSpells().values()) {
            spellsByRarity.computeIfAbsent(spell.getRarity(), k -> new ArrayList<>()).add(spell);
        }
    }

    private LootSettings getSettings(WizardsMode mode) {
        return modeSettings.getOrDefault(mode, defaultSettings);
    }
    
    private void loadLootTables(ConfigurationSection tablesConfig) {
        Preconditions.checkNotNull(tablesConfig, "Missing 'tables' section in loot.yml!");
        // We need to parse tables in an order that respects includes.
        // A simple way is to define included tables (like 'spells') first in the YML.
        // A more robust solution would be two passes, but this works for now.
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
    
    // MODIFIED: Re-added parsing for the guarantees list.
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