package io.github.saboooor.lumiview;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.stream.Stream;
import org.bukkit.World;
import org.bukkit.block.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.loot.LootTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ContainerShelvesTest {
    static Stream<Class<? extends Container>> containers() {
        return Stream.of(Barrel.class, Furnace.class, BlastFurnace.class, Smoker.class,
                Hopper.class, Dispenser.class, Dropper.class, BrewingStand.class,
                ShulkerBox.class, Crafter.class);
    }

    @ParameterizedTest
    @MethodSource("containers")
    void linksOtherContainersRegardlessOfTheirFacing(Class<? extends Container> type) {
        Shelf shelf = mock(Shelf.class);
        var data = mock(org.bukkit.block.data.type.Shelf.class);
        when(data.getFacing()).thenReturn(BlockFace.NORTH);
        when(shelf.getBlockData()).thenReturn(data);
        Block shelfBlock = mock(Block.class), behind = mock(Block.class);
        World world = mock(World.class);
        when(shelf.getBlock()).thenReturn(shelfBlock);
        when(shelfBlock.getRelative(BlockFace.SOUTH)).thenReturn(behind);
        when(behind.getWorld()).thenReturn(world);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        Container container = mock(type);
        when(behind.getState()).thenReturn(container);

        assertSame(container, LumiViewPlugin.containerBehind(shelf));

        when(world.isChunkLoaded(0, 0)).thenReturn(false);
        assertNull(LumiViewPlugin.containerBehind(shelf));
    }

    @Test
    void refusesShelfToShelfLinks() {
        Shelf shelf = mock(Shelf.class);
        var data = mock(org.bukkit.block.data.type.Shelf.class);
        when(data.getFacing()).thenReturn(BlockFace.NORTH);
        when(shelf.getBlockData()).thenReturn(data);
        Block block = mock(Block.class), behind = mock(Block.class);
        World world = mock(World.class);
        when(shelf.getBlock()).thenReturn(block);
        when(block.getRelative(BlockFace.SOUTH)).thenReturn(behind);
        when(behind.getWorld()).thenReturn(world);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(behind.getState()).thenReturn(mock(Shelf.class));
        assertNull(LumiViewPlugin.containerBehind(shelf));
    }

    @Test
    void refusesLockedContainersAndUnresolvedLootBeforeReadingInventory() {
        Barrel barrel = mock(Barrel.class);
        when(barrel.isLocked()).thenReturn(true);
        assertFalse(LumiViewPlugin.safeToPreview(barrel));
        verify(barrel, never()).getInventory();
        when(barrel.isLocked()).thenReturn(false);
        when(barrel.getLootTable()).thenReturn(mock(LootTable.class));
        assertFalse(LumiViewPlugin.safeToPreview(barrel));
        verify(barrel, never()).getInventory();
    }

    @Test
    void checksBothHalvesOfDoubleChest() {
        Chest chest = mock(Chest.class), other = mock(Chest.class);
        Inventory inventory = mock(Inventory.class);
        DoubleChest pair = mock(DoubleChest.class);
        when(chest.getInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(pair);
        when(pair.getLeftSide()).thenReturn(chest);
        when(pair.getRightSide()).thenReturn(other);
        assertTrue(LumiViewPlugin.safeToPreview(chest));
        when(other.isLocked()).thenReturn(true);
        assertFalse(LumiViewPlugin.safeToPreview(chest));
        when(other.isLocked()).thenReturn(false);
        when(other.getLootTable()).thenReturn(mock(LootTable.class));
        assertFalse(LumiViewPlugin.safeToPreview(chest));
    }
}
