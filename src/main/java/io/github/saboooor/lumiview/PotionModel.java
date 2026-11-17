package io.github.saboooor.lumiview;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;

/** Small block-display bottle, using the same translated/scaled parts as Lumistones. */
final class PotionModel {
    private static final float HORIZONTAL_SCALE = .72f;
    static final float WIDTH = .4375f * HORIZONTAL_SCALE;
    private static final float VERTICAL_SCALE = .8f;
    static final float HEIGHT = .625f * VERTICAL_SCALE;

    // Translations are relative to the center of the supporting block's top face.
    record Part(Material material, float x, float y, float z, float width, float height, float depth) {}

    record Offset(float x, float z) {}

    static Offset offset(int count, int index) {
        if (count < 1 || count > PlacedStack.MAX_BOTTLES || index < 0 || index >= count) {
            throw new IllegalArgumentException("Invalid bottle position");
        }
        if (count == 1) return new Offset(0, 0);
        // Stable, staggered clusters rather than rows; model, hitbox, and label share these offsets.
        return switch (count) {
            case 2 -> index == 0 ? new Offset(-.15625f, -.109375f) : new Offset(.1875f, .125f);
            case 3 -> switch (index) {
                case 0 -> new Offset(-.203125f, -.171875f);
                case 1 -> new Offset(.1875f, -.09375f);
                default -> new Offset(-.03125f, .234375f);
            };
            default -> switch (index) {
                case 0 -> new Offset(-.21875f, -.15625f);
                case 1 -> new Offset(.140625f, -.234375f);
                case 2 -> new Offset(-.125f, .203125f);
                default -> new Offset(.234375f, .109375f);
            };
        };
    }

    private PotionModel() {}

    static Material liquid(ItemStack item) {
        switch (item.getType()) {
            case GLASS_BOTTLE: return Material.AIR;
            case DRAGON_BREATH: return Material.MAGENTA_STAINED_GLASS;
            case HONEY_BOTTLE: return Material.ORANGE_STAINED_GLASS;
            case EXPERIENCE_BOTTLE: return Material.LIME_STAINED_GLASS;
            case OMINOUS_BOTTLE: return Material.BLACK_STAINED_GLASS;
            default: break;
        }
        Color color = item.getItemMeta() instanceof PotionMeta meta ? meta.computeEffectiveColor() : null;
        return closestLiquid(color == null ? Color.fromRGB(0x385DC6) : color);
    }

    static Material closestLiquid(Color color) {
        DyeColor closest = DyeColor.BLUE;
        long best = Long.MAX_VALUE;
        for (DyeColor dye : DyeColor.values()) {
            Color candidate = dye.getColor();
            long r = color.getRed() - candidate.getRed();
            long g = color.getGreen() - candidate.getGreen();
            long b = color.getBlue() - candidate.getBlue();
            long distance = r * r + g * g + b * b;
            if (distance < best) {
                best = distance;
                closest = dye;
            }
        }
        return Material.valueOf(closest.name() + "_STAINED_GLASS");
    }

    static List<Part> parts(Material potion, Material liquid) {
        boolean empty = potion == Material.GLASS_BOTTLE;
        boolean dragon = potion == Material.DRAGON_BREATH;
        boolean honey = potion == Material.HONEY_BOTTLE;
        boolean lingering = potion == Material.LINGERING_POTION;
        boolean ominous = potion == Material.OMINOUS_BOTTLE;
        boolean experience = potion == Material.EXPERIENCE_BOTTLE;
        boolean splash = potion == Material.SPLASH_POTION;
        float belly = honey || dragon || lingering ? .40625f : experience || ominous ? .34375f : .375f;
        float neckOffset = splash ? .0625f : 0;
        Material glass = ominous ? Material.GRAY_STAINED_GLASS : Material.GLASS;
        Material cork = dragon || ominous ? Material.STRIPPED_DARK_OAK_WOOD : Material.STRIPPED_OAK_WOOD;
        List<Part> parts = new ArrayList<>();
        // A single glass cuboid per layer forms the stepped bottle silhouette.
        bodyLayer(parts, Material.GLASS, 0, 0, .21875f, .03125f);
        bodyLayer(parts, glass, 0, .03125f, belly - .0625f, .060546875f);
        bodyLayer(parts, glass, 0, .091796875f, belly, .142578125f);
        bodyLayer(parts, glass, 0, .234375f, belly - .0625f, .0625f);
        bodyLayer(parts, glass, neckOffset / 2, .296875f, .21875f, .046875f);
        // Hollow neck and lip keep empty bottles visibly open rather than capped by a glass cube.
        ring(parts, glass, neckOffset, .34375f, .125f, .09375f, .15625f);
        ring(parts, glass, neckOffset, .5f, .171875f, .125f, .0625f);
        if (!empty) parts.add(centered(cork, neckOffset, .53125f, .125f, .09375f));
        int liquidStart = parts.size();
        if (liquid != Material.AIR) {
            // Separate liquid tiers follow the glass contours without a flat, full-width slab.
            parts.add(centered(liquid, 0, .0234375f, .15625f, .0390625f));
            parts.add(centered(liquid, 0, .0625f, belly - .109375f, .03125f));
            parts.add(centered(liquid, 0, .09375f, belly - .0625f, .125f));
        }
        if (liquid.name().endsWith("_STAINED_GLASS")) {
            // A small, matching core gives the liquid more body without losing translucent edges.
            Material core = Material.valueOf(liquid.name().replace("_STAINED_GLASS", "_CONCRETE"));
            parts.add(centered(core, 0, .109375f, (belly - .0625f) * .55f, .09375f));
        }
        List<Part> separated = new ArrayList<>();
        for (int index = 0; index < parts.size(); index++) {
            Part part = parts.get(index);
            // Transparent block displays render their own faces even where panels touch.
            // Separate every panel; inset the liquid farther to avoid matching internal glass planes.
            float inset = index >= liquidStart ? .001953125f : .0009765625f;
            float bottom = part.y() == 0 ? 0 : inset;
            float top = part.y() + part.height() == .625f ? 0 : inset;
            separated.add(new Part(part.material(),
                    (part.x() + inset) * HORIZONTAL_SCALE, (part.y() + bottom) * VERTICAL_SCALE,
                    (part.z() + inset) * HORIZONTAL_SCALE,
                    (part.width() - 2 * inset) * HORIZONTAL_SCALE,
                    (part.height() - bottom - top) * VERTICAL_SCALE,
                    (part.depth() - 2 * inset) * HORIZONTAL_SCALE));
        }
        return List.copyOf(separated);
    }

    private static void bodyLayer(List<Part> parts, Material material, float centerX, float y, float width, float height) {
        parts.add(centered(material, centerX, y, width, height));
    }

    private static void ring(List<Part> parts, Material material, float centerX, float y,
                             float outer, float inner, float height) {
        float wall = (outer - inner) / 2;
        parts.add(new Part(material, centerX - outer / 2, y, -outer / 2, outer, height, wall));
        parts.add(new Part(material, centerX - outer / 2, y, inner / 2, outer, height, wall));
        parts.add(new Part(material, centerX - outer / 2, y, -inner / 2, wall, height, inner));
        parts.add(new Part(material, centerX + inner / 2, y, -inner / 2, wall, height, inner));
    }

    private static Part centered(Material material, float centerX, float y, float width, float height) {
        return new Part(material, centerX - width / 2, y, -width / 2, width, height, width);
    }
}
