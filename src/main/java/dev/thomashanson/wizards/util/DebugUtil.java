package dev.thomashanson.wizards.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/**
 * Utility class for sending formatted debug messages.
 */
public final class DebugUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final String DEBUG_FORMAT = "<aqua><bold>[DEBUG <file>:<line>] </bold></aqua><gray><message>";

    private DebugUtil() {
        // Private constructor to prevent instantiation
    }

    /**
     * Sends a formatted debug message to the specified recipients, or to the console if none are provided.
     *
     * @param message   The debug message content.
     * @param recipients The players who should receive the message.
     */
    public static void debugMessage(String message, Player... recipients) {

        StackTraceElement element = getCallingStackTraceElement();
        String fileName = element.getFileName() != null ? element.getFileName() : "Unknown";
        String lineNumber = String.valueOf(element.getLineNumber());

        Component finalComponent = MINI_MESSAGE.deserialize(DEBUG_FORMAT,
            Placeholder.unparsed("file", fileName),
            Placeholder.unparsed("line", lineNumber),
            Placeholder.unparsed("message", message)
        );

        if (recipients.length > 0) {
            for (Player player : recipients) {
                player.sendMessage(finalComponent);
            }
        } else {
            // Default to console to avoid spamming all players on the server.
            Bukkit.getConsoleSender().sendMessage(finalComponent);
        }
    }

    /**
     * Finds the stack trace element of the code that called a method in this class.
     * It iterates the stack to find the first entry that is not from this DebugUtil class.
     *
     * @return The {@link StackTraceElement} of the caller.
     */
    private static StackTraceElement getCallingStackTraceElement() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String thisClassName = DebugUtil.class.getName();

        // Start at index 2 to skip getStackTrace() and this method itself.
        for (int i = 2; i < stackTrace.length; i++) {
            if (!stackTrace[i].getClassName().equals(thisClassName)) {
                return stackTrace[i];
            }
        }
        // Fallback in case something unexpected happens
        return new StackTraceElement("UnknownClass", "UnknownMethod", "UnknownFile", -1);
    }
}