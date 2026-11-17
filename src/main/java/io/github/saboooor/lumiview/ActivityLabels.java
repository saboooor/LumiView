package io.github.saboooor.lumiview;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Transient client-side holograms and item pop previews for block and workstation activity. */
final class ActivityLabels implements Listener {
    static final double RANGE = 12;
    static final int BACKGROUND_ARGB = Color.fromARGB(75, 0, 0, 0).asARGB();
    static final float TEXT_SCALE = .26f;
    static final float ITEM_SCALE = .24f;
    static final float POP_SCALE = .32f;

    private final LumiViewPlugin plugin;
    private final PacketHolograms.Sender sender;
    private final Map<Location, Transient> active = new HashMap<>();
    private final Map<Location, Long> transferCooldowns = new HashMap<>();
    private long tick;

    private static final class Transient {
        final Location key;
        final Set<UUID> viewers = new HashSet<>();
        int entityId;
        long expiresAt;

        Transient(Location key, int entityId, long expiresAt) {
            this.key = key;
            this.entityId = entityId;
            this.expiresAt = expiresAt;
        }

        void destroy(PacketHolograms.Sender sender) {
            if (entityId != 0) {
                for (UUID id : viewers) {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) sender.destroy(player, entityId);
                }
            }
            viewers.clear();
            entityId = 0;
        }
    }

    ActivityLabels(LumiViewPlugin plugin) { this(plugin, PacketHolograms.INSTANCE); }

    ActivityLabels(LumiViewPlugin plugin, PacketHolograms.Sender sender) {
        this.plugin = plugin;
        this.sender = sender;
    }

    static Component noteText(Instrument instrument, Note note) {
        return noteText(instrument.name(), note);
    }

    static Component noteText(String instrumentName, Note note) {
        String tone = note.getTone().name() + (note.isSharped() ? "#" : "");
        String inst = switch (instrumentName) {
            case "PIANO" -> "Harp";
            case "BASS_DRUM" -> "Bass Drum";
            case "SNARE_DRUM" -> "Snare";
            case "STICKS" -> "Hat";
            case "BASS_GUITAR" -> "Bass";
            case "FLUTE" -> "Flute";
            case "BELL" -> "Bell";
            case "GUITAR" -> "Guitar";
            case "CHIME" -> "Chime";
            case "XYLOPHONE" -> "Xylophone";
            case "IRON_XYLOPHONE" -> "Iron Xylophone";
            case "COW_BELL" -> "Cow Bell";
            case "DIDGERIDOO" -> "Didgeridoo";
            case "BIT" -> "Bit";
            case "BANJO" -> "Banjo";
            case "PLING" -> "Pling";
            default -> "Note";
        };
        return Component.text("♪ " + tone + " · " + inst, NamedTextColor.LIGHT_PURPLE);
    }

    static Component composterText(int level, int maxLevel) {
        return level >= maxLevel
                ? Component.text("Compost Ready ✓", NamedTextColor.GREEN)
                : Component.text("Compost " + level + "/" + maxLevel, NamedTextColor.GOLD);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNote(NotePlayEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation().add(.5, 1.25, .5);
        showText(block.getLocation().toBlockLocation(), loc, noteText(event.getInstrument(), event.getNote()), 30);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onComposterInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != Material.COMPOSTER || plugin == null) return;
        Block block = event.getClickedBlock();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (block.getBlockData() instanceof Levelled levelled && levelled.getLevel() > 0) {
                showText(block.getLocation().toBlockLocation(),
                        block.getLocation().add(.5, 1.15, .5),
                        composterText(levelled.getLevel(), levelled.getMaximumLevel()), 40);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Block block = event.getBlock();
        ItemStack item = event.getItem();
        if (item.isEmpty()) return;
        Location spawnLoc = block.getLocation().add(.5, .5, .5);
        if (block.getBlockData() instanceof Directional dir) {
            BlockFace facing = dir.getFacing();
            spawnLoc.add(facing.getModX() * .58, facing.getModY() * .58, facing.getModZ() * .58);
        }
        showItem(block.getLocation().toBlockLocation(), spawnLoc, item, ITEM_SCALE, 16);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        ItemStack item = event.getItem();
        if (item.isEmpty()) return;
        Location dest = event.getDestination().getLocation();
        if (dest == null || dest.getWorld() == null) return;
        Location key = dest.toBlockLocation();
        long last = transferCooldowns.getOrDefault(key, -100L);
        if (tick - last < 10) return;
        transferCooldowns.put(key, tick);
        showItem(key, key.clone().add(.5, 1.12, .5), item, ITEM_SCALE, 14);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorkstationResultClick(InventoryClickEvent event) {
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || event.getSlotType() != InventoryType.SlotType.RESULT) return;
        ItemStack result = event.getCurrentItem();
        if (result == null || result.isEmpty()) return;
        Location loc = clicked.getLocation();
        if (loc == null || loc.getWorld() == null) return;
        Location key = loc.toBlockLocation();
        showItem(key, key.clone().add(.5, 1.48, .5), result, POP_SCALE, 18);
    }

    void showText(Location key, Location location, Component text, int durationTicks) {
        if (location.getWorld() == null) return;
        replace(key, durationTicks, (player, entityId, uuid) ->
                sender.spawnText(player, entityId, uuid, location, text, BACKGROUND_ARGB, 0f, TEXT_SCALE, Display.Billboard.CENTER));
    }

    void showItem(Location key, Location location, ItemStack item, float scale, int durationTicks) {
        if (location.getWorld() == null) return;
        ItemStack snapshot = item.clone();
        replace(key, durationTicks, (player, entityId, uuid) ->
                sender.spawnItem(player, entityId, uuid, location, snapshot, scale, Display.Billboard.CENTER));
    }

    @FunctionalInterface
    private interface Spawner {
        void spawn(Player player, int entityId, UUID uuid);
    }

    private void replace(Location key, int durationTicks, Spawner spawner) {
        Transient existing = active.remove(key);
        if (existing != null) existing.destroy(sender);
        if (!key.getWorld().isChunkLoaded(key.getBlockX() >> 4, key.getBlockZ() >> 4)) return;
        int entityId = sender.nextEntityId();
        UUID uuid = UUID.randomUUID();
        Transient entry = new Transient(key, entityId, tick + durationTicks);
        long chunkKey = Chunk.getChunkKey(key.getBlockX() >> 4, key.getBlockZ() >> 4);
        for (Player player : key.getWorld().getPlayers()) {
            if (PacketHolograms.isVisible(player) && player.isChunkSent(chunkKey)
                    && player.getEyeLocation().distanceSquared(key.clone().add(.5, 1.0, .5)) <= RANGE * RANGE) {
                entry.viewers.add(player.getUniqueId());
                spawner.spawn(player, entityId, uuid);
            }
        }
        if (!entry.viewers.isEmpty()) active.put(key, entry);
    }

    void refresh() {
        tick += 2;
        Iterator<Map.Entry<Location, Transient>> iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            Transient entry = iterator.next().getValue();
            if (tick >= entry.expiresAt) {
                entry.destroy(sender);
                iterator.remove();
            } else {
                Iterator<UUID> viewerIter = entry.viewers.iterator();
                while (viewerIter.hasNext()) {
                    Player player = Bukkit.getPlayer(viewerIter.next());
                    if (player == null || !PacketHolograms.isVisible(player)) {
                        if (player != null) sender.destroy(player, entry.entityId);
                        viewerIter.remove();
                    }
                }
            }
        }
    }

    void disable() {
        active.values().forEach(entry -> entry.destroy(sender));
        active.clear();
        transferCooldowns.clear();
    }
}
