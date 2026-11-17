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
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class BrewingStandLabelsTest {
    private ItemStack item() {
        ItemStack stack = mock(ItemStack.class);
        when(stack.isEmpty()).thenReturn(false);
        when(stack.clone()).thenReturn(stack);
        return stack;
    }

    @Test
    void buildsAquaProgressBarFromRemainingBrewingTime() {
        BrewingStand stand = mock(BrewingStand.class);
        when(stand.getRecipeBrewTime()).thenReturn(400);
        when(stand.getBrewingTime()).thenReturn(0);
        assertNull(BrewingStandLabels.progressBar(stand));

        when(stand.getBrewingTime()).thenReturn(160); // 240/400 elapsed = 60% = 3 segments
        assertEquals(Component.empty()
                        .append(Component.text("███", NamedTextColor.AQUA))
                        .append(Component.text("██", NamedTextColor.DARK_GRAY)),
                BrewingStandLabels.progressBar(stand));
    }

    @Test
    void spawnsBottleIngredientFuelAndProgressAndCleansUpOnDisable() {
        PacketHolograms.Sender sender = mock(PacketHolograms.Sender.class);
        when(sender.nextEntityId()).thenReturn(10, 11, 12, 13);
        BrewingStandLabels labels = new BrewingStandLabels(sender);

        World world = mock(World.class);
        Location blockLoc = new Location(world, 4, 64, 8);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        long chunkKey = Chunk.getChunkKey(0, 0);

        Block block = mock(Block.class);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        when(world.getBlockAt(4, 64, 8)).thenReturn(block);

        BrewingStand stand = mock(BrewingStand.class);
        when(block.getState()).thenReturn(stand);
        when(stand.getRecipeBrewTime()).thenReturn(400);
        when(stand.getBrewingTime()).thenReturn(200);

        BrewerInventory inv = mock(BrewerInventory.class);
        ItemStack bottle0 = item(), ingredient = item(), fuel = item();
        when(stand.getInventory()).thenReturn(inv);
        when(inv.getItem(0)).thenReturn(bottle0);
        when(inv.getItem(3)).thenReturn(ingredient);
        when(inv.getItem(4)).thenReturn(fuel);

        Player near = mock(Player.class), far = mock(Player.class);
        UUID nearId = UUID.randomUUID(), farId = UUID.randomUUID();
        when(near.getUniqueId()).thenReturn(nearId);
        when(far.getUniqueId()).thenReturn(farId);
        when(near.isChunkSent(chunkKey)).thenReturn(true);
        when(far.isChunkSent(chunkKey)).thenReturn(true);
        when(near.getEyeLocation()).thenReturn(new Location(world, 4.5, 65.5, 10.0));
        when(far.getEyeLocation()).thenReturn(new Location(world, 40.0, 65.5, 10.0));
        when(world.getPlayers()).thenReturn(List.of(near, far));

        labels.track(blockLoc);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(nearId)).thenReturn(near);
            labels.refresh();

            verify(sender).spawnItem(same(near), eq(10), any(UUID.class),
                    eq(BrewingStandLabels.slotLocation(blockLoc, 0)), same(bottle0),
                    eq(BrewingStandLabels.BOTTLE_SCALE), eq(Display.Billboard.CENTER));
            verify(sender).spawnItem(same(near), eq(11), any(UUID.class),
                    eq(BrewingStandLabels.slotLocation(blockLoc, 3)), same(ingredient),
                    eq(BrewingStandLabels.INGREDIENT_SCALE), eq(Display.Billboard.CENTER));
            verify(sender).spawnItem(same(near), eq(12), any(UUID.class),
                    eq(BrewingStandLabels.slotLocation(blockLoc, 4)), same(fuel),
                    eq(BrewingStandLabels.FUEL_SCALE), eq(Display.Billboard.CENTER));
            verify(sender).spawnText(same(near), eq(13), any(UUID.class),
                    eq(blockLoc.clone().add(.5, 1.18, .5)), any(Component.class),
                    eq(BrewingStandLabels.BACKGROUND_ARGB), eq(0f), eq(BrewingStandLabels.PROGRESS_SCALE),
                    eq(Display.Billboard.CENTER));
            verify(sender, never()).spawnItem(same(far), anyInt(), any(), any(), any(), anyFloat(), any());

            labels.disable();
        }
        verify(sender).destroy(same(near), eq(10), eq(11), eq(12), eq(13));
    }
}
