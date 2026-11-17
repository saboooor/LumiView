package io.github.saboooor.lumiview;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Beacon;
import org.bukkit.block.Beehive;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Jukebox;
import org.bukkit.block.Lectern;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class TileDisplayLabelsTest {
    @Test
    void buildsSnapshotsForBeaconJukeboxBeehiveAndLectern() {
        World world = mock(World.class);
        Location base = new Location(world, 2, 64, 4);

        Beacon beacon = mock(Beacon.class);
        when(beacon.getTier()).thenReturn(4);
        var beaconSnap = TileDisplayLabels.snapshot(beacon, base);
        assertNotNull(beaconSnap);
        assertNull(beaconSnap.item());
        assertEquals(Display.Billboard.CENTER, beaconSnap.billboard());
        assertEquals(Component.text("Tier 4", NamedTextColor.AQUA)
                        .append(Component.text(" · ", NamedTextColor.GRAY))
                        .append(Component.translatable("effect.minecraft.speed", NamedTextColor.WHITE)),
                TileDisplayLabels.beaconText(4, "effect.minecraft.speed", null));

        Jukebox jukebox = mock(Jukebox.class);
        ItemStack disc = mock(ItemStack.class);
        when(disc.isEmpty()).thenReturn(false);
        when(disc.effectiveName()).thenReturn(Component.text("Pigstep"));
        when(jukebox.isPlaying()).thenReturn(true);
        when(jukebox.getRecord()).thenReturn(disc);
        var jukeboxSnap = TileDisplayLabels.snapshot(jukebox, base);
        assertNotNull(jukeboxSnap);
        assertSame(disc, jukeboxSnap.item());
        assertEquals(Component.text("Pigstep"), jukeboxSnap.text());

        Beehive beehive = mock(Beehive.class);
        var hiveData = mock(org.bukkit.block.data.type.Beehive.class);
        when(hiveData.getFacing()).thenReturn(BlockFace.SOUTH);
        when(hiveData.getHoneyLevel()).thenReturn(5);
        when(hiveData.getMaximumHoneyLevel()).thenReturn(5);
        when(beehive.getBlockData()).thenReturn(hiveData);
        when(beehive.getEntityCount()).thenReturn(3);
        when(beehive.getMaxEntities()).thenReturn(3);
        var hiveSnap = TileDisplayLabels.snapshot(beehive, base);
        assertNotNull(hiveSnap);
        assertEquals(Display.Billboard.FIXED, hiveSnap.billboard());
        assertEquals(Component.text("3/3 bees", NamedTextColor.YELLOW)
                .append(Component.text(" · ", NamedTextColor.GRAY))
                .append(Component.text("Honey 5/5", NamedTextColor.GOLD)), hiveSnap.text());

        Lectern lectern = mock(Lectern.class);
        Inventory lecternInv = mock(Inventory.class);
        ItemStack book = mock(ItemStack.class);
        when(book.isEmpty()).thenReturn(false);
        when(book.effectiveName()).thenReturn(Component.text("Journal"));
        when(lectern.getInventory()).thenReturn(lecternInv);
        when(lecternInv.getItem(0)).thenReturn(book);
        when(lectern.getPage()).thenReturn(2);
        var lecternSnap = TileDisplayLabels.snapshot(lectern, base);
        assertNotNull(lecternSnap);
        assertEquals(Component.text("Journal").append(Component.text(" · p.3", NamedTextColor.GRAY)),
                lecternSnap.text());
    }

    @Test
    void spawnsJukeboxDiscAndTextAndRespectsViewerToggle() {
        PacketHolograms.Sender sender = mock(PacketHolograms.Sender.class);
        when(sender.nextEntityId()).thenReturn(30, 31);
        TileDisplayLabels labels = new TileDisplayLabels(sender);

        World world = mock(World.class);
        Location base = new Location(world, 8, 64, 8);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        long chunkKey = Chunk.getChunkKey(0, 0);

        Block block = mock(Block.class);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        when(world.getBlockAt(8, 64, 8)).thenReturn(block);

        Jukebox jukebox = mock(Jukebox.class);
        ItemStack disc = mock(ItemStack.class);
        when(disc.isEmpty()).thenReturn(false);
        when(disc.clone()).thenReturn(disc);
        when(disc.effectiveName()).thenReturn(Component.text("Cat"));
        when(jukebox.isPlaying()).thenReturn(true);
        when(jukebox.getRecord()).thenReturn(disc);
        when(block.getState()).thenReturn(jukebox);

        Player viewer = mock(Player.class), hidden = mock(Player.class);
        UUID viewerId = UUID.randomUUID(), hiddenId = UUID.randomUUID();
        when(viewer.getUniqueId()).thenReturn(viewerId);
        when(hidden.getUniqueId()).thenReturn(hiddenId);
        when(viewer.isChunkSent(chunkKey)).thenReturn(true);
        when(hidden.isChunkSent(chunkKey)).thenReturn(true);
        when(viewer.getEyeLocation()).thenReturn(new Location(world, 8.5, 65.5, 10.0));
        when(hidden.getEyeLocation()).thenReturn(new Location(world, 8.5, 65.5, 10.0));
        when(world.getPlayers()).thenReturn(List.of(viewer, hidden));

        PacketHolograms.toggleHidden(hidden);
        labels.track(base);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(viewerId)).thenReturn(viewer);
            labels.refresh();

            verify(sender).spawnText(same(viewer), eq(30), any(UUID.class), eq(base.clone().add(.5, 1.46, .5)),
                    eq(Component.text("Cat")), eq(TileDisplayLabels.BACKGROUND_ARGB), eq(0f),
                    eq(TileDisplayLabels.TEXT_SCALE), eq(Display.Billboard.CENTER));
            verify(sender).spawnItem(same(viewer), eq(31), any(UUID.class), eq(base.clone().add(.5, 1.22, .5)),
                    same(disc), eq(TileDisplayLabels.DISC_SCALE), eq(Display.Billboard.CENTER));
            verify(sender, never()).spawnText(same(hidden), anyInt(), any(), any(), any(), anyInt(), anyFloat(), anyFloat(), any());

            labels.disable();
        } finally {
            PacketHolograms.clearHidden();
        }
        verify(sender).destroy(same(viewer), eq(30), eq(31));
    }
}
