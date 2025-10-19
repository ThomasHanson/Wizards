package dev.thomashanson.wizards.tutorial;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.github.juliarn.npclib.api.Npc;
import com.github.juliarn.npclib.api.Platform;
import com.github.juliarn.npclib.api.event.InteractNpcEvent;
import com.github.juliarn.npclib.api.profile.Profile;
import com.github.juliarn.npclib.bukkit.BukkitPlatform;
import com.github.juliarn.npclib.bukkit.BukkitWorldAccessor;
import com.github.juliarn.npclib.bukkit.util.BukkitPlatformUtil;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.manager.LanguageManager;
import dev.triumphteam.gui.builder.item.ItemBuilder;

/**
 * Manages all tutorial-related activities, including creating per-player NPCs
 * and handling on-demand schematic-based tutorial rooms.
 */
public class TutorialManager implements Listener {

    private final WizardsPlugin plugin;
    private final LanguageManager languageManager;
    private final ItemStack exitItem;

    private final Platform<World, Player, ItemStack, Plugin> platform;
    private final Map<UUID, Npc<World, Player, ItemStack, Plugin>> activeNpcs = new HashMap<>();

    private final Map<UUID, TutorialSession> activeSessions = new HashMap<>();
    private final Map<Location, TutorialSession> activeRooms = new HashMap<>(); // Tracks pasted rooms by their origin

    // Schematic-based room management fields
    private World tutorialWorld;
    private final int roomSpacing = 500;
    private final Queue<Location> availableGridLocations = new LinkedList<>();
    private Clipboard tutorialSchematic;
    private final Location lobbySpawn;
    private final Location npcLocation; // Single spawn location for all NPC instances

    public TutorialManager(WizardsPlugin plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();

        this.platform = BukkitPlatform.bukkitNpcPlatformBuilder()
                .extension(plugin) // Required: Links to your plugin for scheduling tasks
                .actionController(builder -> {}) // Enables the default action controller for automatic visibility
                .worldAccessor(BukkitWorldAccessor.nameBasedAccessor())
                .build();

        this.exitItem = ItemBuilder.from(Material.BARRIER)
                .name(languageManager.getTranslated(null, "wizards.item.tutorial.exit.name"))
                .lore(languageManager.getTranslated(null, "wizards.item.tutorial.exit.lore"))
                .build();

        String worldName = plugin.getConfig().getString("lobby.world");

        this.tutorialWorld = Bukkit.getWorld(worldName);
        this.lobbySpawn = plugin.getLobbySpawnLocation();

        if (this.tutorialWorld == null && this.lobbySpawn != null) {
            plugin.getLogger().info("[Debug] 'tutorialWorld' was null, attempting to get it from lobbySpawn...");
            this.tutorialWorld = this.lobbySpawn.getWorld();
            plugin.getLogger().info(String.format("[Debug] New value of 'tutorialWorld': %s", this.tutorialWorld));
        }

        ConfigurationSection npcSection = plugin.getConfig().getConfigurationSection("lobby.tutorial-npc");
        
        // FIX 1: Check if the config section exists before trying to use it.
        if (npcSection == null) {
            plugin.getLogger().severe("The 'lobby.tutorial-npc' section is missing from your config.yml! Tutorial system disabled.");
            this.npcLocation = null; // Set to null to prevent further errors
            return;
        }

        double x = npcSection.getDouble("x");
        double y = npcSection.getDouble("y");
        double z = npcSection.getDouble("z");
        float yaw = (float) npcSection.getDouble("yaw");
        float pitch = (float) npcSection.getDouble("pitch");

        this.npcLocation = new Location(this.tutorialWorld, x, y, z, yaw, pitch);

        if (this.tutorialWorld == null || this.lobbySpawn == null) {
            plugin.getLogger().severe("Tutorial world or lobby spawn not set! Tutorial system disabled.");
            return;
        }

        loadSchematic();
        populateGridLocations(100); // Pre-calculate 100 available spots for concurrent tutorials

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.registerNpcListeners();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isInTutorial(player)) return;

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (this.exitItem.isSimilar(event.getItem())) {
                endTutorial(player, false); // End the tutorial, not completed
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        Optional<TutorialSession> sessionOpt = getSession(player);

        if (sessionOpt.isEmpty() || sessionOpt.get().getCurrentStep() != TutorialSession.TutorialStep.LOOT_CHEST) {
            return;
        }

        // Check if they closed a chest and now have the Mana Bolt spell
        if (event.getInventory().getHolder() instanceof Chest && player.getInventory().contains(Material.DIAMOND_HOE)) { // Assuming Mana Bolt is a Diamond Hoe
            TutorialSession session = sessionOpt.get();
            session.setCurrentStep(TutorialSession.TutorialStep.EQUIP_SPELL);
            player.sendTitle("§aGood!", "§eNow, open your spellbook and bind it.", 10, 70, 20);
        }
    }

    private void registerNpcListeners() {
        this.platform.eventManager().registerEventHandler(InteractNpcEvent.class, event -> {
            Player player = event.player();
            Npc<World, Player, ItemStack, Plugin> clickedNpc = event.npc();
            Npc<World, Player, ItemStack, Plugin> playerNpc = activeNpcs.get(player.getUniqueId());

            if (playerNpc != null && playerNpc.equals(clickedNpc)) {
                Bukkit.getScheduler().runTask(this.plugin, () -> startTutorial(player));
            }
        });
    }

    private void loadSchematic() {
        File schematicFile = new File(plugin.getDataFolder(), "schematics/tutorial.schem");
        if (!schematicFile.exists()) {
            plugin.getLogger().severe("Could not find tutorial.schem in the schematics folder! The tutorial system will not work.");
            return;
        }

        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);

        // FIX 2: Check if WorldEdit could identify the schematic format.
        if (format == null) {
            plugin.getLogger().severe("Could not determine the format of the tutorial schematic. Is it a valid .schem file?");
            return;
        }

        try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
            this.tutorialSchematic = reader.read();
            plugin.getLogger().info("Tutorial schematic loaded successfully!");
        } catch (Exception e) {
            plugin.getLogger().severe(String.format("Failed to load schematic: %s", schematicFile.getName()));
            e.printStackTrace();
        }
    }

    private void populateGridLocations(int count) {
        for (int i = 0; i < count; i++) {
            availableGridLocations.add(new Location(tutorialWorld, i * roomSpacing, 100, 0));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (npcLocation == null) return;

        this.platform.newNpcBuilder()
                .position(BukkitPlatformUtil.positionFromBukkitLegacy(npcLocation))
                .profile(Profile.unresolved("Wizard"))
                .thenAccept(builder -> {
                    builder.flag(Npc.LOOK_AT_PLAYER, true);
                    builder.npcSettings(settings -> settings.profileResolver((p, n) -> {
                        var playerProfile = Profile.unresolved(player.getUniqueId());
                        return this.platform.profileResolver()
                                .resolveProfile(playerProfile)
                                .thenApply(profile -> n.profile().withProperties(profile.properties()));
                    }));

                    Npc<World, Player, ItemStack, Plugin> npc = builder.buildAndTrack();
                    activeNpcs.put(player.getUniqueId(), npc);
                });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isInTutorial(player)) {
            endTutorial(player, false);
        }

        Npc<World, Player, ItemStack, Plugin> npc = activeNpcs.remove(player.getUniqueId());
        if (npc != null) {
            npc.unlink();
        }
    }

    public void startTutorial(Player player) {
        if (isInTutorial(player)) {
            player.sendMessage("§cYou are already in the tutorial!");
            return;
        }
        if (tutorialSchematic == null) {
            player.sendMessage("§cThe tutorial system is currently unavailable. Please contact an admin.");
            return;
        }

        Location pasteOrigin = availableGridLocations.poll();
        if (pasteOrigin == null) {
            player.sendMessage("§cSorry, all tutorial instances are busy. Please try again in a moment.");
            return;
        }

        player.sendMessage("§aPreparing your private tutorial room, please wait...");

        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                // FIX 3: Check if the paste location's world is valid before using it.
                World world = pasteOrigin.getWorld();
                if (world == null) {
                    plugin.getLogger().severe("Cannot paste tutorial schematic: The world for the paste origin is null!");
                    availableGridLocations.add(pasteOrigin); // Return location to pool
                    return;
                }
                
                com.sk89q.worldedit.world.World worldEditWorld = BukkitAdapter.adapt(world);
                BlockVector3 pasteVector = BlockVector3.at(pasteOrigin.getBlockX(), pasteOrigin.getBlockY(), pasteOrigin.getBlockZ());

                try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder().world(worldEditWorld).build()) {
                    Operation operation = new ClipboardHolder(this.tutorialSchematic)
                            .createPaste(editSession)
                            .to(pasteVector)
                            .ignoreAirBlocks(false)
                            .build();

                    Operations.complete(operation);
                }

                Bukkit.getScheduler().runTask(this.plugin, () -> {

                    plugin.getLogger().info("--- TUTORIAL SPAWN DEBUG ---");
                    plugin.getLogger().info(String.format("Paste Origin (Schematic's 0,0,0): %s", pasteOrigin.toVector()));

                    int dummySpawnOffsetX = 5;
                    int dummySpawnOffsetY = 1;
                    int dummySpawnOffsetZ = 10;

                    int chestOffsetX = 5;
                    int chestOffsetY = 1;
                    int chestOffsetZ = 2;

                    BlockVector3 dimensions = this.tutorialSchematic.getDimensions();
                    BlockVector3 minPoint = this.tutorialSchematic.getMinimumPoint();

                    plugin.getLogger().info(String.format("Schematic Dimensions (X,Y,Z): %s", dimensions));
                    plugin.getLogger().info(String.format("Schematic Minimum Point: %s", minPoint));

                    Location playerSpawn = pasteOrigin.clone();
                    Location dummySpawn = pasteOrigin.clone().add(dummySpawnOffsetX, dummySpawnOffsetY, dummySpawnOffsetZ);
                    Location chestLocation = pasteOrigin.clone().add(chestOffsetX, chestOffsetY, chestOffsetZ);

                    TutorialRoom room = new TutorialRoom(playerSpawn, dummySpawn, chestLocation);
                    TutorialSession session = new TutorialSession(player.getUniqueId(), room);

                    activeSessions.put(player.getUniqueId(), session);
                    activeRooms.put(pasteOrigin, session);

                    session.begin();
                });

            } catch (WorldEditException e) {
                e.printStackTrace();
                availableGridLocations.add(pasteOrigin);
            }
        });
    }

    public void endTutorial(Player player, boolean completed) {
        TutorialSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) return;

        session.end(this.lobbySpawn);

        Optional<Location> originOpt = activeRooms.entrySet().stream()
                .filter(entry -> entry.getValue().equals(session))
                .map(Map.Entry::getKey)
                .findFirst();

        if (originOpt.isPresent()) {
            Location pasteOrigin = originOpt.get();
            activeRooms.remove(pasteOrigin);

            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
                try {
                    // We can reuse the same world check logic here for safety
                    World world = pasteOrigin.getWorld();
                    if (world == null) {
                        plugin.getLogger().severe("Cannot clear tutorial room: World is null for paste origin " + pasteOrigin);
                        return;
                    }

                    com.sk89q.worldedit.world.World worldEditWorld = BukkitAdapter.adapt(world);

                    Region region = this.tutorialSchematic.getRegion().clone();
                    region.shift(BlockVector3.at(pasteOrigin.getX(), pasteOrigin.getY(), pasteOrigin.getZ()));

                    try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder().world(worldEditWorld).build()) {
                        editSession.setBlocks(region, BlockTypes.AIR.getDefaultState());
                    }

                    availableGridLocations.add(pasteOrigin);

                } catch (WorldEditException e) {
                    e.printStackTrace();
                    plugin.getLogger().severe(String.format("Failed to clear tutorial room at %s", pasteOrigin.toVector()));
                }
            });
        }

        if (completed) {
            player.sendMessage("§aCongratulations! You have unlocked the Magician Kit!");
            // plugin.getDatabaseManager().unlockKit(player.getUniqueId(), "magician");
        }
    }

    public Optional<TutorialSession> getSession(Player player) {
        return Optional.ofNullable(activeSessions.get(player.getUniqueId()));
    }

    public boolean isInTutorial(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }
}