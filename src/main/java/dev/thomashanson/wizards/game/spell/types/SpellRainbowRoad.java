package dev.thomashanson.wizards.game.spell.types;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;
import dev.thomashanson.wizards.util.BlockUtil;

public class SpellRainbowRoad extends Spell implements Tickable {

    private static final List<RoadBuilder> ACTIVE_BUILDERS = new ArrayList<>();
    private static final Map<Block, RoadData> ROAD_BLOCKS = new ConcurrentHashMap<>();

    private final List<Material> rainbowMaterials;

    public SpellRainbowRoad(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);

        this.rainbowMaterials = config.getStringList("rainbow-materials").stream()
                .map(Material::matchMaterial)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public boolean cast(Player player, int level) {
        // Prevent player from casting if they are already building a road
        if (ACTIVE_BUILDERS.stream().anyMatch(builder -> builder.caster.getUniqueId().equals(player.getUniqueId()))) {
            return false;
        }

        ACTIVE_BUILDERS.add(new RoadBuilder(this, player, level));
        return true;
    }

    @Override
    public void tick(long gameTick) {
        // Build new road segments
        ACTIVE_BUILDERS.removeIf(RoadBuilder::tick);

        // Check for melting blocks
        if (ROAD_BLOCKS.isEmpty()) return;
        Instant now = Instant.now();
        ROAD_BLOCKS.entrySet().removeIf(entry -> {
            if (now.isAfter(entry.getValue().expiry)) {
                if (rainbowMaterials.contains(entry.getKey().getType())) {
                    entry.getKey().setType(Material.AIR);
                }
                return true;
            }
            return false;
        });
    }

    @Override
    public void cleanup() {
        ACTIVE_BUILDERS.clear();
        ROAD_BLOCKS.keySet().forEach(block -> {
            if (rainbowMaterials.contains(block.getType())) block.setType(Material.AIR);
        });
        ROAD_BLOCKS.clear();
    }

    @EventHandler
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        Block block = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
        RoadData data = ROAD_BLOCKS.get(block);

        // Cancel fall damage if the player lands on their own road
        if (data != null && data.owner.equals(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private record RoadData(UUID owner, Instant expiry) {}

    private static class RoadBuilder {
        final SpellRainbowRoad parent;
        final Player caster;
        final int level;
        final Vector direction;
        Location currentLocation;
        int blocksPlaced = 0;
        int colorProgress = 0;

        // Configurable stats
        final int maxLength;
        final long durationSeconds;

        RoadBuilder(SpellRainbowRoad parent, Player caster, int level) {
            this.parent = parent;
            this.caster = caster;
            this.level = level;
            this.currentLocation = caster.getLocation().getBlock().getLocation().add(0.5, -0.5, 0.5);

            StatContext context = StatContext.of(level);
            this.maxLength = (int) parent.getStat("length", level);
            this.durationSeconds = (long) parent.getStat("duration-seconds", level);

            BlockFace facing = BlockUtil.getFace(caster.getEyeLocation().getYaw());
            double yMod = Math.min(Math.max(caster.getLocation().getPitch() / 30, -1), 1);
            this.direction = new Vector(facing.getModX(), -yMod, facing.getModZ());

            // Instantly stop falling if cast mid-air
            if (caster.getVelocity().getY() < 0) {
                caster.setVelocity(new Vector(0, 0, 0));
            }
        }

        /** @return true if this builder is finished and should be removed */
        boolean tick() {
            if (!caster.isOnline() || blocksPlaced >= maxLength) {
                return true;
            }

            buildSegment();
            currentLocation.add(direction);
            blocksPlaced++;
            return false;
        }

        void buildSegment() {
            Block block = currentLocation.getBlock();
            for (Block b : getRoadSegment(block, BlockUtil.getFace(caster.getEyeLocation().getYaw()))) {
                if (parent.rainbowMaterials.contains(b.getType()) || b.getType().isSolid()) continue;

                b.setType(parent.rainbowMaterials.get(colorProgress++ % parent.rainbowMaterials.size()));
                Instant expiry = Instant.now().plusSeconds(durationSeconds + ThreadLocalRandom.current().nextInt(5));
                ROAD_BLOCKS.put(b, new RoadData(caster.getUniqueId(), expiry));
            }
            block.getWorld().playSound(block.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.5F, 1F);
        }

        List<Block> getRoadSegment(Block center, BlockFace facing) {
            List<Block> segment = new ArrayList<>();
            segment.add(center);
            for (BlockFace face : BlockUtil.getSideBlockFaces(facing)) {
                segment.add(center.getRelative(face));
            }
            return segment;
        }
    }
}
