package dev.thomashanson.wizards.util;

import java.util.Arrays;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class DebugUtil {

    public static void debugMessage(String message, Player... players) {

        StackTraceElement element = getStackTraceElement();
        Component finalComponent = MiniMessage.miniMessage().deserialize(
                "<aqua><bold>[DEBUG <file>:<line>] </bold></aqua><reset><message>",
                Placeholder.unparsed("file", element.getFileName()),
                Placeholder.unparsed("line", String.valueOf(element.getLineNumber())),
                Placeholder.unparsed("message", message)
        );

        if (players.length > 0)
            Arrays.stream(players).forEach(player -> player.sendMessage(finalComponent));
        else
            Bukkit.broadcast(finalComponent);
    }

    private static StackTraceElement getStackTraceElement() {
        return Thread.currentThread().getStackTrace()[3];
    }
}