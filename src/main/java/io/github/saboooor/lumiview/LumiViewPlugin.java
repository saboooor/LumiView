package io.github.saboooor.lumiview;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.loot.Lootable;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Shelf;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Shelf items exist only in outgoing block-entity packets, never in world storage. */
public final class LumiViewPlugin extends JavaPlugin implements Listener {
    private final Set<Location> shelves = new HashSet<>();
    private PlacedItems placedItems;
    private HoverLabels hoverLabels;
    private DroppedItemLabels droppedItemLabels;
    private FurnaceLabels furnaceLabels;
    private BrewingStandLabels brewingStandLabels;
    private WorkstationLabels workstationLabels;
    private TileDisplayLabels tileDisplayLabels;
    private ActivityLabels activityLabels;
    private boolean shelvesEnabled = true;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var config = getConfig();
        shelvesEnabled = config.getBoolean("shelves", true);
        Bukkit.getPluginManager().registerEvents(this, this);
        if (config.getBoolean("placed-items", true)) {
            placedItems = new PlacedItems(this);
            Bukkit.getPluginManager().registerEvents(placedItems, this);
            placedItems.enable();
        }
        if (config.getBoolean("hover-labels", true) && placedItems != null) {
            hoverLabels = new HoverLabels(this, placedItems);
            Bukkit.getPluginManager().registerEvents(hoverLabels, this);
            Bukkit.getScheduler().runTaskTimer(this, hoverLabels::refresh, 2L, 2L);
        }
        if (config.getBoolean("dropped-items", true)) {
            droppedItemLabels = new DroppedItemLabels();
            Bukkit.getPluginManager().registerEvents(droppedItemLabels, this);
            droppedItemLabels.enable();
            Bukkit.getScheduler().runTaskTimer(this, droppedItemLabels::refresh, 2L, 2L);
        }
        if (config.getBoolean("furnaces", true)) {
            furnaceLabels = new FurnaceLabels();
            Bukkit.getPluginManager().registerEvents(furnaceLabels, this);
            furnaceLabels.enable();
            Bukkit.getScheduler().runTaskTimer(this, furnaceLabels::refresh, 4L, 4L);
        }
        if (config.getBoolean("brewing-stands", true)) {
            brewingStandLabels = new BrewingStandLabels();
            Bukkit.getPluginManager().registerEvents(brewingStandLabels, this);
            brewingStandLabels.enable();
            Bukkit.getScheduler().runTaskTimer(this, brewingStandLabels::refresh, 4L, 4L);
        }
        if (config.getBoolean("workstations", true)) {
            workstationLabels = new WorkstationLabels();
            Bukkit.getScheduler().runTaskTimer(this, () -> workstationLabels.refresh(), 2L, 2L);
        }
        if (config.getBoolean("world-displays", true)) {
            tileDisplayLabels = new TileDisplayLabels();
            Bukkit.getPluginManager().registerEvents(tileDisplayLabels, this);
            tileDisplayLabels.enable();
            Bukkit.getScheduler().runTaskTimer(this, tileDisplayLabels::refresh, 10L, 10L);
            activityLabels = new ActivityLabels(this);
            Bukkit.getPluginManager().registerEvents(activityLabels, this);
            Bukkit.getScheduler().runTaskTimer(this, activityLabels::refresh, 2L, 2L);
        }
        if (shelvesEnabled) {
            Bukkit.getWorlds().forEach(world -> {
                for (Chunk chunk : world.getLoadedChunks()) scan(chunk);
            });
            Bukkit.getScheduler().runTaskTimer(this, this::refresh, 1L, 10L);
        }
    }

    @Override
    public void onDisable() {
        if (activityLabels != null) activityLabels.disable();
        if (tileDisplayLabels != null) tileDisplayLabels.disable();
        if (workstationLabels != null) workstationLabels.disable();
        if (brewingStandLabels != null) brewingStandLabels.disable();
        if (furnaceLabels != null) furnaceLabels.disable();
        if (droppedItemLabels != null) droppedItemLabels.disable();
        if (hoverLabels != null) hoverLabels.disable();
        if (placedItems != null) placedItems.disable();
        PacketHolograms.clearHidden();
        // Restore real (empty) shelf inventories in clients when disabling/reloading.
        for (Location location : shelves) {
            if (location.isWorldLoaded() && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                    && location.getBlock().getState() instanceof Shelf shelf) send(shelf);
        }
        shelves.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("lumiview")) return false;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can toggle personal holograms.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("toggle")) {
            sender.sendMessage(Component.text("Usage: /" + label + " toggle", NamedTextColor.GRAY));
            return true;
        }
        boolean hidden = PacketHolograms.toggleHidden(player);
        player.sendMessage(Component.text(hidden ? "LumiView holograms hidden." : "LumiView holograms shown.",
                hidden ? NamedTextColor.GRAY : NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return command.getName().equalsIgnoreCase("lumiview") && args.length == 1 ? List.of("toggle") : List.of();
    }

    private void scan(Chunk chunk) {
        if (!shelvesEnabled) return;
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Shelf) shelves.add(state.getLocation());
        }
    }

    @EventHandler
    public void onLoad(ChunkLoadEvent event) { scan(event.getChunk()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        shelves.removeIf(loc -> loc.getWorld().equals(chunk.getWorld())
                && loc.getBlockX() >> 4 == chunk.getX() && loc.getBlockZ() >> 4 == chunk.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        shelves.removeIf(loc -> loc.getWorld().equals(event.getWorld()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getState() instanceof Shelf) shelves.add(event.getBlockPlaced().getLocation());
    }

    static Container containerBehind(Shelf shelf) {
        if (!(shelf.getBlockData() instanceof org.bukkit.block.data.type.Shelf data)) return null;
        Block behind = shelf.getBlock().getRelative(data.getFacing().getOppositeFace());
        // Do not load adjacent chunks just to show a preview.
        if (!behind.getWorld().isChunkLoaded(behind.getX() >> 4, behind.getZ() >> 4)) return null;
        if (!(behind.getState() instanceof Container container) || container instanceof Shelf) return null;
        // Chests retain their front-facing rule; other containers can have a shelf on any side.
        if (container instanceof Chest chest
                && (!(chest.getBlockData() instanceof org.bukkit.block.data.type.Chest chestData)
                || chestData.getFacing() != data.getFacing())) return null;
        return container;
    }

    private boolean empty(Shelf shelf) { return shelf.getInventory().isEmpty(); }

    private void refresh() {
        Iterator<Location> iterator = shelves.iterator();
        while (iterator.hasNext()) {
            Location location = iterator.next();
            if (!location.isWorldLoaded() || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                iterator.remove();
                continue;
            }
            if (!(location.getBlock().getState() instanceof Shelf shelf)) {
                iterator.remove();
                continue;
            }
            if (empty(shelf)) {
                ItemStack[] items = shelfItems(shelf);
                for (int slot = 0; slot < items.length; slot++) {
                    shelf.getSnapshotInventory().setItem(slot, items[slot]);
                }
            }
            // Sending the actual snapshot also clears a previous preview after unlinking.
            send(shelf);
        }
    }

    ItemStack[] shelfItems(Shelf shelf) {
        if (!empty(shelf)) return shelf.getInventory().getContents();
        Container container = containerBehind(shelf);
        if (container == null || !safeToPreview(container)) return new ItemStack[0];
        return PreviewItems.select(container.getInventory().getStorageContents(),
                container instanceof Chest chest ? previewOffset(chest) : 0).toArray(ItemStack[]::new);
    }

    private int previewOffset(Chest chest) {
        if (!(chest.getInventory().getHolder() instanceof DoubleChest pair)
                || !(pair.getLeftSide() instanceof Chest left)
                || !(pair.getRightSide() instanceof Chest right)) return 0;
        boolean isLeft = chest.getLocation().equals(left.getLocation());
        Chest partner = isLeft ? right : left;
        var data = (org.bukkit.block.data.type.Chest) partner.getBlockData();
        Block partnerBlock = partner.getBlock().getRelative(data.getFacing());
        if (!partnerBlock.getWorld().isChunkLoaded(partnerBlock.getX() >> 4, partnerBlock.getZ() >> 4)
                || !(partnerBlock.getState() instanceof Shelf partnerShelf)
                || !empty(partnerShelf)) return 0;
        Container partnerBehind = containerBehind(partnerShelf);
        if (partnerBehind == null || !partnerBehind.getLocation().equals(partner.getLocation())) return 0;
        // Bukkit's double-chest half order is stable, regardless of which shelf refreshes first.
        return isLeft ? 0 : 3;
    }

    static boolean safeToPreview(Container container) {
        if (container.isLocked() || container instanceof Lootable lootable && lootable.getLootTable() != null) return false;
        if (container.getInventory().getHolder() instanceof DoubleChest pair) {
            return pair.getLeftSide() instanceof Chest left && pair.getRightSide() instanceof Chest right
                    && !left.isLocked() && !right.isLocked()
                    && left.getLootTable() == null && right.getLootTable() == null;
        }
        return true;
    }

    static boolean blocked(Container container) {
        if (!(container instanceof Chest chest)) {
            if (container instanceof org.bukkit.block.ShulkerBox box
                    && box.getBlockData() instanceof org.bukkit.block.data.Directional data) {
                Block front = box.getBlock().getRelative(data.getFacing());
                return !front.getWorld().isChunkLoaded(front.getX() >> 4, front.getZ() >> 4)
                        || !front.getCollisionShape().getBoundingBoxes().isEmpty();
            }
            return false;
        }
        if (chest.isBlocked()) return true;
        if (chest.getInventory().getHolder() instanceof DoubleChest pair) {
            return pair.getLeftSide() instanceof Chest left && left.isBlocked()
                    || pair.getRightSide() instanceof Chest right && right.isBlocked();
        }
        return false;
    }

    private void send(Shelf shelf) {
        Location location = shelf.getLocation();
        for (Player player : location.getWorld().getPlayers()) {
            if (player.isChunkSent(Chunk.getChunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4))) {
                player.sendBlockUpdate(location, shelf);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || !(event.getClickedBlock().getState() instanceof Shelf shelf)) return;
        Container container = containerBehind(shelf);
        if (container == null || !empty(shelf)) return;
        if (PlacedItems.isPlacement(event)) return;
        boolean denied = event.useInteractedBlock() == Event.Result.DENY;
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (denied || event.getHand() != EquipmentSlot.HAND) return;

        // Give protection plugins the linked container interaction to authorize before opening.
        PlayerInteractEvent containerEvent = new PlayerInteractEvent(event.getPlayer(), Action.RIGHT_CLICK_BLOCK,
                event.getItem(), container.getBlock(), event.getBlockFace(), EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(containerEvent);
        if (containerEvent.useInteractedBlock() == Event.Result.DENY || blocked(container)) return;
        // Fail closed for locked/unopened loot containers; direct inventory opening skips vanilla checks.
        if (!safeToPreview(container)) return;
        Inventory inventory = container.getInventory();
        event.getPlayer().openInventory(inventory);
    }
}
