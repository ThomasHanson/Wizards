package dev.thomashanson.wizards.game.manager;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.bukkit.entity.Player;

import dev.thomashanson.wizards.WizardsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.util.UTF8ResourceBundleControl;

/**
 * Manages all localization and translation for the plugin.
 * <p>
 * This class loads {@link ResourceBundle}s (e.g., {@code Bundle_en_US.properties})
 * and provides methods to retrieve translated, MiniMessage-formatted {@link Component}s
 * based on a player's client {@link Locale}.
 */
public class LanguageManager {

    private final WizardsPlugin plugin;

    /** Caches the loaded resource bundles, keyed by their Locale. */
    private final Map<Locale, ResourceBundle> bundles = new HashMap<>();

    /** The fallback locale to use if a player's locale is not available. */
    private final Locale defaultLocale = Locale.US;

    /**
     * Creates a new LanguageManager and loads the default bundles.
     *
     * @param plugin The main plugin instance.
     */
    public LanguageManager(WizardsPlugin plugin) {
        this.plugin = plugin;
        // Load your languages here. For now, just English.
        // You could later scan for all Bundle_xx_XX.properties files.
        loadBundle(Locale.US);
        // loadBundle(new Locale("es", "ES")); // Example for Spanish
    }

    /**
     * Loads a specific {@link ResourceBundle} from the plugin's resources
     * and caches it.
     *
     * @param locale The {@link Locale} to load (e.g., {@code Locale.US}).
     */
    private void loadBundle(Locale locale) {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle("Bundle", locale, UTF8ResourceBundleControl.get());
            bundles.put(locale, bundle);
            plugin.getLogger().info("Loaded language bundle for: " + locale);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load language bundle for: " + locale);
        }
    }

    /**
     * Gets a translated string for a player, with MiniMessage formatting and placeholders.
     *
     * @param player    The player whose locale to use. If null, the default locale is used.
     * @param key       The translation key from your .properties file.
     * @param resolvers Placeholders for MiniMessage (e.g., Placeholder.unparsed("player", player.getName())).
     * @return A formatted, translated Component.
     */
    public Component getTranslated(Player player, String key, TagResolver... resolvers) {
        Locale locale = (player != null) ? player.locale() : defaultLocale;
        
        ResourceBundle bundle = bundles.getOrDefault(locale, bundles.get(defaultLocale));

        if (bundle == null) {
            return Component.text(key);
        }

        String message = bundle.containsKey(key) ? bundle.getString(key) : key;
        return MiniMessage.miniMessage().deserialize(message, resolvers);
    }
    
    /**
     * Gets a translated string for a player using legacy MessageFormat style placeholders.
     * This is useful for simpler cases without complex color/styling in the arguments.
     *
     * @param player The player whose locale to use. If null, the default locale is used.
     * @param key    The translation key.
     * @param args   The arguments to insert into the string (e.g., for {0}, {1}).
     * @return A formatted, translated Component.
     */
    public Component getTranslatedLegacy(Player player, String key, Object... args) {
        Locale locale = (player != null) ? player.locale() : defaultLocale;

        ResourceBundle bundle = bundles.getOrDefault(locale, bundles.get(defaultLocale));

        if (bundle == null) {
            return Component.text(key);
        }

        String format = bundle.containsKey(key) ? bundle.getString(key) : key;
        String message = MessageFormat.format(format, args);
        
        return MiniMessage.miniMessage().deserialize(message);
    }
}