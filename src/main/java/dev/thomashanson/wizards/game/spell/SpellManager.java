package dev.thomashanson.wizards.game.spell;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.common.base.Preconditions;

import dev.thomashanson.wizards.WizardsPlugin;

public class SpellManager {

    private final WizardsPlugin plugin;
    private final Map<String, Spell> spells = new HashMap<>();

    public SpellManager(@NotNull WizardsPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin instance cannot be null");
    }

    public void loadSpells() {
        spells.clear();
        File spellsFile = new File(plugin.getDataFolder(), "spells.yml");
        if (!spellsFile.exists()) {
            plugin.saveResource("spells.yml", false);
        }

        FileConfiguration spellConfig = YamlConfiguration.loadConfiguration(spellsFile);
        ConfigurationSection spellsSection = spellConfig.getConfigurationSection("spells");
        
        if (spellsSection == null) {
            plugin.getLogger().severe("'spells' section is missing or empty in spells.yml!");
            return;
        }

        for (String key : spellsSection.getKeys(false)) {
            ConfigurationSection config = spellsSection.getConfigurationSection(key);
            if (config == null) continue;

            try {
                String className = config.getString("class");
                Preconditions.checkNotNull(className, "Class path for spell '" + key + "' is not defined!");

                Class<?> clazz = Class.forName(className);
                if (!Spell.class.isAssignableFrom(clazz)) {
                    plugin.getLogger().log(Level.SEVERE, "Class {0} for spell {1} does not extend Spell!", new Object[]{className, key});
                    continue;
                }

                @SuppressWarnings("unchecked")
                Class<? extends Spell> spellClass = (Class<? extends Spell>) clazz;
                Constructor<? extends Spell> constructor = spellClass.getConstructor(WizardsPlugin.class, String.class, ConfigurationSection.class);

                Spell spell = constructor.newInstance(plugin, key, config);
                spells.put(key.toUpperCase(), spell);
                plugin.getServer().getPluginManager().registerEvents(spell, plugin);

            } catch (ClassNotFoundException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not find spell class for key ''{0}''", key);
            // FIX 4: Combine related reflection exceptions into a single multi-catch block.
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to instantiate spell for key ''{0}''. Check constructor and class definition.", key);
            } catch (IllegalArgumentException | SecurityException e) { // This catch remains for any other unexpected errors during a single spell load.
                plugin.getLogger().log(Level.SEVERE, "An unexpected error occurred while loading spell: ''{0}''", key);
            }
        }
        
        // FIX 5: Use parameterized logging here as well.
        plugin.getLogger().log(Level.INFO, "Successfully loaded {0} spells.", spells.size());
        assignGuiSlots();
    }

    @Nullable
    public Spell getSpell(@NotNull String key) {
        return spells.get(key.toUpperCase());
    }

    @Nullable
    public Spell getSpell(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        String spellKey = container.get(Spell.SPELL_ID_KEY, PersistentDataType.STRING);

        return spellKey != null ? getSpell(spellKey) : null;
    }

    /**
     * Returns an unmodifiable view of all loaded spells.
     * @return An unmodifiable map of spell keys to Spell instances.
     */
    public Map<String, Spell> getAllSpells() {
        return Collections.unmodifiableMap(spells);
    }
    
    /**
     * Iterates through all loaded spells and assigns them a GUI slot programmatically.
     * This assigns slots left-to-right, then top-to-bottom within each element's columns.
     */
    private void assignGuiSlots() {
        // Group spells by their element, handling null elements gracefully.
        Map<SpellElement, List<Spell>> spellsByElement = spells.values().stream()
                .filter(spell -> spell.getElement() != null)
                .collect(Collectors.groupingBy(Spell::getElement, () -> new EnumMap<>(SpellElement.class), Collectors.toList()));

        // Sort each element's list of spells by rarity and then name.
        spellsByElement.values().forEach(list -> list.sort(Comparator.comparing(Spell::getRarity).thenComparing(Spell::getName)));

        // Use a map to track the next index for each element's list.
        Map<SpellElement, Integer> nextSpellIndex = Arrays.stream(SpellElement.values())
                .collect(Collectors.toMap(Function.identity(), el -> 0, (a, b) -> b, () -> new EnumMap<>(SpellElement.class)));
        
        final int maxRows = 6;
        final int maxCols = 9;

        // Iterate through the GUI grid, row by row, left to right, skipping header row.
        for (int row = 1; row < maxRows; row++) {
            for (int col = 0; col < maxCols; col++) {
                final int currentSlot = (row * maxCols) + col;

                // Find which element this column belongs to.
                for (SpellElement element : SpellElement.values()) {
                    if (col >= element.getFirstSlot() && col <= element.getSecondSlot()) {
                        List<Spell> elementSpells = spellsByElement.get(element);
                        if (elementSpells == null) continue;

                        int spellIndex = nextSpellIndex.get(element);
                        if (spellIndex < elementSpells.size()) {
                            Spell spellToPlace = elementSpells.get(spellIndex);
                            spellToPlace.setGuiSlot(currentSlot);
                            nextSpellIndex.put(element, spellIndex + 1);
                        }
                        break; // Move to the next column once element is found
                    }
                }
            }
        }
    }
}