package io.github.saboooor.lumiview;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

/** Closed books with separate covers, pages, and a spine; each layer alternates its angle. */
final class BookModel {
    static final float WIDTH = .8f;
    static final float THICKNESS = .1875f;
    private BookModel() {}

    static boolean isBook(ItemStack item) {
        return item != null && item.getAmount() > 0 && switch (item.getType()) {
            case BOOK, ENCHANTED_BOOK, WRITABLE_BOOK, WRITTEN_BOOK, KNOWLEDGE_BOOK -> true;
            default -> false;
        };
    }

    static float rotationFor(BlockFace facing) {
        return switch (facing) {
            case EAST -> -90;
            case SOUTH -> 180;
            case WEST -> 90;
            default -> 0;
        };
    }

    static float rotation(int layer, float facing) {
        return (float) Math.toRadians(facing + (layer % 2 == 0 ? -5 : 5));
    }

    static List<PotionModel.Part> parts(Material type) {
        Material cover = switch (type) {
            case ENCHANTED_BOOK -> Material.PURPLE_WOOL;
            case WRITABLE_BOOK -> Material.LIGHT_GRAY_WOOL;
            case WRITTEN_BOOK -> Material.RED_WOOL;
            case KNOWLEDGE_BOOK -> Material.GREEN_WOOL;
            default -> Material.BROWN_WOOL;
        };
        Material panel = switch (type) {
            case ENCHANTED_BOOK -> Material.PURPLE_TERRACOTTA;
            case WRITTEN_BOOK -> Material.RED_TERRACOTTA;
            case KNOWLEDGE_BOOK -> Material.GREEN_TERRACOTTA;
            default -> Material.BROWN_TERRACOTTA;
        };
        Material accent = switch (type) {
            case ENCHANTED_BOOK, WRITTEN_BOOK -> Material.GOLD_BLOCK;
            case KNOWLEDGE_BOOK -> Material.OXIDIZED_COPPER;
            default -> Material.OAK_PLANKS;
        };
        List<PotionModel.Part> parts = new ArrayList<>(List.of(
                new PotionModel.Part(cover, -.3125f, 0, -.21875f, .625f, .0234375f, .4375f),
                new PotionModel.Part(Material.SMOOTH_QUARTZ, -.21875f, .0234375f, -.1875f, .5f, .125f, .375f),
                new PotionModel.Part(cover, -.3125f, .1484375f, -.21875f, .625f, .0234375f, .4375f),
                // The binding occupies the LEFT edge of the book's own cover coordinates.
                // It wraps onto the upper cover so it reads as a spine from above, too.
                new PotionModel.Part(Material.GRAY_TERRACOTTA, -.3125f, .0234375f, -.21875f, .09375f, .125f, .4375f),
                new PotionModel.Part(Material.GRAY_TERRACOTTA, -.3125f, .171875f, -.21875f, .09375f, .0078125f, .4375f),
                // A raised inset leaves a visible border around the cover.
                new PotionModel.Part(panel, -.1875f, .171875f, -.15625f, .40625f, .0078125f, .3125f),
                new PotionModel.Part(accent, -.0625f, .1796875f, -.015625f, .125f, .0078125f, .03125f),
                // Join the emblem arms at their edges instead of overlaying coplanar faces.
                new PotionModel.Part(accent, -.015625f, .1796875f, -.0625f, .03125f, .0078125f, .046875f),
                new PotionModel.Part(accent, -.015625f, .1796875f, .015625f, .03125f, .0078125f, .046875f),
                // Binding bands wrap only the left strip, including its visible upper surface.
                new PotionModel.Part(accent, -.31640625f, .02734375f, -.15625f, .1015625f, .1171875f, .03125f),
                new PotionModel.Part(accent, -.31640625f, .02734375f, .125f, .1015625f, .1171875f, .03125f),
                new PotionModel.Part(accent, -.31640625f, .1796875f, -.15625f, .1015625f, .0078125f, .03125f),
                new PotionModel.Part(accent, -.31640625f, .1796875f, .125f, .1015625f, .0078125f, .03125f),
                // Ribbon projects beyond the page edge while staying inside the pile hitbox.
                new PotionModel.Part(Material.RED_WOOL, .125f, .078125f, .15625f, .0390625f, .0078125f, .109375f)));
        // Contrasting tooling frames the inset, with metal corner protectors and a hinge seam.
        parts.add(new PotionModel.Part(accent, -.203125f, .171875f, -.1875f, .46875f, .00390625f, .015625f));
        parts.add(new PotionModel.Part(accent, -.203125f, .171875f, .171875f, .46875f, .00390625f, .015625f));
        parts.add(new PotionModel.Part(accent, -.203125f, .171875f, -.171875f, .015625f, .00390625f, .34375f));
        parts.add(new PotionModel.Part(accent, .25f, .171875f, -.171875f, .015625f, .00390625f, .34375f));
        for (float x : new float[]{-.296875f, .2578125f}) {
            for (float z : new float[]{-.203125f, .1640625f}) {
                // Left corners sit on the raised spine, right corners on the cover itself.
                float supportY = x < 0 ? .1796875f : .171875f;
                parts.add(new PotionModel.Part(accent, x, supportY, z, .0390625f,
                        THICKNESS - supportY, .0390625f));
            }
        }
        parts.add(new PotionModel.Part(Material.GRAY_TERRACOTTA,
                -.2265625f, .1796875f, -.21875f, .0078125f, .00390625f, .4375f));
        for (float y : new float[]{.0546875f, .0859375f, .1171875f}) {
            parts.add(new PotionModel.Part(Material.LIGHT_GRAY_CONCRETE,
                    .28125f, y, -.1796875f, .00390625f, .00390625f, .359375f));
            parts.add(new PotionModel.Part(Material.LIGHT_GRAY_CONCRETE,
                    -.2109375f, y, .1875f, .4921875f, .00390625f, .00390625f));
        }
        // Portrait proportions: the bound edge runs down the left side of the cover.
        return parts.stream().map(part -> new PotionModel.Part(part.material(),
                part.x() * .7f, part.y(), part.z() * (10f / 7f),
                part.width() * .7f, part.height(), part.depth() * (10f / 7f))).toList();
    }
}
