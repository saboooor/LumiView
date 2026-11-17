package io.github.saboooor.lumiview;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.junit.jupiter.api.Test;

class PotionModelTest {
    @Test
    void everyBottleFitsItsHitboxAndSitsOnTheSupport() {
        for (Material type : new Material[]{Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION,
                Material.DRAGON_BREATH, Material.HONEY_BOTTLE, Material.EXPERIENCE_BOTTLE, Material.OMINOUS_BOTTLE}) {
            var parts = PotionModel.parts(type, Material.RED_STAINED_GLASS);
            assertEquals(0f, parts.stream().map(PotionModel.Part::y).min(Float::compare).orElseThrow());
            assertEquals(PotionModel.HEIGHT, parts.stream().map(p -> p.y() + p.height()).max(Float::compare).orElseThrow());
            for (var part : parts) {
                assertTrue(part.width() > 0 && part.height() > 0 && part.depth() > 0);
                assertTrue(part.x() >= -PotionModel.WIDTH / 2 && part.x() + part.width() <= PotionModel.WIDTH / 2);
                assertTrue(part.z() >= -PotionModel.WIDTH / 2 && part.z() + part.depth() <= PotionModel.WIDTH / 2);
            }
            assertTrue(parts.size() <= 18, "Bottles should use the simplified model");
            assertTrue(parts.stream().allMatch(p -> p.material() == Material.GLASS
                    || p.material() == Material.GRAY_STAINED_GLASS
                    || p.material() == Material.RED_STAINED_GLASS
                    || p.material() == Material.RED_CONCRETE
                    || p.material() == Material.STRIPPED_OAK_WOOD
                    || p.material() == Material.STRIPPED_DARK_OAK_WOOD));
            assertTrue(parts.stream().anyMatch(p -> p.material() == Material.GLASS));
            assertTrue(parts.stream().anyMatch(p -> p.material() == Material.RED_STAINED_GLASS));
        }
    }

    @Test
    void variantsHaveDistinctGeometry() {
        var normal = PotionModel.parts(Material.POTION, Material.BLUE_STAINED_GLASS);
        var splash = PotionModel.parts(Material.SPLASH_POTION, Material.BLUE_STAINED_GLASS);
        var lingering = PotionModel.parts(Material.LINGERING_POTION, Material.BLUE_STAINED_GLASS);
        assertNotEquals(normal, splash);
        assertNotEquals(normal, lingering);
        assertNotEquals(splash, lingering);
    }

    @Test
    void usesEffectivePotionColorWithoutChangingSavedItem() {
        ItemStack item = mock(ItemStack.class);
        PotionMeta meta = mock(PotionMeta.class);
        when(item.getType()).thenReturn(Material.POTION);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.computeEffectiveColor()).thenReturn(DyeColor.MAGENTA.getColor());
        assertEquals(Material.MAGENTA_STAINED_GLASS, PotionModel.liquid(item));
        verify(item, never()).setItemMeta(any());
        verify(meta, never()).setColor(any());
    }

    @Test
    void otherBottleContentsHaveDistinctColorsAndEmptyGlassHasNoLiquidOrCork() {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.DRAGON_BREATH);
        assertEquals(Material.MAGENTA_STAINED_GLASS, PotionModel.liquid(item));
        when(item.getType()).thenReturn(Material.HONEY_BOTTLE);
        assertEquals(Material.ORANGE_STAINED_GLASS, PotionModel.liquid(item));
        when(item.getType()).thenReturn(Material.EXPERIENCE_BOTTLE);
        assertEquals(Material.LIME_STAINED_GLASS, PotionModel.liquid(item));
        when(item.getType()).thenReturn(Material.OMINOUS_BOTTLE);
        assertEquals(Material.BLACK_STAINED_GLASS, PotionModel.liquid(item));
        when(item.getType()).thenReturn(Material.GLASS_BOTTLE);
        assertEquals(Material.AIR, PotionModel.liquid(item));
        var parts = PotionModel.parts(Material.GLASS_BOTTLE, PotionModel.liquid(item));
        assertTrue(parts.stream().allMatch(part -> part.material() == Material.GLASS));
        assertTrue(parts.stream().allMatch(part -> part.y() + part.height() <= PotionModel.HEIGHT));
        verify(item, never()).getItemMeta();
    }

    @Test
    void candleStyleGroupsFitOneBlockAndHaveSeparateHitboxes() {
        for (int count = 1; count <= 4; count++) {
            for (int index = 0; index < count; index++) {
                var offset = PotionModel.offset(count, index);
                assertTrue(Math.abs(offset.x()) + PotionModel.WIDTH / 2 <= .5f);
                assertTrue(Math.abs(offset.z()) + PotionModel.WIDTH / 2 <= .5f);
                for (int other = index + 1; other < count; other++) {
                    var partner = PotionModel.offset(count, other);
                    assertNotEquals(offset.x(), partner.x(), "Bottles should not share a grid column");
                    assertNotEquals(offset.z(), partner.z(), "Bottles should not share a grid row");
                    assertTrue(Math.abs(offset.x() - partner.x()) >= PotionModel.WIDTH
                            || Math.abs(offset.z() - partner.z()) >= PotionModel.WIDTH);
                }
            }
        }
        assertEquals(new PotionModel.Offset(0, 0), PotionModel.offset(1, 0));
        assertThrows(IllegalArgumentException.class, () -> PotionModel.offset(5, 0));
        assertThrows(IllegalArgumentException.class, () -> PotionModel.offset(2, 2));
    }

    @Test
    void bottleFacesHaveNoCoplanarOverlapsOnAnyAxis() {
        for (Material type : new Material[]{Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION,
                Material.DRAGON_BREATH, Material.HONEY_BOTTLE, Material.EXPERIENCE_BOTTLE,
                Material.OMINOUS_BOTTLE, Material.GLASS_BOTTLE}) {
            var parts = PotionModel.parts(type, type == Material.GLASS_BOTTLE ? Material.AIR : Material.RED_STAINED_GLASS);
            for (int i = 0; i < parts.size(); i++) {
                var a = parts.get(i);
                for (int j = i + 1; j < parts.size(); j++) {
                    var b = parts.get(j);
                    float[] minA = {a.x(), a.y(), a.z()}, maxA = {a.x() + a.width(), a.y() + a.height(), a.z() + a.depth()};
                    float[] minB = {b.x(), b.y(), b.z()}, maxB = {b.x() + b.width(), b.y() + b.height(), b.z() + b.depth()};
                    for (int axis = 0; axis < 3; axis++) {
                        int u = (axis + 1) % 3, v = (axis + 2) % 3;
                        float overlapU = Math.min(maxA[u], maxB[u]) - Math.max(minA[u], minB[u]);
                        float overlapV = Math.min(maxA[v], maxB[v]) - Math.max(minA[v], minB[v]);
                        if (overlapU <= .000001f || overlapV <= .000001f) continue;
                        for (float planeA : new float[]{minA[axis], maxA[axis]}) {
                            for (float planeB : new float[]{minB[axis], maxB[axis]}) {
                                assertTrue(Math.abs(planeA - planeB) > .000001f,
                                        type + ": overlapping faces on axis " + axis + ": " + a + " and " + b);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void liquidKeepsTranslucentEdgesAroundASmallerSolidCore() {
        var parts = PotionModel.parts(Material.POTION, Material.BLUE_STAINED_GLASS);
        var core = parts.stream().filter(p -> p.material() == Material.BLUE_CONCRETE).findFirst().orElseThrow();
        assertTrue(parts.stream().filter(p -> p.material() == Material.BLUE_STAINED_GLASS).anyMatch(glass ->
                glass.x() < core.x() && glass.x() + glass.width() > core.x() + core.width()
                && glass.z() < core.z() && glass.z() + glass.depth() > core.z() + core.depth()
                && glass.y() < core.y() && glass.y() + glass.height() > core.y() + core.height()));
        assertFalse(PotionModel.parts(Material.GLASS_BOTTLE, Material.AIR).stream()
                .anyMatch(p -> p.material().name().endsWith("_CONCRETE")));
    }

    @Test
    void mapsEveryDyeColorToItsMatchingLiquidMaterial() {
        for (DyeColor dye : DyeColor.values()) {
            assertEquals(Material.valueOf(dye.name() + "_STAINED_GLASS"), PotionModel.closestLiquid(dye.getColor()));
        }
    }
}
