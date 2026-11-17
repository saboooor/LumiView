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
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;

/** Floating client-side stack names and despawn timers anchored to dropped item entities. */
final class DroppedItemLabels implements Listener {
    static final double RANGE = 16;
    static final int BACKGROUND_ARGB = Color.fromARGB(75, 0, 0, 0).asARGB();
    static final float MAIN_Y_OFFSET = .50f;
    static final float MAIN_SCALE = .4f;
    static final float TIMER_Y_OFFSET = .43f;
    static final float TIMER_SCALE = .28f;
    static final int DESPAWN_TICKS = 6000;
    private final PacketHolograms.Sender sender;
    private final Map<UUID, Tracked> items = new HashMap<>();

    static final class Tracked {
        final Item item;
        final Set<UUID> viewers = new HashSet<>();
        int entityId;
        UUID uuid;
        int timerEntityId;
        UUID timerUuid;
        World world;
        ItemStack snapshot;
        Component timerText;

        Tracked(Item item) { this.item = item; }

        int[] entityIds() {
            return timerEntityId == 0 ? new int[] {entityId} : new int[] {entityId, timerEntityId};
        }

        void removeDisplay(PacketHolograms.Sender sender) {
            if (entityId != 0) {
                int[] ids = entityIds();
                for (UUID viewerId : viewers) {
                    Player player = Bukkit.getPlayer(viewerId);
                    if (player != null) sender.destroy(player, ids);
                }
            }
            entityId = 0;
            uuid = null;
            timerEntityId = 0;
            timerUuid = null;
            world = null;
            snapshot = null;
            timerText = null;
            viewers.clear();
        }
    }

    DroppedItemLabels() { this(PacketHolograms.INSTANCE); }

    DroppedItemLabels(PacketHolograms.Sender sender) { this.sender = sender; }

    void enable() {
        Bukkit.getWorlds().forEach(world -> world.getEntitiesByClass(Item.class).forEach(this::track));
    }

    void track(Item item) { items.putIfAbsent(item.getUniqueId(), new Tracked(item)); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void spawn(ItemSpawnEvent event) { track(event.getEntity()); }

    @EventHandler
    public void load(EntitiesLoadEvent event) {
        event.getEntities().forEach(entity -> { if (entity instanceof Item item) track(item); });
    }

    @EventHandler
    public void unload(EntitiesUnloadEvent event) {
        event.getEntities().forEach(entity -> {
            Tracked tracked = items.remove(entity.getUniqueId());
            if (tracked != null) tracked.removeDisplay(sender);
        });
    }

    static Component text(ItemStack stack) {
        Component name = stack.effectiveName();
        return stack.getAmount() > 1 ? name.append(Component.text(" × " + stack.getAmount(), NamedTextColor.GRAY)) : name;
    }

    static Component timerText(Item item) {
        if (item.isUnlimitedLifetime() || !item.willAge()) return null;
        int remainingSeconds = Math.max(0, (DESPAWN_TICKS - item.getTicksLived() + 19) / 20);
        return Component.text(String.format("%d:%02d", remainingSeconds / 60, remainingSeconds % 60), NamedTextColor.GRAY);
    }

    void refresh() {
        Iterator<Tracked> iterator = items.values().iterator();
        while (iterator.hasNext()) {
            Tracked tracked = iterator.next();
            Item item = tracked.item;
            Location location = item.getLocation();
            if (!item.isValid() || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                    || item.getItemStack().isEmpty()) {
                tracked.removeDisplay(sender);
                iterator.remove();
                continue;
            }
            update(tracked, location);
        }
    }

    void update(Tracked tracked, Location location) {
        Item item = tracked.item;
        if (tracked.entityId != 0 && !location.getWorld().equals(tracked.world)) {
            tracked.removeDisplay(sender);
        }
        // Only send holograms while a nearby viewer can see the actual dropped item.
        Map<UUID, Player> viewers = new HashMap<>();
        long chunkKey = Chunk.getChunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        for (Player player : location.getWorld().getPlayers()) {
            if (PacketHolograms.isVisible(player) && player.canSee(item)
                    && player.getLocation().distanceSquared(location) <= RANGE * RANGE
                    && player.isChunkSent(chunkKey)) {
                viewers.put(player.getUniqueId(), player);
            } else if (tracked.viewers.remove(player.getUniqueId()) && tracked.entityId != 0) {
                sender.destroy(player, tracked.entityIds());
            }
        }
        tracked.viewers.retainAll(viewers.keySet());
        if (viewers.isEmpty()) {
            tracked.removeDisplay(sender);
            return;
        }
        ItemStack stack = item.getItemStack();
        Component currentTimer = timerText(item);
        int itemEntityId = item.getEntityId();
        if (tracked.entityId == 0) {
            tracked.entityId = sender.nextEntityId();
            tracked.uuid = UUID.randomUUID();
            if (currentTimer != null) {
                tracked.timerEntityId = sender.nextEntityId();
                tracked.timerUuid = UUID.randomUUID();
            }
            tracked.world = location.getWorld();
            tracked.snapshot = stack.clone();
            tracked.timerText = currentTimer;
            Component label = text(stack);
            int[] passengers = tracked.entityIds();
            viewers.forEach((id, player) -> {
                tracked.viewers.add(id);
                spawnForViewer(player, tracked, location, label, currentTimer, itemEntityId, passengers);
            });
            return;
        }
        boolean textChanged = tracked.snapshot == null
                || tracked.snapshot.getAmount() != stack.getAmount() || !tracked.snapshot.isSimilar(stack);
        if (textChanged) tracked.snapshot = stack.clone();
        boolean timerChanged = currentTimer != null && !Objects.equals(tracked.timerText, currentTimer);
        if (timerChanged) tracked.timerText = currentTimer;
        Component label = null;
        int[] passengers = tracked.entityIds();
        for (Map.Entry<UUID, Player> entry : viewers.entrySet()) {
            Player player = entry.getValue();
            if (tracked.viewers.add(entry.getKey())) {
                if (label == null) label = text(stack);
                spawnForViewer(player, tracked, location, label, currentTimer, itemEntityId, passengers);
            } else {
                if (textChanged) {
                    if (label == null) label = text(stack);
                    sender.updateText(player, tracked.entityId, label);
                }
                if (timerChanged && tracked.timerEntityId != 0) {
                    sender.updateText(player, tracked.timerEntityId, currentTimer);
                }
            }
        }
    }

    private void spawnForViewer(Player player, Tracked tracked, Location location, Component label,
            Component timerLabel, int itemEntityId, int[] passengers) {
        sender.spawnText(player, tracked.entityId, tracked.uuid, location, label,
                BACKGROUND_ARGB, MAIN_Y_OFFSET, MAIN_SCALE, Display.Billboard.CENTER);
        if (tracked.timerEntityId != 0 && timerLabel != null) {
            sender.spawnText(player, tracked.timerEntityId, tracked.timerUuid, location, timerLabel,
                    BACKGROUND_ARGB, TIMER_Y_OFFSET, TIMER_SCALE, Display.Billboard.CENTER);
        }
        sender.mount(player, itemEntityId, passengers);
    }

    void disable() {
        items.values().forEach(tracked -> tracked.removeDisplay(sender));
        items.clear();
    }
}
