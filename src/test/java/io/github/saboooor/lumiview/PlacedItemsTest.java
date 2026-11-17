package io.github.saboooor.lumiview;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.WorldBorder;
import org.bukkit.Location;
import org.bukkit.plugin.PluginManager;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlacedItemsTest {
    @TempDir Path folder;

    private ItemStack item(Material material) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenReturn(1);
        return item;
    }

    private PlacedItems potions() {
        LumiViewPlugin plugin = mock(LumiViewPlugin.class);
        when(plugin.namespace()).thenReturn("lumiview");
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        return new PlacedItems(plugin);
    }

    @Test
    void onlyBottledItemsWithPositiveCountsArePlaceable() {
        assertTrue(PlacedItems.isBottle(item(Material.POTION)));
        assertTrue(PlacedItems.isBottle(item(Material.SPLASH_POTION)));
        assertTrue(PlacedItems.isBottle(item(Material.LINGERING_POTION)));
        assertTrue(PlacedItems.isBottle(item(Material.GLASS_BOTTLE)));
        assertTrue(PlacedItems.isBottle(item(Material.DRAGON_BREATH)));
        assertTrue(PlacedItems.isBottle(item(Material.HONEY_BOTTLE)));
        assertTrue(PlacedItems.isBottle(item(Material.EXPERIENCE_BOTTLE)));
        assertTrue(PlacedItems.isBottle(item(Material.OMINOUS_BOTTLE)));
        assertFalse(PlacedItems.isBottle(item(Material.MILK_BUCKET)));
        assertFalse(PlacedItems.isBottle(item(Material.DIAMOND)));
        assertFalse(PlacedItems.isBottle(null));
        ItemStack empty = item(Material.POTION);
        when(empty.getAmount()).thenReturn(0);
        assertFalse(PlacedItems.isBottle(empty));
    }

    @Test
    void bottlePlacementRequiresSneakingAndAllowsEitherHand() {
        Player player = mock(Player.class);
        Block block = mock(Block.class);
        for (EquipmentSlot hand : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
            PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                    item(Material.POTION), block, BlockFace.UP, hand);
            when(player.isSneaking()).thenReturn(false);
            assertFalse(PlacedItems.isPlacement(event));
            when(player.isSneaking()).thenReturn(true);
            assertTrue(PlacedItems.isPlacement(event));
        }
    }

    @Test
    void deniedPlacementNeverConsumesOrThrowsPotion() {
        Player player = mock(Player.class);
        when(player.isSneaking()).thenReturn(true);
        ItemStack item = item(Material.SPLASH_POTION);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                item, mock(Block.class), BlockFace.UP, EquipmentSlot.HAND);
        event.setUseInteractedBlock(Event.Result.DENY);
        potions().place(event);
        assertEquals(Event.Result.DENY, event.useItemInHand());
        verify(item, never()).setAmount(anyInt());
        verify(player, never()).getInventory();
        assertFalse(folder.resolve("potions.yml").toFile().exists());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = Material.class, names = {
            "DRAGON_BREATH", "HONEY_BOTTLE", "EXPERIENCE_BOTTLE", "OMINOUS_BOTTLE", "GLASS_BOTTLE"})
    void stackedBottlesUsePlacementGestureAndCannotBeConsumedWhenDenied(Material type) {
        for (EquipmentSlot hand : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
            Player player = mock(Player.class);
            when(player.isSneaking()).thenReturn(true);
            ItemStack bottle = item(type);
            when(bottle.getAmount()).thenReturn(16);
            PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                    bottle, mock(Block.class), BlockFace.UP, hand);
            assertTrue(PlacedItems.isPlacement(event));
            event.setUseItemInHand(Event.Result.DENY);
            potions().place(event);
            assertEquals(Event.Result.DENY, event.useInteractedBlock());
            assertEquals(Event.Result.DENY, event.useItemInHand());
            verify(bottle, never()).setAmount(anyInt());
            verify(player, never()).getInventory();
        }
    }

    @Test
    void adventureModeCannotPlaceButGestureStillSuppressesDrinking() {
        Player player = mock(Player.class);
        when(player.isSneaking()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.ADVENTURE);
        ItemStack item = item(Material.POTION);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                item, mock(Block.class), BlockFace.UP, EquipmentSlot.HAND);
        potions().place(event);
        assertEquals(Event.Result.DENY, event.useItemInHand());
        verify(item, never()).setAmount(anyInt());
    }

    @Test
    void blockProtectionCanDenyVirtualPlacementBeforeConsumption() {
        Player player = mock(Player.class);
        when(player.isSneaking()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        ItemStack item = item(Material.POTION);
        Block clicked = mock(Block.class), target = mock(Block.class);
        BlockData supportData = mock(BlockData.class);
        when(supportData.isFaceSturdy(BlockFace.UP, BlockSupport.FULL)).thenReturn(true);
        when(clicked.getBlockData()).thenReturn(supportData);
        when(clicked.getRelative(BlockFace.UP)).thenReturn(target);
        when(target.getRelative(BlockFace.DOWN)).thenReturn(clicked);
        when(target.isEmpty()).thenReturn(true);
        when(target.getY()).thenReturn(70);
        World world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(target.getWorld()).thenReturn(world);
        when(target.getLocation()).thenReturn(new Location(world, 0, 70, 0));
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(world.getMaxHeight()).thenReturn(320);
        WorldBorder border = mock(WorldBorder.class);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(any())).thenReturn(true);
        PluginManager manager = mock(PluginManager.class);
        doAnswer(call -> {
            ((BlockPlaceEvent) call.getArgument(0)).setCancelled(true);
            return null;
        }).when(manager).callEvent(any(BlockPlaceEvent.class));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                    item, clicked, BlockFace.UP, EquipmentSlot.HAND);
            potions().place(event);
            verify(manager).callEvent(any(BlockPlaceEvent.class));
            verify(item, never()).setAmount(anyInt());
            verify(player, never()).getInventory();
            assertFalse(folder.resolve("potions.yml").toFile().exists());
        }
    }

    @Test
    void thickerPilesRequireAirInEveryOccupiedBlock() {
        Block base = mock(Block.class), above = mock(Block.class);
        World world = mock(World.class);
        when(base.getWorld()).thenReturn(world);
        when(base.getY()).thenReturn(70);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(base.isEmpty()).thenReturn(true);
        when(base.getRelative(0, 1, 0)).thenReturn(above);
        assertTrue(PlacedItems.clearSpace(base, 4 * BookModel.THICKNESS));
        assertFalse(PlacedItems.clearSpace(base, 1.125f));
        when(above.isEmpty()).thenReturn(true);
        assertTrue(PlacedItems.clearSpace(base, 1.5f));
        when(base.isEmpty()).thenReturn(false);
        assertFalse(PlacedItems.clearSpace(base, BookModel.THICKNESS));
    }

    @Test
    void thickerPilesCannotExtendAboveWorldBuildHeight() {
        Block base = mock(Block.class);
        World world = mock(World.class);
        when(base.getWorld()).thenReturn(world);
        when(base.getY()).thenReturn(319);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(base.isEmpty()).thenReturn(true);
        assertTrue(PlacedItems.clearSpace(base, BookModel.THICKNESS));
        assertFalse(PlacedItems.clearSpace(base, 1.5f));
        verify(base, never()).getRelative(anyInt(), anyInt(), anyInt());
    }

    @Test
    void requiresFullTopSupport() {
        Block target = mock(Block.class), support = mock(Block.class);
        BlockData data = mock(BlockData.class);
        when(target.getRelative(BlockFace.DOWN)).thenReturn(support);
        when(support.getBlockData()).thenReturn(data);
        assertFalse(PlacedItems.supported(target));
        when(data.isFaceSturdy(BlockFace.UP, BlockSupport.FULL)).thenReturn(true);
        assertTrue(PlacedItems.supported(target));
    }

    @Test
    void positionKeepsWorldIdentityAndNegativeCoordinates() {
        Block block = mock(Block.class);
        World world = mock(World.class);
        UUID id = UUID.randomUUID();
        when(block.getWorld()).thenReturn(world);
        when(world.getUID()).thenReturn(id);
        when(block.getX()).thenReturn(-17);
        when(block.getY()).thenReturn(70);
        when(block.getZ()).thenReturn(31);
        assertEquals(new PlacedItems.Position(id, -17, 70, 31), PlacedItems.Position.at(block));
    }
}
