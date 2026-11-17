package io.github.saboooor.lumiview;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class HoverLabelsTest {
    @Test
    void mapsLeftCenterAndRightSlotsForEveryFacing() {
        double[] coordinates = {.1, .5, .9};
        for (int slot = 0; slot < 3; slot++) {
            double coordinate = coordinates[slot];
            assertEquals(slot, HoverLabels.shelfSlot(BlockFace.SOUTH, new Vector(coordinate, .5, 1)));
            assertEquals(slot, HoverLabels.shelfSlot(BlockFace.NORTH, new Vector(1 - coordinate, .5, 0)));
            assertEquals(slot, HoverLabels.shelfSlot(BlockFace.EAST, new Vector(1, .5, 1 - coordinate)));
            assertEquals(slot, HoverLabels.shelfSlot(BlockFace.WEST, new Vector(0, .5, coordinate)));
        }
        assertEquals(2, HoverLabels.shelfSlot(BlockFace.SOUTH, new Vector(1, .5, 1)));
    }

    @Test
    void excludesTopBottomAndInvalidFaces() {
        assertEquals(-1, HoverLabels.shelfSlot(BlockFace.NORTH, new Vector(.5, .05, 0)));
        assertEquals(-1, HoverLabels.shelfSlot(BlockFace.NORTH, new Vector(.5, .95, 0)));
        assertEquals(-1, HoverLabels.shelfSlot(BlockFace.UP, new Vector(.5, .5, 0)));
    }

    @Test
    void blocksLabelsBehindWallsButToleratesSurfaceRounding() {
        assertTrue(HoverLabels.visible(2, 3));
        assertTrue(HoverLabels.visible(2.01, 2));
        assertFalse(HoverLabels.visible(3, 2));
    }

    @Test
    void preservesTooltipFormattingAndLimitsLongTooltips() {
        ItemStack item = mock(ItemStack.class);
        Player player = mock(Player.class);
        when(item.computeTooltipLines(any(), same(player))).thenReturn(List.of(
                Component.text("Named potion"), Component.text("Speed II")));
        assertEquals(Component.empty().append(Component.text("Named potion")).append(Component.newline())
                .append(Component.text("Speed II")), HoverLabels.text(item, player));
        when(item.computeTooltipLines(any(), same(player))).thenReturn(
                java.util.stream.IntStream.range(0, 20).<Component>mapToObj(i -> Component.text("Line " + i)).toList());
        Component expected = Component.empty();
        for (int i = 0; i < 6; i++) {
            if (i > 0) expected = expected.append(Component.newline());
            expected = expected.append(Component.text("Line " + i));
        }
        assertEquals(expected.append(Component.newline()).append(Component.text("…")), HoverLabels.text(item, player));
    }

    @Test
    void bookHoverShowsTopTooltipAndPileCount() {
        ItemStack item = mock(ItemStack.class);
        Player player = mock(Player.class);
        when(item.computeTooltipLines(any(), same(player))).thenReturn(List.of(Component.text("My journal")));
        Component expected = Component.empty().append(Component.text("My journal"))
                .append(Component.newline()).append(Component.text("3 books · top book",
                        net.kyori.adventure.text.format.NamedTextColor.GRAY));
        assertEquals(expected, HoverLabels.text(new HoverLabels.Target(null, item, 2, 3), player));
    }

    @Test
    void spawnsUpdatesAndDestroysClientSideHologramOnlyForViewer() {
        LumiViewPlugin plugin = mock(LumiViewPlugin.class);
        PacketHolograms.Sender sender = mock(PacketHolograms.Sender.class);
        when(sender.nextEntityId()).thenReturn(42);
        HoverLabels labels = new HoverLabels(plugin, mock(PlacedItems.class), sender);
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class), other = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        World world = mock(World.class);
        Location location = new Location(world, .5, 70.8, .5);
        Location movedLocation = new Location(world, 1.5, 70.8, .5);
        ItemStack item = mock(ItemStack.class), snapshot = mock(ItemStack.class);
        when(item.clone()).thenReturn(snapshot);
        when(item.computeTooltipLines(any(), same(player))).thenReturn(List.of(Component.text("Potion")));
        when(snapshot.isSimilar(item)).thenReturn(true);

        var target = new HoverLabels.Target(location, item, 2);
        labels.update(player, target);
        verify(sender).spawnText(same(player), eq(42), any(UUID.class), eq(location),
                eq(Component.empty().append(Component.text("Potion"))), eq(HoverLabels.BACKGROUND_ARGB),
                eq(0f), eq(.4f), eq(Display.Billboard.CENTER));
        verify(sender, never()).spawnText(same(other), anyInt(), any(), any(), any(), anyInt(), anyFloat(), anyFloat(), any());
        verifyNoInteractions(world);

        labels.update(player, target);
        verify(sender, times(1)).spawnText(any(), anyInt(), any(), any(), any(), anyInt(), anyFloat(), anyFloat(), any());
        verify(sender, never()).teleport(any(), anyInt(), any());

        labels.update(player, new HoverLabels.Target(movedLocation, item, 2));
        verify(sender).teleport(same(player), eq(42), eq(movedLocation));

        labels.update(player, null);
        verify(sender).destroy(same(player), eq(42));

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
            labels.disable();
        }
        verify(sender, times(1)).destroy(same(player), eq(42));
    }
}
