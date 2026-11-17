package io.github.saboooor.lumiview;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Matrix4f;

/** Saved items are authoritative; tagged display and hitbox entities are disposable models. */
final class PlacedItems implements Listener {
    record Position(UUID world, int x, int y, int z) {
        static Position at(Block block) {
            return new Position(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
        Location location() {
            var loadedWorld = Bukkit.getWorld(world);
            return loadedWorld == null ? null : new Location(loadedWorld, x, y, z);
        }
        boolean in(Chunk chunk) {
            return world.equals(chunk.getWorld().getUID()) && x >> 4 == chunk.getX() && z >> 4 == chunk.getZ();
        }
    }

    private final LumiViewPlugin plugin;
    private final File file;
    private final NamespacedKey modelKey;
    private final Map<Position, PlacedStack> placements = new HashMap<>();
    private final Map<Position, List<Entity>> models = new HashMap<>();
    private record Hit(Position position, int index) {}

    private final Map<UUID, Hit> hitboxes = new HashMap<>();

    PlacedItems(LumiViewPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "potions.yml");
        modelKey = new NamespacedKey(plugin, "potion_model");
    }

    void enable() {
        load();
        Bukkit.getWorlds().forEach(world -> {
            for (Chunk chunk : world.getLoadedChunks()) reconcile(chunk);
        });
        Bukkit.getScheduler().runTaskTimer(plugin, this::validate, 10, 10);
    }

    void disable() {
        for (Position position : List.copyOf(models.keySet())) removeModel(position);
    }

    static boolean isBottle(ItemStack item) {
        return item != null && item.getAmount() > 0 && switch (item.getType()) {
            case POTION, SPLASH_POTION, LINGERING_POTION, DRAGON_BREATH, HONEY_BOTTLE,
                    EXPERIENCE_BOTTLE, OMINOUS_BOTTLE, GLASS_BOTTLE -> true;
            default -> false;
        };
    }

    static boolean isPlacement(PlayerInteractEvent event) {
        return event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getHand() != null
                && (BookModel.isBook(event.getItem())
                || event.getPlayer().isSneaking() && (isBottle(event.getItem()) || FoodModel.isFood(event.getItem())));
    }

    static boolean supported(Block block) {
        Block below = block.getRelative(BlockFace.DOWN);
        return below.getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.FULL);
    }

    static boolean clearSpace(Block base, float height) {
        int blocks = (int) Math.ceil(height);
        if (base.getY() < base.getWorld().getMinHeight()
                || base.getY() + blocks > base.getWorld().getMaxHeight()) return false;
        for (int dy = 0; dy < blocks; dy++) {
            Block block = dy == 0 ? base : base.getRelative(0, dy, 0);
            if (!block.isEmpty()) return false;
        }
        return true;
    }

    private boolean fits(Position position, PlacedStack stack) {
        Location location = position.location();
        return stack.items().size() <= PlacedStack.MAX_BOOKS
                && loaded(location) && clearSpace(location.getBlock(), stack.height());
    }

    private boolean loaded(Location location) {
        return location != null && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void place(PlayerInteractEvent event) {
        if (!isPlacement(event) || event.getClickedBlock() == null) return;
        boolean denied = event.useInteractedBlock() == Event.Result.DENY || event.useItemInHand() == Event.Result.DENY;
        // Always suppress ordinary item use for a placement gesture, including failed placement.
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        Player player = event.getPlayer();
        if (denied || player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.ADVENTURE) return;
        Block target = event.getClickedBlock().getRelative(event.getBlockFace());
        if (!loaded(target.getLocation()) || target.getY() <= target.getWorld().getMinHeight()
                || target.getY() >= target.getWorld().getMaxHeight() || !target.isEmpty()
                || !target.getWorld().getWorldBorder().isInside(target.getLocation())
                || !supported(target)) return;
        PlacedStack existing = placements.get(Position.at(target));
        if (existing != null && !existing.canAdd(event.getItem())) return;

        // The model occupies air, so dispatch a virtual placement for block protection plugins.
        BlockPlaceEvent placement = new BlockPlaceEvent(target, target.getState(), event.getClickedBlock(),
                event.getItem(), player, true, event.getHand());
        Bukkit.getPluginManager().callEvent(placement);
        if (placement.isCancelled() || !placement.canBuild()) return;
        Position position = Position.at(target);
        ItemStack held = player.getInventory().getItem(event.getHand());
        if (!(isBottle(held) || BookModel.isBook(held) || FoodModel.isFood(held)) || !held.isSimilar(event.getItem())
                || !target.isEmpty() || !supported(target) || placements.get(position) != existing
                || existing != null && !existing.canAdd(held)) return;
        PlacedStack placed = existing == null ? new PlacedStack(List.of(held), BookModel.rotationFor(player.getFacing())) : existing.add(held);
        if (!fits(position, placed) || !store(position, placed)) return;
        if (player.getGameMode() != GameMode.CREATIVE) held.setAmount(held.getAmount() - 1);
        rebuild(position, placed);
        player.swingHand(event.getHand());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEntityEvent event) {
        Hit hit = hitboxes.get(event.getRightClicked().getUniqueId());
        if (hit == null) return;
        Position position = hit.position();
        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItem(event.getHand());
        if (addingGesture(player, held)) {
            addItem(player, position, event.getHand(), held);
        } else if (event.getHand() == EquipmentSlot.HAND) {
            // Allow the offhand placement gesture to run without first retrieving the pile.
            if (addingGesture(player, player.getInventory().getItemInOffHand())) return;
            retrieve(player, position, hit.index());
        }
    }

    private static boolean addingGesture(Player player, ItemStack item) {
        return BookModel.isBook(item) || player.isSneaking() && (isBottle(item) || FoodModel.isFood(item));
    }

    private void addItem(Player player, Position position, EquipmentSlot hand, ItemStack held) {
        PlacedStack previous = placements.get(position);
        Location location = position.location();
        if (previous == null || !previous.canAdd(held) || !loaded(location)
                || !location.getWorld().getWorldBorder().isInside(location)
                || !location.getBlock().isEmpty() || !supported(location.getBlock())
                || player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.SPECTATOR) return;
        Block target = location.getBlock();
        BlockPlaceEvent placement = new BlockPlaceEvent(target, target.getState(), target.getRelative(BlockFace.DOWN),
                held, player, true, hand);
        Bukkit.getPluginManager().callEvent(placement);
        ItemStack current = player.getInventory().getItem(hand);
        if (placement.isCancelled() || !placement.canBuild() || placements.get(position) != previous
                || !previous.canAdd(current) || !current.isSimilar(held)
                || !target.isEmpty() || !supported(target)) return;
        PlacedStack updated = previous.add(current);
        if (!fits(position, updated) || !store(position, updated)) return;
        if (player.getGameMode() != GameMode.CREATIVE) current.setAmount(current.getAmount() - 1);
        rebuild(position, updated);
        player.swingHand(hand);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void attack(PrePlayerAttackEntityEvent event) {
        Hit hit = hitboxes.get(event.getAttacked().getUniqueId());
        if (hit == null) return;
        event.setCancelled(true);
        retrieve(event.getPlayer(), hit.position(), hit.index());
    }

    record HoverTarget(Location location, ItemStack item, int books) {}

    boolean isHitbox(Entity entity) { return hitboxes.containsKey(entity.getUniqueId()); }

    HoverTarget hoverTarget(Entity entity) {
        Hit hit = hitboxes.get(entity.getUniqueId());
        if (hit == null) return null;
        Position position = hit.position();
        PlacedStack stack = placements.get(position);
        Location location = position.location();
        if (stack == null || !loaded(location) || !fits(position, stack)
                || !supported(location.getBlock())) return null;
        PotionModel.Offset offset = stack.books() ? new PotionModel.Offset(0, 0)
                : PotionModel.offset(stack.items().size(), hit.index());
        return new HoverTarget(location.add(.5 + offset.x(), stack.height() + .07, .5 + offset.z()),
                stack.items().get(hit.index()), stack.books() ? stack.items().size() : 0);
    }

    private void retrieve(Player player, Position position, int index) {
        PlacedStack stack = placements.get(position);
        Location location = position.location();
        if (stack == null || !loaded(location) || player.getGameMode() == GameMode.SPECTATOR
                || player.getGameMode() == GameMode.ADVENTURE) return;
        // Match block-region checks as well as the original entity interaction checks.
        BlockBreakEvent breaking = new BlockBreakEvent(location.getBlock(), player);
        Bukkit.getPluginManager().callEvent(breaking);
        if (breaking.isCancelled() || placements.get(position) != stack) return;
        ItemStack retrieved = stack.items().get(index);
        PlacedStack remaining = stack.without(index);
        if (!store(position, remaining)) return;
        rebuild(position, remaining);
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.getInventory().addItem(retrieved.clone()).values().forEach(leftover ->
                    location.getWorld().dropItemNaturally(location.clone().add(.5, .2, .5), leftover));
        }
    }

    private boolean store(Position position, PlacedStack stack) {
        PlacedStack previous = placements.get(position);
        if (stack == null) placements.remove(position); else placements.put(position, stack);
        if (save()) return true;
        if (previous == null) placements.remove(position); else placements.put(position, previous);
        return false;
    }

    private void rebuild(Position position, PlacedStack stack) {
        removeModel(position);
        if (stack != null) spawn(position, stack);
    }

    private PlacedStack normalize(Position position, PlacedStack stack) {
        if (stack.overflow().isEmpty()) return stack;
        PlacedStack limited = stack.limited();
        if (!store(position, limited)) return null;
        removeModel(position);
        Location location = position.location().add(.5, .2, .5);
        for (ItemStack item : stack.overflow()) location.getWorld().dropItemNaturally(location, item.clone());
        return limited;
    }

    private void validate() {
        for (var entry : List.copyOf(placements.entrySet())) {
            Location location = entry.getKey().location();
            if (!loaded(location)) continue;
            PlacedStack current = normalize(entry.getKey(), entry.getValue());
            if (current == null) continue;
            if (!fits(entry.getKey(), current) || !supported(location.getBlock())) {
                if (store(entry.getKey(), null)) {
                    removeModel(entry.getKey());
                    for (ItemStack item : current.items()) {
                        location.getWorld().dropItemNaturally(location.clone().add(.5, .2, .5), item.clone());
                    }
                }
            } else {
                List<Entity> entities = models.get(entry.getKey());
                if (entities == null || entities.stream().anyMatch(entity -> !entity.isValid())) {
                    removeModel(entry.getKey());
                    spawn(entry.getKey(), current);
                }
            }
        }
    }

    @EventHandler
    public void loadEntities(EntitiesLoadEvent event) { reconcile(event.getChunk()); }

    @EventHandler
    public void unloadEntities(EntitiesUnloadEvent event) {
        for (Position position : List.copyOf(models.keySet())) {
            if (position.in(event.getChunk())) removeModel(position);
        }
    }

    private void reconcile(Chunk chunk) {
        for (Position position : List.copyOf(models.keySet())) if (position.in(chunk)) removeModel(position);
        // Clean up only our models, including remnants from a previous reload.
        for (Entity entity : chunk.getEntities()) {
            if (entity.getPersistentDataContainer().has(modelKey, PersistentDataType.BYTE)) entity.remove();
        }
        placements.forEach((position, item) -> {
            if (position.in(chunk)) spawn(position, item);
        });
    }

    private void tag(Entity entity) {
        entity.setPersistent(false);
        entity.setGravity(false);
        entity.getPersistentDataContainer().set(modelKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void spawn(Position position, PlacedStack stack) {
        Location base = position.location();
        if (!loaded(base)) return;
        PlacedStack effective = normalize(position, stack);
        if (effective == null || models.containsKey(position)) return;
        base.add(.5, 0, .5);
        List<Entity> entities = new ArrayList<>();
        try {
            for (int layer = 0; layer < effective.items().size(); layer++) {
                ItemStack item = effective.items().get(layer);
                PotionModel.Offset offset = effective.books() ? new PotionModel.Offset(0, 0)
                        : PotionModel.offset(effective.items().size(), layer);
                if (effective.foods()) {
                    int foodLayer = layer;
                    ItemDisplay display = base.getWorld().spawn(base, ItemDisplay.class, entity -> {
                        tag(entity);
                        entity.setInvulnerable(true);
                        entity.setItemStack(item);
                        entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                        entity.setTransformationMatrix(FoodModel.transform(offset.x(), offset.z(), effective.rotation(), foodLayer));
                        entity.setShadowRadius(0);
                    });
                    entities.add(display);
                } else {
                    List<PotionModel.Part> parts = effective.books() ? BookModel.parts(item.getType())
                            : PotionModel.parts(item.getType(), PotionModel.liquid(item));
                    float height = effective.books() ? layer * BookModel.THICKNESS : 0;
                    float rotation = effective.books() ? BookModel.rotation(layer, effective.rotation()) : 0;
                    for (PotionModel.Part part : parts) {
                        BlockDisplay display = base.getWorld().spawn(base, BlockDisplay.class, entity -> {
                            tag(entity);
                            entity.setInvulnerable(true);
                            entity.setBlock(part.material().createBlockData());
                            entity.setTransformationMatrix(new Matrix4f().translation(offset.x(), height, offset.z()).rotateY(rotation)
                                    .translate(part.x(), part.y(), part.z()).scale(part.width(), part.height(), part.depth()));
                            entity.setShadowRadius(0);
                        });
                        entities.add(display);
                    }
                }
                if (!effective.books() || layer == effective.items().size() - 1) {
                    Interaction hitbox = base.getWorld().spawn(base.clone().add(offset.x(), 0, offset.z()), Interaction.class, entity -> {
                        tag(entity);
                        entity.setInteractionWidth(effective.width());
                        entity.setInteractionHeight(effective.height());
                        entity.setResponsive(true);
                    });
                    entities.add(hitbox);
                    hitboxes.put(hitbox.getUniqueId(), new Hit(position, layer));
                }
            }
            models.put(position, List.copyOf(entities));
        } catch (RuntimeException exception) {
            // A saved item can be rebuilt on the next refresh; leave no partial model behind.
            entities.forEach(entity -> {
                hitboxes.remove(entity.getUniqueId());
                entity.remove();
            });
            plugin.getLogger().warning("Cannot build placed item model: " + exception.getMessage());
        }
    }

    private void removeModel(Position position) {
        List<Entity> entities = models.remove(position);
        if (entities == null) return;
        for (Entity entity : entities) {
            hitboxes.remove(entity.getUniqueId());
            entity.remove();
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration data = new YamlConfiguration();
        try {
            data.load(file);
            for (String key : data.getKeys(false)) {
                var section = data.getConfigurationSection(key);
                if (section == null) throw new IllegalArgumentException("Invalid placed item record: " + key);
                Position position = new Position(UUID.fromString(section.getString("world")),
                        section.getInt("x"), section.getInt("y"), section.getInt("z"));
                List<ItemStack> items = new ArrayList<>();
                if (section.contains("items")) {
                    List<?> saved = section.getList("items");
                    if (saved == null) throw new IllegalArgumentException("Invalid item list: " + key);
                    for (Object value : saved) {
                        if (!(value instanceof ItemStack item)) throw new IllegalArgumentException("Invalid item: " + key);
                        items.add(item);
                    }
                } else {
                    ItemStack item = section.getItemStack("item"); // Legacy bottle records.
                    if (item == null) throw new IllegalArgumentException("Missing item: " + key);
                    items.add(item);
                }
                if (placements.containsKey(position)) throw new IllegalArgumentException("Duplicate placed stack: " + key);
                placements.put(position, new PlacedStack(items, (float) section.getDouble("rotation", 90)));
            }
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException exception) {
            throw new IllegalStateException("Cannot load potions.yml; refusing to overwrite saved potions", exception);
        }
    }

    private boolean save() {
        YamlConfiguration data = new YamlConfiguration();
        int index = 0;
        for (var entry : placements.entrySet()) {
            String key = Integer.toString(index++);
            Position position = entry.getKey();
            data.set(key + ".world", position.world().toString());
            data.set(key + ".x", position.x());
            data.set(key + ".y", position.y());
            data.set(key + ".z", position.z());
            if (entry.getValue().books() || entry.getValue().foods() || entry.getValue().items().size() > 1) {
                data.set(key + ".items", entry.getValue().items());
                data.set(key + ".rotation", entry.getValue().rotation());
            }
            else data.set(key + ".item", entry.getValue().top());
        }
        try {
            Files.createDirectories(file.toPath().getParent());
            var temporary = file.toPath().resolveSibling("potions.yml.tmp");
            data.save(temporary.toFile());
            try {
                Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().severe("Cannot save placed items; action cancelled: " + exception.getMessage());
            return false;
        }
    }
}
