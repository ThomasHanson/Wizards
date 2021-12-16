package dev.thomashanson.wizards.game;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.wrappers.Pair;
import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.damage.DamageTick;
import dev.thomashanson.wizards.damage.types.PlayerDamageTick;
import dev.thomashanson.wizards.damage.types.VoidDamageTick;
import dev.thomashanson.wizards.event.*;
import dev.thomashanson.wizards.game.kit.KitSelectMenu;
import dev.thomashanson.wizards.game.kit.WizardsKit;
import dev.thomashanson.wizards.game.kit.types.*;
import dev.thomashanson.wizards.game.loot.ChestLoot;
import dev.thomashanson.wizards.game.manager.DamageManager;
import dev.thomashanson.wizards.game.manager.GameManager;
import dev.thomashanson.wizards.game.mode.GameTeam;
import dev.thomashanson.wizards.game.mode.WizardsMode;
import dev.thomashanson.wizards.game.overtime.Disaster;
import dev.thomashanson.wizards.game.overtime.types.*;
import dev.thomashanson.wizards.game.potion.Potion;
import dev.thomashanson.wizards.game.potion.PotionType;
import dev.thomashanson.wizards.game.spell.*;
import dev.thomashanson.wizards.game.spell.types.SpellDoppelganger;
import dev.thomashanson.wizards.game.state.GameState;
import dev.thomashanson.wizards.game.state.types.*;
import dev.thomashanson.wizards.map.LocalGameMap;
import dev.thomashanson.wizards.util.EntityUtil;
import dev.thomashanson.wizards.util.LocationUtil;
import dev.thomashanson.wizards.util.MathUtil;
import dev.thomashanson.wizards.util.menu.ItemBuilder;
import dev.thomashanson.wizards.util.npc.NPC;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class Wizards implements Listener {

    private final WizardsPlugin plugin;

    private final Set<Wizard> wizards = new HashSet<>();
    private final List<GameTeam> teams = new ArrayList<>();

    private WizardsMode currentMode = WizardsMode.SOLO_NORMAL;
    private final List<GameTeam> gameTeams = new ArrayList<>();

    private final KitSelectMenu kitSelectMenu;

    private final ItemStack kitSelectIcon = new ItemBuilder(Material.NETHER_STAR)
            .withName(ChatColor.GOLD.toString() + ChatColor.BOLD + "Kit Selection")
            .withLore("", ChatColor.GRAY + "Right-Click with this to view the kits")
            .get();

    private final SpellBook spellBook;

    private final ItemStack spellMenuBook = new ItemBuilder(Material.ENCHANTED_BOOK)
            .withName(ChatColor.GOLD.toString() + ChatColor.BOLD + "Wizard Spells")
            .withLore("", ChatColor.GRAY + "Right-Click with this to view the spells")
            .get();

    private final ChestLoot chestLoot = new ChestLoot();

    private final Map<SpellType, Spell> spells = new HashMap<>();

    private final Map<PotionType, Potion> potions = new HashMap<>();
    private final Map<UUID, Map<PotionType, Instant>> potionTimes = new HashMap<>();

    /**
     * Represents the last time when a power surge
     * happened on the battlefield.
     */
    private Instant lastSurge;

    /**
     * Represents the slots held when a spell is cast.
     * Specific spells (Ice Shards, Doppelganger, etc.),
     * cancel when swapping wands. This keeps track of
     * that data.
     */
    private final Map<UUID, Spell.SpellData> heldSlots = new HashMap<>();

    private final List<Item> droppedItems = new ArrayList<>();
    private final Set<Material> permittedCrafting = new HashSet<>();

    private Disaster disaster;
    private final List<Disaster> disasters = new ArrayList<>();

    public static final Set<NPC> NPC_SET = new HashSet<>();

    public Wizards(WizardsPlugin plugin) {

        this.plugin = plugin;

        this.kitSelectMenu = new KitSelectMenu(this);
        this.spellBook = new SpellBook(this);

        plugin.getGameManager().addKits(
                new KitScholar(),
                new KitMage(),
                new KitSorcerer(),
                new KitMystic(),
                new KitWarlock(),
                new KitEnchantress(this),
                new KitLich(this)
        );
    }

    public void setupGame() {

        //setupPackets(plugin);

        addLoot();
        setupLoot();

        setupSpells();
        setupPotions();
        setupDisasters();
        setupCrafting();

        plugin.getGameManager().getWizardsKits().forEach(kit -> Bukkit.getPluginManager().registerEvents(kit, plugin));
    }

    public void setupWizard(Player player) {

        if (getWizard(player) != null)
            return;

        WizardsKit kit = getKit(player);

        if (kit == null)
            setKit(player, plugin.getGameManager().getWizardsKits().get(0));

        final float maxMana = kit instanceof KitWarlock ? 150 : 100;
        final int maxWands = kit instanceof KitSorcerer ? 6 : 5;

        Wizard wizard = new Wizard(player.getUniqueId(), maxWands, maxMana);
        wizard.setGame(this);

        wizards.add(wizard);

        if (kit instanceof KitScholar) {
            wizard.learnSpell(SpellType.MANA_BOLT);
            wizard.learnSpell(SpellType.FIREBALL);
            wizard.learnSpell(SpellType.ICE_PRISON);
        }

        wizard.learnSpell(SpellType.WIZARDS_COMPASS);

        wizard.setCooldownModifier(kit instanceof KitMage ? 0.9F : 1F, false);
        wizard.setManaPerTick((kit instanceof KitLich ? 1.5F : 2.5F) / 20F);
        wizard.setManaRate(kit instanceof KitMystic ? 1.1F : 1F, false);
        wizard.setWandsOwned(kit instanceof KitSorcerer ? 3 : 2);

        wizard.setManaBar(Bukkit.createBossBar(wizard.getManaBarTitle(), BarColor.BLUE, BarStyle.SOLID));
        wizard.getManaBar().setProgress(wizard.getMana() / wizard.getMaxMana());
        wizard.getManaBar().addPlayer(player);

        wizard.setPotionStatusBar(Bukkit.createBossBar(wizard.getPotionBarTitle(), BarColor.WHITE, BarStyle.SOLID));

        for (int i = 0; i < maxWands; i++) {

            if (i < wizard.getWandsOwned()) {
                player.getInventory().addItem(getBlankWand());

            } else {

                ItemBuilder builder = new ItemBuilder(Material.GRAY_DYE)
                        .withCustomModelData(i)
                        .withName(ChatColor.GRAY + "Empty wand slot")
                        .withLore("", ChatColor.GRAY.toString() + ChatColor.ITALIC + "Wands can be found in chests and dead players");

                player.getInventory().addItem(builder.get());
            }
        }

        updateWandTitle(player);
    }

    private void setupPackets(WizardsPlugin plugin) {

        PacketListener listener = new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.WINDOW_ITEMS, PacketType.Play.Server.SET_SLOT) {

            @Override
            public void onPacketSending(PacketEvent event) {

                PacketContainer packet = event.getPacket();
                Player player = event.getPlayer();

                Wizard wizard = getWizard(player);

                if (wizard == null)
                    return;

                Inventory inventory = player.getOpenInventory().getTopInventory();

                if (inventory.getType() != InventoryType.CHEST)
                    return;

                if (packet.getType() == PacketType.Play.Server.WINDOW_ITEMS) {

                    ItemStack[] itemModifier = packet.getItemArrayModifier().read(0);

                    /*
                    List<ItemStack> items = packet.getItemListModifier().getValues().get(0);

                    for (ItemStack item : items) {

                        if (item == null || item.getType() == Material.AIR)
                            continue;

                        SpellType spell = getSpell(item);

                        if (spell == null)
                            continue;

                        packet = event.getPacket().deepClone();
                        event.setPacket(packet);

                        item.setAmount (
                                wizard.getLevel(spell) < getMaxLevel(player, spell) ?
                                        wizard.getLevel(spell) + 1 :
                                        (int) spell.getRarity().getManaGain()
                        );

                        ItemMeta meta = item.getItemMeta();

                        if (!item.hasItemMeta())
                            continue;

                        meta.setLore (
                                Arrays.asList (
                                        "",
                                        ChatColor.AQUA + "Click to convert to mana"
                                )
                        );

                        item.setItemMeta(meta);
                    }
                     */

                } else {

                    packet = event.getPacket();

                    for (ItemStack item : packet.getItemModifier().getValues()) {

                        SpellType spell = getSpell(item);

                        if (spell != null) {

                            item.setAmount(

                                    wizard.getLevel(spell) < spell.getMaxLevel() ?
                                            wizard.getLevel(spell) + 1 :
                                            (int) spell.getRarity().getManaGain()
                            );
                        }
                    }
                }
            }
        };

        ProtocolLibrary.getProtocolManager().addPacketListener(listener);
    }

    private void addLoot() {

        Arrays.stream(SpellType.values()).forEach(spell -> chestLoot.addLoot(spell.getSpellBook(), spell.getRarity().getLootAmount()));
        Arrays.stream(PotionType.values()).forEach(potion -> chestLoot.addLoot(potion.createPotion(), SpellRarity.MEDIUM.getLootAmount()));

        chestLoot.addLoot(getBlankWand(), 4);

        chestLoot.addLoot(Material.GOLDEN_CARROT, SpellRarity.COMMON.getLootAmount(), 1, 2);
        chestLoot.addLoot(Material.COOKED_BEEF, SpellRarity.COMMON.getLootAmount(), 1, 2);
        chestLoot.addLoot(Material.BREAD, SpellRarity.COMMON.getLootAmount(), 1, 2);
        chestLoot.addLoot(Material.CAKE, SpellRarity.RARE.getLootAmount());
        chestLoot.addLoot(new ItemBuilder(Material.COOKED_CHICKEN).withName(ChatColor.WHITE + "Cheese").get(), SpellRarity.COMMON.getLootAmount(), 1, 2);

        chestLoot.addLoot(Material.WHEAT, SpellRarity.MEDIUM.getLootAmount(), 1, 2);
        chestLoot.addLoot(Material.OAK_PLANKS, SpellRarity.MED_RARE.getLootAmount(), 1, 8);

        chestLoot.addLoot(Material.GOLD_INGOT, SpellRarity.MEDIUM.getLootAmount(), 1, 2);
        chestLoot.addLoot(Material.IRON_INGOT, SpellRarity.MEDIUM.getLootAmount(), 1, 2);
        chestLoot.addLoot(Material.DIAMOND, SpellRarity.MED_RARE.getLootAmount());

        chestLoot.addLoot(Material.LEATHER_BOOTS, SpellRarity.MEDIUM.getLootAmount() + 1);
        chestLoot.addLoot(Material.LEATHER_LEGGINGS, SpellRarity.MEDIUM.getLootAmount() + 1);
        chestLoot.addLoot(Material.LEATHER_CHESTPLATE, SpellRarity.MEDIUM.getLootAmount() + 1);
        chestLoot.addLoot(Material.LEATHER_HELMET, SpellRarity.MEDIUM.getLootAmount() + 1);

        chestLoot.addLoot(Material.GOLDEN_BOOTS, SpellRarity.MEDIUM.getLootAmount());
        chestLoot.addLoot(Material.GOLDEN_CHESTPLATE, SpellRarity.MEDIUM.getLootAmount());
        chestLoot.addLoot(Material.GOLDEN_HELMET, SpellRarity.MEDIUM.getLootAmount());
        chestLoot.addLoot(Material.GOLDEN_LEGGINGS, SpellRarity.MEDIUM.getLootAmount());

        chestLoot.addLoot(Material.IRON_BOOTS, SpellRarity.MED_RARE.getLootAmount() - 1);
        chestLoot.addLoot(Material.IRON_CHESTPLATE, SpellRarity.MED_RARE.getLootAmount() - 1);
        chestLoot.addLoot(Material.IRON_HELMET, SpellRarity.MED_RARE.getLootAmount() - 1);
        chestLoot.addLoot(Material.IRON_LEGGINGS, SpellRarity.MED_RARE.getLootAmount() - 1);
    }

    private void setupLoot() {

        Set<Location> chests = getActiveMap().getChestLocations();

        if (chests.isEmpty()) {
            Bukkit.getLogger().info("There are no chests to fill!");
            return;
        }

        for (Location chestLocation : chests)
            fillChest(chestLocation.getBlock());

        /*
        int spawn = 0;
        Location spawnPoint = LocationUtil.getAverageLocation(getActiveMap().getSpawnLocations());

        // Chests
        System.out.println("Chests: " + Math.min(250, chests.size()));

        for (int i = 0; i < 250 && !chests.isEmpty(); i++) {

            Location location = chests.remove(ThreadLocalRandom.current().nextInt(chests.size()));

            fillChest(location.getBlock());

            if (MathUtil.getOffset2D(location, Objects.requireNonNull(spawnPoint)) < 8)
                spawn++;
        }

        for (Location location : chests) {

            if (spawn < 10 && MathUtil.getOffset2D(location, spawnPoint) < 8) {
                spawn++;
                fillChest(location.getBlock());
                continue;
            }

            location.getBlock().setType(Material.AIR);
        }
         */
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {

        Player player = event.getPlayer();
        Wizard wizard = getWizard(player);

        if (wizard == null)
            return;

        if (event.getMessage().equals("max")) {

            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {

                for (int i = 0; i < wizard.getMaxWands(); i++)
                    gainWand(player);

                for (SpellType spell : SpellType.values())
                    for (int i = 0; i < getMaxLevel(player, spell); i++)
                        learnSpell(player, spell);

                wizard.setMana(wizard.getMaxMana());

                player.getInventory().setItem(8, new ItemStack(Material.COOKED_BEEF, 64));
            });

            event.setCancelled(true);

        } else if (event.getMessage().equals("event")) {

            Pair<String, Instant> eventPair = getNextEvent();
            Instant instant = eventPair.getSecond();

            Duration between = Duration.between(Instant.now(), instant);

            String message = String.format("%s - %01d:%02d", eventPair.getFirst(), between.toMinutes(), between.toSecondsPart());
            player.sendMessage(message);

            event.setCancelled(true);
        }
    }

    public void fillChest(Block block) {

        BlockState state = block.getState();

        if (!(state instanceof InventoryHolder))
            return;

        InventoryHolder holder = (InventoryHolder) state;

        Inventory inventory = null;

        if (holder instanceof Chest)
            inventory = ((Chest) state).getBlockInventory();
        else if (holder instanceof DoubleChest)
            inventory = ((DoubleChest) state).getInventory();

        fillChest(inventory);
    }

    private void fillChest(Inventory inventory) {

        if (inventory == null)
            return;

        if (!inventory.isEmpty())
            inventory.clear();

        boolean containsSpell = false;

        for (int i = 0; i < 5 || !containsSpell; i++) {

            ItemStack item = chestLoot.getLoot();
            SpellType spellType = getSpell(item);

            // Every chest has a spell.
            if (i > 5 && spellType == null)
                continue;

            if (spellType != null) {
                containsSpell = true;
                //UtilInv.addDullEnchantment(item);
            }

            int slot = ThreadLocalRandom.current().nextInt(inventory.getSize());
            inventory.setItem(slot, item);
        }

        Objects.requireNonNull(inventory.getLocation()).getBlock().getState().update(true);
    }

    private void setupSpells() {

        Arrays.stream(SpellType.values()).forEach(spellType -> {

            try {

                Spell spell = spellType.getSpellClass().getConstructor().newInstance();

                spell.setSpell(spellType);
                spell.setGame(this);
                spells.put(spellType, spell);

                Bukkit.getPluginManager().registerEvents(spell, plugin);

            } catch (Exception e) {
                Bukkit.getLogger().severe(e.getMessage());
            }
        });
    }

    private void setupPotions() {

        Arrays.stream(PotionType.values()).forEach(potionType -> {

            try {

                Potion potion = potionType.getPotionClass().getConstructor().newInstance();

                potion.setPotion(potionType);
                potion.setGame(this);
                potions.put(potionType, potion);

                Bukkit.getPluginManager().registerEvents(potion, plugin);

            } catch (Exception e) {
                Bukkit.getLogger().severe(e.getMessage());
            }
        });
    }

    private void setupDisasters() {

        disasters.addAll (
                Arrays.asList (
                        new DisasterEarthquake(this),
                        new DisasterHail(this),
                        new DisasterLightning(this),
                        new DisasterManaStorm(this),
                        new DisasterMeteors(this)
                )
        );


        if (disasters.isEmpty()) {
            Bukkit.getLogger().severe("Could not locate any disasters!");
            return;
        }

        int numDisasters = disasters.size();
        this.disaster = disasters.get(ThreadLocalRandom.current().nextInt(numDisasters));

        Bukkit.getLogger().info(disaster.getName() + " selected as randomized disaster.");
    }

    private void setupCrafting() {

        permittedCrafting.add(Material.CRAFTING_TABLE);
        permittedCrafting.add(Material.CHEST);
    }

    @EventHandler
    public void onChestOpen(InventoryOpenEvent event) {

        /*
         * Checking chest locations to prevent exploiting
         * and boosting your opened stats.
         */
        HumanEntity humanEntity = event.getPlayer();
        Inventory inventory = event.getInventory();

        if (inventory.getType() != InventoryType.CHEST)
            return;

        if (!(humanEntity instanceof Player))
            return;

        Player player = (Player) humanEntity;

        if (isLive() && getWizard(player) == null) {
            event.setCancelled(true);
            return;
        }

        Wizard wizard = getWizard(player);

        if (inventory.getLocation() != null)
            wizard.getChestsLooted().add(inventory.getLocation());

        // Then at end of game, we can use wizard.getChestsLooted().size();
    }

    private HashMap.SimpleEntry<Double, Wizard.DisplayType> getUsableTime(Wizard wizard, SpellType type) {

        double usableTime = 0;
        Wizard.DisplayType displayType = Wizard.DisplayType.DISABLED_SPELL;

        // cooldown, mana, spite

        if (wizard.getMana() < wizard.getManaCost(type)) {
            usableTime = (wizard.getManaCost(type) - wizard.getMana()) / (20 * wizard.getManaPerTick());
            displayType = Wizard.DisplayType.NOT_ENOUGH_MANA;
        }

        //double timeLength = (wizard.getDisabledSpell() == null || wizard.getDisabledUsableTime().isBefore(Instant.now())) ? 0 :
        //      Duration.between(Instant.now(), wizard.getDisabledUsableTime()).toMillis();

        double timeLength = wizard.getCooldown(type).isBefore(Instant.now()) ? 0 :
                Duration.between(Instant.now(), wizard.getCooldown(type)).toSeconds();

        if (timeLength > 0 && timeLength > usableTime) {
            usableTime = timeLength;
            displayType = Wizard.DisplayType.SPELL_COOLDOWN;
        }

        return new HashMap.SimpleEntry<>(usableTime, displayType);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {

        if (event.getClickedInventory() == null)
            return;

        /*
         * Handle wand swapping
        if (event.getClick() == ClickType.NUMBER_KEY) {

            Player player = (Player) event.getWhoClicked();
            Wizard wizard = getWizard(player);

            if (wizard == null)
                return;

            int oldSlot = event.getSlot();
            int newSlot = event.getHotbarButton();

            // Trying to swap something else

            if (oldSlot >= wizard.getWandsOwned())
                return;

            ItemStack currentItem =
                    event.getClick() == ClickType.NUMBER_KEY ?
                            event.getWhoClicked().getInventory().getItem(newSlot) :
                            event.getCurrentItem();

            if (currentItem == null || currentItem.getType() == Material.AIR)
                return;

            SpellType temp = wizard.getSpell(oldSlot);
        }
         */

        /*
         * Handle spell clicking from chests
         */
        if (
                event.getClickedInventory().getHolder() instanceof Chest ||
                        event.getClickedInventory().getHolder() instanceof DoubleChest
        ) {

            ItemStack item = event.getCurrentItem();
            boolean gameItem = false;

            if (item == null)
                return;

            Player player = (Player) event.getWhoClicked();
            Wizard wizard = getWizard(player);

            if (!(event.getInventory().getHolder() instanceof Chest) && (!(event.getInventory().getHolder() instanceof DoubleChest)))
                return;

            SpellType spell = getSpell(item);

            if (spell != null) {
                learnSpell(player, spell);
                gameItem = true;
            }

            if (item.getType() == Material.BLAZE_ROD) {

                if (event.getClickedInventory().getType() != InventoryType.PLAYER) {

                    if (wizard != null) {
                        gainWand(player);
                        gameItem = true;
                    }
                }
            }

            if (gameItem) {

                event.setCancelled(true);
                event.setCurrentItem(null);

                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7F, 0F);
            }
        }
    }

    public void learnSpell(Player player, SpellType spell) {

        Wizard wizard = getWizard(player);

        if (wizard == null)
            return;

        SpellCollectEvent event = new SpellCollectEvent(player, spell);
        event.setManaGain(spell.getRarity().getManaGain());

        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled())
            return;

        int spellLevel = wizard.getLevel(spell);

        if (spellLevel < getMaxLevel(player, spell)) {

            wizard.learnSpell(spell);

            TranslatableComponent learnedSpell = new TranslatableComponent("wizards.learnedSpell");
            learnedSpell.setColor(ChatColor.GREEN);

            player.spigot().sendMessage(learnedSpell);

        } else {

            if (!currentMode.isTeamMode()) {

                /*
                 * The game is not in a teams mode. We can add mana based
                 * on the rarity and just send translatable messages.
                 */
                wizard.addMana(event.getManaGain());

                TranslatableComponent duplicateSpell = new TranslatableComponent("Spellbook converted into "); //("wizards.duplicateSpell");

                if (spell.getRarity().getManaGain() > 0) {

                    TranslatableComponent extraMana = new TranslatableComponent(Float.toString(spell.getRarity().getManaGain()));
                    extraMana.setColor(ChatColor.GOLD);

                    player.spigot().sendMessage(extraMana);

                    duplicateSpell.addExtra(extraMana);
                    duplicateSpell.addExtra(" mana");

                    player.spigot().sendMessage(duplicateSpell);
                }

            } else {

                List<Player> allies = getAllies(player);

                if (allies == null || allies.isEmpty())
                    return;

                // Just in case teams are larger than 2 - Pick random ally
                Player randomAlly = allies.get(allies.size() - 1);

                // Find the ally (if alive)
                if (getWizard(randomAlly) != null) {

                    /*
                     * Now we are sure that the game is in teams mode and
                     * our team mate is still alive. They will learn the
                     * spell and receive the mana for it.
                     */

                    Wizard allyWizard = getWizard(randomAlly);
                    allyWizard.learnSpell(spell);

                    TranslatableComponent friendlyName = new TranslatableComponent("wizards.spellUpgradeTeam");
                    friendlyName.setColor(ChatColor.GOLD);

                    friendlyName.addExtra(friendlyName);
                    randomAlly.spigot().sendMessage(friendlyName);
                }
            }
        }
    }

    public void updateGame(AtomicInteger atomicInteger) {

        if (isLive()) {

            if (!currentMode.isTeamMode()) {

                if (getPlayers(true).size() <= 1) {

                    getGameManager().setState(new WinnerState("Someone won the game!"));
                }

            } else {

                ArrayList<GameTeam> teamsAlive = new ArrayList<>();

                for (GameTeam team : teams)
                    if (team.getPlayers(true).size() > 0)
                        teamsAlive.add(team);

                if (teamsAlive.size() <= 1) {

                    String formattedTeam = "";

                    if (teamsAlive.size() > 0) {

                        GameTeam winningTeam = teamsAlive.get(0);

                        List<String> playerNames = new ArrayList<>(winningTeam.getPlayers().size());
                        winningTeam.getPlayers().forEach(player -> playerNames.add(player.getName()));

                        String playerNameText = String.join(",", playerNames);
                        formattedTeam = playerNameText + " won the game!";
                    }

                    getGameManager().setState(new WinnerState(formattedTeam));
                }
            }
        }

        for (Player player : getPlayers(true)) {

            updateMana(player);
            updateCooldown(player);
            updatePotions(player);
        }

        if (atomicInteger.incrementAndGet() % 15 == 0) {

            atomicInteger.set(0);

            for (Player player : Bukkit.getOnlinePlayers()) {

                if (getWizard(player) == null)
                    continue;

                World world = player.getWorld();

                if (!world.equals(getActiveMap().getWorld()))
                    continue;

                if (isInsideMap(player))
                    continue;

                MathUtil.setVelocity (

                        player,

                        MathUtil.getTrajectory2D(
                                player.getLocation(),
                                getActiveMap().getSpectatorLocation()
                        ),

                        1, // strength
                        true, // ySet

                        player.getLocation().getY() > getActiveMap().getMaxY() ? 0 : 0.4, // yBase

                        0, // yAdd
                        10, // yMax
                        true // groundBoost
                );

                // Find the tick before void damage (to award player kills)
                DamageTick lastLoggedTick = plugin.getDamageManager().getLastLoggedTick(player.getUniqueId());

                // Create new void damage tick with reference to previous damage tick
                VoidDamageTick borderTick = new VoidDamageTick(4.0, "Border", lastLoggedTick, Instant.now());

                // Log void damage
                plugin.getDamageManager().damage(player, borderTick);

                for (int i = 0; i < 2; i++)
                    player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 2F, 1F);
            }
        }

        GameState state = plugin.getGameManager().getState();

        if (state instanceof ActiveState) {

            Instant startTime = state.getStartTime();

            if (startTime.plus(Duration.ofMinutes(10)).isBefore(Instant.now()))
                plugin.getGameManager().setState(new OvertimeState());
        }
    }

    private void updateMana(Player player) {

        Wizard wizard = getWizard(player);

        if (wizard == null)
            return;

        if (Duration.between(lastSurge, Instant.now()).toMinutes() >= 2) {

            if (isOvertime())
                return;

            lastSurge = Instant.now();

            plugin.getGameManager().announce("");
            plugin.getGameManager().announce("Power surges through the battlefield!", true);
            plugin.getGameManager().announce("Mana cost and spell cooldown has been lowered!");
            plugin.getGameManager().announce("");

            wizard.decreaseCooldown();
            updateWandTitle(player);
        }

        wizard.setMana(Math.min(wizard.getMana() + wizard.getManaPerTick(), wizard.getMaxMana()));

        float percentage = Math.min(Math.max(0, wizard.getMana() / wizard.getMaxMana()), 1);

        wizard.getManaBar().setTitle(wizard.getManaBarTitle());
        wizard.getManaBar().setProgress(percentage);

        updateActionBar(player);
    }

    private void updateCooldown(Player player) {

        if (!isLive())
            return;

        int heldSlot = player.getInventory().getHeldItemSlot();
        Wizard wizard = getWizard(player);

        if (wizard.getDisabledSpell() != null)
            if (wizard.getDisabledUsableTime().isBefore(Instant.now()))
                wizard.setDisabledSpell(null);

        for (int i = 0; i < wizard.getMaxWands(); i++) {

            if (i == heldSlot)
                continue;

            ItemStack item = player.getInventory().getItem(i);

            if (item == null)
                continue;

            SpellType spell = wizard.getSpell(i);

            if (spell == null)
                continue;

            int timeLeft = (int) (Math.ceil(getUsableTime(wizard, spell).getKey()));
            timeLeft = Math.max(0, Math.min(63, timeLeft)) + 1;

            if (timeLeft != item.getAmount()) {
                item.setAmount(timeLeft);
                player.getInventory().setItem(i, item);
            }

            //player.setCooldown(spell.getIcon(), timeLeft * 20);
        }
    }

    private void updatePotions(Player player) {

        if (!isLive())
            return;

        Wizard wizard = getWizard(player);

        if (wizard.getActivePotion() == null)
            return;

        if (!potionTimes.containsKey(player.getUniqueId()))
            return;

        Set<Map.Entry<PotionType, Instant>> entries = potionTimes.get(player.getUniqueId()).entrySet();

        for (Map.Entry<PotionType, Instant> entry : entries) {

            PotionType potionType = entry.getKey();
            Duration potionDuration = getPotionDuration(player, potionType);

            if (potionDuration == null)
                continue;

            if (!potionDuration.isZero()) {
                wizard.getPotionStatusBar().setTitle(wizard.getPotionBarTitle());
                wizard.getPotionStatusBar().setProgress(1 - (double) (potionDuration.toSeconds() / potionType.getDuration().toSeconds()));

            } else {
                wizard.getPotionStatusBar().removePlayer(player);
                potionTimes.remove(player.getUniqueId());
            }
        }
    }

    public void updateWandTitle(Player player) {

        Wizard wizard = getWizard(player);

        for (int slot = 0; slot < wizard.getMaxWands(); slot++) {

            if (slot < wizard.getWandsOwned()) {

                ItemStack item = player.getInventory().getItem(slot);
                SpellType type = wizard.getSpell(slot);

                if (item == null)
                    return;

                String display;

                if (type != null) {

                    display = ChatColor.YELLOW + "Mana: " + ChatColor.RESET + wizard.getManaCost(type)
                            + "      " +
                            ChatColor.YELLOW + "Cooldown: " + ChatColor.RESET
                            + wizard.getSpellCooldown(type);

                } else {
                    display = ChatColor.WHITE + "Right-Click to set a spell";
                }

                ItemMeta meta = item.getItemMeta();

                if (meta == null || (meta.hasDisplayName() && meta.getDisplayName().equals(display)))
                    return;

                meta.setDisplayName(display);
                item.setItemMeta(meta);
            }
        }
    }

    public void updateWandIcon(Player player, int oldSlot, int newSlot) {

        Wizard wizard = getWizard(player);

        if (oldSlot >= 0 && oldSlot < wizard.getMaxWands()) {

            SpellType spell = wizard.getSpell(oldSlot);

            if (spell != null) {

                int timeLeft = (int) (Math.ceil(getUsableTime(wizard, spell).getKey()));
                timeLeft = Math.max(0, Math.min(63, timeLeft)) + 1;

                ItemStack item = player.getInventory().getItem(oldSlot);

                if (item != null) {

                    item.setType(spell.getIcon());
                    item.setAmount(timeLeft);

                    player.getInventory().setItem(oldSlot, item);
                }
            }
        }

        if (newSlot >= 0 && newSlot < wizard.getMaxWands()) {

            SpellType spell = wizard.getSpell(newSlot);

            if (spell != null) {

                ItemStack item = player.getInventory().getItem(newSlot);

                if (item != null) {

                    /*
                     * Corner Case
                     *
                     * This code has to be implemented. The icon will
                     * still look the same (with packet manipulation),
                     * though we need the fishing rod in hand to cast
                     * a real fishing line in the spell.
                     */
                    item.setType (
                            spell != SpellType.GRAPPLING_BEAM ?
                                    spell.getWandElement().getMaterial() :
                                    Material.FISHING_ROD
                    );

                    item.setAmount(1);

                    ItemMeta meta = item.getItemMeta();

                    if (meta != null) {
                        meta.setUnbreakable(true);
                        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                        meta.setCustomModelData(2);
                    }

                    item.setItemMeta(meta);
                    player.getInventory().setItem(newSlot, item);
                }
            }
        }
    }

    private void updateActionBar(Player player) {

        Wizard wizard = getWizard(player);

        if (wizard == null)
            return;

        int slot = player.getInventory().getHeldItemSlot();

        if (slot < 0 || slot >= wizard.getMaxWands())
            return;

        SpellType spell = wizard.getSpell(slot);

        if (slot >= wizard.getMaxWands()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent());
            return;
        }

        if (spell == null) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("Spell Wand"));

        } else {

            Map.Entry<Double, Wizard.DisplayType> entry = getUsableTime(wizard, spell);

            double usableTime = entry.getKey();

            if (usableTime > 0) {

                usableTime = MathUtil.trim(1, usableTime);

                double maxSeconds = Math.max(wizard.getSpellCooldown(spell), wizard.getManaCost(spell) / (wizard.getManaPerTick() * 20));

                EntityUtil.displayProgress(player, ChatColor.RED + spell.getSpellName(), 1F - (usableTime / maxSeconds),

                        (entry.getValue() == Wizard.DisplayType.SPELL_COOLDOWN) ?
                                MathUtil.formatTime((long) usableTime * 1000, 1) :

                                (entry.getValue() == Wizard.DisplayType.NOT_ENOUGH_MANA) ?
                                usableTime + (usableTime < 60 ? "s" : "m") + " for mana" :

                                        usableTime + (usableTime < 60 ? "s" : "m") + ChatColor.RED + " from Spite"
                );

            } else {

                player.spigot().sendMessage (

                        ChatMessageType.ACTION_BAR,

                        new TextComponent (
                                ChatColor.GREEN.toString() + ChatColor.BOLD + spell.getSpellName() +
                                        " [" + wizard.getLevel(spell) + "]"
                        )
                );
            }
        }
    }

    private void gainWand(Player player) {

        Wizard wizard = getWizard(player);

        if (wizard == null)
            return;

        WandGainEvent event = new WandGainEvent(player);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled())
            return;

        int slot = wizard.getWandsOwned();

        if (slot >= 0 && slot < wizard.getMaxWands()) {

            wizard.setWandsOwned(wizard.getWandsOwned() + 1);
            player.getInventory().setItem(slot, getBlankWand());
            player.updateInventory();

            TranslatableComponent gainedWand = new TranslatableComponent("wizards.gainedWand");
            gainedWand.setColor(ChatColor.YELLOW);

            player.spigot().sendMessage(gainedWand);

        } else {

            wizard.addMana(100F);

            TranslatableComponent duplicateWand = new TranslatableComponent("wizards.duplicateWand");
            player.spigot().sendMessage(duplicateWand);
        }
    }

    @EventHandler
    public void interactMenu(PlayerInteractEvent event) {

        Player player = event.getPlayer();
        Action action = event.getAction();

        Wizard wizard = getWizard(player);
        SpellComparator comparator = SpellComparator.values()[ThreadLocalRandom.current().nextInt(SpellComparator.values().length)];

        // TODO: 2020-06-06 get sorting method
        //player.sendMessage("Sorting spell menu by: " + comparator.toString());

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)
            castSpell(event.getPlayer(), event.getClickedBlock());

        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {

            ItemStack item = event.getItem();

            if (item != null) {
                if (item.isSimilar(spellMenuBook))
                    spellBook.showBook(player, comparator);
                else if (item.isSimilar(kitSelectIcon))
                    kitSelectMenu.showMenu(player);
            }

            if (!isLive() || wizard == null)
                return;

            if (player.getInventory().getHeldItemSlot() >= wizard.getWandsOwned())
                return;

            if (player.getInventory().getHeldItemSlot() >= wizard.getMaxWands())
                return;

            if (event.getClickedBlock() == null || !(event.getClickedBlock().getState() instanceof InventoryHolder))
                spellBook.showBook(player, comparator);
        }
    }

    private void castSpell(Player player, Object interacted) {

        if (!isLive())
            return;

        Wizard wizard = getWizard(player);

        if (wizard == null)
            return;

        if (player.getInventory().getHeldItemSlot() >= wizard.getMaxWands())
            return;

        SpellType spell = wizard.getSpell(player.getInventory().getHeldItemSlot());

        if (spell == null)
            return;

        castSpell(player, wizard, spell, interacted, false);
    }

    public void castSpell(Player player, Wizard wizard, SpellType spellType, Object interacted, boolean quickcast) {

        int spellLevel = wizard.getLevel(spellType);

        if (spellLevel <= 0)
            return;

        TranslatableComponent message;

        if (wizard.getDisabledSpell() != spellType) {

            if (wizard.getCooldown(spellType).isBefore(Instant.now())) {

                if (wizard.getMana() >= wizard.getManaCost(spellType)) {

                    Spell spell = spells.get(spellType);

                    SpellCastEvent spellCastEvent = new SpellCastEvent(player, spellType);
                    Bukkit.getPluginManager().callEvent(spellCastEvent);

                    if (spellCastEvent.isCancelled())
                        return;

                    spell.castSpell(player, getLevel(player, spellType));
                    castParticles(player, getKit(player));

                    heldSlots.put(player.getUniqueId(), new Spell.SpellData(player.getInventory().getHeldItemSlot(), spell, quickcast));

                    if (spell instanceof Spell.SpellBlock)
                        if (interacted instanceof Block)
                            ((Spell.SpellBlock) spell).castSpell(player, (Block) interacted, getLevel(player, spellType));

                    spell.charge(player, spellCastEvent.getManaMultiplier());

                } else {

                    player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 300F, 1F);

                    message = new TranslatableComponent("wizards.spellSputters");
                    message.setColor(ChatColor.BLUE);
                    player.spigot().sendMessage(message);

                    message = new TranslatableComponent("wizards.notEnoughMana");
                    message.setColor(ChatColor.BLUE);
                    player.spigot().sendMessage(message);
                }

            } else {

                TranslatableComponent spellName = new TranslatableComponent(spellType.getSpellName());
                spellName.setColor(spellType.getSpellElement().getColor());
                spellName.setBold(true);

                double timeLeft = (getUsableTime(wizard, spellType).getKey());

                TranslatableComponent usableTime = new TranslatableComponent(Double.toString(timeLeft));
                usableTime.setColor(ChatColor.RED);
                usableTime.setBold(true);

                message = new TranslatableComponent("wizards.notRecharged");
                message.setColor(ChatColor.YELLOW);

                message.addWith(spellName);
                message.addWith(usableTime);

                player.spigot().sendMessage(message);
            }

        } else {

            message = new TranslatableComponent("wizards.spellDisabled");
            message.setColor(ChatColor.RED);
            player.spigot().sendMessage(message);
        }
    }

    private void castParticles(Player player, WizardsKit kit) {

        if (kit == null)
            return;

        Location location = player.getMainHand() == MainHand.LEFT ?
                LocationUtil.getLeftSide(player.getEyeLocation(), 0.45).subtract(0, 0.6, 0) :
                LocationUtil.getRightSide(player.getEyeLocation(), 0.45).subtract(0, 0.6, 0);

        kit.playSpellEffect(player, location);
    }

    @EventHandler
    public void onSpellCollect(SpellCollectEvent event) {

        Player player = event.getPlayer();
        Wizard wizard = getWizard(player);

        if (wizard.getKnownSpells().size() >= SpellType.values().length) {
            // achievement
            player.sendMessage("Achievement");
        }
    }

    @EventHandler
    public void onSwap(PlayerItemHeldEvent event) {

        if (!isLive())
            return;

        Player player = event.getPlayer();
        Wizard wizard = getWizard(player);

        if (wizard == null)
            return;

        updateWandIcon(player, event.getPreviousSlot(), event.getNewSlot());

        if (event.getNewSlot() >= 0 && event.getNewSlot() < wizard.getMaxWands())
            updateWandTitle(player);

        if (!heldSlots.containsKey(player.getUniqueId()))
            return;

        Spell.SpellData data = heldSlots.get(player.getUniqueId());

        if (data.getSlot() != event.getPreviousSlot())
            return;

        Spell spell = data.getSpell();

        if (!spell.isCancelOnSwap())
            return;

        if (!(spell instanceof Spell.Cancellable))
            return;

        if (data.isQuickCast()) {

            if (spell.getProgress() >= (2.0 / 3.0)) {
                ((Spell.Cancellable) spell).cancelSpell(player);
                spell.setCancelled(true);
            }

        } else {
            ((Spell.Cancellable) spell).cancelSpell(player);
            spell.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {

        Player player = event.getEntity();

        if (!isLive())
            return;

        if (getWizard(player) == null)
            return;

        DamageManager damageManager = plugin.getDamageManager();

        Wizard wizard = getWizard(player);
        WizardsKit killerKit = null;

        DamageTick lastTick = damageManager.getLastLoggedTick(player.getUniqueId());

        if (lastTick instanceof PlayerDamageTick) {

            Player killer = ((PlayerDamageTick) lastTick).getPlayer();
            Wizard killerWizard = getWizard(killer);

            if (killerWizard != null)
                killerKit = getKit(killer);
        }

        dropItems(player, killerKit);

        wizard.getManaBar().removePlayer(player);
        wizard.getPotionStatusBar().removePlayer(player);

        wizards.remove(wizard);

        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            player.teleport(getActiveMap().getSpectatorLocation());
            player.setGameMode(GameMode.SPECTATOR);
        }, 20L);
    }

    private List<ItemStack> getItems(Player player) {

        List<ItemStack> items = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();

        Wizard wizard = getWizard(player);

        for (int i = wizard.getWandsOwned(); i < inventory.getSize(); i++) {

            ItemStack item = inventory.getItem(i);

            if (item != null && item.getType() != Material.AIR)
                items.add(item.clone());
        }

        Arrays.stream(inventory.getArmorContents()).filter(item -> item != null && item.getType() != Material.AIR).forEach(item -> items.add(item.clone()));

        ItemStack cursorItem = player.getItemOnCursor();

        if (cursorItem.getType() != Material.AIR)
            items.add(cursorItem.clone());

        return items;
    }

    private void dropItems(Player player, WizardsKit killerKit) {

        Wizard wizard = getWizard(player);

        Set<SpellType> droppedSpells = new HashSet<>();
        List<ItemStack> droppedItems = new ArrayList<>();

        for (int slot = 0; slot < wizard.getMaxWands(); slot++) {

            SpellType type = wizard.getSpell(slot);

            if (type != null && type != SpellType.MANA_BOLT)
                droppedSpells.add(type);
        }

        for (SpellType type : wizard.getKnownSpells()) {

            if (ThreadLocalRandom.current().nextDouble() > 0.2)
                continue;

            droppedSpells.add(type);
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

            ItemStack item = droppedSpell.getSpellBook();

            // TODO: 4/8/21 add dull enchantment
            droppedItems.add(item);
        });

        double dropWandChance;

        switch (wizard.getWandsOwned()) {

            case 2:
                dropWandChance = 0.2;
                break;

            case 3:
                dropWandChance = 0.4;
                break;

            case 4:
                dropWandChance = 0.6;
                break;

            case 5:
                dropWandChance = killerKit instanceof KitSorcerer ? 1 : 0.8;
                break;

            default:
                dropWandChance = 1;
        }

        if (ThreadLocalRandom.current().nextDouble() < dropWandChance)
            droppedItems.add(getBlankWand());

        droppedItems.add (
                new ItemBuilder(Material.NETHER_STAR)
                        .withName(System.currentTimeMillis() + "")
                        .withLore("")
                        .get()
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

    @EventHandler
    public void onSpawn(ItemSpawnEvent event) {

        ItemStack item = event.getEntity().getItemStack();
        SpellType spell = getSpell(item);

        /*
         * Prevent player item drops from being
         * burned by Lightning Strike spell.
         */

        if (droppedItems.contains(event.getEntity()))
            event.getEntity().setInvulnerable(true);

        String hologramText = "";

        /*
         * Add holograms to game items.
         */
        if (spell != null) {

            hologramText = ChatColor.LIGHT_PURPLE + ChatColor.BOLD.toString() + "Spell\n" +
                    spell.getSpellElement().getColor() + spell.getSpellName();

            // TODO: 2020-05-15 spell hologram

        } else if (item.getType() == Material.BLAZE_ROD) {

            hologramText = ChatColor.BOLD + "Spell Wand";
            // TODO: 2020-05-15 wand hologram

        } else if (item.getType() == Material.NETHER_STAR) {

            hologramText = ChatColor.BOLD + "Wizard Soul";
            // TODO: 2020-05-15 soul hologram
        }

        if (!hologramText.isEmpty()) {

            //item.removeEnchantment(UtilInv.getDullEnchantment());

            Location hologramLocation = event.getEntity().getLocation().add(0, 1, 0);

            //holo.setFollowEntity(event.getEntity());
            //holo.setRemoveOnEntityDeath();
            //holo.setViewDistance(16);
            //holo.start();
            droppedItems.add(event.getEntity());
        }
    }

    @EventHandler
    public void onDespawn(ItemDespawnEvent event) {

        if (!event.getEntity().isInvulnerable())
            return;

        event.getEntity().setTicksLived(1);
        event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {

        if (!(event.getEntity() instanceof Player))
            return;

        Player player = (Player) event.getEntity();

        ItemStack item = event.getItem().getItemStack();
        boolean gameItem = false;

        if (getSpell(item) != null) {

            learnSpell(player, getSpell(item));
            gameItem = true;

        } else if (item.getType() == Material.BLAZE_ROD) {

            gameItem = true;
            gainWand(player);

        } else if (item.getType() == Material.NETHER_STAR) {

            gameItem = true;

            Wizard wizard = getWizard(player);

            if (wizard == null)
                return;

            wizard.addSoulStar();
            updateActionBar(player);

            TranslatableComponent message = new TranslatableComponent("wizards.soulAbsorbed");
            message.setColor(ChatColor.GREEN);
            player.spigot().sendMessage(message);
        }

        if (gameItem) {

            event.setCancelled(true);
            event.getItem().remove();

            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7F, 0F);
        }
    }

    @EventHandler
    public void disablePunching(EntityDamageByEntityEvent event) {

        if (!(event.getDamager() instanceof Player))
            return;

        if (!(event.getEntity() instanceof Player))
            return;

        event.setDamage(0);
    }

    @EventHandler
    public void onItemConsume(PlayerItemConsumeEvent event) {

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item.hasItemMeta() && !(item.getItemMeta() instanceof PotionMeta))
            return;

        Wizard wizard = getWizard(player);

        if (wizard == null || getPotion(item) == null)
            return;

        PotionType currentPotion = getPotion(event.getItem());
        PotionType activePotion = wizard.getActivePotion();

        if (currentPotion == null)
            return;

        PotionConsumeEvent potionConsumeEvent = new PotionConsumeEvent(player, currentPotion);

        if (potionConsumeEvent.isCancelled())
            return;

        if (activePotion != null) {

            if (currentPotion.equals(activePotion)) {

                // Player is drinking the same potion, reset the time
                potions.get(activePotion).deactivate(wizard);

            } else {

                // Player is drinking a different potion, remove the old
                potions.get(activePotion).deactivate(wizard);

                if (!potionTimes.get(player.getUniqueId()).isEmpty())
                    potionTimes.get(player.getUniqueId()).clear();

                TranslatableComponent potionsMix = new TranslatableComponent("wizards.potionsMix");
                potionsMix.setColor(ChatColor.GOLD);

                player.spigot().sendMessage(potionsMix);
            }
        }

        event.setItem(null);

        // Activate the new potion
        potions.get(currentPotion).activate(wizard);
        wizard.setActivePotion(currentPotion);

        /*
         * Update map with the new times
         */
        if (!potionTimes.containsKey(player.getUniqueId())) {
            potionTimes.put(player.getUniqueId(), new HashMap<>() {{ put(currentPotion, Instant.now()); }});

        } else {
            potionTimes.get(player.getUniqueId()).put(currentPotion, Instant.now());
        }

        // Add player to status indicator bar
        wizard.getPotionStatusBar().addPlayer(player);
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {

        Player player = event.getPlayer();
        Wizard wizard = getWizard(player);

        if (wizard == null)
            return;

        int droppedSlot = -1;
        ItemStack droppedItem = event.getItemDrop().getItemStack();

        for (int i = 0; i < player.getInventory().getSize(); i++) {

            ItemStack currentItem = player.getInventory().getItem(i);

            if (droppedItem.equals(currentItem))
                droppedSlot = i;
        }

        if (droppedSlot > 0 && droppedSlot < wizard.getMaxWands())
            event.setCancelled(true);
    }

    @EventHandler
    public void preventCrafting(PrepareItemCraftEvent event) {

        Recipe recipe = event.getRecipe();
        ItemStack result = recipe != null ? recipe.getResult() : null;

        if (result == null)
            return;

        Material material = result.getType();

        if (permittedCrafting.contains(material))
            return;

        event.getInventory().setResult(new ItemStack(Material.AIR));

        TranslatableComponent bannedCrafting = new TranslatableComponent("wizards.bannedCrafting");
        bannedCrafting.setColor(ChatColor.RED);

        event.getViewers().get(0).spigot().sendMessage(bannedCrafting);
    }

    @EventHandler
    public void onArmorEquip(ArmorEquipEvent event) {

        Player player = event.getPlayer();

        ItemStack item = event.getNewArmorPiece();
        ItemMeta itemMeta = item.getItemMeta();

        if (!(itemMeta instanceof LeatherArmorMeta))
            return;

        ArmorEquipEvent.EquipMethod method = event.getMethod();

        Wizard wizard = getWizard(player);

        if (wizard == null)
            return;

        WizardsKit kit = getKit(player);
        ((LeatherArmorMeta) itemMeta).setColor(kit.getColor());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {

        Player player = event.getPlayer();
        GameState state = plugin.getGameManager().getState();

        if (!(state instanceof LobbyState) && (!(state instanceof PrepareState)))
            return;

        EntityUtil.resetPlayer(player, state instanceof LobbyState ? GameMode.SURVIVAL : GameMode.SPECTATOR);

        player.getInventory().setItem(1, kitSelectIcon);
        player.getInventory().setItem(3, spellMenuBook);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {

        Player player = event.getPlayer();
        Wizard wizard = getWizard(player);

        if (wizard == null)
            return;

        if (player.getInventory().getHeldItemSlot() < wizard.getWandsOwned())
            event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {

        Player player = event.getPlayer();
        Wizard wizard = getWizard(player);

        if (wizard != null) {
            dropItems(player, null);
            wizard.getManaBar().removePlayer(player);
            wizards.remove(wizard);
        }
    }

    public Location getTargetLocation(Player player) {

        if (!hasDoppelganger(player))
            return player.getLocation();

        SpellDoppelganger doppelganger = (SpellDoppelganger) getSpells().get(SpellType.DOPPELGANGER);

        Map<UUID, NPC> clones = doppelganger.getClones();
        NPC fakePlayer = clones.get(player.getUniqueId());

        return fakePlayer.getLocation();
    }

    private boolean hasDoppelganger(Player player) {

        if (getWizard(player) == null)
            return false;

        SpellType spell = SpellType.DOPPELGANGER;

        if (getLevel(player, spell) == 0)
            return false;

        SpellDoppelganger doppelganger = (SpellDoppelganger) getSpells().get(spell);
        return doppelganger.isActive(player);
    }

    public WizardsKit getKit(Player player) {
        return plugin.getGameManager().getKit(player);
    }

    private void setKit(Player player, WizardsKit kit) {
        plugin.getGameManager().setKit(player, kit);
    }

    private ItemStack getBlankWand() {

        ItemBuilder builder = new ItemBuilder(Material.BLAZE_ROD)

                .withCustomModelData(1)
                .withName(ChatColor.WHITE + "Right-Click to set a spell")

                .withLore (
                        "",
                        ChatColor.GREEN + "" + ChatColor.BOLD + "Left-Click" + ChatColor.WHITE + " Bind to Wand",
                        ChatColor.GREEN + "" + ChatColor.BOLD + "Right-Click" + ChatColor.WHITE + " Quickcast Spell"
                );

        ItemStack finalStack = builder.get();

        NamespacedKey key = new NamespacedKey(plugin, "time");
        ItemMeta itemMeta = finalStack.getItemMeta();

        if (itemMeta != null)
            itemMeta.getPersistentDataContainer().set(key, PersistentDataType.LONG, System.nanoTime());

        finalStack.setItemMeta(itemMeta);
        return finalStack;
    }

    public SpellType getSpell(ItemStack item) {

        if (item.getItemMeta() != null) {
            if (item.getItemMeta().hasDisplayName()) {

                String title = item.getItemMeta().getDisplayName();

                if (title.contains(" ")) {

                    title = ChatColor.stripColor(title.substring(title.split(" ")[0].length() + 1));

                    for (SpellType spell : SpellType.values())
                        if (spell.getSpellName().equals(title))
                            return spell;
                }
            }
        }

        return null;
    }

    private PotionType getPotion(ItemStack item) {

        for (PotionType potion : potions.keySet())
            if (item.isSimilar(potion.createPotion()))
                return potion;

        return null;
    }

    public Duration getPotionDuration(Player player, PotionType type) {

        Duration between = null;

        if (potionTimes.containsKey(player.getUniqueId())) {

            Map<PotionType, Instant> typeInstantMap = potionTimes.get(player.getUniqueId());
            Set<Map.Entry<PotionType, Instant>> entries = typeInstantMap.entrySet();

            for (Map.Entry<PotionType, Instant> entry : entries) {

                if (entry.getKey() != type)
                    continue;

                Instant consumedAtWithDuration = entry.getValue().plusSeconds(type.getDuration().toSeconds());

                if (consumedAtWithDuration.isBefore(Instant.now()))
                    continue;

                between = Duration.between(Instant.now(), consumedAtWithDuration);
            }
        }

        return between;
    }

    public Wizard getWizard(Player player) {

        for (Wizard wizard : wizards)
            if (player.getUniqueId().equals(wizard.getUniqueId()))
                return wizard;

        return null;
    }

    public int getLevel(Player player, SpellType spell) {
        return getWizard(player).getLevel(spell);
    }

    private int getMaxLevel(Player player, SpellType spell) {

        if (getKit(player) instanceof KitScholar) {

            switch (spell) {
                case MANA_BOLT:
                case FIREBALL:
                case HEAL:
                case ICE_PRISON:
                    return spell.getMaxLevel() + 1;
            }
        }

        return spell.getMaxLevel();
    }

    public GameTeam getTeam(Player player) {

        for (GameTeam team : gameTeams)
            if (team.isOnTeam(player))
                return team;

        return null;
    }

    public List<Player> getPlayers(boolean alive) {

        List<Player> players = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {

            if (alive) {

                if (isLive()) {

                    // Preparation stage is over - all wizards already setup
                    if (getWizard(player) != null)
                        players.add(player);

                } else {

                    // Preparation phase - players not fully setup yet
                    if (!player.hasMetadata(GameManager.SPECTATING_KEY))
                        players.add(player);
                }

            } else {
                players.add(player);
            }
        }

        return players;
    }

    private List<Player> getAllies(Player player) {

        List<Player> allies = null;
        GameTeam team = getTeam(player);

        if (currentMode.isTeamMode() && team != null) {
            allies = new ArrayList<>(team.getPlayers());
            allies.remove(player);
        }

        return allies;
    }

    public GameTeam.TeamRelation getRelation(Player a, Player b) {

        WizardsMode currentMode = getCurrentMode();

        if (!currentMode.isTeamMode()) {
            return GameTeam.TeamRelation.SOLO;

        } else {

            GameTeam
                    teamA = getTeam(a),
                    teamB = getTeam(b);

            return (teamA == null || teamB == null) ?
                    GameTeam.TeamRelation.UNKNOWN :

                    (teamA.equals(teamB)) ?
                            GameTeam.TeamRelation.ALLY :
                            GameTeam.TeamRelation.ENEMY;
        }
    }

    private boolean isInsideMap(Player player) {
        return isInsideMap(player.getLocation());
    }

    public boolean isInsideMap(Location location) {

        return
                !(
                        location.getX() >= getActiveMap().getMaxX() + 1 ||
                        location.getX() <= getActiveMap().getMinX() ||
                        location.getZ() >= getActiveMap().getMaxZ() + 1 ||
                        location.getZ() <= getActiveMap().getMinZ() ||
                        location.getY() >= getActiveMap().getMaxY() + 1 ||
                        location.getY() <= getActiveMap().getMinY()
                );
    }

    public boolean shouldEnd() {
        return currentMode.isTeamMode() ? getGameTeams().size() <= 1 : wizards.size() <= 1;
    }

    Instant getLastSurge() {
        return lastSurge;
    }

    public void setLastSurge(Instant lastSurge) {
        this.lastSurge = lastSurge;
    }

    public Pair<String, Instant> getNextEvent() {

        String nextEvent;
        Instant nextEventInstant;

        GameState state = plugin.getGameManager().getState();
        Instant startTime = state.getStartTime();

        if (state instanceof ActiveState) {

            if (Duration.between(startTime, Instant.now()).toMinutes() >= 8) {
                nextEvent = "Overtime";
                nextEventInstant = startTime.plus(Duration.ofMinutes(10));

            } else {
                nextEvent = "Power Surge";
                nextEventInstant = lastSurge.plus(Duration.ofMinutes(2));
            }

        } else if (state instanceof OvertimeState) {
            nextEvent = "Game End";
            nextEventInstant = startTime.plus(Duration.ofMinutes(10));

        } else {
            nextEvent = "Game Start";
            nextEventInstant = startTime.plus(Duration.ofSeconds(currentMode.isBrawl() ? 15 : 10));
        }

        return new Pair<>(nextEvent, nextEventInstant);
    }

    public NPC getNPC(Location location) {

        NPC npc = null;

        for (NPC currentNpc : NPC_SET)
            if (currentNpc.getLocation().distance(location) <= 0)
                npc = currentNpc;

        return npc;
    }

    public boolean isLive() {
        GameState state = plugin.getGameManager().getState();
        return state instanceof ActiveState || state instanceof OvertimeState;
    }

    public boolean isOvertime() {
        return plugin.getGameManager().getState() instanceof OvertimeState;
    }

    public LocalGameMap getActiveMap() {
        return plugin.getMapManager().getActiveMap();
    }

    public Set<Wizard> getWizards() {
        return wizards;
    }

    public List<GameTeam> getTeams() {
        return teams;
    }

    public GameTeam getRandomTeam(WizardsMode mode) {

        GameTeam team = null;

        for (GameTeam gameTeam : teams)
            if (team == null || gameTeam.getTeamSize() < mode.getNumPlayers())
                team = gameTeam;

        return team;
    }

    public WizardsMode getCurrentMode() {
        return currentMode;
    }

    public void setCurrentMode(WizardsMode currentMode) {
        Bukkit.getLogger().info("Changed current mode from " + this.currentMode.toString() + " to " + currentMode.toString());
        this.currentMode = currentMode;
    }

    private List<GameTeam> getGameTeams() {
        return gameTeams;
    }

    public ChestLoot getChestLoot() {
        return chestLoot;
    }

    public Map<SpellType, Spell> getSpells() {
        return spells;
    }

    public Map<PotionType, Potion> getPotions() {
        return potions;
    }

    public Disaster getDisaster() {
        return disaster;
    }

    public KitSelectMenu getKitSelectMenu() {
        return kitSelectMenu;
    }

    private GameManager getGameManager() {
        return plugin.getGameManager();
    }

    public WizardsPlugin getPlugin() {
        return plugin;
    }
}