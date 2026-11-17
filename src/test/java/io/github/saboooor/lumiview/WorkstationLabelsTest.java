package io.github.saboooor.lumiview;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;
import org.junit.jupiter.api.Test;

class WorkstationLabelsTest {
    private ItemStack item() {
        ItemStack stack = mock(ItemStack.class);
        when(stack.isEmpty()).thenReturn(false);
        when(stack.clone()).thenReturn(stack);
        return stack;
    }

    @Test
    void definesLayoutsForAllSupportedWorkstationsAndAnvilCostText() {
        assertEquals(10, WorkstationLabels.layoutFor("WORKBENCH").size());
        assertEquals(9, WorkstationLabels.layoutFor("CRAFTER").size());
        assertEquals(3, WorkstationLabels.layoutFor("ANVIL").size());
        assertEquals(2, WorkstationLabels.layoutFor("ENCHANTING").size());
        assertEquals(4, WorkstationLabels.layoutFor("LOOM").size());
        assertEquals(2, WorkstationLabels.layoutFor("STONECUTTER").size());
        assertEquals(3, WorkstationLabels.layoutFor("GRINDSTONE").size());
        assertEquals(4, WorkstationLabels.layoutFor("SMITHING").size());
        assertEquals(3, WorkstationLabels.layoutFor("CARTOGRAPHY").size());
        assertEquals(3, WorkstationLabels.layoutFor("CHEST").size());
        assertEquals(3, WorkstationLabels.layoutFor("ENDER_CHEST").size());
        assertEquals(3, WorkstationLabels.layoutFor("BARREL").size());
        assertEquals(3, WorkstationLabels.layoutFor("SHULKER_BOX").size());
        assertEquals(1, WorkstationLabels.layoutFor("BEACON").size());
        assertTrue(WorkstationLabels.layoutFor("UNKNOWN").isEmpty());

        AnvilView cheap = mock(AnvilView.class);
        when(cheap.getRepairCost()).thenReturn(5);
        assertEquals(Component.text("5 levels", NamedTextColor.GREEN), WorkstationLabels.statusText(cheap));

        AnvilView expensive = mock(AnvilView.class);
        when(expensive.getRepairCost()).thenReturn(42);
        assertEquals(Component.text("42 levels", NamedTextColor.RED), WorkstationLabels.statusText(expensive));
    }

    @Test
    void spawnsFlatCraftingGridAndFloatingResultAndDestroysWhenClosed() {
        PacketHolograms.Sender sender = mock(PacketHolograms.Sender.class);
        when(sender.nextEntityId()).thenReturn(20, 21);
        WorkstationLabels labels = new WorkstationLabels(sender);

        World world = mock(World.class);
        Location tableLoc = new Location(world, 6, 64, 12);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        long chunkKey = Chunk.getChunkKey(0, 0);

        Player viewer = mock(Player.class);
        UUID viewerId = UUID.randomUUID();
        when(viewer.getUniqueId()).thenReturn(viewerId);
        when(viewer.isChunkSent(chunkKey)).thenReturn(true);
        when(viewer.getEyeLocation()).thenReturn(new Location(world, 6.5, 65.5, 14.0));
        when(world.getPlayers()).thenReturn(List.of(viewer));

        Inventory inv = mock(Inventory.class);
        when(inv.getSize()).thenReturn(10);
        ItemStack plank = item(), sticks = item();
        when(inv.getItem(1)).thenReturn(plank);
        when(inv.getItem(0)).thenReturn(sticks);

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(viewerId)).thenReturn(viewer);

            labels.refresh(Map.of(tableLoc, new WorkstationLabels.OpenStation(inv, "WORKBENCH", null)));
            Location expectedInputLoc = new Location(world, 6.5 + (-1 * .22), 64.0 + 1.02, 12.5 + (-1 * .22), 0f, -90f);
            Location expectedResultLoc = new Location(world, 6.5, 64.0 + 1.28, 12.5, 0f, 0f);
            verify(sender).spawnItem(same(viewer), eq(20), any(UUID.class), eq(expectedInputLoc),
                    same(plank), eq(.18f), eq(Display.Billboard.FIXED));
            verify(sender).spawnItem(same(viewer), eq(21), any(UUID.class), eq(expectedResultLoc),
                    same(sticks), eq(.26f), eq(Display.Billboard.CENTER));

            // Closing the crafting table destroys the active station displays.
            labels.refresh(Map.of());
            verify(sender).destroy(same(viewer), eq(20), eq(21));
        }
    }
}
