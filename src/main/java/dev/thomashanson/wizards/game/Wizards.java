package dev.thomashanson.wizards.game;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.wrappers.Pair;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.damage.types.VoidDamageTick;
import dev.thomashanson.wizards.event.SpellCastEvent;
import dev.thomashanson.wizards.event.SpellCollectEvent;
import dev.thomashanson.wizards.game.kit.KitSelectMenu;
import dev.thomashanson.wizards.game.kit.WizardsKit;
import dev.thomashanson.wizards.game.kit.types.KitSorcerer;
import dev.thomashanson.wizards.game.loot.LootManager;
import dev.thomashanson.wizards.game.manager.GameManager;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.thomashanson.wizards.game.manager.PlayerStatsManager;
import dev.thomashanson.wizards.game.manager.PlayerStatsManager.StatType;
import dev.thomashanson.wizards.game.manager.TeamManager;
import dev.thomashanson.wizards.game.manager.WandManager;
import dev.thomashanson.wizards.game.manager.WizardManager;
import dev.thomashanson.wizards.game.mode.GameTeam;
import dev.thomashanson.wizards.game.mode.WizardsMode;
import dev.thomashanson.wizards.game.overtime.Disaster;
import dev.thomashanson.wizards.game.overtime.types.DisasterEarthquake;
import dev.thomashanson.wizards.game.overtime.types.DisasterHail;
import dev.thomashanson.wizards.game.overtime.types.DisasterLightning;
import dev.thomashanson.wizards.game.overtime.types.DisasterManaStorm;
import dev.thomashanson.wizards.game.overtime.types.DisasterMeteors;
import dev.thomashanson.wizards.game.potion.Potion;
import dev.thomashanson.wizards.game.potion.PotionType;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.SpellBook;
import dev.thomashanson.wizards.game.spell.SpellManager;
import dev.thomashanson.wizards.game.state.GameState;
import dev.thomashanson.wizards.game.state.types.ActiveState;
import dev.thomashanson.wizards.game.state.types.OvertimeState;
import dev.thomashanson.wizards.game.state.types.WinnerState;
import dev.thomashanson.wizards.map.LocalGameMap;
import dev.thomashanson.wizards.map.MapBorder;
import dev.thomashanson.wizards.util.DebugUtil;
import dev.thomashanson.wizards.util.MathUtil;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;


public class Wizards implements Listener, Tickable {

    private final WizardsPlugin plugin;
    private final WizardManager wizardManager;
    private final SpellManager spellManager;
    private final LootManager lootManager;
    private final TeamManager teamManager;
    private final WandManager wandManager;

    private WizardsMode currentMode = WizardsMode.SOLO_NORMAL;
    private MapBorder mapBorder;

    // Holds all teams that are currently still in the game.
    private final List<GameTeam> activeTeams = new ArrayList<>();

    // A map for instant lookups of a player's team.
    private final Map<UUID, GameTeam> playerTeamMap = new HashMap<>();

    // Stores teams in the order they are eliminated to create the final leaderboard.
    private final LinkedList<GameTeam> placementRankings = new LinkedList<>();

    private final KitSelectMenu kitSelectMenu;
    private final SpellBook spellBook;

    private PacketListener packetListener;

    private final Map<PotionType, Potion> potions = new HashMap<>();

    // Tracks SpellData for spells that might be cancelled on wand swap
    private final Map<UUID, Spell.SpellData> heldSlots = new HashMap<>();

    // Tracks items specifically dropped by the game (spells, wands, souls) for holograms/invulnerability
    private final List<Item> droppedGameItems = new ArrayList<>();
    private final Set<Material> bannedCrafting = new HashSet<>();

    private double currentMinX, currentMaxX, currentMinZ, currentMaxZ;
    private double initialMapMinY, initialMapMaxY;
    private boolean overtimeBordersActive = false;

    private Instant gameStartTime;
    private Disaster disaster;
    private final List<Disaster> disasters = new ArrayList<>();

    public static final NamespacedKey SPELL_ID_KEY;
    public static final NamespacedKey POTION_ID_KEY;

    static {
        SPELL_ID_KEY = new NamespacedKey(WizardsPlugin.getInstance(), "spell_type_id");
        POTION_ID_KEY = new NamespacedKey(WizardsPlugin.getInstance(), "potion_type_id");
    }

    public Wizards(WizardsPlugin plugin) {
        this.plugin = plugin;
        this.spellManager = plugin.getSpellManager();
        this.wizardManager = new WizardManager(plugin, this);
        this.lootManager = plugin.getLootManager();
        this.wandManager = new WandManager(plugin, this);
        this.teamManager = new TeamManager(this);

        this.kitSelectMenu = new KitSelectMenu(plugin);
        this.spellBook = new SpellBook(this, this.spellManager);

        // getGameManager().addKits(
        //     new KitScholar(this), new KitMage(this), new KitSorcerer(this),
        //     new KitMystic(this), new KitWarlock(this), new KitEnchantress(this),
        //     new KitLich(this)
        // );

        disasters.add(new DisasterHail(this));
        disasters.add(new DisasterLightning(this));
        disasters.add(new DisasterManaStorm(this));
        disasters.add(new DisasterMeteors(this));
        disasters.add(new DisasterEarthquake(this));
    }

    public void setupGame() {
        setupPackets(plugin);
        setupPotions();
        setupCrafting();
        initializeOvertimeBorders();

        GameManager gameManager = getGameManager();
        if (gameManager != null && gameManager.getKitManager() != null) {
            gameManager.getKitManager().getAllKits().forEach(kit -> Bukkit.getPluginManager().registerEvents(kit, plugin));
        }
    }

    private void setupPackets(WizardsPlugin plugin) {
        LanguageManager lang = plugin.getLanguageManager();
        this.packetListener = new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.WINDOW_ITEMS, PacketType.Play.Server.SET_SLOT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player player = event.getPlayer();
                Wizard wizard = getWizard(player); // Uses WizardManager's getWizard via delegation

                if (wizard == null || player.getOpenInventory().getTopInventory().getType() != InventoryType.CHEST || wizard.hasSpellBookOpen()) {
                    return;
                }

                PacketContainer packet = event.getPacket();
                PacketContainer newPacket = null; // Initialize to null
                boolean modified = false;

                if (packet.getType() == PacketType.Play.Server.WINDOW_ITEMS) {
                    newPacket = packet.deepClone(); // Clone only if we're going to modify
                    List<ItemStack> items = newPacket.getItemListModifier().getValues().get(0);
                    for (ItemStack item : items) {
                        if (item == null || item.getType() == Material.AIR) continue;
                        Spell spell = getSpell(item);
                        if (spell == null) continue;

                        modified = true;
                        final float manaFromDuplicate = spell.getRarity().getManaGain();

                        item.setAmount((int) (wizard.getLevel(spell.getKey()) < getMaxLevel(player, spell) ? 
                            wizard.getLevel(spell.getKey()) + 1 : manaFromDuplicate));
                        
                        ItemMeta meta = item.getItemMeta();

                        if (meta == null) continue;
                        boolean canLevelUp = wizard.getLevel(spell.getKey()) < getMaxLevel(player, spell);
                        String loreKey = canLevelUp ? "wizards.packet.chest.levelUp" : "wizards.packet.chest.convertToMana";
                        
                        meta.lore(Arrays.asList(
                            Component.empty(),
                            lang.getTranslated(player, loreKey)
                        ));
                        item.setItemMeta(meta);
                    }
                } else { // SET_SLOT
                    // This assumes a single item is being modified. Read, modify if necessary, write back.
                    ItemStack item = packet.getItemModifier().read(0); // Read from original packet
                    if (item != null && item.getType() != Material.AIR) {
                        Spell spell = getSpell(item);
                        if (spell != null) {
                            newPacket = packet.deepClone(); // Clone only if we're going to modify
                            ItemStack clonedItem = newPacket.getItemModifier().read(0); // Modify the clone
                            
                            clonedItem.setAmount(getLevel(player, spell) < getMaxLevel(player, spell) ?
                                    getLevel(player, spell) + 1 : (int) spell.getRarity().getManaGain());
                            newPacket.getItemModifier().write(0, clonedItem);
                            modified = true;
                        }
                    }
                }
                if (modified && newPacket != null) {
                    event.setPacket(newPacket);
                }
            }
        };
        ProtocolLibrary.getProtocolManager().addPacketListener(this.packetListener);
    }

    private void setupPotions() {

        Arrays.stream(PotionType.values()).forEach(potionType -> {

            try {

                Potion potion = potionType.getPotionClass().getConstructor().newInstance();

                potion.setPotion(potionType);
                potion.setGame(this);
                potions.put(potionType, potion);

                Bukkit.getPluginManager().registerEvents(potion, plugin);

            } catch (InstantiationException | IllegalAccessException | java.lang.reflect.InvocationTargetException | NoSuchMethodException e) {
                Bukkit.getLogger().severe(String.format("Failed to instantiate potion for type %s: %s", potionType.name(), e.getMessage()));
            }
        });
    }

    private void setupCrafting() {

        for (Spell spell : spellManager.getAllSpells().values()) {
            bannedCrafting.add(spell.getIcon());
        }

        Arrays.stream(Material.values()).forEach(material -> {

            String name = material.name();

            if (name.contains("SWORD") || name.contains("AXE") || name.contains("HOE"))
                bannedCrafting.add(material);
        });

        bannedCrafting.add(Material.STICK);
        bannedCrafting.add(Material.BUCKET);
    }

    public void reset() {
        // --- 1. Unregister All Event Listeners ---
        // This is the most critical step to prevent leaks.

        // Unregister listeners in this class (@EventHandler methods)
        HandlerList.unregisterAll(this);

        // Unregister ProtocolLib packet listeners
        ProtocolLibrary.getProtocolManager().removePacketListener(this.packetListener);

        // Unregister all Potion listeners
        for (Potion potion : potions.values()) {
            org.bukkit.event.HandlerList.unregisterAll(potion);
        }

        // Unregister all Kit listeners
        getGameManager().getKitManager().getAllKits().forEach(kit -> org.bukkit.event.HandlerList.unregisterAll(kit));


        // --- 2. Cancel Active Tasks & Disasters ---
        if (disaster != null) {
            // You will need to add a stop() or cancel() method to your Disaster class
            // that cancels any BukkitRunnables it has started.
            // disaster.stop(); 
            disaster = null;
        }


        // --- 3. Clean Up Game Entities & Holograms ---
        for (Item item : droppedGameItems) {
            // Remove the hologram associated with the item
            // plugin.getHologramManager().destroyHologram(item); // You'll need a method for this
            // Remove the item entity from the world
            item.remove();
        }
        droppedGameItems.clear();


        // --- 4. Reset Manager Classes ---
        // You must create a corresponding reset() method in your WizardManager class
        // to clear its internal maps and lists of players.
        if (wizardManager != null) {
            wizardManager.reset();
        }


        // --- 5. Clear All Game-State Collections ---
        activeTeams.clear();
        playerTeamMap.clear();
        placementRankings.clear();
        heldSlots.clear();


        // --- 6. Reset State Variables ---
        resetOvertimeBorders(); // You already have this method, which is great!
        gameStartTime = null;
        currentMode = WizardsMode.SOLO_NORMAL; // Or your default
    }

    public void setDisaster(Disaster disaster) {
        this.disaster = disaster;
    }

    public void pickRandomDisaster() {

        if (disaster == null) {

            if (disasters.isEmpty()) {
                Bukkit.getLogger().severe("Could not locate any disasters!");
                return;
            }

            int numDisasters = disasters.size();
            this.disaster = disasters.get(ThreadLocalRandom.current().nextInt(numDisasters));

            Bukkit.getLogger().info(String.format("%s selected as randomized disaster.", disaster.getName()));
        }
    }

    // Setup methods (spells, potions, disasters, crafting) remain
    // ... (setupSpells, setupPotions, setupDisasters, setupCrafting) ...

    public HashMap.SimpleEntry<Double, Wizard.DisplayType> getUsableTime(Wizard wizard, Spell spell) {
        // This important method remains in Wizards.java as it uses wizard data and game context.
        double usableTimeInSeconds = 0;
        Wizard.DisplayType displayType = Wizard.DisplayType.AVAILABLE;

        String spellKey = spell.getKey();

        // Spite Check
        if (wizard.isSpellDisabledBySpite(spellKey)) {
            usableTimeInSeconds = Duration.between(Instant.now(), wizard.getDisabledSpellSpiteUsableTime()).toMillis() / 1000.0;
            displayType = Wizard.DisplayType.DISABLED_BY_SPITE; // Assuming this enum value exists
            return new HashMap.SimpleEntry<>(Math.max(0, usableTimeInSeconds), displayType);
        }

        // Mana Check
        if (wizard.getMana() < wizard.getManaCost(spell)) {
            if (wizard.getManaPerTick() > 0) { // Avoid division by zero if mana regen is zero
                usableTimeInSeconds = (wizard.getManaCost(spell) - wizard.getMana()) / (wizard.getManaPerTick() * 20.0);
            } else {
                usableTimeInSeconds = Double.POSITIVE_INFINITY; // Effectively infinite time if no mana regen
            }
            displayType = Wizard.DisplayType.NOT_ENOUGH_MANA;
        }

        // Cooldown Check
        if (wizard.getCooldown(spellKey).isAfter(Instant.now())) {
            double cooldownTimeInSeconds = Duration.between(Instant.now(), wizard.getCooldown(spellKey)).toMillis() / 1000.0;
            if (cooldownTimeInSeconds > usableTimeInSeconds) { // If cooldown is the limiting factor
                usableTimeInSeconds = cooldownTimeInSeconds;
                displayType = Wizard.DisplayType.SPELL_COOLDOWN;
            } else if (displayType == Wizard.DisplayType.AVAILABLE) { // Only override if it was previously 'AVAILABLE'
                usableTimeInSeconds = cooldownTimeInSeconds;
                displayType = Wizard.DisplayType.SPELL_COOLDOWN;
            }
        }
        return new HashMap.SimpleEntry<>(Math.max(0, usableTimeInSeconds), displayType);
    }

    /**
     * Handles clicks within the Player's own inventory.
     * This is where wand slot protection and hotbar swapping logic resides.
     */
    public void handlePlayerInventoryClick(InventoryClickEvent event, Player player, Wizard wizard) {
        int clickedSlotInPlayerInv = event.getSlot(); // Slot index within the player's inventory
        LanguageManager lang = plugin.getLanguageManager();

        // Logic for player's wand slots (0 to wizard.getMaxWands() - 1)
        // Note: In a player's inventory, hotbar slots are 0-8. Other main inventory slots are 9-35.
        // We assume wand slots are directly mapped to the first N slots of the player inventory (0, 1, 2, ...).
        if (clickedSlotInPlayerInv >= 0 && clickedSlotInPlayerInv < wizard.getMaxWands()) {

            // Prevent interaction with unowned wand slots (applies to any click type on these slots)
            if (clickedSlotInPlayerInv >= wizard.getWandsOwned()) {
                event.setCancelled(true);
                player.sendMessage(lang.getTranslated(player, "wizards.game.wand.slotNotOwned")); // REFACTORED
                return;
            }

            // Handle wand swapping using number keys (hotbar swap)
            if (event.getClick() == ClickType.NUMBER_KEY) {
                int hotbarButtonPressed = event.getHotbarButton(); // This is the player inventory slot index (0-8 for hotbar)

                // Ensure the hotbar slot used for swapping is also an owned wand slot
                if (hotbarButtonPressed >= 0 && hotbarButtonPressed < wizard.getWandsOwned()) {
                    // We also need to check if the target hotbar slot IS NOT the same as the source slot
                    if (clickedSlotInPlayerInv == hotbarButtonPressed) {
                        event.setCancelled(true); // Cannot swap a slot with itself
                        return;
                    }

                    // At this point, both clickedSlotInPlayerInv (source) and hotbarButtonPressed (target)
                    // are owned wand slots and are distinct.
                    // Proceed with the swap, regardless of whether they are blank or have spells.

                    Spell spellInClickedSlot = wizard.getSpell(clickedSlotInPlayerInv);
                    Spell spellInHotbarSlot = wizard.getSpell(hotbarButtonPressed);

                    // Perform the actual swap in the Wizard's data
                    wizard.setSpell(clickedSlotInPlayerInv, spellInHotbarSlot.getKey());
                    wizard.setSpell(hotbarButtonPressed, spellInClickedSlot.getKey());

                    player.sendMessage(lang.getTranslated(player, "wizards.game.wand.swapped",
                        Placeholder.unparsed("slot_1", String.valueOf(clickedSlotInPlayerInv + 1)),
                        Placeholder.unparsed("slot_2", String.valueOf(hotbarButtonPressed + 1))
                    ));
                    Location playerLocation = player.getLocation();
                    if (playerLocation != null) {
                        player.playSound(playerLocation, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
                    }

                    // The default behavior of a NUMBER_KEY press is to swap the items in the inventory slots.
                    // If your wizard.setSpell() method also updates the ItemStacks in the player's inventory
                    // to visually represent the new spell (or blank state), then this default behavior is desired,
                    // and we should not cancel the event.
                    // If wizard.setSpell() ONLY changes internal data, you might need to:
                    // 1. event.setCancelled(true);
                    // 2. Manually update player.getInventory().setItem(clickedSlotInPlayerInv, newItem1); etc.
                    // Based on your original code's comment, we assume the default item swap is okay.

                } else {
                    event.setCancelled(true);
                    player.sendMessage(lang.getTranslated(player, "wizards.game.wand.invalidSwapTarget")); // REFACTORED
                }
            } else {
                event.setCancelled(true);
                player.sendMessage(lang.getTranslated(player, "wizards.game.wand.cannotMoveItem")); // REFACTORED
            }
        }
        // Other player inventory click logic can go here (e.g., if not a wand slot)
    }

    /**
     * Handles clicks within a Chest or DoubleChest inventory.
     * This is where consuming special items (spells, wands from chest) logic resides.
     */
    public void handleChestInventoryClick(InventoryClickEvent event, Player player, Wizard wizard) {
        ItemStack clickedItemInChest = event.getCurrentItem();

        // Ensure there's an item
        if (clickedItemInChest == null || clickedItemInChest.getType() == Material.AIR) {
            return;
        }

        boolean isGameItemAndConsumed = false;
        Spell spell = getSpell(clickedItemInChest); // Assuming getSpell checks item NBT or type

        if (spell != null) {
            learnSpell(player, spell);
            isGameItemAndConsumed = true;
        } else if (clickedItemInChest.getType() == Material.BLAZE_ROD) { // Check if it's a wand item
            // Ensure this isn't just any blaze rod, perhaps add a custom NBT if it's a "wand to be gained"
            wandManager.gainWand(player);
            isGameItemAndConsumed = true;
        }

        if (isGameItemAndConsumed) {
            event.setCancelled(true);       // Prevent the item from being picked up normally
            event.setCurrentItem(null);     // Remove the item from the chest
            Location playerLocation = player.getLocation();
            if (playerLocation != null) {
                player.playSound(playerLocation, Sound.BLOCK_NOTE_BLOCK_HAT, 0.7F, 0.0F);
            }
        }
        // If it's not a game item to be consumed, do nothing here, allowing normal chest interaction.
    }

    public void learnSpell(Player player, Spell spell) {
        Wizard wizard = getWizard(player);
        if (wizard == null) return;

        incrementStat(player, StatType.SPELLS_COLLECTED, 1);

        SpellCollectEvent event = new SpellCollectEvent(player, spell);
        event.setManaGain(spell.getRarity().getManaGain());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        LanguageManager lang = getPlugin().getLanguageManager();

        int currentLevel = wizard.getLevel(spell.getKey());
        int maxLevel = getMaxLevel(player, spell);

        if (currentLevel < maxLevel) {
            wizard.learnSpell(spell);
            player.sendMessage(lang.getTranslated(player, "wizards.learnedSpell",
                Placeholder.unparsed("spell_name", spell.getName())
            ));
            if (wizardManager != null) { // Update wand title if the spell was in a wand
                wandManager.updateAllWandDisplays(player);
            }
        } else {
            // ... (mana conversion / ally spell logic) ...
        }
    }

    /**
     * This single method replaces the entire old `updateGame` method.
     * It's called every server tick by the master loop in GameManager.
     */
    @Override
    public void tick(long gameTick) {
        if (!isLive()) return;

        // --- Every Tick (High Frequency) ---
        // Player-specific updates like mana regen, cooldown visuals, etc.
        for (Wizard wizard : wizardManager.getActiveWizards()) {
            wizardManager.updatePlayerTick(wizard.getPlayer(), gameTick);
        }

        // --- Every 4 Ticks (5 times per second) ---
        // Good for less critical updates like scoreboard.
        if (gameTick % 4 == 0) {
            getGameManager().getScoreboard().updateAllScoreboards();
        }
        
        // --- Every 15 Ticks ---
        // Border damage checks don't need to happen 20 times a second.
        if (gameTick % 15 == 0) {
            checkBorderDamage();
        }

        // --- Every 20 Ticks (Once per second) ---
        // Good for checking game state timers that don't need sub-second precision.
        if (gameTick % 20 == 0) {
            checkGameTimers();
        }
    }

    private void checkGameTimers() {
        GameState state = getGameManager().getState();
        if (state instanceof ActiveState) {
            Instant startTime = state.getStartTime();
            // Check for overtime transition
            if (startTime.plus(Duration.ofMinutes((currentMode.isBrawl() ? 20 : 10))).isBefore(Instant.now())) {
                getGameManager().setState(new OvertimeState());
            }

            // Check for power surge
            if (Duration.between(wizardManager.getLastSurgeTime(), Instant.now()).toMinutes() >= 2) {
                wizardManager.handleGlobalPowerSurge();
                if ("wizards.event.overtime".equals(getNextEvent().getFirst())) {
                    pickRandomDisaster();
                }
            }
        }
    }

    private void checkBorderDamage() {
        for (Player player : getPlayers(true)) { // getPlayers(true) uses WizardManager
            World world = player.getWorld();
            LocalGameMap currentMap = getActiveMap();
            if (currentMap == null || !world.equals(currentMap.getWorld())) continue;
            if (isInsideMap(player.getLocation())) continue; // Use location based check

            if (player.getGameMode() != GameMode.SURVIVAL) { // Non-survival players outside map
                player.teleport(currentMap.getSpectatorLocation());
                continue;
            }
            // Survival players outside map
            // UPDATED: getTrajectory2D is now getDirection, and setVelocity is now applyVelocity.
            Vector vector = MathUtil.getDirection(player.getLocation(), currentMap.getSpectatorLocation()).setY(0);
            double yBase = player.getLocation().getY() > currentMap.getBounds().getMaxY() ? 0 : 0.4;
            MathUtil.applyVelocity(player, vector, 1, yBase, 0, 10);

            DamageTick lastLoggedTick = plugin.getDamageManager().getLastLoggedTick(player.getUniqueId());
            VoidDamageTick borderTick = new VoidDamageTick(4.0, Instant.now(), lastLoggedTick);
            plugin.getDamageManager().damage(player, borderTick);

            Location playerLocation = player.getLocation();

            if (playerLocation != null) {
                world.playSound(playerLocation, Sound.BLOCK_NOTE_BLOCK_BASS, 2F, 1F);
                world.playSound(playerLocation, Sound.BLOCK_NOTE_BLOCK_BASS, 2F, 1F);
            }
        }
    }

    /**
     * A helper method to initiate a spell cast from a player's interaction.
     * This is typically called from a PlayerInteractEvent.
     */
    public void castSpell(Player player, Object interacted) {

        if (!isLive())
            return;

        Wizard wizard = getWizard(player);

        if (wizard != null) {
            
            // Ensure the player is interacting with a valid wand slot
            if (player.getInventory().getHeldItemSlot() >= wizard.getMaxWands())
                return;

            // CHANGED: wizard.getSpell() now correctly returns a 'Spell' object, not a 'SpellType'.
            Spell spell = wizard.getSpell(player.getInventory().getHeldItemSlot());

            if (spell != null) {
                // The 'spell' variable is the instance we need, so we pass it directly.
                castSpell(player, wizard, spell, interacted, false);
            }
        }
    }

    /**
     * The single, authoritative method for casting a spell.
     * It performs all prerequisite checks (mana, cooldown, events) and, upon
     * successful execution, applies all costs and effects to the Wizard.
     *
     * @param player     The player casting the spell.
     * @param wizard     The wizard object for the player.
     * @param spell      The actual Spell instance to be cast.
     * @param interacted The block or entity interacted with, if any.
     * @param quickcast  True if the spell was cast from the spellbook GUI.
     */
    public void castSpell(Player player, Wizard wizard, Spell spell, Object interacted, boolean quickcast) {

        int spellLevel = wizard.getLevel(spell.getKey());
        if (spellLevel <= 0) return;

        LanguageManager lang = plugin.getLanguageManager();

        // --- 1. Prerequisite Checks ---

        // Check for Spite effect
        if (wizard.isSpellDisabledBySpite(spell.getKey())) {
            player.sendMessage(lang.getTranslated(player, "wizards.game.spell.disabledBySpite"));
            return;
        }

        // Check for Cooldown
        if (wizard.getCooldown(spell.getKey()).isAfter(Instant.now())) {
            double timeLeft = Duration.between(Instant.now(), wizard.getCooldown(spell.getKey())).toMillis() / 1000.0;
            Component spellName = Component.text(spell.getName(), NamedTextColor.GOLD, TextDecoration.BOLD);
            player.sendMessage(lang.getTranslated(player, "wizards.notRecharged",
                Placeholder.component("spell_name", spellName),
                Placeholder.unparsed("time", String.format("%.1f", timeLeft))
            ));
            return;
        }

        // Check for Mana
        float manaCost = spell.getManaCost(spellLevel);
        if (wizard.getMana() < manaCost) {
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1F, 1F);
            player.sendMessage(lang.getTranslated(player, "wizards.notEnoughMana"));
            return;
        }

        // --- 2. Event & Spell Execution ---

        SpellCastEvent spellCastEvent = new SpellCastEvent(player, spell);
        Bukkit.getPluginManager().callEvent(spellCastEvent);
        if (spellCastEvent.isCancelled()) return;

        // Execute the spell's unique logic
        boolean castSuccess = spell.cast(player, spellLevel);

        // If the spell's internal logic failed (e.g., no valid target), do not proceed.
        if (!castSuccess) return;

        // --- 3. Apply Costs & Effects (This is the new "charge" logic) ---

        // A. Consume Mana
        wizard.setMana(wizard.getMana() - manaCost);

        // B. Set Cooldown
        double cooldownTicks = spell.getCooldown(spellLevel);
        if (cooldownTicks > 0) {
            // Apply cooldown modifiers (e.g., from kits or potions) here if you have them
            // cooldownTicks *= wizard.getCooldownModifier();
            wizard.setCooldown(spell.getKey(), Duration.ofMillis((long) (cooldownTicks * 50)));
        }

        // --- 4. Post-Cast Housekeeping ---

        // Handle special SpellBlock casting
        if (spell instanceof Spell.SpellBlock && interacted instanceof Block) {
            ((Spell.SpellBlock) spell).castSpell(player, (Block) interacted, spellLevel);
        }
        
        // Track the active spell for potential cancellation on wand swap
        if (spell.isCancelOnSwap()) {
             heldSlots.put(player.getUniqueId(), new Spell.SpellData(player.getInventory().getHeldItemSlot(), spell, quickcast));
        }

        incrementStat(player, StatType.SPELLS_CAST, 1);

        // Immediate UI updates
        wizardManager.updatePlayerCooldownVisuals(wizard);
        wizardManager.updateActionBar(wizard);
    }

    public void dropItems(Player player, WizardsKit killerKit) {

        Wizard wizard = getWizard(player);

        Set<Spell> droppedSpells = new HashSet<>();
        List<ItemStack> droppedItems = new ArrayList<>();

        for (int slot = 0; slot < wizard.getMaxWands(); slot++) {
            Spell spell = wizard.getSpell(slot);
            if (spell != null && !spell.getKey().equalsIgnoreCase("wizards.spell.mana_bolt")) {
                droppedSpells.add(spell);
            }
        }

        for (String key : wizard.getKnownSpellKeys()) {
            if (ThreadLocalRandom.current().nextDouble() <= 0.2) {
                droppedSpells.add(spellManager.getSpell(key));
            }
        }

        getItems(player).forEach(item -> {

            // Remove the dyed leather armor
            if (item.getItemMeta() != null && item.getItemMeta() instanceof LeatherArmorMeta) {
                ItemMeta meta = Bukkit.getItemFactory().getItemMeta(item.getType());
                item.setItemMeta(meta);
            }

            player.getWorld().dropItemNaturally(player.getLocation(), item);
        });

        droppedSpells.forEach(droppedSpell -> {
            ItemStack item = droppedSpell.createItemStack(null, 1, 1);
            droppedItems.add(ItemBuilder.from(item).glow().build());
        });

        double dropWandChance;

        dropWandChance = switch (wizard.getWandsOwned()) {
            case 2 -> 0.2;
            case 3 -> 0.4;
            case 4 -> 0.6;
            case 5 -> killerKit instanceof KitSorcerer ? 1 : 0.8;
            case 6 -> 1;
            default -> 0;
        };

        if (ThreadLocalRandom.current().nextDouble() < dropWandChance)
            droppedItems.add(wandManager.createLootableWandItem());

        droppedItems.add(
                ItemBuilder
                    .from(Material.NETHER_STAR)
                    .name(Component.text(System.currentTimeMillis() + ""))
                    .build()
        );

        // TODO: 7/1/21 add bounty amount to nether star

        Collections.shuffle(droppedItems, ThreadLocalRandom.current());

        double beginnerAngle = Math.random() * 360;

        for (ItemStack itemStack : droppedItems) {

            Item item = player.getWorld().dropItem(player.getLocation(), itemStack);
            item.setPickupDelay(60);

            beginnerAngle += 360.0 / droppedItems.size();

            double angle = (((2 * Math.PI) / 360) * beginnerAngle) % 360;
            double x = 0.2 * Math.cos(angle);
            double z = 0.2 * Math.sin(angle);

            item.setVelocity(new Vector(x, 0.3, z));
        }
    }

    /**
     * Gathers all items from a player's inventory, excluding a specified number of initial slots (for wands).
     * This includes main inventory, armor, off-hand, and the item on the cursor.
     *
     * @param player The player to get items from.
     * @return A List of ItemStacks to be dropped.
     */
    private List<ItemStack> getItems(Player player) {
        List<ItemStack> items = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        Wizard wizard = getWizard(player); // Assuming getWizard(player) works correctly.

        // Define the starting slot. Wands are typically in the first few hotbar slots (0-8).
        int startSlot = wizard.getMaxWands();

        // Iterate through the main inventory storage (slots 0-35).
        // This correctly skips the first 'startSlot' slots.
        for (int i = startSlot; i <= 35; i++) {
            ItemStack item = inventory.getItem(i);

            // Check if the item exists
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }

        // Separately add armor contents to avoid double-counting.
        for (ItemStack armorPiece : inventory.getArmorContents()) {
            if (armorPiece != null && !armorPiece.getType().isAir()) {
                items.add(armorPiece.clone());
            }
        }

        // Separately add the off-hand item.
        ItemStack offHandItem = inventory.getItemInOffHand();
        if (!offHandItem.getType().isAir()) {
            items.add(offHandItem.clone());
        }

        // Don't forget the item on the cursor!
        ItemStack cursorItem = player.getItemOnCursor();
        if (!cursorItem.getType().isAir()) {
            items.add(cursorItem.clone());
        }

        return items;
    }
    
    // getKit method
    public WizardsKit getKit(Player player) {
        return getGameManager().getKitManager().getKit(player);
    }

    /**
     * Retrieves the SpellType from an ItemStack using PersistentDataContainer.
     * Falls back to display name check if PDC is not found (for backward compatibility or items not yet updated).
     */
    public Spell getSpell(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Check if our custom spell key exists on the item
        if (container.has(Spell.SPELL_ID_KEY, PersistentDataType.STRING)) {

            // Get the key (e.g., "FIREBALL") from the item's data
            String spellKey = container.get(Spell.SPELL_ID_KEY, PersistentDataType.STRING);
            
            // Use the SpellManager to look up the corresponding Spell object
            return spellManager.getSpell(spellKey);
        }
        
        return null;
    }

    /**
     * Retrieves the PotionType from an ItemStack using PersistentDataContainer.
     * Falls back to item comparison if PDC is not found.
     */
    public PotionType getPotion(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer container = meta.getPersistentDataContainer();

        // 1. Try PDC first
        if (container.has(POTION_ID_KEY, PersistentDataType.STRING)) {
            String potionName = container.get(POTION_ID_KEY, PersistentDataType.STRING);
            try {
                return PotionType.valueOf(potionName);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning(String.format("Invalid potion_type_id '%s' found in PDC for item: %s", potionName, item.getType()));
            }
        }

        // 2. Fallback: Item Comparison (less reliable)
        // TODO: Phase out item comparison once all potions are created with PDC.
        for (PotionType potionType : potions.keySet()) { // 'potions' is Map<PotionType, Potion>
            ItemStack potionItemToCompare = potionType.createPotion(); // Assumes PotionType.createPotion sets PDC
            if (arePotionsEqual(item, potionItemToCompare)) { // arePotionsEqual might need an update or rely on isSimilar
                // plugin.getLogger().info("Identified potion " + potionType.name() + " by item comparison (fallback).");
                return potionType;
            }
        }
        return null;
    }

    private boolean arePotionsEqual(ItemStack item1, ItemStack item2) {
        if (item1 == null || item2 == null) return false;
        if (item1.getType() != item2.getType()) return false;
        return item1.isSimilar(item2);
    }

    // Other getters and utility methods
    public SpellBook getSpellBook() { return this.spellBook; }
    public Wizard getWizard(Player player) { return wizardManager.getWizard(player); }

    public int getLevel(Player player, Spell spell) { // Takes Spell
        Wizard w = getWizard(player);
        return w != null ? w.getLevel(spell.getKey()) : 0;
    }

    public int getMaxLevel(Player player, Spell spell) { // Takes Spell
        WizardsKit k = getKit(player);
        return k != null ? k.getModifiedMaxSpellLevel(spell, spell.getMaxLevel()) : spell.getMaxLevel();
    }

    public void incrementStat(Player player, PlayerStatsManager.StatType stat, double value) { plugin.getStatsManager().incrementStat(player, stat, value); }

    public List<Player> getPlayers(boolean aliveOnly) {
        // If we just want everyone, return all online players.
        if (!aliveOnly) {
            return new ArrayList<>(Bukkit.getOnlinePlayers());
        }

        // Create a Set of participant UUIDs for efficient lookup (O(1) average time complexity)
        Set<UUID> participantUuids = wizardManager.getActiveWizards().stream()
                .map(Wizard::getUniqueId)
                .collect(Collectors.toSet());

        // Filter all online players
        return Bukkit.getOnlinePlayers().stream()
                // To be "alive", a player MUST be a registered participant AND not be in spectator mode.
                .filter(player -> participantUuids.contains(player.getUniqueId()) 
                                && player.getGameMode() != GameMode.SPECTATOR)
                .collect(Collectors.toList());
    }
    public GameTeam.TeamRelation getRelation(Player a, Player b) { /* ... (unchanged) ... */ return null;} // Placeholder
    /**
     * Checks if a location is inside the map's boundaries, plus a grace area.
     * @param location The location to check.
     * @return True if the location is within the map's playable area.
     */
    public boolean isInsideMap(Location location) {
        if (getActiveMap() == null) {
            return false;
        }

        int grace = 20;

        // The BoundingBox object makes this check much cleaner and more reliable.
        // We get the map's bounds and expand it by the grace amount.
        BoundingBox mapBounds = getActiveMap().getBounds().clone().expand(grace);

        // Then, we simply check if the location is contained within the expanded box.
        return mapBounds.contains(location.toVector());
    }
    /**
     * Checks if the game has a winner and transitions to the WinnerState if so.
     */
    public void checkEndGameCondition() {
        DebugUtil.debugMessage("Checking end game condition!");
        DebugUtil.debugMessage(activeTeams.size() + " active teams!");
        if (activeTeams.size() <= 1) {
            // Game is over!

            // If there's a team left, they are the winner.
            if (!activeTeams.isEmpty()) {
                GameTeam winningTeam = activeTeams.get(0);
                placementRankings.addFirst(winningTeam); // Add winner to the very front (1st place)
            }

            // We now have a complete, ordered list of placements!
            // The list is [1st, 2nd, 3rd, ...]
            getGameManager().setState(new WinnerState(placementRankings));
        }
    }

    public Pair<String, Instant> getNextEvent() {
        // The String is now a translation key, not a final display name.
        String nextEventKey;
        Instant nextEventTime;
        GameState state = getGameManager().getState();
        Instant startTime = state.getStartTime();

        if (state instanceof ActiveState) {
            Instant overtimeTriggerTime = startTime.plus(Duration.ofMinutes(currentMode.isBrawl() ? 20 : 10));
            Instant nextSurgeTime = wizardManager.getLastSurgeTime().plus(Duration.ofMinutes(2));

            if (nextSurgeTime.isBefore(overtimeTriggerTime) && Instant.now().isBefore(nextSurgeTime.minusSeconds(5))) {
                nextEventKey = "wizards.event.powerSurge"; // Changed from "Power Surge"
                nextEventTime = nextSurgeTime;
            } else {
                nextEventKey = "wizards.event.overtime"; // Changed from "Overtime"
                nextEventTime = overtimeTriggerTime;
            }
        } else if (state instanceof OvertimeState) {
            nextEventKey = "wizards.event.gameEnd"; // Changed from "Game End"
            nextEventTime = startTime.plus(Duration.ofMinutes(10));
        } else { // Lobby/Prepare
            nextEventKey = "wizards.event.gameStart"; // Changed from "Game Start"
            nextEventTime = startTime.plus(Duration.ofSeconds(currentMode.isBrawl() ? 15 : 10));
        }
        return new Pair<>(nextEventKey, nextEventTime);
    }

    public boolean isLive() { GameState state = getGameManager().getState(); return state instanceof ActiveState || state instanceof OvertimeState; }
    public boolean isOvertime() { return getGameManager().getState() instanceof OvertimeState; }
    public void initializeOvertimeBorders() {
        LocalGameMap activeMap = getActiveMap();
        if (activeMap == null) {
            Bukkit.getLogger().severe("Cannot initialize overtime borders: activeMap is null!");
            return;
        }

        // Get the BoundingBox from the map
        BoundingBox bounds = activeMap.getBounds();

        // Set the current border dimensions from the BoundingBox
        this.currentMinX = bounds.getMinX();
        this.currentMaxX = bounds.getMaxX();
        this.currentMinZ = bounds.getMinZ();
        this.currentMaxZ = bounds.getMaxZ();
        
        this.initialMapMinY = bounds.getMinY();
        this.initialMapMaxY = bounds.getMaxY();
        
        this.overtimeBordersActive = true;
        Bukkit.getLogger().info(String.format("Overtime borders initialized: X(%.1f-%.1f), Z(%.1f-%.1f)", currentMinX, currentMaxX, currentMinZ, currentMaxZ));
    }

    public void updateOvertimeBorders(double minX, double maxX, double minZ, double maxZ) {
        this.currentMinX = minX;
        this.currentMaxX = maxX;
        this.currentMinZ = minZ;
        this.currentMaxZ = maxZ;
        this.overtimeBordersActive = true;
    }

    public void resetOvertimeBorders() {
        this.overtimeBordersActive = false;

        if (getActiveMap() != null) {
            // Get the BoundingBox from the map
            BoundingBox bounds = getActiveMap().getBounds();

            // Reset the current border dimensions from the BoundingBox
            this.currentMinX = bounds.getMinX();
            this.currentMaxX = bounds.getMaxX();
            this.currentMinZ = bounds.getMinZ();
            this.currentMaxZ = bounds.getMaxZ();
        }
        Bukkit.getLogger().info("Overtime borders reset.");
    }

    public boolean areOvertimeBordersActive() { return overtimeBordersActive; }
    public double getCurrentMinX() { return overtimeBordersActive && getActiveMap() != null ? currentMinX : (getActiveMap() != null ? getActiveMap().getBounds().getMinX() : 0); }
    public double getCurrentMaxX() { return overtimeBordersActive && getActiveMap() != null ? currentMaxX : (getActiveMap() != null ? getActiveMap().getBounds().getMaxX() : 0); }
    public double getCurrentMinZ() { return overtimeBordersActive && getActiveMap() != null ? currentMinZ : (getActiveMap() != null ? getActiveMap().getBounds().getMinZ() : 0); }
    public double getCurrentMaxZ() { return overtimeBordersActive && getActiveMap() != null ? currentMaxZ : (getActiveMap() != null ? getActiveMap().getBounds().getMaxZ() : 0); }
    public double getInitialMapMinY() { return initialMapMinY; }
    public double getInitialMapMaxY() { return initialMapMaxY; }
    public LocalGameMap getActiveMap() { return plugin.getMapManager().getActiveMap(); }
    /**
     * Gets the list of teams that are still alive and in the game.
     * @return A list of the currently active GameTeams.
     */
    public List<GameTeam> getActiveTeams() {
        return activeTeams;
    }
    public WizardsMode getCurrentMode() { return currentMode; }
    public void setCurrentMode(WizardsMode currentMode) {
    if (this.currentMode == currentMode) return;
        Bukkit.getLogger().info(String.format("Changed mode: %s", currentMode));
        this.currentMode = currentMode;
    }
    public MapBorder getMapBorder() { return mapBorder; }
    public Map<String, Spell> getSpells() {
        return spellManager.getAllSpells();
    }
    public Map<PotionType, Potion> getPotions() { return potions; }
    public void setGameStartTime(Instant time) {
        this.gameStartTime = time;
    }
    public Instant getGameStartTime() {
        return this.gameStartTime;
    }
    public Disaster getDisaster() { return disaster; }

    public ItemStack getSpellMenuBook(Player player) {
        LanguageManager lang = plugin.getLanguageManager();
        return ItemBuilder.from(Material.ENCHANTED_BOOK)
                .name(
                    lang.getTranslated(player, "wizards.item.spellMenu.name")
                        .decoration(TextDecoration.ITALIC, false)
                )
                .lore(lang.getTranslated(player, "wizards.item.spellMenu.lore"))
                .build();
    }
    public KitSelectMenu getKitSelectMenu() { return kitSelectMenu; }
    public WizardManager getWizardManager() { return wizardManager; }
    public Map<UUID, Spell.SpellData> getPlayerHeldSpellData() { return this.heldSlots; }
    public GameManager getGameManager() { return plugin.getGameManager(); }
    public WizardsPlugin getPlugin() { return plugin; }

    public List<Disaster> getDisasters() {
        return this.disasters;
    }

    public List<Item> getDroppedGameItems() {
        return this.droppedGameItems;
    }

    public LootManager getLootManager() {
        return this.lootManager;
    }

    public TeamManager getTeamManager() {
        return this.teamManager;
    }

    public WandManager getWandManager() {
        return this.wandManager;
    }
}