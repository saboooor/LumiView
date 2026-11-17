package io.github.saboooor.lumiview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;

/** Client-side live item previews and repair cost holograms for open workstation GUIs. */
final class WorkstationLabels {
    static final double RANGE = 12;
    static final int BACKGROUND_ARGB = Color.fromARGB(75, 0, 0, 0).asARGB();
    static final float COST_SCALE = .25f;
    private static final float DEFAULT_ITEM_SCALE = .22f;

    private final PacketHolograms.Sender sender;
    private final Map<Location, Station> stations = new HashMap<>();

    record SlotSpec(int slot, double dx, double dy, double dz, float scale, boolean flat) {
        SlotSpec(int slot, double dx, double dy) { this(slot, dx, dy, 0.0, DEFAULT_ITEM_SCALE, false); }
    }

    record OpenStation(Inventory inventory, String typeName, Component statusText) {}

    private static final List<SlotSpec> WORKBENCH_SLOTS = buildGrid(1, true);
    private static final List<SlotSpec> CRAFTER_SLOTS = buildGrid(0, false);
    private static final List<SlotSpec> ANVIL_SLOTS = List.of(
            new SlotSpec(0, -.25, 1.12), new SlotSpec(1, 0.0, 1.12), new SlotSpec(2, .25, 1.12));
    private static final List<SlotSpec> ENCHANTING_SLOTS = List.of(
            new SlotSpec(0, -.14, 1.08), new SlotSpec(1, .14, 1.08));
    private static final List<SlotSpec> LOOM_SLOTS = List.of(
            new SlotSpec(0, -.27, 1.12), new SlotSpec(1, -.09, 1.12),
            new SlotSpec(2, .09, 1.12), new SlotSpec(3, .27, 1.12));
    private static final List<SlotSpec> STONECUTTER_SLOTS = List.of(
            new SlotSpec(0, -.16, .72), new SlotSpec(1, .16, .72));
    private static final List<SlotSpec> GRINDSTONE_SLOTS = List.of(
            new SlotSpec(0, -.22, 1.02), new SlotSpec(1, 0.0, 1.02), new SlotSpec(2, .22, 1.02));
    private static final List<SlotSpec> SMITHING_SLOTS = LOOM_SLOTS;
    private static final List<SlotSpec> CARTOGRAPHY_SLOTS = List.of(
            new SlotSpec(0, -.22, 1.12), new SlotSpec(1, 0.0, 1.12), new SlotSpec(2, .22, 1.12));
    private static final List<SlotSpec> CONTAINER_SLOTS = CARTOGRAPHY_SLOTS;
    private static final List<SlotSpec> BEACON_SLOTS = List.of(new SlotSpec(0, 0.0, 1.12));

    private static List<SlotSpec> buildGrid(int firstInputSlot, boolean includeResult) {
        List<SlotSpec> list = new ArrayList<>(includeResult ? 10 : 9);
        for (int i = 0; i < 9; i++) {
            int row = i / 3;
            int col = i % 3;
            list.add(new SlotSpec(firstInputSlot + i, (col - 1) * .22, 1.02, (row - 1) * .22, .18f, true));
        }
        if (includeResult) list.add(new SlotSpec(0, 0.0, 1.28, 0.0, .26f, false));
        return List.copyOf(list);
    }

    private static final class SlotDisplay {
        int entityId;
        UUID uuid;
        ItemStack item;

        void reset() {
            entityId = 0;
            uuid = null;
            item = null;
        }
    }

    private static final class Station {
        final Location blockLocation;
        final Set<UUID> viewers = new HashSet<>();
        final Map<Integer, SlotDisplay> displays = new LinkedHashMap<>();
        int textId;
        UUID textUuid;
        Component text;

        Station(Location blockLocation) { this.blockLocation = blockLocation; }

        int[] activeIds() {
            int[] buffer = new int[displays.size() + (textId != 0 ? 1 : 0)];
            int index = 0;
            for (SlotDisplay slot : displays.values()) {
                if (slot.entityId != 0) buffer[index++] = slot.entityId;
            }
            if (textId != 0) buffer[index++] = textId;
            if (index == buffer.length) return buffer;
            int[] trimmed = new int[index];
            System.arraycopy(buffer, 0, trimmed, 0, index);
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
            displays.clear();
            textId = 0;
            textUuid = null;
            text = null;
        }
    }

    WorkstationLabels() { this(PacketHolograms.INSTANCE); }

    WorkstationLabels(PacketHolograms.Sender sender) { this.sender = sender; }

    static List<SlotSpec> layoutFor(InventoryType type) {
        return type == null ? List.of() : layoutFor(type.name());
    }

    static List<SlotSpec> layoutFor(String typeName) {
        return switch (typeName) {
            case "WORKBENCH" -> WORKBENCH_SLOTS;
            case "CRAFTER" -> CRAFTER_SLOTS;
            case "ANVIL" -> ANVIL_SLOTS;
            case "ENCHANTING" -> ENCHANTING_SLOTS;
            case "LOOM" -> LOOM_SLOTS;
            case "STONECUTTER" -> STONECUTTER_SLOTS;
            case "GRINDSTONE" -> GRINDSTONE_SLOTS;
            case "SMITHING" -> SMITHING_SLOTS;
            case "CARTOGRAPHY" -> CARTOGRAPHY_SLOTS;
            case "CHEST", "ENDER_CHEST", "BARREL", "SHULKER_BOX" -> CONTAINER_SLOTS;
            case "BEACON" -> BEACON_SLOTS;
            default -> List.of();
        };
    }

    static Component statusText(InventoryView view) {
        if (view instanceof AnvilView anvil && anvil.getRepairCost() > 0) {
            int cost = anvil.getRepairCost();
            NamedTextColor color = cost > 39 ? NamedTextColor.RED : NamedTextColor.GREEN;
            return Component.text(cost + (cost == 1 ? " level" : " levels"), color);
        }
        return null;
    }

    void refresh() {
        Map<Location, OpenStation> active = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryView view = player.getOpenInventory();
            if (view == null) continue;
            Inventory top = view.getTopInventory();
            String typeName = top.getType().name();
            if (layoutFor(typeName).isEmpty()) continue;
            Location loc = top.getLocation();
            if (loc == null || loc.getWorld() == null) continue;
            active.putIfAbsent(loc.toBlockLocation(), new OpenStation(top, typeName, statusText(view)));
        }
        refresh(active);
    }

    void refresh(Map<Location, OpenStation> active) {
        Iterator<Map.Entry<Location, Station>> iterator = stations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Location, Station> entry = iterator.next();
            if (!active.containsKey(entry.getKey())) {
                entry.getValue().destroyAll(sender);
                iterator.remove();
            }
        }
        for (Map.Entry<Location, OpenStation> entry : active.entrySet()) {
            Location loc = entry.getKey();
            Station station = stations.computeIfAbsent(loc, Station::new);
            update(station, entry.getValue());
        }
    }

    private void update(Station station, OpenStation open) {
        Inventory inv = open.inventory();
        List<SlotSpec> specs = layoutFor(open.typeName());
        Component desiredText = open.statusText();

        boolean anyItem = false;
        int size = inv.getSize();
        for (SlotSpec spec : specs) {
            if (spec.slot() < size) {
                ItemStack stack = inv.getItem(spec.slot());
                if (stack != null && !stack.isEmpty()) {
                    anyItem = true;
                    break;
                }
            }
        }
        if (!anyItem && desiredText == null) {
            station.destroyAll(sender);
            return;
        }

        Location base = station.blockLocation;
        if (base.getWorld() == null || !base.getWorld().isChunkLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4)) {
            station.destroyAll(sender);
            return;
        }
        Location center = base.clone().add(.5, 1.0, .5);
        Map<UUID, Player> viewers = new HashMap<>();
        long chunkKey = Chunk.getChunkKey(base.getBlockX() >> 4, base.getBlockZ() >> 4);
        for (Player player : base.getWorld().getPlayers()) {
            if (PacketHolograms.isVisible(player)
                    && player.getEyeLocation().distanceSquared(center) <= RANGE * RANGE && player.isChunkSent(chunkKey)) {
                viewers.put(player.getUniqueId(), player);
            } else if (station.viewers.remove(player.getUniqueId())) {
                int[] ids = station.activeIds();
                if (ids.length > 0) sender.destroy(player, ids);
            }
        }
        station.viewers.retainAll(viewers.keySet());
        if (viewers.isEmpty()) {
            station.destroyAll(sender);
            return;
        }

        for (SlotSpec spec : specs) {
            ItemStack item = spec.slot() < size ? inv.getItem(spec.slot()) : null;
            if (item != null && item.isEmpty()) item = null;
            Location loc = new Location(base.getWorld(),
                    base.getX() + .5 + spec.dx(), base.getY() + spec.dy(), base.getZ() + .5 + spec.dz(),
                    0f, spec.flat() ? -90f : 0f);
            Display.Billboard billboard = spec.flat() ? Display.Billboard.FIXED : Display.Billboard.CENTER;
            syncItem(viewers, station, spec.slot(), item, loc, spec.scale(), billboard);
        }
        syncText(viewers, station, desiredText, base.clone().add(.5, 1.36, .5));
        station.viewers.addAll(viewers.keySet());
    }

    private void syncItem(Map<UUID, Player> viewers, Station station, int slotIndex,
            ItemStack desired, Location location, float scale, Display.Billboard billboard) {
        SlotDisplay slot = station.displays.computeIfAbsent(slotIndex, k -> new SlotDisplay());
        if (desired == null) {
            if (slot.entityId != 0) {
                for (UUID viewerId : station.viewers) {
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
                sender.spawnItem(player, slot.entityId, slot.uuid, location, slot.item, scale, billboard);
            }
            return;
        }
        boolean changed = slot.item == null || !slot.item.isSimilar(desired);
        if (changed) slot.item = desired.clone();
        for (Map.Entry<UUID, Player> entry : viewers.entrySet()) {
            Player player = entry.getValue();
            if (!station.viewers.contains(entry.getKey())) {
                sender.spawnItem(player, slot.entityId, slot.uuid, location, slot.item, scale, billboard);
            } else if (changed) {
                sender.updateItem(player, slot.entityId, slot.item);
            }
        }
    }

    private void syncText(Map<UUID, Player> viewers, Station station, Component desired, Location location) {
        if (desired == null) {
            if (station.textId != 0) {
                for (UUID viewerId : station.viewers) {
                    Player player = viewers.get(viewerId);
                    if (player != null) sender.destroy(player, station.textId);
                }
                station.textId = 0;
                station.textUuid = null;
                station.text = null;
            }
            return;
        }
        if (station.textId == 0) {
            station.textId = sender.nextEntityId();
            station.textUuid = UUID.randomUUID();
            station.text = desired;
            for (Player player : viewers.values()) {
                sender.spawnText(player, station.textId, station.textUuid, location, desired,
                        BACKGROUND_ARGB, 0f, COST_SCALE, Display.Billboard.CENTER);
            }
            return;
        }
        boolean changed = !Objects.equals(station.text, desired);
        if (changed) station.text = desired;
        for (Map.Entry<UUID, Player> entry : viewers.entrySet()) {
            Player player = entry.getValue();
            if (!station.viewers.contains(entry.getKey())) {
                sender.spawnText(player, station.textId, station.textUuid, location, desired,
                        BACKGROUND_ARGB, 0f, COST_SCALE, Display.Billboard.CENTER);
            } else if (changed) {
                sender.updateText(player, station.textId, desired);
            }
        }
    }

    void disable() {
        stations.values().forEach(station -> station.destroyAll(sender));
        stations.clear();
    }
}
