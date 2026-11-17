package io.github.saboooor.lumiview;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class FoodModelTest {
    private ItemStack item(Material type, int amount) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(type);
        when(stack.getAmount()).thenReturn(amount);
        return stack;
    }

    @Test
    void identifiesEdibleFoodsAndExcludesBottlesAndNonFoods() {
        assertTrue(FoodModel.isFood(item(Material.APPLE, 1)));
        assertTrue(FoodModel.isFood(item(Material.COOKED_BEEF, 16)));
        assertTrue(FoodModel.isFood(item(Material.MUSHROOM_STEW, 1)));
        assertFalse(FoodModel.isFood(item(Material.HONEY_BOTTLE, 1)));
        assertFalse(FoodModel.isFood(item(Material.POTION, 1)));
        assertFalse(FoodModel.isFood(item(Material.STONE, 1)));
        assertFalse(FoodModel.isFood(item(Material.APPLE, 0)));
        assertFalse(FoodModel.isFood(null));
    }

    @Test
    void laysFoodModelFlatAboveSupportingBlockAtClusterOffset() {
        Matrix4f matrix = FoodModel.transform(.125f, -.25f, 90f, 0);
        Vector3f translation = matrix.getTranslation(new Vector3f());
        assertEquals(.125f, translation.x, 1e-5f);
        assertEquals(.025f, translation.y, 1e-5f);
        assertEquals(-.25f, translation.z, 1e-5f);
        Vector3f scale = matrix.getScale(new Vector3f());
        assertEquals(.45f, scale.x, 1e-5f);
        assertEquals(.45f, scale.y, 1e-5f);
        assertEquals(.45f, scale.z, 1e-5f);
    }
}
