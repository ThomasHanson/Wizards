package dev.thomashanson.wizards.game.kit.types;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;

import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.damage.types.CustomDamageTick;
import dev.thomashanson.wizards.event.CustomDamageEvent;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.kit.WizardsKit;
import dev.thomashanson.wizards.game.spell.Spell;

public class KitLich extends WizardsKit {

    private final Wizards game;

    public KitLich(Wizards game, Map<String, Object> data) {
        super(data);
        this.game = game;
    }

    @Override
    public void playSpellEffect(Player player, Location location) {

    }

    @Override
    public void playIntro(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        Location playerSpawnLoc = player.getLocation().getBlock().getLocation().add(0.5, 0, 0.5); // Center of the block
        World world = playerSpawnLoc.getWorld();

        if (world == null) {
            return;
        }

        // 1. Soulsand and cobblewall "grave"
        Map<Location, BlockData> originalBlocks = new HashMap<>();
        Location graveBase = playerSpawnLoc.clone().subtract(0,1,0); // One block below player spawn

        // Place Soulsand
        Block soulSandBlock = graveBase.getBlock();
        originalBlocks.put(soulSandBlock.getLocation(), soulSandBlock.getBlockData());
        soulSandBlock.setType(Material.SOUL_SAND, false);

        // Place Cobblestone Walls around
        BlockFace[] wallFaces = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
        for (BlockFace face : wallFaces) {
            Block wallBlock = soulSandBlock.getRelative(face);
            originalBlocks.put(wallBlock.getLocation(), wallBlock.getBlockData());
            wallBlock.setType(Material.COBBLESTONE_WALL, false);
        }
        player.playSound(playerSpawnLoc, Sound.BLOCK_GRAVEL_PLACE, 1F, 0.5F);
        player.playSound(playerSpawnLoc, Sound.BLOCK_SOUL_SAND_PLACE, 1F, 0.7F);


        // 2. NPC (ArmorStand with skull) slowly rises
        Location armorStandSpawnLoc = graveBase.clone().add(0, -1, 0); // Start below soulsand
        ArmorStand npc = (ArmorStand) world.spawnEntity(armorStandSpawnLoc, EntityType.ARMOR_STAND);
        npc.setGravity(false);
        npc.setVisible(false); // Invisible initially, particles will be the focus
        npc.setSmall(false);
        npc.setArms(true);
        npc.setBasePlate(false);

        ItemStack skull = new ItemStack(Material.SKELETON_SKULL);
        // To set a player's skull, you'd need SkullMeta and setOwningPlayer,
        // but for simplicity, a generic skeleton skull is used.
        npc.getEquipment().setHelmet(skull);
        // Pose: As if clawing out - one arm up
        npc.setRightArmPose(new EulerAngle(Math.toRadians(-90), 0, 0)); // Arm pointing upwards


        new BukkitRunnable() {
            int ticks = 0;
            final int duration = 60; // 3 seconds
            final double riseHeight = 1.5; // How much it rises from below soulsand to ground level

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= duration) {
                    npc.remove();
                    // Revert blocks
                    for (Map.Entry<Location, BlockData> entry : originalBlocks.entrySet()) {
                        entry.getKey().getBlock().setBlockData(entry.getValue(), false);
                    }

                    if (player.isOnline()) {
                        player.teleport(playerSpawnLoc); // Ensure player is at the exact spot
                        player.setInvisible(false);
                        world.spawnParticle(Particle.SOUL, player.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
                        player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1F, 0.8F);
                        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.7F, 0.5F);
                    }
                    this.cancel();
                    return;
                }

                // Animate NPC rising
                double currentYOffset = (riseHeight / duration) * ticks;
                npc.teleport(armorStandSpawnLoc.clone().add(0, currentYOffset, 0));
                if (!npc.isVisible() && currentYOffset > 0.5) { // Make visible once it starts emerging
                    npc.setVisible(true);
                }


                // Animate arm "punching" - simple up/down bob for the arm
                if (ticks % 10 < 5) {
                    npc.setRightArmPose(new EulerAngle(Math.toRadians(-90 - (ticks % 5 * 5) ), 0, 0));
                } else {
                    npc.setRightArmPose(new EulerAngle(Math.toRadians(-110 + (ticks % 5 * 5)), 0, 0));
                }


                // Black tendrils of particles
                if (ticks % 3 == 0) {
                    for (int i = 0; i < 2; i++) {
                        double angle = Math.random() * Math.PI * 2;
                        double radius = Math.random() * 1.5;
                        Location tendrilStart = graveBase.clone().add(radius * Math.cos(angle), 0.1, radius * Math.sin(angle));
                        // Particle with upward velocity and slight spread
                        world.spawnParticle(Particle.SQUID_INK, tendrilStart, 1, 0, 0.05, 0, 0.01);
                         world.spawnParticle(Particle.SOUL, tendrilStart.clone().add(0,0.2,0), 1, 0,0.05,0,0.02);
                    }
                }
                
                if (ticks % 15 == 0) {
                    player.playSound(playerSpawnLoc, Sound.ENTITY_HUSK_AMBIENT, 0.5F, 0.5F + (float)ticks/duration);
                     player.playSound(playerSpawnLoc, Sound.BLOCK_SOUL_SAND_STEP, 0.7F, 0.5F);
                }


                ticks++;
            }
        }.runTaskTimer(game.getPlugin(), 0L, 1L);
    }

    @EventHandler
    public void onDamage(CustomDamageEvent event) {
        DamageTick damageTick = event.getDamageTick();
        double heartsDmg = damageTick.getFinalDamage() / 2;
        Player damager = null;

        if (damageTick instanceof CustomDamageTick customDamageTick) {
            damager = customDamageTick.getPlayer();
        }

        if (damager == null || !(game.getKit(damager) instanceof KitLich)) {
            return;
        }

        Wizard wizard = game.getWizard(damager);
        if (wizard == null) return;

        float gainedMana = getManaGain(1);

        for (int i = 0; i < heartsDmg; i++) {
            wizard.addMana(gainedMana);
        }
    }

    @Override
    public List<String> getLevelDescription(int level) {
        // Mana gain (rounded) = 10 + (1.25 * (level - 1))
        int manaGain = 10 + (int) Math.round(1.25 * (Math.max(0, level - 1)));

        return Arrays.asList(
            "<gray>Gain <aqua>" + manaGain + " Mana</aqua> per heart dealt.",
            "<gray>Double mana regen with souls."
        );
    }

    private float getManaGain(int level) {
        return (float) (10 + (1.25 * (level - 1)));
    }

    @Override
    public float getInitialMaxMana(int kitLevel) {
        return 100;
    }

    @Override
    public int getInitialWands() {
        return 2;
    }

    @Override
    public int getInitialMaxWands(int kitLevel) {
        return 5;
    }

    @Override
    public float getBaseManaPerTick(int kitLevel) {
        return 1.5F / 20F;
    }

    @Override
    public void applyModifiers(Wizard wizard, int kitLevel) { }

    @Override
    public void applyInitialSpells(Wizard wizard) { }

    @Override
    public int getModifiedMaxSpellLevel(Spell spell, int currentDefaultMaxLevel) {
        return currentDefaultMaxLevel;
    }
}