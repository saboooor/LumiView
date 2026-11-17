package io.github.saboooor.lumiview;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.ItemStack;

/** A group of bottles or foods, or an ordered pile of individually preserved books, bottom first. */
record PlacedStack(List<ItemStack> items, float rotation) {
    static final int MAX_BOOKS = 4;
    static final int MAX_BOTTLES = 4;
    static final int MAX_FOODS = 4;
    private static final int LEGACY_MAX_BOOKS = 8;

    PlacedStack(List<ItemStack> items) { this(items, 90); }

    PlacedStack {
        if (!Float.isFinite(rotation)) throw new IllegalArgumentException("Invalid placed stack rotation");
        if (items.isEmpty() || items.size() > LEGACY_MAX_BOOKS) throw new IllegalArgumentException("Invalid placed stack size");
        ItemStack first = items.getFirst();
        boolean books = BookModel.isBook(first);
        boolean foods = !books && FoodModel.isFood(first);
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack item : items) {
            boolean valid = books ? BookModel.isBook(item)
                    : foods ? items.size() <= MAX_FOODS && FoodModel.isFood(item)
                    : items.size() <= MAX_BOTTLES && PlacedItems.isBottle(item);
            if (!valid) throw new IllegalArgumentException("A placed stack must contain books, bottles, or foods");
            ItemStack copy = item.clone();
            copy.setAmount(1);
            copies.add(copy);
        }
        items = List.copyOf(copies);
    }

    ItemStack top() { return items.getLast(); }
    boolean books() { return BookModel.isBook(top()); }
    boolean foods() { return FoodModel.isFood(top()); }
    float height() { return books() ? items.size() * BookModel.THICKNESS : foods() ? FoodModel.HEIGHT : PotionModel.HEIGHT; }
    float width() { return books() ? BookModel.WIDTH : foods() ? FoodModel.WIDTH : PotionModel.WIDTH; }
    boolean canAdd(ItemStack item) {
        return books() ? BookModel.isBook(item) && items.size() < MAX_BOOKS
                : foods() ? FoodModel.isFood(item) && items.size() < MAX_FOODS
                : PlacedItems.isBottle(item) && items.size() < MAX_BOTTLES;
    }

    PlacedStack add(ItemStack item) {
        if (!canAdd(item)) throw new IllegalArgumentException("Cannot add to this placed stack");
        List<ItemStack> updated = new ArrayList<>(items);
        updated.add(item);
        return new PlacedStack(updated, rotation);
    }

    // Older releases allowed eight books. Keep those records readable until their chunks load.
    List<ItemStack> overflow() {
        return items.size() <= MAX_BOOKS ? List.of() : items.subList(MAX_BOOKS, items.size());
    }

    PlacedStack limited() {
        return items.size() <= MAX_BOOKS ? this : new PlacedStack(items.subList(0, MAX_BOOKS), rotation);
    }

    PlacedStack without(int index) {
        List<ItemStack> updated = new ArrayList<>(items);
        updated.remove(index);
        return updated.isEmpty() ? null : new PlacedStack(updated, rotation);
    }

    PlacedStack withoutTop() { return without(items.size() - 1); }
}
