package io.github.saboooor.lumiview;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

class PlacedStackTest {
    private ItemStack item(Material type, ItemMeta metadata, int count) {
        ItemStack item = mock(ItemStack.class);
        AtomicInteger amount = new AtomicInteger(count);
        when(item.getType()).thenReturn(type);
        when(item.getAmount()).thenAnswer(call -> amount.get());
        doAnswer(call -> { amount.set(call.getArgument(0)); return null; }).when(item).setAmount(anyInt());
        when(item.getItemMeta()).thenReturn(metadata);
        when(item.clone()).thenAnswer(call -> item(type, metadata, amount.get()));
        return item;
    }

    @Test
    void preservesDistinctBookMetadataAndRetrievesTopFirstWithoutChangingHeldStack() {
        ItemMeta firstMeta = mock(ItemMeta.class), secondMeta = mock(ItemMeta.class);
        ItemStack first = item(Material.WRITTEN_BOOK, firstMeta, 8);
        ItemStack second = item(Material.ENCHANTED_BOOK, secondMeta, 1);
        PlacedStack one = new PlacedStack(List.of(first));
        PlacedStack two = one.add(second);
        assertEquals(8, first.getAmount());
        assertEquals(1, one.top().getAmount());
        assertSame(secondMeta, two.top().getItemMeta());
        assertSame(firstMeta, two.withoutTop().top().getItemMeta());
        assertNull(one.withoutTop());
        assertEquals(1, one.items().size());
        assertEquals(2, two.items().size());
        first.setAmount(0);
        assertEquals(1, one.top().getAmount());
    }

    @Test
    void capsBookStacksAndTracksDoubleThicknessHeight() {
        ItemStack book = item(Material.BOOK, mock(ItemMeta.class), 64);
        PlacedStack stack = new PlacedStack(List.of(book));
        for (int count = 1; count < PlacedStack.MAX_BOOKS; count++) {
            assertTrue(stack.canAdd(book));
            stack = stack.add(book);
        }
        assertEquals(4, stack.items().size());
        assertFalse(stack.canAdd(book));
        PlacedStack full = stack;
        assertThrows(IllegalArgumentException.class, () -> full.add(book));
        assertEquals(.75f, stack.height());
        assertEquals(64, book.getAmount());
    }

    @Test
    void rejectsMixedBottlesBooksAndInvalidSavedStacks() {
        ItemStack book = item(Material.BOOK, mock(ItemMeta.class), 1);
        ItemStack bottle = item(Material.POTION, mock(ItemMeta.class), 1);
        assertThrows(IllegalArgumentException.class, () -> new PlacedStack(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new PlacedStack(List.of(book, bottle)));
        assertThrows(IllegalArgumentException.class, () -> new PlacedStack(List.of(bottle, book)));
        assertThrows(IllegalArgumentException.class, () -> new PlacedStack(java.util.Collections.nCopies(5, bottle)));
        assertThrows(IllegalArgumentException.class, () -> new PlacedStack(java.util.Collections.nCopies(9, book)));
        PlacedStack singleBottle = new PlacedStack(List.of(bottle));
        assertFalse(singleBottle.canAdd(book));
        assertTrue(singleBottle.canAdd(bottle));
        assertEquals(PotionModel.HEIGHT, singleBottle.height());
    }

    @Test
    void mixedBottleGroupsPreserveEachItemAndRemoveTheSelectedBottle() {
        ItemMeta potionMeta = mock(ItemMeta.class), breathMeta = mock(ItemMeta.class), honeyMeta = mock(ItemMeta.class);
        ItemStack potion = item(Material.POTION, potionMeta, 1);
        ItemStack breath = item(Material.DRAGON_BREATH, breathMeta, 16);
        ItemStack honey = item(Material.HONEY_BOTTLE, honeyMeta, 16);
        PlacedStack group = new PlacedStack(List.of(potion)).add(breath).add(honey);
        assertEquals(3, group.items().size());
        assertEquals(16, breath.getAmount());
        assertEquals(16, honey.getAmount());
        assertSame(breathMeta, group.items().get(1).getItemMeta());
        PlacedStack remaining = group.without(1);
        assertEquals(2, remaining.items().size());
        assertSame(potionMeta, remaining.items().getFirst().getItemMeta());
        assertSame(honeyMeta, remaining.items().getLast().getItemMeta());
        assertEquals(3, group.items().size());
        assertEquals(PotionModel.HEIGHT, group.height());
        assertNull(new PlacedStack(List.of(potion)).without(0));
        PlacedStack full = group.add(breath);
        assertEquals(4, full.items().size());
        assertFalse(full.canAdd(potion));
        assertFalse(full.canAdd(item(Material.BOOK, mock(ItemMeta.class), 1)));
        assertThrows(IllegalArgumentException.class, () -> full.add(potion));
    }

    @Test
    void preservesEveryBookWhenReducingLegacyEightBookPilesToFour() {
        List<ItemStack> books = new ArrayList<>();
        for (int i = 0; i < 8; i++) books.add(item(Material.WRITTEN_BOOK, mock(ItemMeta.class), 1));
        PlacedStack legacy = new PlacedStack(books);
        assertEquals(4, legacy.limited().items().size());
        assertEquals(4, legacy.overflow().size());
        for (int i = 0; i < 4; i++) {
            assertSame(books.get(i).getItemMeta(), legacy.limited().items().get(i).getItemMeta());
            assertSame(books.get(i + 4).getItemMeta(), legacy.overflow().get(i).getItemMeta());
        }
        assertFalse(legacy.canAdd(books.getFirst()));
        assertTrue(legacy.limited().overflow().isEmpty());
    }

    @Test
    void keepsPlacementOrientationWhenStackingRetrievingAndMigrating() {
        ItemStack book = item(Material.BOOK, mock(ItemMeta.class), 1);
        PlacedStack pile = new PlacedStack(List.of(book), -90);
        assertEquals(-90, pile.add(book).rotation());
        assertEquals(-90, pile.add(book).withoutTop().rotation());
        PlacedStack legacy = new PlacedStack(java.util.Collections.nCopies(8, book), 180);
        assertEquals(180, legacy.limited().rotation());
    }

    @Test
    void capturesIndependentOrderedListForPersistence() {
        ItemStack written = item(Material.WRITTEN_BOOK, mock(ItemMeta.class), 1);
        ItemStack enchanted = item(Material.ENCHANTED_BOOK, mock(ItemMeta.class), 1);
        List<ItemStack> saved = new ArrayList<>(List.of(written, enchanted));
        PlacedStack restored = new PlacedStack(saved);
        saved.clear();
        assertEquals(Material.WRITTEN_BOOK, restored.items().getFirst().getType());
        assertEquals(Material.ENCHANTED_BOOK, restored.top().getType());
        assertThrows(UnsupportedOperationException.class, () -> restored.items().clear());
    }

    @Test
    void groupsMixedFoodsUpToFourAndRejectsMixingWithBottlesOrBooks() {
        ItemStack apple = item(Material.APPLE, mock(ItemMeta.class), 1);
        ItemStack steak = item(Material.COOKED_BEEF, mock(ItemMeta.class), 1);
        ItemStack bread = item(Material.BREAD, mock(ItemMeta.class), 1);
        ItemStack cookie = item(Material.COOKIE, mock(ItemMeta.class), 1);
        ItemStack bottle = item(Material.POTION, mock(ItemMeta.class), 1);
        ItemStack book = item(Material.BOOK, mock(ItemMeta.class), 1);

        PlacedStack foods = new PlacedStack(List.of(apple), 180);
        assertTrue(foods.foods());
        assertFalse(foods.books());
        assertEquals(FoodModel.HEIGHT, foods.height());
        assertEquals(FoodModel.WIDTH, foods.width());
        assertFalse(foods.canAdd(bottle));
        assertFalse(foods.canAdd(book));
        assertThrows(IllegalArgumentException.class, () -> new PlacedStack(List.of(apple, bottle)));
        assertThrows(IllegalArgumentException.class, () -> new PlacedStack(List.of(apple, book)));

        PlacedStack full = foods.add(steak).add(bread).add(cookie);
        assertEquals(4, full.items().size());
        assertEquals(180, full.rotation());
        assertFalse(full.canAdd(apple));
        assertThrows(IllegalArgumentException.class, () -> full.add(apple));
        assertThrows(IllegalArgumentException.class, () -> new PlacedStack(java.util.Collections.nCopies(5, apple)));
    }
}
