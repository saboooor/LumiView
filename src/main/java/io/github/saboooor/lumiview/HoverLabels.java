package io.github.saboooor.lumiview;

import io.papermc.paper.inventory.tooltip.TooltipContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Shelf;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** A single private client-side hologram per viewer, refreshed while their crosshair points at an item. */
final class HoverLabels implements Listener {
    static final double RANGE = 5;
    static final int BACKGROUND_ARGB = Color.fromARGB(160, 0, 0, 0).asARGB();
    private static final TooltipContext TOOLTIP = TooltipContext.create();
    private final LumiViewPlugin plugin;
    private final PlacedItems placedItems;
    private final PacketHolograms.Sender sender;
    private final Map<UUID, Label> labels = new HashMap<>();

    record Target(Location location, ItemStack item, double distance, int books) {
        Target(Location location, ItemStack item, double distance) { this(location, item, distance, 0); }
    }
    private record Label(int entityId, Location location, ItemStack item, int books) {}

    HoverLabels(LumiViewPlugin plugin, PlacedItems placedItems) {
        this(plugin, placedItems, PacketHolograms.INSTANCE);
    }

    HoverLabels(LumiViewPlugin plugin, PlacedItems placedItems, PacketHolograms.Sender sender) {
        this.plugin = plugin;
        this.placedItems = placedItems;
        this.sender = sender;
    }

    void refresh() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Target target = player.isDead() || !PacketHolograms.isVisible(player) ? null : target(player);
            update(player, target);
        }
    }

    private Target target(Player player) {
        Location eye = player.getEyeLocation();
        Vector start = eye.toVector(), direction = eye.getDirection();
        double range = loadedRange(eye, direction);
        if (range <= 0) return null;
        RayTraceResult blockHit = eye.getWorld().rayTraceBlocks(eye, direction, range, FluidCollisionMode.NEVER, false);
        double obstruction = blockHit == null ? range : blockHit.getHitPosition().distance(start);
        Target nearest = null;
        RayTraceResult entityHit = eye.getWorld().rayTraceEntities(eye, direction, range, 0, placedItems::isHitbox);
        if (entityHit != null && entityHit.getHitEntity() != null) {
            double distance = entityHit.getHitPosition().distance(start);
            PlacedItems.HoverTarget placed = placedItems.hoverTarget(entityHit.getHitEntity());
            if (placed != null && visible(distance, obstruction)) {
                nearest = new Target(placed.location(), placed.item(), distance, placed.books());
            }
        }

        // Walk only the short sight ray; never scan every shelf or load an unseen chunk.
        Set<Location> visited = new HashSet<>();
        for (double step = 0; step <= range; step += .125) {
            Location sample = eye.clone().add(direction.clone().multiply(step));
            Location location = new Location(eye.getWorld(), sample.getBlockX(), sample.getBlockY(), sample.getBlockZ());
            if (!visited.add(location)) continue;
            if (!eye.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) break;
            if (!(location.getBlock().getState() instanceof Shelf shelf)
                    || !(shelf.getBlockData() instanceof org.bukkit.block.data.type.Shelf data)) continue;
            RayTraceResult hit = shelf.getBlock().getBoundingBox().rayTrace(start, direction, range);
            if (hit == null || hit.getHitBlockFace() != data.getFacing()) continue;
            double distance = hit.getHitPosition().distance(start);
            boolean sameShelf = blockHit != null && shelf.getBlock().equals(blockHit.getHitBlock());
            if ((!sameShelf && !visible(distance, obstruction)) || nearest != null && distance >= nearest.distance()) continue;
            Vector local = hit.getHitPosition().clone().subtract(location.toVector());
            int slot = shelfSlot(data.getFacing(), local);
            if (slot < 0) continue;
            ItemStack[] contents = plugin.shelfItems(shelf);
            if (slot >= contents.length || contents[slot] == null || contents[slot].isEmpty()) continue;
            double horizontalOffset = (slot - 1) / 3.0;
            Location anchor = location.clone().add(.5, .78, .5)
                    .add(data.getFacing().getDirection().multiply(.3))
                    .add(data.getFacing().getModZ() * horizontalOffset, 0,
                            -data.getFacing().getModX() * horizontalOffset);
            nearest = new Target(anchor, contents[slot], distance);
        }
        return nearest;
    }

    private static double loadedRange(Location eye, Vector direction) {
        double range = RANGE;
        int chunkX = eye.getBlockX() >> 4, chunkZ = eye.getBlockZ() >> 4;
        // Bukkit block ray tracing may load chunks. Stop before any unloaded chunk on this ray.
        for (int x = chunkX - 1; x <= chunkX + 1; x++) {
            for (int z = chunkZ - 1; z <= chunkZ + 1; z++) {
                if (eye.getWorld().isChunkLoaded(x, z)) continue;
                BoundingBox box = new BoundingBox(x * 16, eye.getWorld().getMinHeight(), z * 16,
                        x * 16 + 16, eye.getWorld().getMaxHeight(), z * 16 + 16);
                RayTraceResult hit = box.rayTrace(eye.toVector(), direction, range);
                if (hit != null) range = Math.max(0, hit.getHitPosition().distance(eye.toVector()) - .001);
                if (range == 0) return 0;
            }
        }
        return range;
    }

    static boolean visible(double distance, double obstruction) { return distance <= obstruction + .025; }

    static int shelfSlot(BlockFace facing, Vector hit) {
        if (hit.getY() < .15 || hit.getY() > .85) return -1;
        double horizontal = switch (facing) {
            case NORTH -> 1 - hit.getX();
            case SOUTH -> hit.getX();
            case EAST -> 1 - hit.getZ();
            case WEST -> hit.getZ();
            default -> -1;
        };
        return horizontal < 0 || horizontal > 1 ? -1 : Math.min(2, (int) (horizontal * 3));
    }

    static Component text(ItemStack item, Player player) {
        List<Component> lines = item.computeTooltipLines(TOOLTIP, player);
        if (lines.isEmpty()) return item.effectiveName();
        Component result = Component.empty();
        int count = Math.min(lines.size(), 6);
        for (int index = 0; index < count; index++) {
            if (index > 0) result = result.append(Component.newline());
            result = result.append(lines.get(index));
        }
        if (lines.size() > count) result = result.append(Component.newline()).append(Component.text("…"));
        return result;
    }

    static Component text(Target target, Player player) {
        Component tooltip = text(target.item(), player);
        if (target.books() == 0) return tooltip;
        return tooltip.append(Component.newline()).append(Component.text(
                target.books() + (target.books() == 1 ? " book" : " books") + " · top book",
                net.kyori.adventure.text.format.NamedTextColor.GRAY));
    }

    void update(Player player, Target target) {
        UUID id = player.getUniqueId();
        Label label = labels.get(id);
        if (target == null) { remove(player); return; }
        if (label != null && !label.location().getWorld().equals(target.location().getWorld())) {
            remove(player);
            label = null;
        }
        if (label == null) {
            int entityId = sender.nextEntityId();
            sender.spawnText(player, entityId, UUID.randomUUID(), target.location(), text(target, player),
                    BACKGROUND_ARGB, 0f, .4f, Display.Billboard.CENTER);
            labels.put(id, new Label(entityId, target.location().clone(), target.item().clone(), target.books()));
        } else {
            Location location = label.location();
            boolean moved = location.distanceSquared(target.location()) > .0001;
            if (moved) {
                sender.teleport(player, label.entityId(), target.location());
                location = target.location().clone();
            }
            ItemStack item = label.item();
            int books = label.books();
            boolean textChanged = !item.isSimilar(target.item()) || item.getAmount() != target.item().getAmount()
                    || books != target.books();
            if (textChanged) {
                sender.updateText(player, label.entityId(), text(target, player));
                item = target.item().clone();
                books = target.books();
            }
            if (moved || textChanged) labels.put(id, new Label(label.entityId(), location, item, books));
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) { labels.remove(event.getPlayer().getUniqueId()); }

    private void remove(Player player) {
        Label label = labels.remove(player.getUniqueId());
        if (label != null) sender.destroy(player, label.entityId());
    }

    void disable() {
        for (UUID id : List.copyOf(labels.keySet())) {
            Label label = labels.remove(id);
            Player player = Bukkit.getPlayer(id);
            if (label != null && player != null) sender.destroy(player, label.entityId());
        }
    }
}
