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
import org.bukkit.entity.Display;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class DroppedItemLabelsTest {
    @Test
    void formatsSingleAndStackedItemNames() {
        ItemStack single = mock(ItemStack.class);
        when(single.effectiveName()).thenReturn(Component.text("Diamond"));
        when(single.getAmount()).thenReturn(1);
        assertEquals(Component.text("Diamond"), DroppedItemLabels.text(single));

        ItemStack stack = mock(ItemStack.class);
        when(stack.effectiveName()).thenReturn(Component.text("Diamond"));
        when(stack.getAmount()).thenReturn(5);
        assertEquals(Component.text("Diamond").append(Component.text(" × 5", NamedTextColor.GRAY)),
                DroppedItemLabels.text(stack));
    }

    @Test
    void formatsDespawnTimerAndOmitsUnlimitedLifetimeItems() {
        Item item = mock(Item.class);
        when(item.willAge()).thenReturn(true);
        when(item.isUnlimitedLifetime()).thenReturn(false);
        when(item.getTicksLived()).thenReturn(0);
        assertEquals(Component.text("5:00", NamedTextColor.GRAY), DroppedItemLabels.timerText(item));

        when(item.getTicksLived()).thenReturn(20 * 65);
        assertEquals(Component.text("3:55", NamedTextColor.GRAY), DroppedItemLabels.timerText(item));

        when(item.isUnlimitedLifetime()).thenReturn(true);
        assertNull(DroppedItemLabels.timerText(item));
    }

    @Test
    void spawnsMountsUpdatesAndDestroysMainAndTimerHolograms() {
        PacketHolograms.Sender sender = mock(PacketHolograms.Sender.class);
        when(sender.nextEntityId()).thenReturn(99, 100);
        DroppedItemLabels labels = new DroppedItemLabels(sender);

        World world = mock(World.class);
        Location itemLocation = new Location(world, 8.5, 64.0, 8.5);
        long chunkKey = Chunk.getChunkKey(0, 0);

        Item item = mock(Item.class);
        UUID itemId = UUID.randomUUID();
        when(item.getUniqueId()).thenReturn(itemId);
        when(item.getEntityId()).thenReturn(777);
        when(item.isValid()).thenReturn(true);
        when(item.willAge()).thenReturn(true);
        when(item.isUnlimitedLifetime()).thenReturn(false);
        when(item.getTicksLived()).thenReturn(20);
        when(item.getLocation()).thenReturn(itemLocation);

        ItemStack stack1 = mock(ItemStack.class), snapshot1 = mock(ItemStack.class);
        when(stack1.effectiveName()).thenReturn(Component.text("Apple"));
        when(stack1.getAmount()).thenReturn(1);
        when(stack1.clone()).thenReturn(snapshot1);
        when(snapshot1.getAmount()).thenReturn(1);
        when(snapshot1.isSimilar(stack1)).thenReturn(true);
        when(item.getItemStack()).thenReturn(stack1);

        Player near = mock(Player.class), far = mock(Player.class);
        UUID nearId = UUID.randomUUID(), farId = UUID.randomUUID();
        when(near.getUniqueId()).thenReturn(nearId);
        when(far.getUniqueId()).thenReturn(farId);
        when(near.canSee(item)).thenReturn(true);
        when(far.canSee(item)).thenReturn(true);
        when(near.isChunkSent(chunkKey)).thenReturn(true);
        when(far.isChunkSent(chunkKey)).thenReturn(true);
        when(near.getLocation()).thenReturn(new Location(world, 10.0, 64.0, 8.5));
        when(far.getLocation()).thenReturn(new Location(world, 40.0, 64.0, 8.5));
        when(world.getPlayers()).thenReturn(List.of(near, far));
        when(world.isChunkLoaded(0, 0)).thenReturn(true);

        labels.track(item);
        labels.refresh();

        verify(sender).spawnText(same(near), eq(99), any(UUID.class), eq(itemLocation),
                eq(Component.text("Apple")), eq(DroppedItemLabels.BACKGROUND_ARGB),
                eq(DroppedItemLabels.MAIN_Y_OFFSET), eq(DroppedItemLabels.MAIN_SCALE), eq(Display.Billboard.CENTER));
        verify(sender).spawnText(same(near), eq(100), any(UUID.class), eq(itemLocation),
                eq(Component.text("4:59", NamedTextColor.GRAY)), eq(DroppedItemLabels.BACKGROUND_ARGB),
                eq(DroppedItemLabels.TIMER_Y_OFFSET), eq(DroppedItemLabels.TIMER_SCALE), eq(Display.Billboard.CENTER));
        verify(sender).mount(same(near), eq(777), eq(99), eq(100));
        verify(sender, never()).spawnText(same(far), anyInt(), any(), any(), any(), anyInt(), anyFloat(), anyFloat(), any());

        // Advance timer and stack amount.
        when(item.getTicksLived()).thenReturn(40);
        ItemStack stack2 = mock(ItemStack.class), snapshot2 = mock(ItemStack.class);
        when(stack2.effectiveName()).thenReturn(Component.text("Apple"));
        when(stack2.getAmount()).thenReturn(3);
        when(stack2.clone()).thenReturn(snapshot2);
        when(snapshot1.isSimilar(stack2)).thenReturn(true);
        when(item.getItemStack()).thenReturn(stack2);

        labels.refresh();
        verify(sender, never()).teleport(any(), anyInt(), any());
        verify(sender).updateText(same(near), eq(99),
                eq(Component.text("Apple").append(Component.text(" × 3", NamedTextColor.GRAY))));
        verify(sender).updateText(same(near), eq(100),
                eq(Component.text("4:58", NamedTextColor.GRAY)));

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(nearId)).thenReturn(near);
            labels.disable();
        }
        verify(sender).destroy(same(near), eq(99), eq(100));
    }
}
