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

public class LanguageManager {

    private WizardsPlugin plugin;
    private final Map<Locale, ResourceBundle> bundles = new HashMap<>();
    private final Locale defaultLocale = Locale.US;

    public LanguageManager(WizardsPlugin plugin) {
        this.plugin = plugin;
        // Load your languages here. For now, just English.
        // You could later scan for all Bundle_xx_XX.properties files.
        loadBundle(Locale.US);
        // loadBundle(new Locale("es", "ES")); // Example for Spanish
    }

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
     * @param player The player whose locale to use.
     * @param key    The translation key from your .properties file.
     * @param resolvers Placeholders for MiniMessage (e.g., Placeholder.unparsed("player", player.getName())).
     * @return A formatted, translated Component.
     */
    public Component getTranslated(Player player, String key, TagResolver... resolvers) {
        // MODIFIED: If player is null, use the default locale. Otherwise, use the player's locale.
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
     * @param player The player whose locale to use.
     * @param key The translation key.
     * @param args The arguments to insert into the string (e.g., for {0}, {1}).
     * @return A formatted, translated Component.
     */
    public Component getTranslatedLegacy(Player player, String key, Object... args) {
        // MODIFIED: If player is null, use the default locale. Otherwise, use the player's locale.
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