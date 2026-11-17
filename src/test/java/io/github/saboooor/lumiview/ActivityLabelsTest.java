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
import org.bukkit.Note;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class ActivityLabelsTest {
    @Test
    void formatsNoteBlockAndComposterLabels() {
        assertEquals(Component.text("♪ F# · Harp", NamedTextColor.LIGHT_PURPLE),
                ActivityLabels.noteText("PIANO", Note.sharp(0, Note.Tone.F)));
        assertEquals(Component.text("Compost 5/8", NamedTextColor.GOLD),
                ActivityLabels.composterText(5, 8));
        assertEquals(Component.text("Compost Ready ✓", NamedTextColor.GREEN),
                ActivityLabels.composterText(8, 8));
    }

    @Test
    void spawnsAndExpiresTransientHologramAfterDuration() {
        PacketHolograms.Sender sender = mock(PacketHolograms.Sender.class);
        when(sender.nextEntityId()).thenReturn(70);
        ActivityLabels labels = new ActivityLabels(null, sender);

        World world = mock(World.class);
        Location key = new Location(world, 3, 64, 5);
        Location displayLoc = key.clone().add(.5, 1.25, .5);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        long chunkKey = Chunk.getChunkKey(0, 0);

        Player viewer = mock(Player.class);
        UUID viewerId = UUID.randomUUID();
        when(viewer.getUniqueId()).thenReturn(viewerId);
        when(viewer.isChunkSent(chunkKey)).thenReturn(true);
        when(viewer.getEyeLocation()).thenReturn(new Location(world, 3.5, 65.5, 7.0));
        when(world.getPlayers()).thenReturn(List.of(viewer));

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(viewerId)).thenReturn(viewer);

            labels.showText(key, displayLoc, Component.text("♪ C · Harp", NamedTextColor.LIGHT_PURPLE), 4);
            verify(sender).spawnText(same(viewer), eq(70), any(UUID.class), eq(displayLoc),
                    eq(Component.text("♪ C · Harp", NamedTextColor.LIGHT_PURPLE)),
                    eq(ActivityLabels.BACKGROUND_ARGB), eq(0f), eq(ActivityLabels.TEXT_SCALE),
                    eq(Display.Billboard.CENTER));

            labels.refresh(); // tick 2 < 4: still active
            verify(sender, never()).destroy(any(), anyInt());

            labels.refresh(); // tick 4 >= 4: expired
            verify(sender).destroy(same(viewer), eq(70));
        }
    }
}
