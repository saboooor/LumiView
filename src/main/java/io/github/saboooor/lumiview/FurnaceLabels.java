package io.github.saboooor.lumiview;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;

/** Client-side item previews and stack counts anchored flat against furnace front faces. */
final class FurnaceLabels implements Listener {
    static final double RANGE = 12;
    static final int BACKGROUND_ARGB = Color.fromARGB(75, 0, 0, 0).asARGB();
    static final float ITEM_SCALE = .20f;
    static final float COUNT_SCALE = .20f;
    static final float PROGRESS_SCALE = .18f;
    static final int BAR_SEGMENTS = 5;
    static final double FRONT_ITEM_OFFSET = .52;
    static final double FRONT_TEXT_OFFSET = .54;
    static final double INPUT_SIDE_OFFSET = -.22;
    static final double OUTPUT_SIDE_OFFSET = .20;
    static final double FUEL_SIDE_OFFSET = 0.0;
    static final double COUNT_SIDE_OFFSET = .11;
    static final float COUNT_Y_OFFSET = -.04f;

    private final PacketHolograms.Sender sender;
    private final Map<Location, Tracked> furnaces = new HashMap<>();

    record Layout(float topY, float bottomY) {}

    private static final class SlotState {
        int itemId;
        UUID itemUuid;
        Location itemLocation;
        ItemStack item;
        int countId;
        UUID countUuid;
        Location countLocation;
        Component countText;

        void collectIds(int[] out, int[] count) {
            if (itemId != 0) out[count[0]++] = itemId;
            if (countId != 0) out[count[0]++] = countId;
        }

        void reset() {
            itemId = 0;
            itemUuid = null;
            itemLocation = null;
            item = null;
            countId = 0;
            countUuid = null;
            countLocation = null;
            countText = null;
        }
    }

    private static final class Tracked {
        final Location blockLocation;
        final Set<UUID> viewers = new HashSet<>();
        final SlotState input = new SlotState();
        final SlotState output = new SlotState();
        final SlotState fuel = new SlotState();
        final SlotState progress = new SlotState();

        Tracked(Location blockLocation) { this.blockLocation = blockLocation; }

        void destroyAll(PacketHolograms.Sender sender) {
            int[] ids = activeIds();
            if (ids.length > 0) {
                for (UUID viewerId : viewers) {
                    Player player = Bukkit.getPlayer(viewerId);
                    if (player != null) sender.destroy(player, ids);
                }
            }
            viewers.clear();
            input.reset();
            output.reset();
            fuel.reset();
            progress.reset();
        }

        int[] activeIds() {
            int[] buffer = new int[7];
            int[] count = {0};
            input.collectIds(buffer, count);
            output.collectIds(buffer, count);
            fuel.collectIds(buffer, count);
            progress.collectIds(buffer, count);
            if (count[0] == 7) return buffer;
            int[] trimmed = new int[count[0]];
            System.arraycopy(buffer, 0, trimmed, 0, count[0]);
            return trimmed;
        }
    }

    FurnaceLabels() { this(PacketHolograms.INSTANCE); }

    FurnaceLabels(PacketHolograms.Sender sender) { this.sender = sender; }

    void enable() {
        Bukkit.getWorlds().forEach(world -> {
            for (Chunk chunk : world.getLoadedChunks()) scan(chunk);
        });
    }

    private void scan(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Furnace) track(state.getLocation());
        }
    }

    void track(Location location) {
        Location blockLoc = location.toBlockLocation();
        furnaces.putIfAbsent(blockLoc, new Tracked(blockLoc));
    }

    @EventHandler
    public void onLoad(ChunkLoadEvent event) { scan(event.getChunk()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getState() instanceof Furnace) track(event.getBlockPlaced().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        Iterator<Map.Entry<Location, Tracked>> iterator = furnaces.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Location, Tracked> entry = iterator.next();
            Location loc = entry.getKey();
            if (loc.getWorld().equals(chunk.getWorld()) && loc.getBlockX() >> 4 == chunk.getX()
                    && loc.getBlockZ() >> 4 == chunk.getZ()) {
                entry.getValue().destroyAll(sender);
                iterator.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        Iterator<Map.Entry<Location, Tracked>> iterator = furnaces.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Location, Tracked> entry = iterator.next();
            if (entry.getKey().getWorld().equals(event.getWorld())) {
                entry.getValue().destroyAll(sender);
                iterator.remove();
            }
        }
    }

    static Layout layout(Material type) {
        return switch (type) {
            case BLAST_FURNACE -> new Layout(.60f, .22f);
            case SMOKER -> new Layout(.56f, .22f);
            default -> new Layout(.66f, .19f);
        };
    }

    static float yawFor(BlockFace facing) {
        return switch (facing) {
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> -90f;
            default -> 0f;
        };
    }

    static Component countText(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getAmount() > 1
                ? Component.text("×" + stack.getAmount(), NamedTextColor.WHITE)
                : null;
    }

    static Component progressBar(Furnace furnace) {
        int cook = furnace.getCookTime();
        int total = furnace.getCookTimeTotal();
        if (cook <= 0 || total <= 0) return null;
        int filled = Math.clamp(Math.round((float) cook * BAR_SEGMENTS / total), 0, BAR_SEGMENTS);
        Component bar = Component.empty();
        if (filled > 0) bar = bar.append(Component.text("█".repeat(filled), NamedTextColor.GOLD));
        if (filled < BAR_SEGMENTS) bar = bar.append(Component.text("█".repeat(BAR_SEGMENTS - filled), NamedTextColor.DARK_GRAY));
        return bar;
    }

    private static ItemStack present(ItemStack stack) {
        return stack != null && !stack.isEmpty() ? stack : null;
    }

    void refresh() {
        Iterator<Map.Entry<Location, Tracked>> iterator = furnaces.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Location, Tracked> entry = iterator.next();
            Location location = entry.getKey();
            Tracked tracked = entry.getValue();
            if (location.getWorld() == null || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                    || !(location.getBlock().getState() instanceof Furnace furnace)
                    || !(furnace.getBlockData() instanceof org.bukkit.block.data.type.Furnace data)) {
                tracked.destroyAll(sender);
                iterator.remove();
                continue;
            }
            update(tracked, furnace, data.getFacing());
        }
    }

    void update(Tracked tracked, Furnace furnace, BlockFace facing) {
        if (!LumiViewPlugin.safeToPreview(furnace)) {
            tracked.destroyAll(sender);
            return;
        }
        FurnaceInventory inv = furnace.getInventory();
        ItemStack inputItem = present(inv.getSmelting());
        ItemStack outputItem = present(inv.getResult());
        ItemStack fuelItem = present(inv.getFuel());
        if (inputItem == null && outputItem == null && fuelItem == null) {
            tracked.destroyAll(sender);
            return;
        }

        Location base = tracked.blockLocation;
        Layout layout = layout(furnace.getType());
        float yaw = yawFor(facing);
        int rightX = facing.getModZ();
        int rightZ = -facing.getModX();
        double frontX = base.getX() + .5 + facing.getModX() * FRONT_TEXT_OFFSET;
        double frontZ = base.getZ() + .5 + facing.getModZ() * FRONT_TEXT_OFFSET;

        Map<UUID, Player> viewers = new HashMap<>();
        long chunkKey = Chunk.getChunkKey(base.getBlockX() >> 4, base.getBlockZ() >> 4);
        Location checkCenter = new Location(base.getWorld(), frontX, base.getY() + layout.topY(), frontZ);
        for (Player player : base.getWorld().getPlayers()) {
            Location eye = player.getEyeLocation();
            double dx = eye.getX() - frontX;
            double dz = eye.getZ() - frontZ;
            boolean inFront = dx * facing.getModX() + dz * facing.getModZ() > -.05;
            if (PacketHolograms.isVisible(player) && inFront
                    && eye.distanceSquared(checkCenter) <= RANGE * RANGE && player.isChunkSent(chunkKey)) {
                viewers.put(player.getUniqueId(), player);
            } else if (tracked.viewers.remove(player.getUniqueId())) {
                int[] ids = tracked.activeIds();
                if (ids.length > 0) sender.destroy(player, ids);
            }
        }
        tracked.viewers.retainAll(viewers.keySet());
        if (viewers.isEmpty()) {
            tracked.destroyAll(sender);
            return;
        }

        syncSlot(viewers, tracked, tracked.input, inputItem, base, facing, rightX, rightZ, INPUT_SIDE_OFFSET, layout.topY(), yaw);
        syncSlot(viewers, tracked, tracked.output, outputItem, base, facing, rightX, rightZ, OUTPUT_SIDE_OFFSET, layout.topY(), yaw);
        syncSlot(viewers, tracked, tracked.fuel, fuelItem, base, facing, rightX, rightZ, FUEL_SIDE_OFFSET, layout.bottomY(), yaw);
        syncText(viewers, tracked, tracked.progress, progressBar(furnace),
                faceLocation(base, facing, rightX, rightZ, FRONT_TEXT_OFFSET, 0.0, layout.topY(), yaw), PROGRESS_SCALE);
        tracked.viewers.addAll(viewers.keySet());
    }

    private static Location faceLocation(Location base, BlockFace facing, int rightX, int rightZ,
            double frontOffset, double sideOffset, float yOffset, float yaw) {
        return new Location(base.getWorld(),
                base.getX() + .5 + facing.getModX() * frontOffset + rightX * sideOffset,
                base.getY() + yOffset,
                base.getZ() + .5 + facing.getModZ() * frontOffset + rightZ * sideOffset,
                yaw, 0f);
    }

    private void syncSlot(Map<UUID, Player> viewers, Tracked tracked, SlotState slot, ItemStack desiredItem,
            Location base, BlockFace facing, int rightX, int rightZ, double sideOffset, float yOffset, float yaw) {
        syncItem(viewers, tracked, slot, desiredItem,
                faceLocation(base, facing, rightX, rightZ, FRONT_ITEM_OFFSET, sideOffset, yOffset, yaw));
        syncCount(viewers, tracked, slot, countText(desiredItem),
                faceLocation(base, facing, rightX, rightZ, FRONT_TEXT_OFFSET, sideOffset + COUNT_SIDE_OFFSET, yOffset + COUNT_Y_OFFSET, yaw));
    }

    private void syncCount(Map<UUID, Player> viewers, Tracked tracked, SlotState slot,
            Component desiredText, Location desiredLocation) {
        syncText(viewers, tracked, slot, desiredText, desiredLocation, COUNT_SCALE);
    }

    private void syncItem(Map<UUID, Player> viewers, Tracked tracked, SlotState slot,
            ItemStack desiredItem, Location desiredLocation) {
        if (desiredItem == null) {
            if (slot.itemId != 0) {
                for (UUID viewerId : tracked.viewers) {
                    Player player = viewers.get(viewerId);
                    if (player != null) sender.destroy(player, slot.itemId);
                }
                slot.itemId = 0;
                slot.itemUuid = null;
                slot.itemLocation = null;
                slot.item = null;
            }
            return;
        }
        if (slot.itemId == 0) {
            slot.itemId = sender.nextEntityId();
            slot.itemUuid = UUID.randomUUID();
            slot.itemLocation = desiredLocation;
            slot.item = desiredItem.clone();
            for (Player player : viewers.values()) {
                sender.spawnItem(player, slot.itemId, slot.itemUuid, desiredLocation, slot.item,
                        ITEM_SCALE, Display.Billboard.FIXED);
            }
            return;
        }
        boolean moved = slot.itemLocation == null || slot.itemLocation.distanceSquared(desiredLocation) > .0001
                || Float.compare(slot.itemLocation.getYaw(), desiredLocation.getYaw()) != 0;
        boolean itemChanged = slot.item == null || !slot.item.isSimilar(desiredItem);
        if (moved) slot.itemLocation = desiredLocation;
        if (itemChanged) slot.item = desiredItem.clone();
        for (Map.Entry<UUID, Player> entry : viewers.entrySet()) {
            Player player = entry.getValue();
            if (!tracked.viewers.contains(entry.getKey())) {
                sender.spawnItem(player, slot.itemId, slot.itemUuid, desiredLocation, slot.item,
                        ITEM_SCALE, Display.Billboard.FIXED);
            } else {
                if (moved) sender.teleport(player, slot.itemId, desiredLocation);
                if (itemChanged) sender.updateItem(player, slot.itemId, slot.item);
            }
        }
    }

    private void syncText(Map<UUID, Player> viewers, Tracked tracked, SlotState slot,
            Component desiredText, Location desiredLocation, float scale) {
        if (desiredText == null) {
            if (slot.countId != 0) {
                for (UUID viewerId : tracked.viewers) {
                    Player player = viewers.get(viewerId);
                    if (player != null) sender.destroy(player, slot.countId);
                }
                slot.countId = 0;
                slot.countUuid = null;
                slot.countLocation = null;
                slot.countText = null;
            }
            return;
        }
        if (slot.countId == 0) {
            slot.countId = sender.nextEntityId();
            slot.countUuid = UUID.randomUUID();
            slot.countLocation = desiredLocation;
            slot.countText = desiredText;
            for (Player player : viewers.values()) {
                sender.spawnText(player, slot.countId, slot.countUuid, desiredLocation, desiredText,
                        BACKGROUND_ARGB, 0f, scale, Display.Billboard.FIXED);
            }
            return;
        }
        boolean moved = slot.countLocation == null || slot.countLocation.distanceSquared(desiredLocation) > .0001
                || Float.compare(slot.countLocation.getYaw(), desiredLocation.getYaw()) != 0;
        boolean textChanged = !Objects.equals(slot.countText, desiredText);
        if (moved) slot.countLocation = desiredLocation;
        if (textChanged) slot.countText = desiredText;
        for (Map.Entry<UUID, Player> entry : viewers.entrySet()) {
            Player player = entry.getValue();
            if (!tracked.viewers.contains(entry.getKey())) {
                sender.spawnText(player, slot.countId, slot.countUuid, desiredLocation, desiredText,
                        BACKGROUND_ARGB, 0f, scale, Display.Billboard.FIXED);
            } else {
                if (moved) sender.teleport(player, slot.countId, desiredLocation);
                if (textChanged) sender.updateText(player, slot.countId, desiredText);
            }
        }
    }

    void disable() {
        furnaces.values().forEach(tracked -> tracked.destroyAll(sender));
        furnaces.clear();
    }
}
