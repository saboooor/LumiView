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
import org.bukkit.block.Beacon;
import org.bukkit.block.Beehive;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Jukebox;
import org.bukkit.block.Lectern;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/** Client-side holograms and item previews for beacons, jukeboxes, beehives, and lecterns. */
final class TileDisplayLabels implements Listener {
    static final double RANGE = 12;
    static final int BACKGROUND_ARGB = Color.fromARGB(75, 0, 0, 0).asARGB();
    static final float TEXT_SCALE = .26f;
    static final float DISC_SCALE = .28f;
    private static final double FRONT_OFFSET = .54;

    private final PacketHolograms.Sender sender;
    private final Map<Location, Tracked> tiles = new HashMap<>();

    record Snapshot(Component text, Location textLoc, Display.Billboard billboard,
            ItemStack item, Location itemLoc) {}

    private static final class Tracked {
        final Location blockLocation;
        final Set<UUID> viewers = new HashSet<>();
        int textId;
        UUID textUuid;
        Location textLoc;
        Component text;
        int itemId;
        UUID itemUuid;
        Location itemLoc;
        ItemStack item;

        Tracked(Location blockLocation) { this.blockLocation = blockLocation; }

        int[] activeIds() {
            if (textId != 0 && itemId != 0) return new int[] {textId, itemId};
            if (textId != 0) return new int[] {textId};
            if (itemId != 0) return new int[] {itemId};
            return new int[0];
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
            textId = 0;
            textUuid = null;
            textLoc = null;
            text = null;
            itemId = 0;
            itemUuid = null;
            itemLoc = null;
            item = null;
        }
    }

    TileDisplayLabels() { this(PacketHolograms.INSTANCE); }

    TileDisplayLabels(PacketHolograms.Sender sender) { this.sender = sender; }

    void enable() {
        Bukkit.getWorlds().forEach(world -> {
            for (Chunk chunk : world.getLoadedChunks()) scan(chunk);
        });
    }

    private void scan(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            if (isSupported(state)) track(state.getLocation());
        }
    }

    private static boolean isSupported(BlockState state) {
        return state instanceof Beacon || state instanceof Jukebox
                || state instanceof Beehive || state instanceof Lectern;
    }

    void track(Location location) {
        Location blockLoc = location.toBlockLocation();
        tiles.putIfAbsent(blockLoc, new Tracked(blockLoc));
    }

    @EventHandler
    public void onLoad(ChunkLoadEvent event) { scan(event.getChunk()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isSupported(event.getBlockPlaced().getState())) track(event.getBlockPlaced().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        Iterator<Map.Entry<Location, Tracked>> iterator = tiles.entrySet().iterator();
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
        Iterator<Map.Entry<Location, Tracked>> iterator = tiles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Location, Tracked> entry = iterator.next();
            if (entry.getKey().getWorld().equals(event.getWorld())) {
                entry.getValue().destroyAll(sender);
                iterator.remove();
            }
        }
    }

    static Snapshot snapshot(BlockState state, Location base) {
        if (state instanceof Beacon beacon) return beaconSnapshot(beacon, base);
        if (state instanceof Jukebox jukebox) return jukeboxSnapshot(jukebox, base);
        if (state instanceof Beehive beehive) return beehiveSnapshot(beehive, base);
        if (state instanceof Lectern lectern) return lecternSnapshot(lectern, base);
        return null;
    }

    private static Snapshot beaconSnapshot(Beacon beacon, Location base) {
        int tier = beacon.getTier();
        PotionEffect primary = beacon.getPrimaryEffect();
        PotionEffect secondary = beacon.getSecondaryEffect();
        if (tier <= 0 && primary == null) return null;
        Component text = beaconText(tier,
                primary == null ? null : primary.getType().translationKey(),
                secondary == null ? null : secondary.getType().translationKey());
        return new Snapshot(text, base.clone().add(.5, 1.25, .5), Display.Billboard.CENTER, null, null);
    }

    static Component beaconText(int tier, String primaryKey, String secondaryKey) {
        Component text = Component.text("Tier " + tier, NamedTextColor.AQUA);
        if (primaryKey != null) {
            text = text.append(Component.text(" · ", NamedTextColor.GRAY))
                    .append(Component.translatable(primaryKey, NamedTextColor.WHITE));
        }
        if (secondaryKey != null) {
            text = text.append(Component.text(" + ", NamedTextColor.GRAY))
                    .append(Component.translatable(secondaryKey, NamedTextColor.WHITE));
        }
        return text;
    }

    private static Snapshot jukeboxSnapshot(Jukebox jukebox, Location base) {
        if (!jukebox.isPlaying()) return null;
        ItemStack record = jukebox.getRecord();
        if (record.isEmpty()) return null;
        return new Snapshot(record.effectiveName(), base.clone().add(.5, 1.46, .5),
                Display.Billboard.CENTER, record, base.clone().add(.5, 1.22, .5));
    }

    private static Snapshot beehiveSnapshot(Beehive beehive, Location base) {
        if (!(beehive.getBlockData() instanceof org.bukkit.block.data.type.Beehive data)) return null;
        BlockFace facing = data.getFacing();
        Component text = Component.text(beehive.getEntityCount() + "/" + beehive.getMaxEntities() + " bees", NamedTextColor.YELLOW)
                .append(Component.text(" · ", NamedTextColor.GRAY))
                .append(Component.text("Honey " + data.getHoneyLevel() + "/" + data.getMaximumHoneyLevel(), NamedTextColor.GOLD));
        Location loc = new Location(base.getWorld(),
                base.getX() + .5 + facing.getModX() * FRONT_OFFSET,
                base.getY() + .55,
                base.getZ() + .5 + facing.getModZ() * FRONT_OFFSET,
                FurnaceLabels.yawFor(facing), 0f);
        return new Snapshot(text, loc, Display.Billboard.FIXED, null, null);
    }

    private static Snapshot lecternSnapshot(Lectern lectern, Location base) {
        ItemStack book = lectern.getInventory().getItem(0);
        if (book == null || book.isEmpty()) return null;
        Component text = book.effectiveName()
                .append(Component.text(" · p." + (lectern.getPage() + 1), NamedTextColor.GRAY));
        return new Snapshot(text, base.clone().add(.5, 1.25, .5), Display.Billboard.CENTER, null, null);
    }

    void refresh() {
        Iterator<Map.Entry<Location, Tracked>> iterator = tiles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Location, Tracked> entry = iterator.next();
            Location location = entry.getKey();
            Tracked tracked = entry.getValue();
            if (location.getWorld() == null || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                tracked.destroyAll(sender);
                iterator.remove();
                continue;
            }
            BlockState state = location.getBlock().getState();
            if (!isSupported(state)) {
                tracked.destroyAll(sender);
                iterator.remove();
                continue;
            }
            update(tracked, state);
        }
    }

    void update(Tracked tracked, BlockState state) {
        Snapshot snap = snapshot(state, tracked.blockLocation);
        if (snap == null) {
            tracked.destroyAll(sender);
            return;
        }
        Location base = tracked.blockLocation;
        BlockFace frontFacing = state instanceof Beehive && state.getBlockData() instanceof Directional dir
                ? dir.getFacing() : null;
        Map<UUID, Player> viewers = new HashMap<>();
        long chunkKey = Chunk.getChunkKey(base.getBlockX() >> 4, base.getBlockZ() >> 4);
        for (Player player : base.getWorld().getPlayers()) {
            Location eye = player.getEyeLocation();
            boolean inFront = frontFacing == null
                    || (eye.getX() - snap.textLoc().getX()) * frontFacing.getModX()
                            + (eye.getZ() - snap.textLoc().getZ()) * frontFacing.getModZ() > -.05;
            if (PacketHolograms.isVisible(player) && inFront
                    && eye.distanceSquared(snap.textLoc()) <= RANGE * RANGE && player.isChunkSent(chunkKey)) {
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

        syncText(viewers, tracked, snap.text(), snap.textLoc(), snap.billboard());
        syncItem(viewers, tracked, snap.item(), snap.itemLoc(), snap.billboard());
        tracked.viewers.addAll(viewers.keySet());
    }

    private void syncText(Map<UUID, Player> viewers, Tracked tracked,
            Component desiredText, Location desiredLoc, Display.Billboard billboard) {
        if (desiredText == null) {
            if (tracked.textId != 0) {
                for (UUID viewerId : tracked.viewers) {
                    Player player = viewers.get(viewerId);
                    if (player != null) sender.destroy(player, tracked.textId);
                }
                tracked.textId = 0;
                tracked.textUuid = null;
                tracked.textLoc = null;
                tracked.text = null;
            }
            return;
        }
        if (tracked.textId == 0) {
            tracked.textId = sender.nextEntityId();
            tracked.textUuid = UUID.randomUUID();
            tracked.textLoc = desiredLoc;
            tracked.text = desiredText;
            for (Player player : viewers.values()) {
                sender.spawnText(player, tracked.textId, tracked.textUuid, desiredLoc, desiredText,
                        BACKGROUND_ARGB, 0f, TEXT_SCALE, billboard);
            }
            return;
        }
        boolean moved = tracked.textLoc == null || tracked.textLoc.distanceSquared(desiredLoc) > .0001
                || Float.compare(tracked.textLoc.getYaw(), desiredLoc.getYaw()) != 0;
        boolean changed = !Objects.equals(tracked.text, desiredText);
        if (moved) tracked.textLoc = desiredLoc;
        if (changed) tracked.text = desiredText;
        for (Map.Entry<UUID, Player> entry : viewers.entrySet()) {
            Player player = entry.getValue();
            if (!tracked.viewers.contains(entry.getKey())) {
                sender.spawnText(player, tracked.textId, tracked.textUuid, desiredLoc, desiredText,
                        BACKGROUND_ARGB, 0f, TEXT_SCALE, billboard);
            } else {
                if (moved) sender.teleport(player, tracked.textId, desiredLoc);
                if (changed) sender.updateText(player, tracked.textId, desiredText);
            }
        }
    }

    private void syncItem(Map<UUID, Player> viewers, Tracked tracked,
            ItemStack desiredItem, Location desiredLoc, Display.Billboard billboard) {
        if (desiredItem == null) {
            if (tracked.itemId != 0) {
                for (UUID viewerId : tracked.viewers) {
                    Player player = viewers.get(viewerId);
                    if (player != null) sender.destroy(player, tracked.itemId);
                }
                tracked.itemId = 0;
                tracked.itemUuid = null;
                tracked.itemLoc = null;
                tracked.item = null;
            }
            return;
        }
        if (tracked.itemId == 0) {
            tracked.itemId = sender.nextEntityId();
            tracked.itemUuid = UUID.randomUUID();
            tracked.itemLoc = desiredLoc;
            tracked.item = desiredItem.clone();
            for (Player player : viewers.values()) {
                sender.spawnItem(player, tracked.itemId, tracked.itemUuid, desiredLoc, tracked.item,
                        DISC_SCALE, billboard);
            }
            return;
        }
        boolean moved = tracked.itemLoc == null || tracked.itemLoc.distanceSquared(desiredLoc) > .0001;
        boolean changed = tracked.item == null || !tracked.item.isSimilar(desiredItem);
        if (moved) tracked.itemLoc = desiredLoc;
        if (changed) tracked.item = desiredItem.clone();
        for (Map.Entry<UUID, Player> entry : viewers.entrySet()) {
            Player player = entry.getValue();
            if (!tracked.viewers.contains(entry.getKey())) {
                sender.spawnItem(player, tracked.itemId, tracked.itemUuid, desiredLoc, tracked.item,
                        DISC_SCALE, billboard);
            } else {
                if (moved) sender.teleport(player, tracked.itemId, desiredLoc);
                if (changed) sender.updateItem(player, tracked.itemId, tracked.item);
            }
        }
    }

    void disable() {
        tiles.values().forEach(tracked -> tracked.destroyAll(sender));
        tiles.clear();
    }
}
