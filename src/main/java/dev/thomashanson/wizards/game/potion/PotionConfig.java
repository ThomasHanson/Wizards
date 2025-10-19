package dev.thomashanson.wizards.game.potion;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Holds all the configured data for a single potion, loaded from potions.yml.
 * This class is immutable after creation.
 */
public class PotionConfig {

    private final String key;
    private final String displayNameKey;
    private final String descriptionKey;
    private final Color color;
    private final int durationTicks;

    private final Map<String, List<PotionEffectConfig>> effects;

    @SuppressWarnings("unchecked")
    public PotionConfig(String key, ConfigurationSection section) {
        this.key = key;
        this.displayNameKey = section.getString("display_name_key");
        this.descriptionKey = section.getString("description_key");
        this.color = parseColor(section.getString("color", "#FFFFFF"));
        this.durationTicks = section.getInt("duration", 0) * 20; // Convert seconds to ticks

        this.effects = new HashMap<>();
        ConfigurationSection effectsSection = section.getConfigurationSection("effects");

        if (effectsSection != null) {
            for (String trigger : effectsSection.getKeys(false)) { // e.g., "on_activate", "on_spell_hit"
                
                List<Map<String, Object>> effectMaps = (List<Map<String, Object>>) effectsSection.getList(trigger);

                if (effectMaps != null) {
                    List<PotionEffectConfig> parsedEffects = effectMaps.stream()
                            .map(PotionEffectConfig::new)
                            .collect(Collectors.toList());
                    
                    effects.put(trigger.toLowerCase(), parsedEffects);
                }
            }
        }
    }

    /**
     * Parses a hex color string (e.g., "#RRGGBB") into a Bukkit Color object.
     * @param hex The hex color string.
     * @return A Bukkit Color object.
     */
    private Color parseColor(String hex) {
        try {
            hex = hex.startsWith("#") ? hex.substring(1) : hex;
            java.awt.Color awtColor = java.awt.Color.decode("0x" + hex);
            return Color.fromRGB(awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue());
        } catch (NumberFormatException e) {
            System.err.println("[Wizards] Invalid color format in potions.yml: " + hex);
            return Color.WHITE; // Fallback to white on error
        }
    }

    /**
     * Retrieves the list of effects for a specific trigger.
     * @param trigger The event trigger (e.g., "on_activate").
     * @return A list of effect configurations, or an empty list if none.
     */
    public List<PotionEffectConfig> getEffects(String trigger) {
        return effects.getOrDefault(trigger.toLowerCase(), Collections.emptyList());
    }

    public String getKey() {
        return key;
    }

    public String getDisplayNameKey() {
        return displayNameKey;
    }

    public String getDescriptionKey() {
        return descriptionKey;
    }

    public Color getColor() {
        return color;
    }

    public int getDurationTicks() {
        return durationTicks;
    }
}
