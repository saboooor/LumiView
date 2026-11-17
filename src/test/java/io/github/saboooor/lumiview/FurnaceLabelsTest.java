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
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class FurnaceLabelsTest {
    private ItemStack item(int amount) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getAmount()).thenReturn(amount);
        when(stack.isEmpty()).thenReturn(false);
        when(stack.clone()).thenReturn(stack);
        return stack;
    }

    @Test
    void usesDistinctFrontFaceSectionHeightsAndFacingYaws() {
        var furnace = FurnaceLabels.layout(Material.FURNACE);
        var blast = FurnaceLabels.layout(Material.BLAST_FURNACE);
        var smoker = FurnaceLabels.layout(Material.SMOKER);
        assertTrue(furnace.topY() > furnace.bottomY());
        assertTrue(blast.topY() > blast.bottomY());
        assertTrue(smoker.topY() > smoker.bottomY());
        assertNotEquals(furnace, blast);
        assertNotEquals(furnace, smoker);
        assertNotEquals(blast, smoker);

        assertEquals(0f, FurnaceLabels.yawFor(BlockFace.SOUTH));
        assertEquals(90f, FurnaceLabels.yawFor(BlockFace.WEST));
        assertEquals(180f, FurnaceLabels.yawFor(BlockFace.NORTH));
        assertEquals(-90f, FurnaceLabels.yawFor(BlockFace.EAST));
    }

    @Test
    void formatsCountOnlyWhenAmountExceedsOneAndBuildsProgressBar() {
        assertNull(FurnaceLabels.countText(null));
        assertNull(FurnaceLabels.countText(item(1)));
        assertEquals(Component.text("×8", NamedTextColor.WHITE), FurnaceLabels.countText(item(8)));

        Furnace furnace = mock(Furnace.class);
        when(furnace.getCookTime()).thenReturn((short) 0);
        when(furnace.getCookTimeTotal()).thenReturn(200);
        assertNull(FurnaceLabels.progressBar(furnace));

        when(furnace.getCookTime()).thenReturn((short) 120);
        assertEquals(Component.empty()
                        .append(Component.text("███", NamedTextColor.GOLD))
                        .append(Component.text("██", NamedTextColor.DARK_GRAY)),
                FurnaceLabels.progressBar(furnace));
    }

    @Test
    void spawnsInputOutputAndFuelPreviewsWithCountsNextToThem() {
        PacketHolograms.Sender sender = mock(PacketHolograms.Sender.class);
        when(sender.nextEntityId()).thenReturn(50, 51, 52, 53, 54, 55);
        FurnaceLabels labels = new FurnaceLabels(sender);

        World world = mock(World.class);
        UUID worldId = UUID.randomUUID();
        when(world.getUID()).thenReturn(worldId);
        Location blockLoc = new Location(world, 10, 64, 20);
        when(world.isChunkLoaded(0, 1)).thenReturn(true);
        long chunkKey = Chunk.getChunkKey(0, 1);

        Block block = mock(Block.class);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        when(world.getBlockAt(10, 64, 20)).thenReturn(block);

        Furnace furnace = mock(Furnace.class);
        org.bukkit.block.data.type.Furnace data = mock(org.bukkit.block.data.type.Furnace.class);
        when(data.getFacing()).thenReturn(BlockFace.SOUTH);
        when(furnace.getBlockData()).thenReturn(data);
        when(furnace.getType()).thenReturn(Material.BLAST_FURNACE);
        when(block.getState()).thenReturn(furnace);

        FurnaceInventory inv = mock(FurnaceInventory.class);
        ItemStack input = item(4);
        ItemStack output = item(2);
        ItemStack fuel = item(1); // Single fuel item -> preview without count hologram.
        when(furnace.getInventory()).thenReturn(inv);
        when(inv.getSmelting()).thenReturn(input);
        when(inv.getResult()).thenReturn(output);
        when(inv.getFuel()).thenReturn(fuel);
        when(furnace.getCookTime()).thenReturn((short) 40);
        when(furnace.getCookTimeTotal()).thenReturn(100);

        Player front = mock(Player.class), behind = mock(Player.class);
        UUID frontId = UUID.randomUUID(), behindId = UUID.randomUUID();
        when(front.getUniqueId()).thenReturn(frontId);
        when(behind.getUniqueId()).thenReturn(behindId);
        when(front.isChunkSent(chunkKey)).thenReturn(true);
        when(behind.isChunkSent(chunkKey)).thenReturn(true);
        when(front.getEyeLocation()).thenReturn(new Location(world, 10.5, 65.5, 23.0));
        when(behind.getEyeLocation()).thenReturn(new Location(world, 10.5, 65.5, 18.0));
        when(world.getPlayers()).thenReturn(List.of(front, behind));

        labels.track(blockLoc);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
            bukkit.when(() -> Bukkit.getPlayer(frontId)).thenReturn(front);
            labels.refresh();

            var layout = FurnaceLabels.layout(Material.BLAST_FURNACE);
            Location expectedInputItem = new Location(world, 10.5 + FurnaceLabels.INPUT_SIDE_OFFSET,
                    64.0 + layout.topY(), 20.5 + FurnaceLabels.FRONT_ITEM_OFFSET, 0f, 0f);
            Location expectedInputCount = new Location(world,
                    10.5 + (FurnaceLabels.INPUT_SIDE_OFFSET + FurnaceLabels.COUNT_SIDE_OFFSET),
                    64.0 + (layout.topY() + FurnaceLabels.COUNT_Y_OFFSET),
                    20.5 + FurnaceLabels.FRONT_TEXT_OFFSET, 0f, 0f);
            Location expectedOutputItem = new Location(world, 10.5 + FurnaceLabels.OUTPUT_SIDE_OFFSET,
                    64.0 + layout.topY(), 20.5 + FurnaceLabels.FRONT_ITEM_OFFSET, 0f, 0f);
            Location expectedOutputCount = new Location(world,
                    10.5 + (FurnaceLabels.OUTPUT_SIDE_OFFSET + FurnaceLabels.COUNT_SIDE_OFFSET),
                    64.0 + (layout.topY() + FurnaceLabels.COUNT_Y_OFFSET),
                    20.5 + FurnaceLabels.FRONT_TEXT_OFFSET, 0f, 0f);
            Location expectedFuelItem = new Location(world, 10.5 + FurnaceLabels.FUEL_SIDE_OFFSET,
                    64.0 + layout.bottomY(), 20.5 + FurnaceLabels.FRONT_ITEM_OFFSET, 0f, 0f);

            verify(sender).spawnItem(same(front), eq(50), any(UUID.class), eq(expectedInputItem),
                    same(input), eq(FurnaceLabels.ITEM_SCALE), eq(Display.Billboard.FIXED));
            verify(sender).spawnText(same(front), eq(51), any(UUID.class), eq(expectedInputCount),
                    eq(Component.text("×4", NamedTextColor.WHITE)), eq(FurnaceLabels.BACKGROUND_ARGB),
                    eq(0f), eq(FurnaceLabels.COUNT_SCALE), eq(Display.Billboard.FIXED));
            verify(sender).spawnItem(same(front), eq(52), any(UUID.class), eq(expectedOutputItem),
                    same(output), eq(FurnaceLabels.ITEM_SCALE), eq(Display.Billboard.FIXED));
            verify(sender).spawnText(same(front), eq(53), any(UUID.class), eq(expectedOutputCount),
                    eq(Component.text("×2", NamedTextColor.WHITE)), eq(FurnaceLabels.BACKGROUND_ARGB),
                    eq(0f), eq(FurnaceLabels.COUNT_SCALE), eq(Display.Billboard.FIXED));
            verify(sender).spawnItem(same(front), eq(54), any(UUID.class), eq(expectedFuelItem),
                    same(fuel), eq(FurnaceLabels.ITEM_SCALE), eq(Display.Billboard.FIXED));
            Location expectedProgress = new Location(world, 10.5, 64.0 + layout.topY(),
                    20.5 + FurnaceLabels.FRONT_TEXT_OFFSET, 0f, 0f);
            verify(sender).spawnText(same(front), eq(55), any(UUID.class), eq(expectedProgress),
                    eq(Component.empty()
                            .append(Component.text("██", NamedTextColor.GOLD))
                            .append(Component.text("███", NamedTextColor.DARK_GRAY))),
                    eq(FurnaceLabels.BACKGROUND_ARGB), eq(0f), eq(FurnaceLabels.PROGRESS_SCALE),
                    eq(Display.Billboard.FIXED));
            verify(sender, never()).spawnText(same(behind), anyInt(), any(), any(), any(), anyInt(), anyFloat(), anyFloat(), any());
            verify(sender, never()).spawnItem(same(behind), anyInt(), any(), any(), any(), anyFloat(), any());

            labels.disable();
        }
        verify(sender).destroy(same(front), eq(50), eq(51), eq(52), eq(53), eq(54), eq(55));
    }
}
