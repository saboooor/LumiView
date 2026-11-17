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
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;

/** Client-side item previews and brewing progress holograms for brewing stands. */
final class BrewingStandLabels implements Listener {
    static final double RANGE = 12;
    static final int BACKGROUND_ARGB = Color.fromARGB(75, 0, 0, 0).asARGB();
    static final float BOTTLE_SCALE = .22f;
    static final float INGREDIENT_SCALE = .24f;
    static final float FUEL_SCALE = .18f;
    static final float PROGRESS_SCALE = .22f;
    static final int BAR_SEGMENTS = 5;

    private final PacketHolograms.Sender sender;
    private final Map<Location, Tracked> stands = new HashMap<>();

    private static final class ItemSlot {
        int entityId;
        UUID uuid;
        ItemStack item;

        void reset() {
            entityId = 0;
            uuid = null;
            item = null;
        }
    }

    private static final class Tracked {
        final Location blockLocation;
        final Set<UUID> viewers = new HashSet<>();
        final ItemSlot[] items = {new ItemSlot(), new ItemSlot(), new ItemSlot(), new ItemSlot(), new ItemSlot()};
        int progressId;
        UUID progressUuid;
        Component progressText;

        Tracked(Location blockLocation) { this.blockLocation = blockLocation; }

        int[] activeIds() {
            int[] buffer = new int[6];
            int count = 0;
            for (ItemSlot slot : items) {
                if (slot.entityId != 0) buffer[count++] = slot.entityId;
            }
            if (progressId != 0) buffer[count++] = progressId;
            if (count == 6) return buffer;
            int[] trimmed = new int[count];
            System.arraycopy(buffer, 0, trimmed, 0, count);
            return trimmed;
        }

        void destroyAll(PacketHolograms.Sender sender) {
            int[] ids = activeIds();
            if (ids.length > 0) {
                for (UUID viewerId : viewers) {
                    Player player = Bukkit.getPlayer(viewerId);
                    if (player != null) sender.destroy(player, ids);
                }
            }
            viewers.clear();
            for (ItemSlot slot : items) slot.reset();
            progressId = 0;
            progressUuid = null;
            progressText = null;
        }
    }

    BrewingStandLabels() { this(PacketHolograms.INSTANCE); }

    BrewingStandLabels(PacketHolograms.Sender sender) { this.sender = sender; }

    void enable() {
        Bukkit.getWorlds().forEach(world -> {
            for (Chunk chunk : world.getLoadedChunks()) scan(chunk);
        });
    }

    private void scan(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof BrewingStand) track(state.getLocation());
        }
    }

    void track(Location location) {
        Location blockLoc = location.toBlockLocation();
        stands.putIfAbsent(blockLoc, new Tracked(blockLoc));
    }

    @EventHandler
    public void onLoad(ChunkLoadEvent event) { scan(event.getChunk()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getState() instanceof BrewingStand) track(event.getBlockPlaced().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        Iterator<Map.Entry<Location, Tracked>> iterator = stands.entrySet().iterator();
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
        Iterator<Map.Entry<Location, Tracked>> iterator = stands.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Location, Tracked> entry = iterator.next();
            if (entry.getKey().getWorld().equals(event.getWorld())) {
                entry.getValue().destroyAll(sender);
                iterator.remove();
            }
        }
    }

    static Component progressBar(BrewingStand stand) {
        int remaining = stand.getBrewingTime();
        int total = stand.getRecipeBrewTime();
        if (remaining <= 0 || total <= 0) return null;
        int elapsed = Math.max(0, total - remaining);
        int filled = Math.clamp(Math.round((float) elapsed * BAR_SEGMENTS / total), 0, BAR_SEGMENTS);
        Component bar = Component.empty();
        if (filled > 0) bar = bar.append(Component.text("█".repeat(filled), NamedTextColor.AQUA));
        if (filled < BAR_SEGMENTS) bar = bar.append(Component.text("█".repeat(BAR_SEGMENTS - filled), NamedTextColor.DARK_GRAY));
        return bar;
    }

    static Location slotLocation(Location base, int slot) {
        return switch (slot) {
            case 0 -> base.clone().add(.5 - .22, .42, .5 + .12);
            case 1 -> base.clone().add(.5, .42, .5 - .24);
            case 2 -> base.clone().add(.5 + .22, .42, .5 + .12);
            case 3 -> base.clone().add(.5, .96, .5);
            default -> base.clone().add(.5 + .26, .72, .5);
        };
    }

    private static float slotScale(int slot) {
        return switch (slot) {
            case 3 -> INGREDIENT_SCALE;
            case 4 -> FUEL_SCALE;
            default -> BOTTLE_SCALE;
        };
    }

    void refresh() {
        Iterator<Map.Entry<Location, Tracked>> iterator = stands.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Location, Tracked> entry = iterator.next();
            Location location = entry.getKey();
            Tracked tracked = entry.getValue();
            if (location.getWorld() == null || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                    || !(location.getBlock().getState() instanceof BrewingStand stand)) {
                tracked.destroyAll(sender);
                iterator.remove();
                continue;
            }
            update(tracked, stand);
        }
    }

    void update(Tracked tracked, BrewingStand stand) {
        if (!LumiViewPlugin.safeToPreview(stand)) {
            tracked.destroyAll(sender);
            return;
        }
        BrewerInventory inv = stand.getInventory();
        ItemStack[] desiredItems = new ItemStack[5];
        boolean any = false;
        for (int i = 0; i < 5; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && !item.isEmpty()) {
                desiredItems[i] = item;
                any = true;
            }
        }
        Component desiredProgress = progressBar(stand);
        if (!any && desiredProgress == null) {
            tracked.destroyAll(sender);
            return;
        }

        Location base = tracked.blockLocation;
        Location center = base.clone().add(.5, .6, .5);
        Map<UUID, Player> viewers = new HashMap<>();
        long chunkKey = Chunk.getChunkKey(base.getBlockX() >> 4, base.getBlockZ() >> 4);
        for (Player player : base.getWorld().getPlayers()) {
            if (PacketHolograms.isVisible(player)
                    && player.getEyeLocation().distanceSquared(center) <= RANGE * RANGE && player.isChunkSent(chunkKey)) {
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

        for (int i = 0; i < 5; i++) {
            syncItem(viewers, tracked, tracked.items[i], desiredItems[i], slotLocation(base, i), slotScale(i));
        }
        syncProgress(viewers, tracked, desiredProgress, base.clone().add(.5, 1.18, .5));
        tracked.viewers.addAll(viewers.keySet());
    }

    private void syncItem(Map<UUID, Player> viewers, Tracked tracked, ItemSlot slot,
            ItemStack desired, Location location, float scale) {
        if (desired == null) {
            if (slot.entityId != 0) {
                for (UUID viewerId : tracked.viewers) {
                    Player player = viewers.get(viewerId);
                    if (player != null) sender.destroy(player, slot.entityId);
                }
                slot.reset();
            }
            return;
        }
        if (slot.entityId == 0) {
            slot.entityId = sender.nextEntityId();
            slot.uuid = UUID.randomUUID();
            slot.item = desired.clone();
            for (Player player : viewers.values()) {
                sender.spawnItem(player, slot.entityId, slot.uuid, location, slot.item, scale, Display.Billboard.CENTER);
            }
            return;
        }
        boolean changed = slot.item == null || !slot.item.isSimilar(desired);
        if (changed) slot.item = desired.clone();
        for (Map.Entry<UUID, Player> entry : viewers.entrySet()) {
            Player player = entry.getValue();
            if (!tracked.viewers.contains(entry.getKey())) {
                sender.spawnItem(player, slot.entityId, slot.uuid, location, slot.item, scale, Display.Billboard.CENTER);
            } else if (changed) {
                sender.updateItem(player, slot.entityId, slot.item);
            }
        }
    }

    private void syncProgress(Map<UUID, Player> viewers, Tracked tracked, Component desired, Location location) {
        if (desired == null) {
            if (tracked.progressId != 0) {
                for (UUID viewerId : tracked.viewers) {
                    Player player = viewers.get(viewerId);
                    if (player != null) sender.destroy(player, tracked.progressId);
                }
                tracked.progressId = 0;
                tracked.progressUuid = null;
                tracked.progressText = null;
            }
            return;
        }
        if (tracked.progressId == 0) {
            tracked.progressId = sender.nextEntityId();
            tracked.progressUuid = UUID.randomUUID();
            tracked.progressText = desired;
            for (Player player : viewers.values()) {
                sender.spawnText(player, tracked.progressId, tracked.progressUuid, location, desired,
                        BACKGROUND_ARGB, 0f, PROGRESS_SCALE, Display.Billboard.CENTER);
            }
            return;
        }
        boolean changed = !Objects.equals(tracked.progressText, desired);
        if (changed) tracked.progressText = desired;
        for (Map.Entry<UUID, Player> entry : viewers.entrySet()) {
            Player player = entry.getValue();
            if (!tracked.viewers.contains(entry.getKey())) {
                sender.spawnText(player, tracked.progressId, tracked.progressUuid, location, desired,
                        BACKGROUND_ARGB, 0f, PROGRESS_SCALE, Display.Billboard.CENTER);
            } else if (changed) {
                sender.updateText(player, tracked.progressId, desired);
            }
        }
    }

    void disable() {
        stands.values().forEach(tracked -> tracked.destroyAll(sender));
        stands.clear();
    }
}
