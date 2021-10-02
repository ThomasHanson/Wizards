package dev.thomashanson.wizards.damage.types;

import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.game.manager.DamageManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.time.Instant;

public class BlockDamageTick extends DamageTick {

    private final Material type;
    private final Location location;

    public BlockDamageTick(double damage, EntityDamageEvent.DamageCause cause, String name, Instant timestamp, Material type, Location location) {
        super(damage, cause, name, timestamp);
        this.type = type;
        this.location = location;
    }

    private Material getType() {
        return type;
    }

    private Location getLocation() {
        return location;
    }

    @Override
    public boolean matches(DamageTick tick) {
        return (tick instanceof BlockDamageTick) && ((BlockDamageTick) tick).getType().equals(getType()) && getLocation().equals(((BlockDamageTick) tick).getLocation());
    }

    @Override
    public String getDeathMessage(Player player) {
        return DamageManager.ACCENT_COLOR + player.getDisplayName() + DamageManager.BASE_COLOR + " was killed by " + DamageManager.ACCENT_COLOR + getType().name().replace("_", " ");
    }

    @Override
    public String getSingleLineSummary() {
        return DamageManager.BASE_COLOR + "Hurt by " + DamageManager.ACCENT_COLOR + getType().name().replace("_", " ");
    }
}