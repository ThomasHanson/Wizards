package dev.thomashanson.wizards.game.manager;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.damage.types.PlayerDamageTick;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.kit.WizardsKit;
import dev.thomashanson.wizards.game.manager.PlayerStatsManager.StatType;
import dev.thomashanson.wizards.game.potion.PotionType;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.util.EntityUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

public class WizardManager implements Listener {

    private final WizardsPlugin plugin;
    private final Wizards game;
    private final Map<UUID, Wizard> wizards = new HashMap<>();
    private Instant lastSurge = Instant.now();

    private final Map<UUID, Map<PotionType, Instant>> activePotionTimes = new HashMap<>();

    public WizardManager(WizardsPlugin plugin, Wizards game) {
        this.plugin = plugin;
        this.game = game;
        this.lastSurge = Instant.now();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void reset() {
        // --- 1. Unregister this manager's listener ---
        HandlerList.unregisterAll(this);

        // --- 2. Clean up individual Wizard objects ---
        // Each wizard holds Bukkit BossBar objects that need to be cleared.
        for (Wizard wizard : wizards.values()) {
            wizard.cleanup(); // We will create this method next.
        }

        // --- 3. Clear all collections ---
        wizards.clear();
        activePotionTimes.clear();

        // --- 4. Reset state variables ---
        lastSurge = Instant.now();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        handleDeathOrQuit(event.getPlayer(), false);
    }

    public void handleDeathOrQuit(Player player, boolean death) {
        activePotionTimes.remove(player.getUniqueId());
        Wizard wizard = wizards.get(player.getUniqueId());

        if (wizard != null) {
            WizardsKit killerKit = null;

            if (death) {
                DamageManager damageManager = plugin.getDamageManager();
                DamageTick lastTick = damageManager.getLastLoggedTick(player.getUniqueId());

                if (lastTick instanceof PlayerDamageTick playerDamageTick) {
                    Player killer = playerDamageTick.getPlayer();
                    killerKit = game.getKit(killer);
                }
            }

            game.dropItems(player, killerKit);

            if (wizard.getManaBar() != null) wizard.getManaBar().removePlayer(player);
            if (wizard.getPotionStatusBar() != null) wizard.getPotionStatusBar().removePlayer(player);

            if (game.isLive() && game.getMapBorder() != null) {
                game.getMapBorder().updateBorderSize(getActiveWizards().size());
            }

            EntityUtil.resetPlayer(player, GameMode.SPECTATOR);
        }

        // Get a list of all wizards that are still alive
        List<Wizard> survivors = getActiveWizards().stream()
            .filter(survivorWizard -> !survivorWizard.getPlayer().equals(player)) // Exclude the player being eliminated
            .collect(Collectors.toList());
        
        // Increment the stat for every survivor
        for (Wizard survivor : survivors) {
            plugin.getStatsManager().incrementStat(survivor.getPlayer(), StatType.PLAYERS_OUTLASTED, 1);
        }

        Instant startTime = game.getGameStartTime();

        if (startTime != null) {
            long secondsPlayed = Duration.between(startTime, Instant.now()).getSeconds();

            // Set the final time played stat for this player
            plugin.getStatsManager().setStat(player, StatType.TIME_PLAYED_SECONDS, secondsPlayed);
        }
        
        wizards.remove(player.getUniqueId());
        // updateTeamAndCheckEndGame(player);
    }

    // /**
    //  * Checks the status of an eliminated player's team to see if the entire team is out.
    //  * If so, it updates the placement rankings and checks for a game winner.
    //  *
    //  * @param eliminatedPlayer The player who was just removed from the game.
    //  */
    // private void updateTeamAndCheckEndGame(Player eliminatedPlayer) {
    //     DebugUtil.debugMessage("Updating team!");
    //     GameTeam team = game.getTeam(eliminatedPlayer);

    //     if (team == null) {
    //         // Player wasn't on a tracked team.
    //         return;
    //     }

    //     if (!team.isTeamAlive()) {
    //         game.getActiveTeams().remove(team);
    //         DebugUtil.debugMessage("Removing active team!");
    //         game.recordTeamPlacement(team); // Add to the front of the rankings
            
    //         game.checkEndGameCondition();
    //     }
    // }

    public void setupWizard(Player player) {
        if (wizards.containsKey(player.getUniqueId())) return;

        WizardsKit kit = game.getKit(player);
        if (kit == null) {
            kit = game.getGameManager().getKitManager().getKit(1);
            game.getGameManager().getKitManager().setKit(player, kit);
        }
        int kitLevel = 1; // Placeholder for potential future kit leveling

        final float maxMana = kit.getInitialMaxMana(kitLevel);
        final int maxWands = kit.getInitialMaxWands(kitLevel);

        // Pass the main game instance into the wizard
        Wizard wizard = new Wizard(game, player.getUniqueId(), maxWands, maxMana);
        wizards.put(player.getUniqueId(), wizard);

        String sql = "SELECT kit_id FROM player_kits WHERE player_uuid = ?";
        
        plugin.getDatabaseManager().executeQueryAsync(sql, results -> {
            for (Map<String, Object> row : results) {
                wizard.addUnlockedKit((Integer) row.get("kit_id"));
            }
        }, player.getUniqueId().toString());

        // kit.applyInitialSpells(wizard);
        kit.applyModifiers(wizard, kitLevel);

        // Learn the compass spell by looking it up from the manager
        Spell compass = game.getPlugin().getSpellManager().getSpell("WIZARDS_COMPASS");
        if (compass != null) {
            wizard.learnSpell(compass);
        }

        wizard.setManaBar(Bukkit.createBossBar(wizard.getManaBarTitle(), BarColor.BLUE, BarStyle.SOLID));
        wizard.getManaBar().addPlayer(player);
        wizard.setPotionStatusBar(Bukkit.createBossBar(wizard.getPotionBarTitle(), BarColor.WHITE, BarStyle.SOLID));

        game.getWandManager().issueInitialWands(player);
    }

    public Wizard getWizard(Player player) {
        return wizards.get(player.getUniqueId());
    }

    public Collection<Wizard> getActiveWizards() {
        return wizards.values();
    }

    public void updatePlayerTick(Player player, float gameTickCounter) {
        Wizard wizard = getWizard(player);
        if (wizard == null || !game.isLive() || player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        updateMana(wizard);
        updateWizardInternalCooldownStates(wizard);
        updatePlayerCooldownVisuals(wizard);
        updatePotions(wizard);
        updateActionBar(wizard);
    }

    private void updateMana(Wizard wizard) {
        if (wizard.getMana() < wizard.getMaxMana()) {
            wizard.addMana(wizard.getManaPerTick());
            game.incrementStat(wizard.getPlayer(), StatType.MANA_GAINED, wizard.getManaPerTick());
        }

        float percentage = Math.min(Math.max(0f, wizard.getMana() / wizard.getMaxMana()), 1f);
        if (wizard.getManaBar() != null) {
            wizard.getManaBar().setTitle(wizard.getManaBarTitle());
            wizard.getManaBar().setProgress(percentage);
        }
    }

    public void updateWizardInternalCooldownStates(Wizard wizard) {
        if (!game.isLive()) return;
        // This is handled inside wizard.getDisabledSpellBySpite() now
        wizard.getDisabledSpellBySpite();
    }

    public void updatePlayerCooldownVisuals(Wizard wizard) {
        Player player = wizard.getPlayer();
        if (player == null || !player.isOnline() || !game.isLive()) return;

        int heldSlot = player.getInventory().getHeldItemSlot();
        for (int i = 0; i < wizard.getMaxWands(); i++) {
            Spell spell = wizard.getSpell(i); // This now returns a Spell object
            ItemStack itemInSlot = player.getInventory().getItem(i);

            if (itemInSlot == null || itemInSlot.getType() == Material.AIR) continue;

            if (spell == null) {
                if (player.hasCooldown(itemInSlot.getType())) {
                    player.setCooldown(itemInSlot.getType(), 0);
                }
                continue;
            }

            double usableTimeInSeconds = game.getUsableTime(wizard, spell).getKey();
            int bukkitCooldownTicks = (int) Math.ceil(usableTimeInSeconds * 20.0);

            if (i == heldSlot) {
                if (bukkitCooldownTicks > 0) {
                    player.setCooldown(itemInSlot.getType(), bukkitCooldownTicks);
                } else if (player.hasCooldown(itemInSlot.getType())) {
                    player.setCooldown(itemInSlot.getType(), 0);
                }
            } else {
                int displayAmount = (int) Math.ceil(usableTimeInSeconds);
                itemInSlot.setAmount(Math.max(1, Math.min(64, displayAmount)));
                if (player.hasCooldown(itemInSlot.getType())) {
                    player.setCooldown(itemInSlot.getType(), 0);
                }
            }
        }
    }

    private void updatePotions(Wizard wizard) {
        Player player = wizard.getPlayer();
        if (player == null || !game.isLive()) return;

        PotionType activePotionType = wizard.getActivePotion();
        if (activePotionType == null) {
            if (wizard.getPotionStatusBar().getPlayers().contains(player)) {
                wizard.getPotionStatusBar().removePlayer(player);
            }
            return;
        }

        Duration timeRemaining = getPotionDuration(player, activePotionType);
        if (timeRemaining == null || timeRemaining.isNegative() || timeRemaining.isZero()) {
            dev.thomashanson.wizards.game.potion.Potion potionInstance = game.getPotions().get(activePotionType);
            if (potionInstance != null) {
                potionInstance.deactivate(wizard);
            }
            playerPotionEffectCancelled(player, activePotionType);
            player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.8f, 0.8f);

        } else if (wizard.getPotionStatusBar() != null) {
            wizard.getPotionStatusBar().setTitle(wizard.getPotionBarTitle());
            double totalDuration = activePotionType.getDuration().toMillis();
            if (totalDuration == 0) totalDuration = 1;
            wizard.getPotionStatusBar().setProgress(Math.max(0.0, timeRemaining.toMillis() / totalDuration));
            if (!wizard.getPotionStatusBar().getPlayers().contains(player)) {
                wizard.getPotionStatusBar().addPlayer(player);
            }
        }
    }

    public void playerConsumedPotion(Player player, PotionType potionType, Instant consumedAt) {
        activePotionTimes.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(potionType, consumedAt);
        Wizard wizard = getWizard(player);
        if (wizard != null) {
            wizard.setActivePotion(potionType);
            if (!wizard.getPotionStatusBar().getPlayers().contains(player)) {
                wizard.getPotionStatusBar().addPlayer(player);
            }
        }
    }
    
    public void playerPotionEffectCancelled(Player player, PotionType potionType) {
        Map<PotionType, Instant> playerTimes = activePotionTimes.get(player.getUniqueId());
        if (playerTimes != null) {
            playerTimes.remove(potionType);
            if (playerTimes.isEmpty()) {
                activePotionTimes.remove(player.getUniqueId());
            }
        }
        Wizard wizard = getWizard(player);
        if (wizard != null && wizard.getActivePotion() == potionType) {
            wizard.setActivePotion(null);
            if (wizard.getPotionStatusBar() != null) {
                wizard.getPotionStatusBar().removePlayer(player);
            }
        }
    }

    public void updateActionBar(Wizard wizard) {
        Player player = wizard.getPlayer();
        if (player == null || !player.isOnline()) return;

        int currentSlot = player.getInventory().getHeldItemSlot();
        if (currentSlot < 0 || currentSlot >= wizard.getWandsOwned()) {
            player.sendActionBar(Component.empty());
            return;
        }

        Spell spell = wizard.getSpell(currentSlot);
        if (spell == null) {
            player.sendActionBar(Component.text("Spell Wand", NamedTextColor.AQUA));
            return;
        }

        double usableTime = game.getUsableTime(wizard, spell).getKey();
        if (usableTime > 0) {
            player.sendActionBar(Component.text()
                    .append(Component.text(spell.getName(), NamedTextColor.RED))
                    .append(Component.space())
                    .append(Component.text(String.format("%.1fs", usableTime), NamedTextColor.YELLOW))
                    .build());
        } else {
            player.sendActionBar(Component.text(spell.getName() + " [" + wizard.getLevel(spell.getKey()) + "]",
                    Style.style(NamedTextColor.GREEN, TextDecoration.BOLD)));
        }
    }

    public Duration getPotionDuration(Player player, PotionType type) {
        Map<PotionType, Instant> playerTimes = activePotionTimes.get(player.getUniqueId());
        if (playerTimes == null || !playerTimes.containsKey(type)) {
            return Duration.ZERO;
        }
        Instant consumedAtWithDuration = playerTimes.get(type).plus(type.getDuration());
        if (consumedAtWithDuration.isBefore(Instant.now())) {
            return Duration.ZERO;
        }
        return Duration.between(Instant.now(), consumedAtWithDuration);
    }

    public void handleGlobalPowerSurge() {
        if (game.isOvertime()) return;
        this.lastSurge = Instant.now();

        Component surgeMessage = Component.text("Power surges through the battlefield!", NamedTextColor.GOLD, TextDecoration.BOLD);
        Component effectMessage = Component.text("Mana cost and spell cooldown has been lowered!", NamedTextColor.YELLOW);

        for (Player p : Bukkit.getOnlinePlayers()) {
            // CORRECTED: Send each message with a separate call.
            p.sendMessage(Component.empty());
            p.sendMessage(surgeMessage);
            p.sendMessage(effectMessage);
            p.sendMessage(Component.empty());
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.2f);
        }

        for (Wizard wiz : getActiveWizards()) {
            wiz.setCooldownMultiplier(wiz.getCooldownMultiplier() * 0.9F, false); // Example: 10% reduction
            game.getWandManager().updateAllWandDisplays(wiz.getPlayer());
        }
    }

    public Instant getLastSurgeTime() {
        return lastSurge;
    }
}