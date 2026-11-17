package io.github.saboooor.lumiview;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BookModelTest {
    @ParameterizedTest
    @EnumSource(value = Material.class, names = {"BOOK", "ENCHANTED_BOOK", "WRITABLE_BOOK", "WRITTEN_BOOK", "KNOWLEDGE_BOOK"})
    void recognizesBookPlacementInEitherHandAndKeepsModelInsideHitbox(Material type) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(type);
        when(item.getAmount()).thenReturn(1);
        assertTrue(BookModel.isBook(item));
        Player player = mock(Player.class);
        for (boolean sneaking : new boolean[]{false, true}) {
            when(player.isSneaking()).thenReturn(sneaking);
            for (EquipmentSlot hand : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
                assertTrue(PlacedItems.isPlacement(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                        item, mock(Block.class), BlockFace.UP, hand)));
            }
        }
        assertFalse(PlacedItems.isPlacement(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
                item, null, BlockFace.SELF, EquipmentSlot.HAND)));
        for (var part : BookModel.parts(type)) {
            assertTrue(part.height() > 0 && part.width() > 0 && part.depth() > 0);
            assertTrue(part.y() >= 0 && part.y() + part.height() <= BookModel.THICKNESS);
            for (int angle : new int[]{-95, -85, -5, 5, 85, 95, 175, 185}) {
                Matrix4f rotation = new Matrix4f().rotateY((float) Math.toRadians(angle));
                for (float x : new float[]{part.x(), part.x() + part.width()}) {
                    for (float z : new float[]{part.z(), part.z() + part.depth()}) {
                        Vector3f point = rotation.transformPosition(new Vector3f(x, 0, z));
                        assertTrue(Math.abs(point.x) <= BookModel.WIDTH / 2);
                        assertTrue(Math.abs(point.z) <= BookModel.WIDTH / 2);
                    }
                }
            }
        }
        when(item.getAmount()).thenReturn(0);
        assertFalse(BookModel.isBook(item));
    }
    @org.junit.jupiter.api.Test
    void bindingSitsToPlayersLeftForEveryPlacementDirection() {
        for (BlockFace facing : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            Vector3f spine = new Matrix4f().rotateY((float) Math.toRadians(BookModel.rotationFor(facing)))
                    .transformDirection(new Vector3f(-1, 0, 0));
            assertEquals(facing.getModZ(), spine.x, .0001);
            assertEquals(-facing.getModX(), spine.z, .0001);
        }
    }

    @org.junit.jupiter.api.Test
    void spineWrapsTheBooksOwnLeftEdgeAndPagesOpenToTheRight() {
        var parts = BookModel.parts(Material.BOOK);
        var spine = parts.stream().filter(part -> part.material() == Material.GRAY_TERRACOTTA
                && part.height() == .125f).findFirst().orElseThrow();
        var pages = parts.stream().filter(part -> part.material() == Material.SMOOTH_QUARTZ)
                .findFirst().orElseThrow();
        assertTrue(spine.x() < 0 && spine.x() + spine.width() < 0);
        assertTrue(spine.depth() > spine.width() * 5);
        assertEquals(spine.x() + spine.width(), pages.x(), .0001f);
        assertTrue(pages.x() + pages.width() > 0);
        assertTrue(parts.stream().anyMatch(part -> part.material() == Material.GRAY_TERRACOTTA
                && part.y() >= .171875f && part.depth() > .6f && part.width() > .05f));
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {"BOOK", "ENCHANTED_BOOK", "WRITABLE_BOOK", "WRITTEN_BOOK", "KNOWLEDGE_BOOK"})
    void upperSurfacesDoNotOverlapOnTheSamePlane(Material type) {
        var parts = BookModel.parts(type);
        for (int i = 0; i < parts.size(); i++) {
            var first = parts.get(i);
            for (int j = i + 1; j < parts.size(); j++) {
                var second = parts.get(j);
                boolean samePlane = Math.abs(first.y() + first.height() - second.y() - second.height()) < .000001f;
                float overlapX = Math.min(first.x() + first.width(), second.x() + second.width())
                        - Math.max(first.x(), second.x());
                float overlapZ = Math.min(first.z() + first.depth(), second.z() + second.depth())
                        - Math.max(first.z(), second.z());
                assertFalse(samePlane && overlapX > .000001f && overlapZ > .000001f,
                        "Overlapping upper faces: " + first + " and " + second);
            }
        }
    }

}
