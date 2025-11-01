package dev.thomashanson.wizards.game.spell;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.manager.DamageManager;
import dev.thomashanson.wizards.game.manager.GameManager;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public abstract class Spell implements Listener {

    // --- Nested Interfaces & Classes for specialized spell types ---
    public interface SpellBlock {
        boolean castSpell(Player player, Block block, int level);
    }

    public interface Cancellable {
        void cancelSpell(Player player);
    }

    public record SpellData(int slot, Spell spell, boolean quickCast) {}

    // --- Constants ---
    public static final NamespacedKey SPELL_ID_KEY = new NamespacedKey(WizardsPlugin.getInstance(), "spell_key");
    private static final int LORE_LINE_WRAP_LENGTH = 35; // Suggestion: Move to config.yml

    // --- Dependencies (Injected via Constructor) ---
    protected final WizardsPlugin plugin;
    protected final LanguageManager languageManager;
    protected final GameManager gameManager;
    protected final DamageManager damageManager;

    // --- Core Spell Configuration (Loaded from spells.yml) ---
    private final String key;
    private final String name;
    private final String description;
    private final Material icon;
    private final int maxLevel;
    private final SpellRarity rarity;
    private final SpellElement element;
    private final WandElement wandElement;
    private final double baseMana;
    private final double manaPerLevel;
    private final double baseCooldown;
    private final double cooldownPerLevel;
    private final int tickInterval;
    private final Map<String, SpellStat> stats; // Now private

    // --- Runtime State ---
    private int guiSlot = -1;
    private boolean cancelOnSwap = false;
    private final DecimalFormat cooldownFormat = new DecimalFormat("0.#");

    protected Spell(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.gameManager = plugin.getGameManager();
        this.damageManager = plugin.getDamageManager();

        this.key = key;
        this.name = config.getString("name", "Unnamed Spell");
        this.description = config.getString("description", "");
        this.icon = Objects.requireNonNull(Material.matchMaterial(config.getString("icon", "BARRIER")), "Invalid material for icon in spell: " + key);
        this.maxLevel = config.getInt("max-level", 1);
        this.tickInterval = config.getInt("tick-interval", 1);

        this.baseMana = config.getDouble("mana.base", 10);
        this.manaPerLevel = config.getDouble("mana.per-level", 0);
        this.baseCooldown = config.getDouble("cooldown.base", 5);
        this.cooldownPerLevel = config.getDouble("cooldown.per-level", 0);

        this.rarity = safeEnumParse(config.getString("rarity"), SpellRarity.COMMON);
        this.element = safeEnumParse(config.getString("element"), SpellElement.ATTACK);
        this.wandElement = safeEnumParse(config.getString("wand-element"), WandElement.FIRE);

        final Map<String, SpellStat> loadedStats = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final ConfigurationSection statsSection = config.getConfigurationSection("stats");
        if (statsSection != null) {
            for (final String statKey : statsSection.getKeys(false)) {
                final ConfigurationSection statConfig = statsSection.getConfigurationSection(statKey);
                if (statConfig != null) {
                    loadedStats.put(statKey, new SpellStat(statConfig));
                }
            }
        }
        this.stats = Collections.unmodifiableMap(loadedStats);
    }

    public abstract boolean cast(Player player, int level);

    public void cleanup() {}

    public ItemStack createItemStack(@Nullable Player viewer, int spellLevel, int model) {
        plugin.getLogger().info(String.format("Building lore for spell: %s", this.key));
        ItemStack item = ItemBuilder.from(this.icon)
                .model(model)
                .amount(Math.max(1, spellLevel))
                .name(getFormattedName(viewer))
                .lore(buildLore(viewer, spellLevel))
                .build();

        item.editMeta(meta -> meta.getPersistentDataContainer().set(SPELL_ID_KEY, PersistentDataType.STRING, this.key));
        return item;
    }

    private Component getFormattedName(@Nullable Player viewer) {
        String nameKey = "wizards.spell." + this.key.toLowerCase(Locale.ROOT);
        Component spellName = languageManager.getTranslated(viewer, nameKey, Placeholder.unparsed("default", this.name));
        return spellName
                .color(NamedTextColor.YELLOW)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false);
    }

    private List<Component> buildLore(@Nullable Player viewer, int spellLevel) {
        List<Component> lore = new ArrayList<>();
        String descriptionKey = "wizards.spell." + this.key.toLowerCase(Locale.ROOT) + ".description";
        
        Component descriptionComponent = languageManager.getTranslated(viewer, descriptionKey, Placeholder.unparsed("default", this.description));
        lore.addAll(wrapText(descriptionComponent, LORE_LINE_WRAP_LENGTH, Style.style(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.empty());

        int calculationLevel = Math.max(1, spellLevel);

        if (spellLevel > 0) {
            lore.add(languageManager.getTranslated(viewer, "wizards.spell.level", Placeholder.unparsed("level", String.valueOf(spellLevel))));
        }

        lore.add(languageManager.getTranslated(viewer, "wizards.spell.mana_cost", Placeholder.unparsed("cost", String.valueOf((int) getManaCost(calculationLevel)))));
        
        double cooldownInSeconds = getCooldown(calculationLevel) / 20.0;
        lore.add(languageManager.getTranslated(viewer, "wizards.spell.cooldown", Placeholder.unparsed("cooldown", cooldownFormat.format(cooldownInSeconds))));

        if (!this.stats.isEmpty()) {
            lore.add(Component.empty());
            
            StatContext context = StatContext.of(calculationLevel);

            for (Map.Entry<String, SpellStat> entry : this.stats.entrySet()) {
                String statKey = entry.getKey();
                SpellStat spellStat = entry.getValue();

                String statNameKey = "wizards.stat." + statKey.toLowerCase(Locale.ROOT).replace(" ", "_");
                Component translatedStatName = languageManager.getTranslated(viewer, statNameKey, Placeholder.unparsed("default", statKey));

                plugin.getLogger().info(String.format("Building stat lore for %s", statNameKey));
                
                double calculatedValue = spellStat.calculate(context);
                String formattedValue = spellStat.getDisplayFormat().formatted(calculatedValue);

                // Don't display internal stats in the lore
                if (formattedValue.equalsIgnoreCase("internal")) {
                    continue;
                }

                lore.add(languageManager.getTranslated(viewer, "wizards.spell.stat",
                        Placeholder.component("stat_name", translatedStatName),
                        Placeholder.unparsed("stat_value", formattedValue)
                ));
            }
        }
        return lore;
    }

    public float getManaCost(int level) {
        return (float) (baseMana + (manaPerLevel * (Math.max(0, level - 1))));
    }

    public double getCooldown(int level) {
        return baseCooldown + (cooldownPerLevel * (Math.max(0, level - 1)));
    }

    protected void damage(LivingEntity entity, DamageTick tick) {
        damageManager.damage(entity, tick);
    }

    private static <T extends Enum<T>> T safeEnumParse(String name, T defaultValue) {
        if (name == null || name.isEmpty()) return defaultValue;
        try {
            return Enum.valueOf(defaultValue.getDeclaringClass(), name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            WizardsPlugin.getInstance().getLogger().warning(() -> "Invalid enum value '" + name + "' for type " + defaultValue.getDeclaringClass().getSimpleName() + ". Defaulting to " + defaultValue.name());
            return defaultValue;
        }
    }

    static List<Component> wrapText(Component component, int maxLength, Style baseStyle) {
        String plainText = PlainTextComponentSerializer.plainText().serialize(component);
        if (plainText.isEmpty()) return Collections.singletonList(Component.empty());

        return Arrays.stream(plainText.split("(?<=\\G.{" + maxLength + "})"))
                     .map(line -> Component.text(line, baseStyle))
                     .collect(Collectors.toList());
    }
    
    // --- Getters & Setters ---

    public String getKey() { return key; }
    public String getName() { return name; }
    public Material getIcon() { return icon; }
    public int getMaxLevel() { return maxLevel; }
    public SpellRarity getRarity() { return rarity; }
    public SpellElement getElement() { return element; }
    public WandElement getWandElement() { return wandElement; }
    /**
     * Safely calculates a stat value. If the stat is not defined in spells.yml,
     * it logs a warning and returns a default value, preventing crashes.
     *
     * @param key          The case-insensitive name of the stat to calculate.
     * @param context      The context (level, distance) for the calculation.
     * @param defaultValue The value to return if the stat is missing.
     * @return The calculated stat value or the default value.
     */
    public double getStat(String key, StatContext context, double defaultValue) {
        SpellStat stat = getStats().get(key);
        if (stat == null) {
            plugin.getLogger().log(Level.WARNING, "Spell ''{0}'' is missing required stat ''{1}'' in spells.yml. Using default value of {2}.", new Object[]{this.key, key, defaultValue});
            return defaultValue;
        }
        Bukkit.broadcast(Component.text(String.format("%s has value: %s for stat: %s", this.key, stat.calculate(context), key)));
        return stat.calculate(context);
    }
    
    /**
     * Overloaded version of getStat for when distance is not a factor.
     */
    protected double getStat(String key, int level, double defaultValue) {
        return getStat(key, StatContext.of(level), defaultValue);
    }

    protected double getStat(String key, int level) {
        return getStat(key, StatContext.of(level), -1);
    }

    public Map<String, SpellStat> getStats() { return stats; } // Public getter for the now-private map
    public int getTickInterval() { return tickInterval; }
    public boolean isCancelOnSwap() { return cancelOnSwap; }
    protected void setCancelOnSwap(boolean cancelOnSwap) { this.cancelOnSwap = cancelOnSwap; }
    
    protected Optional<Wizards> getGame() {
        return Optional.ofNullable(plugin.getGameManager().getActiveGame());
    }

    protected Optional<Wizard> getWizard(Player player) {
        return getGame().flatMap(game -> Optional.ofNullable(game.getWizard(player)));
    }
    
    public int getGuiSlot() { return this.guiSlot; }
    void setGuiSlot(int slot) { this.guiSlot = slot; }
}