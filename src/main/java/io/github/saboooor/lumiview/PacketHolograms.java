package io.github.saboooor.lumiview;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Client-side text display holograms dispatched through PacketEvents. */
final class PacketHolograms {
    private static final int INDEX_NO_GRAVITY = 5;
    private static final int INDEX_TRANSLATION = 11;
    private static final int INDEX_SCALE = 12;
    private static final int INDEX_BILLBOARD = 15;
    private static final int INDEX_BRIGHTNESS = 16;
    private static final int INDEX_TEXT = 23;
    private static final int INDEX_LINE_WIDTH = 24;
    private static final int INDEX_BACKGROUND_COLOR = 25;
    private static final int INDEX_STYLE_FLAGS = 27;
    private static final int INDEX_ITEM_STACK = 23;
    private static final int INDEX_ITEM_DISPLAY_TYPE = 24;

    private static final int FULL_BRIGHTNESS = (15 << 4) | (15 << 20);
    private static final int LINE_WIDTH = 240;
    private static final byte STYLE_SHADOW = 0x01;
    private static final byte ITEM_DISPLAY_FIXED = (byte) ItemDisplay.ItemDisplayTransform.FIXED.ordinal();

    static final Sender INSTANCE = new PacketEventsSender();
    private static final Set<UUID> HIDDEN_VIEWERS = new HashSet<>();

    static boolean isVisible(Player player) {
        return player != null && !HIDDEN_VIEWERS.contains(player.getUniqueId());
    }

    static boolean toggleHidden(Player player) {
        UUID id = player.getUniqueId();
        if (HIDDEN_VIEWERS.add(id)) return true;
        HIDDEN_VIEWERS.remove(id);
        return false;
    }

    static void clearHidden() {
        HIDDEN_VIEWERS.clear();
    }

    interface Sender {
        int nextEntityId();

        void spawnText(Player player, int entityId, UUID uuid, Location location,
                Component text, int backgroundArgb, float yOffset, float scale, Display.Billboard billboard);

        void spawnItem(Player player, int entityId, UUID uuid, Location location,
                ItemStack item, float scale, Display.Billboard billboard);

        void mount(Player player, int vehicleId, int... passengerIds);

        void updateText(Player player, int entityId, Component text);

        void updateItem(Player player, int entityId, ItemStack item);

        void teleport(Player player, int entityId, Location location);

        void destroy(Player player, int... entityIds);
    }

    private PacketHolograms() {}

    private static final class PacketEventsSender implements Sender {
        @Override
        public int nextEntityId() {
            return SpigotReflectionUtil.generateEntityId();
        }

        @Override
        public void spawnText(Player player, int entityId, UUID uuid, Location location,
                Component text, int backgroundArgb, float yOffset, float scale, Display.Billboard billboard) {
            if (!player.isOnline()) return;
            var peLocation = SpigotConversionUtil.fromBukkitLocation(location);
            var spawn = new WrapperPlayServerSpawnEntity(
                    entityId, uuid, EntityTypes.TEXT_DISPLAY, peLocation, 0f, 0, null);
            List<EntityData<?>> metadata = new ArrayList<>(9);
            metadata.add(new EntityData<>(INDEX_NO_GRAVITY, EntityDataTypes.BOOLEAN, true));
            if (yOffset != 0f) {
                metadata.add(new EntityData<>(INDEX_TRANSLATION, EntityDataTypes.VECTOR3F, new Vector3f(0f, yOffset, 0f)));
            }
            metadata.add(new EntityData<>(INDEX_SCALE, EntityDataTypes.VECTOR3F, new Vector3f(scale, scale, scale)));
            metadata.add(new EntityData<>(INDEX_BILLBOARD, EntityDataTypes.BYTE, (byte) billboard.ordinal()));
            metadata.add(new EntityData<>(INDEX_BRIGHTNESS, EntityDataTypes.INT, FULL_BRIGHTNESS));
            metadata.add(new EntityData<>(INDEX_TEXT, EntityDataTypes.ADV_COMPONENT, text));
            metadata.add(new EntityData<>(INDEX_LINE_WIDTH, EntityDataTypes.INT, LINE_WIDTH));
            metadata.add(new EntityData<>(INDEX_BACKGROUND_COLOR, EntityDataTypes.INT, backgroundArgb));
            metadata.add(new EntityData<>(INDEX_STYLE_FLAGS, EntityDataTypes.BYTE, STYLE_SHADOW));
            var manager = PacketEvents.getAPI().getPlayerManager();
            manager.sendPacket(player, spawn);
            manager.sendPacket(player, new WrapperPlayServerEntityMetadata(entityId, metadata));
        }

        @Override
        public void spawnItem(Player player, int entityId, UUID uuid, Location location,
                ItemStack item, float scale, Display.Billboard billboard) {
            if (!player.isOnline()) return;
            var peLocation = SpigotConversionUtil.fromBukkitLocation(location);
            var spawn = new WrapperPlayServerSpawnEntity(
                    entityId, uuid, EntityTypes.ITEM_DISPLAY, peLocation, 0f, 0, null);
            List<EntityData<?>> metadata = List.of(
                    new EntityData<>(INDEX_NO_GRAVITY, EntityDataTypes.BOOLEAN, true),
                    new EntityData<>(INDEX_SCALE, EntityDataTypes.VECTOR3F, new Vector3f(scale, scale, scale)),
                    new EntityData<>(INDEX_BILLBOARD, EntityDataTypes.BYTE, (byte) billboard.ordinal()),
                    new EntityData<>(INDEX_BRIGHTNESS, EntityDataTypes.INT, FULL_BRIGHTNESS),
                    new EntityData<>(INDEX_ITEM_STACK, EntityDataTypes.ITEMSTACK, SpigotConversionUtil.fromBukkitItemStack(item)),
                    new EntityData<>(INDEX_ITEM_DISPLAY_TYPE, EntityDataTypes.BYTE, ITEM_DISPLAY_FIXED)
            );
            var manager = PacketEvents.getAPI().getPlayerManager();
            manager.sendPacket(player, spawn);
            manager.sendPacket(player, new WrapperPlayServerEntityMetadata(entityId, metadata));
        }

        @Override
        public void mount(Player player, int vehicleId, int... passengerIds) {
            if (!player.isOnline()) return;
            PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                    new WrapperPlayServerSetPassengers(vehicleId, passengerIds));
        }

        @Override
        public void updateText(Player player, int entityId, Component text) {
            if (!player.isOnline()) return;
            List<EntityData<?>> metadata = List.of(new EntityData<>(INDEX_TEXT, EntityDataTypes.ADV_COMPONENT, text));
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerEntityMetadata(entityId, metadata));
        }

        @Override
        public void updateItem(Player player, int entityId, ItemStack item) {
            if (!player.isOnline()) return;
            List<EntityData<?>> metadata = List.of(new EntityData<>(INDEX_ITEM_STACK, EntityDataTypes.ITEMSTACK,
                    SpigotConversionUtil.fromBukkitItemStack(item)));
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerEntityMetadata(entityId, metadata));
        }

        @Override
        public void teleport(Player player, int entityId, Location location) {
            if (!player.isOnline()) return;
            var peLocation = SpigotConversionUtil.fromBukkitLocation(location);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                    new WrapperPlayServerEntityTeleport(entityId, peLocation, false));
        }

        @Override
        public void destroy(Player player, int... entityIds) {
            if (!player.isOnline() || entityIds.length == 0) return;
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerDestroyEntities(entityIds));
        }
    }
}
