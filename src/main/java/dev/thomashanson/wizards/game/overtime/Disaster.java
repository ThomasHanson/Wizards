package dev.thomashanson.wizards.game.overtime;

import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.spell.SpellType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public abstract class Disaster {

    private final String name;
    private final Set<SpellType> buffedSpells;
    private final List<String> messages;

    private Instant
            lastStrike,
            nextStrike = Instant.now().plusSeconds(6),
            nextSound;

    private double accuracy = 0;
    private float size = 1.5F;

    private final Wizards game;

    protected Disaster(Wizards game, String name, Set<SpellType> buffedSpells, List<String> messages) {
        this.game = game;
        this.name = name;
        this.buffedSpells = buffedSpells;
        this.messages = messages;
    }

    public void strike() {
        this.lastStrike = Instant.now();
    }

    public void update() {

        if (Duration.between(Instant.now(), nextStrike).toMillis() > 750)
            nextStrike.minus(Duration.ofMillis(2));

        if (lastStrike == null || Duration.between(lastStrike, nextStrike).toMillis() <= 0)
            strike();

        if (nextSound == null || Duration.between(Instant.now(), nextSound).toMillis() <= 0) {

            Sound sound = null;

            switch (ThreadLocalRandom.current().nextInt(3)) {

                case 0:
                    sound = Sound.ENTITY_GHAST_AMBIENT;
                    break;
                case 1:
                    sound = Sound.ENTITY_CAT_HURT;
                    break;
                default:
                    sound = Sound.ENTITY_GHAST_SCREAM;
                    break;
            }

            for (Player player : game.getPlayers(false))
                player.playSound(player.getLocation(), sound, 0.7F, 0 + (ThreadLocalRandom.current().nextFloat() / 10));

            nextSound = Instant.now().plusSeconds(5 + ThreadLocalRandom.current().nextInt(8));
        }
    }

    protected Location getNextLocation() {

        int chance = ThreadLocalRandom.current().nextInt(50) + 3;
        int accuracy = Math.max((int) (chance - (this.accuracy * chance)), 1);

        this.accuracy += 0.0001;

        List<Player> players = new ArrayList<>(game.getPlayers(true));

        for (int a = 0; a < 50; a++) {

            Player player = players.get(ThreadLocalRandom.current().nextInt(players.size()));

            Location location = player.getLocation().add (
                    ThreadLocalRandom.current().nextDouble(accuracy * 2) - accuracy,
                    0,
                    ThreadLocalRandom.current().nextDouble(accuracy * 2) - accuracy
            );

            location = Objects.requireNonNull(location.getWorld()).getHighestBlockAt(location).getLocation().add(0.5, 0, 0.5);

            if (location.getBlock().getType() == Material.AIR)
                location.subtract(0, 1, 0);

            if (location.getBlockY() > 0 && location.getBlock().getType() != Material.AIR)
                return location;
        }

        return null;
    }

    public String getName() {
        return name;
    }

    public Set<SpellType> getBuffedSpells() {
        return buffedSpells;
    }

    public List<String> getMessages() {
        return messages;
    }

    protected Instant getLastStrike() {
        return lastStrike;
    }

    protected float getSize() {
        return size;
    }

    public void setSize(float size) {
        this.size = size;
    }

    protected Wizards getGame() {
        return game;
    }
}