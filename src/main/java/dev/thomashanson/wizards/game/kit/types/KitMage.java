package dev.thomashanson.wizards.game.kit.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.kit.WizardsKit;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.SpellType;

public class KitMage extends WizardsKit {

    // The 5 starting spells for the Mage
    private static final SpellType[] STARTING_SPELLS = {
            SpellType.MANA_BOLT,
            SpellType.FIREBALL,
            SpellType.HEAL,
            SpellType.ICE_PRISON,
            SpellType.WIZARDS_COMPASS
    };

    private final Wizards game;

    public KitMage(Wizards game, Map<String, Object> data) {
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

        Location playerLoc = player.getLocation();
        World world = player.getWorld();

        if (playerLoc == null) return;

        // Make player visible instantly
        player.setInvisible(false);
        player.playSound(playerLoc, Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1F, 1.5F);

        List<ArmorStand> spellIconStands = new ArrayList<>();
        final double headOffset = 1.8; // Approximate height of head from feet for particle ring
        final double iconOffsetY = -0.6; // Fine-tune Y offset for items on armor stand heads to align with particle ring

        Location particleRingCenter = playerLoc.add(0, headOffset, 0);
        Location armorStandBaseLoc = playerLoc.add(0, headOffset + iconOffsetY, 0); // Base for armor stand calcs

        // Spawn ArmorStands to hold spell icons
        // for (SpellType spellType : STARTING_SPELLS) {
        //     
        //     ItemStack spellItem = new ItemStack(spellType.getIcon());
        //     spellItem = spellType.createSpell(player, spellItem);

        //     if (spellItem == null || spellItem.getType() == Material.AIR) {
        //         game.getPlugin().getLogger().warning("MageKit intro: Null or AIR ItemStack for spell type " + spellType.name());
        //         continue; // Skip if item is invalid
        //     }

        //     ArmorStand iconStand = (ArmorStand) world.spawnEntity(armorStandBaseLoc, EntityType.ARMOR_STAND);
        //     iconStand.setVisible(false);
        //     iconStand.setGravity(false);
        //     iconStand.setSmall(true); // Small armor stand for smaller icon appearance
        //     iconStand.setMarker(true); // No collision, minimal impact
        //     iconStand.getEquipment().setHelmet(spellItem);
        //     spellIconStands.add(iconStand);
        // }

        new BukkitRunnable() {
            double angle = 0;
            int ticks = 0;
            final int duration = 60; // 3 seconds (20 ticks/sec)
            final double radius = 0.8; // Radius of the particle ring and icon rotation

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= duration) {
                    // Cleanup ArmorStands
                    for (ArmorStand stand : spellIconStands) {
                        stand.remove();
                    }
                    spellIconStands.clear();

                    if (player.isOnline() && player.isInvisible()) { // Ensure player is visible
                        player.setInvisible(false);
                    }
                    this.cancel();
                    return;
                }

                Location currentParticleCenter = player.isOnline() ? playerLoc.add(0, headOffset, 0) : particleRingCenter;
                Location currentArmorStandCenter = player.isOnline() ? playerLoc.add(0, headOffset + iconOffsetY, 0) : armorStandBaseLoc;


                // Main green ring particles
                for (int i = 0; i < 360; i += 15) {
                    double currentAngleRad = Math.toRadians(i);
                    double x = radius * Math.cos(currentAngleRad);
                    double z = radius * Math.sin(currentAngleRad);
                    world.spawnParticle(Particle.REDSTONE, currentParticleCenter.clone().add(x, 0, z), 1, new Particle.DustOptions(org.bukkit.Color.GREEN, 1F));
                }

                // Rotate spell icons (ArmorStands)
                for (int i = 0; i < spellIconStands.size(); i++) {
                    ArmorStand stand = spellIconStands.get(i);
                    if (!stand.isValid()) continue;

                    double iconAngle = angle + (Math.PI * 2 / spellIconStands.size() * i);
                    double x = radius * Math.cos(iconAngle);
                    double z = radius * Math.sin(iconAngle);
                    
                    // Teleport the ArmorStand. The item on its head will be at this location + its head offset.
                    stand.teleport(currentArmorStandCenter.clone().add(x, 0, z));
                }

                angle += Math.PI / 16; // Rotation speed
                ticks++;
            }
        }.runTaskTimer(game.getPlugin(), 0L, 1L); // Start immediately, repeat every tick
    }

    @Override
    public List<String> getLevelDescription(int level) {
        // Cooldown decrease = 0.1 + (0.025 * (level - 1))
        double reduction = 0.1 + (0.025 * (Math.max(0, level - 1)));
        String formattedReduction = String.format("%.1f%%", reduction * 100);

        return Collections.singletonList(
            "<gray>Cooldown Reduction: <aqua>" + formattedReduction
        );
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
        return 2.5F / 20F;
    }

    @Override
    public void applyModifiers(Wizard wizard, int kitLevel) {
        double reduction = 0.1 + (0.025 * (kitLevel- 1));
        wizard.setCooldownMultiplier((float) (1 - reduction), false);
    }

    @Override
    public void applyInitialSpells(Wizard wizard) { }

    @Override
    public int getModifiedMaxSpellLevel(Spell spell, int currentDefaultMaxLevel) {
        return currentDefaultMaxLevel;
    }
}