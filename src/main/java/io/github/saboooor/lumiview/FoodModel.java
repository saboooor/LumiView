package io.github.saboooor.lumiview;

import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.joml.Matrix4f;

/** Flat item-display models for placeable foods. */
final class FoodModel {
    static final float WIDTH = .45f;
    static final float HEIGHT = .15f;
    private static final float SCALE = .45f;
    private static final float BASE_Y = .025f;

    private FoodModel() {}

    static boolean isFood(ItemStack item) {
        if (item == null || item.getAmount() <= 0 || PlacedItems.isBottle(item)) return false;
        Material type = item.getType();
        if (type != null && isFoodMaterial(type)) return true;
        return Bukkit.getServer() != null && item.hasData(DataComponentTypes.FOOD);
    }

    private static boolean isFoodMaterial(Material type) {
        return switch (type) {
            case APPLE, GOLDEN_APPLE, ENCHANTED_GOLDEN_APPLE, MELON_SLICE, SWEET_BERRIES, GLOW_BERRIES,
                    CHORUS_FRUIT, CARROT, GOLDEN_CARROT, POTATO, BAKED_POTATO, POISONOUS_POTATO,
                    BEETROOT, DRIED_KELP, BEEF, COOKED_BEEF, PORKCHOP, COOKED_PORKCHOP,
                    MUTTON, COOKED_MUTTON, CHICKEN, COOKED_CHICKEN, RABBIT, COOKED_RABBIT,
                    COD, COOKED_COD, SALMON, COOKED_SALMON, TROPICAL_FISH, PUFFERFISH,
                    ROTTEN_FLESH, SPIDER_EYE, BREAD, COOKIE, PUMPKIN_PIE,
                    MUSHROOM_STEW, BEETROOT_SOUP, RABBIT_STEW, SUSPICIOUS_STEW -> true;
            default -> false;
        };
    }

    static Matrix4f transform(float offsetX, float offsetZ, float facing, int layer) {
        float angle = (float) Math.toRadians(facing + switch (layer) {
            case 0 -> -12;
            case 1 -> 15;
            case 2 -> -8;
            default -> 10;
        });
        return new Matrix4f()
                .translation(offsetX, BASE_Y, offsetZ)
                .rotateY(angle)
                .rotateX((float) (Math.PI / 2))
                .scale(SCALE);
    }
}
