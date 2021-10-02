package dev.thomashanson.wizards.util.menu;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.util.menu.listener.InventoryEventHandler;
import dev.thomashanson.wizards.util.menu.listener.InventoryMenuListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static org.bukkit.event.inventory.ClickType.*;

public class InventoryMenuBuilder extends MenuBuilder<Inventory> {

    private final WizardsPlugin plugin;

    private static final ClickType[] ALL_CLICK_TYPES = new ClickType[] {
            LEFT,
            SHIFT_LEFT,
            RIGHT,
            SHIFT_RIGHT,
            WINDOW_BORDER_LEFT,
            WINDOW_BORDER_RIGHT,
            MIDDLE,
            NUMBER_KEY,
            DOUBLE_CLICK,
            DROP,
            CONTROL_DROP
    };

    private Inventory inventory;

    private final List<ItemCallback> callbackItems = new ArrayList<>();

    private InventoryMenuBuilder(WizardsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Construct a new InventoryMenuBuilder with the specified size
     * @param size Size of the inventory
     */
    public InventoryMenuBuilder(WizardsPlugin plugin, int size) {
        this(plugin);
        withSize(size);
    }

    /**
     * Construct a new InventoryMenuBuilder with the specified title and size
     * @param title Title of the inventory
     * @param size  Size of the inventory
     */
    public InventoryMenuBuilder(WizardsPlugin plugin, String title, int size) {
        this(plugin);
        initInventory(Bukkit.createInventory(null, size, title));
    }

    /**
     * Construct a new InventoryMenuBuilder with the specified {@link InventoryType}
     * @param type {@link InventoryType}
     */
    public InventoryMenuBuilder(WizardsPlugin plugin, InventoryType type) {
        this(plugin);
        withType(type);
    }

    private void initInventory(Inventory inventory) {

        if (this.inventory != null)
            throw new IllegalStateException("Inventory already initialized");

        this.inventory = inventory;
    }

    private void validateInit() {

        if (this.inventory == null)
            throw new IllegalStateException("Inventory not yet initialized");
    }

    /**
     * @return The {@link Inventory} being built
     */
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Sets the initial size
     * @param size Size of the inventory
     * @return the InventoryMenuBuilder
     */
    private InventoryMenuBuilder withSize(int size) {
        initInventory(Bukkit.createInventory(null, size));
        return this;
    }

    /**
     * Sets the initial type
     * @param type {@link InventoryType}
     * @return the InventoryMenuBuilder
     */
    private InventoryMenuBuilder withType(InventoryType type) {
        initInventory(Bukkit.createInventory(null, type));
        return this;
    }

    /**
     * Add an <i>optional</i> {@link InventoryEventHandler} to further customize the click-behaviour
     *
     * @param eventHandler {@link InventoryEventHandler} to add
     * @return the InventoryMenuBuilder
     */
    public InventoryMenuBuilder withEventHandler(InventoryEventHandler eventHandler) {
        plugin.getInventoryListener().registerEventHandler(this, eventHandler);
        return this;
    }

    /**
     * Add a {@link InventoryMenuListener} for the specified {@link ClickType}s
     *
     * @param listener the {@link InventoryMenuListener} to add
     * @param actions  the {@link ClickType}s the listener should listen for (you can also use {@link #ALL_CLICK_TYPES} or {@link ClickType#values()}
     * @return the InventoryMenuBuilder
     */
    private InventoryMenuBuilder onInteract(InventoryMenuListener listener, ClickType... actions) {

        if (actions == null || actions.length == 0)
            throw new IllegalArgumentException("At least one action must be specified.");

        plugin.getInventoryListener().registerListener(this, listener, actions);
        return this;
    }

    /**
     * Set the item for the specified slot
     *
     * @param slot Slot of the item
     * @param item {@link ItemStack} to set
     * @return the InventoryMenuBuilder
     */
    public InventoryMenuBuilder withItem(int slot, ItemStack item) {
        validateInit();
        this.inventory.setItem(slot, item);
        return this;
    }

    /**
     * Set the item for the specified slot and add a {@link ItemListener} for it
     *
     * @param slot     Slot of the item
     * @param item     {@link ItemStack} to set
     * @param listener {@link ItemListener} for the item
     * @param actions  the {@link ClickType}s the listener should listen for (you can also use {@link #ALL_CLICK_TYPES} or {@link ClickType#values()}
     * @return the InventoryMenuBuilder
     */
    public InventoryMenuBuilder withItem(final int slot, final ItemStack item, final ItemListener listener, ClickType... actions) {

        withItem(slot, item);

        onInteract((player, action, itemSlot) -> {

            if (itemSlot == slot)
                listener.onInteract(player, action, item);

        }, actions);

        return this;
    }

    /**
     * Add an item using a {@link ItemCallback}
     * The callback will be called when {@link #show(HumanEntity...)} or {@link #refreshContent()} is called
     *
     * @param callback {@link ItemCallback}
     * @return the InventoryMenuBuilder
     */
    public InventoryMenuBuilder withItem(ItemCallback callback) {
        callbackItems.add(callback);
        return this;
    }

    /**
     * Builds the {@link Inventory}
     *
     * @return a {@link Inventory}
     */
    public Inventory build() {
        return this.inventory;
    }

    /**
     * Shows the inventory to the viewers
     *
     * @param viewers Array of {@link HumanEntity}
     * @return the InventoryMenuBuilder
     */
    public InventoryMenuBuilder show(HumanEntity... viewers) {

        refreshContent();

        for (HumanEntity viewer : viewers)
            viewer.openInventory(this.build());

        return this;
    }

    /**
     * Refresh the content of the inventory
     * Will call all {@link ItemCallback}s registered with {@link #withItem(ItemCallback)}
     *
     * @return the InventoryMenuBuilder
     */
    public InventoryMenuBuilder refreshContent() {

        for (ItemCallback callback : callbackItems) {

            int slot = callback.getSlot();
            ItemStack item = callback.getItem();

            withItem(slot, item);
        }

        return this;
    }

    @Override
    public void dispose() {
        plugin.getInventoryListener().unregisterAllListeners(getInventory());
    }

    public void unregisterListener(InventoryMenuListener listener) {
        plugin.getInventoryListener().unregisterListener(this, listener, ALL_CLICK_TYPES);
    }
}
