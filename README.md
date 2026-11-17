# LumiView

Paper **26.3**, Java **25**. Container previews on vanilla shelves and placeable bottles and book piles; no resource pack or client mod.

## Install

Build with `./gradlew build` (Windows: `gradlew.bat build`), copy `build/libs/LumiView-1.0.0.jar` into your server's `plugins/` folder, and restart the server. When upgrading from ShelfView, remove its old JAR first so both plugins do not run together. Existing shelf links are discovered automatically.

## Shelves

1. Place a container and put items inside.
2. Place an **empty shelf directly against the container**, at the same height, facing away from it. Sneak while placing to avoid opening the container. Chests require the shelf to face the same direction as the chest; other containers allow shelves on any horizontal side.
3. The shelf shows the first three unique items, refreshing every half second. Repeated stacks appear only once, with one item per display slot. Items with different names, enchantments, or other metadata remain distinct.
4. Place two adjacent, matching-facing empty shelves across the front of a double chest to display up to six unique items: three on each shelf, without repeats between them. With only one eligible shelf, it shows the first three items from the combined chest inventory.
5. Right-click a shelf to open the linked container, including while sneaking.

Supported blocks include chests, trapped chests, barrels, furnaces, blast furnaces, smokers, hoppers, dispensers, droppers, brewing stands, shulker boxes, and crafters. Support uses Bukkit's block `Container` interface. Ender chests and other shelves are excluded.

All wood variants work. Shelves without a supported container, or shelves already holding real items, keep their normal behavior. Remove any stored shelf items before using a shelf as a preview. Breaking either block removes the preview; stored items remain in the container. No commands or registration are needed, and existing shelves are discovered when their chunks load.

## Placeable bottles

- Hold a normal, splash, or lingering potion, dragon’s breath, honey bottle, experience bottle, ominous bottle, or empty glass bottle in either hand and **sneak-right-click a block** to place a decorative bottle in the adjacent air space. It needs a block with a full supporting top face beneath it; click the top of a solid block for the usual placement.
- Bottles are compact, slim 3D models with one glass display per body layer, stepped shoulders, hollow necks, simple lips, and wooden stoppers. Colored stained glass gives the liquid translucent edges, with a small matching solid core for stronger color. Glass and liquid pieces have tiny separations to avoid overlapping transparent faces. They use block displays, like Lumistones. Splash bottles have an offset neck; lingering bottles have a wider body and darker stopper. Dragon’s breath has magenta contents, honey is orange, and experience is lime green, and ominous bottles have black contents with a dark stopper. Empty glass bottles are open and contain no liquid. Potion liquid uses the closest vanilla stained-glass color to the potion’s effective color, including custom colors. No resource pack is required. Existing placed potions adopt the model on reload.
- Up to **four bottles share one block space**, arranged in staggered clusters like candles rather than a square grid. Sneak-right-click an existing bottle while holding another bottle to add it, or click the supporting block again. Different bottle types and item metadata can be mixed; bottles and books cannot share a space. The gesture suppresses drinking or throwing, including when placement fails. Sneak-clicking a block also suppresses the bottle’s usual use, such as collecting honey from a beehive.
- Survival placement consumes one bottle from the held stack. Its effects, color, name, and other item metadata are preserved. Right-click without a bottle-placement gesture, or punch, to retrieve the specific bottle you target; a full inventory drops the returned item nearby. Creative placement/removal does not consume or return items. Adventure and spectator players cannot place or retrieve bottles.
- Removing/moving its support, or filling the bottle's space with a block or fluid, drops every saved bottle in the group within half a second while the chunk is loaded.
- All bottles are stored in `plugins/LumiView/potions.yml` and survive restarts and chunk/world unloads. Like Lumistones, their display entities are rebuilt from saved records rather than saved as independent world entities. Back up this file with the world.

## Placeable books

- Hold a regular, enchanted, writable (book and quill), written, or knowledge book and **right-click the top of a solid block** to place it. No sneaking is required, and either hand works. Books face the player when placed, with a broad leather binding along the book’s own left edge, wrapping onto the top cover. They have textured cloth covers, a contrasting leather-style inset and emblem, decorative borders, corner protectors, page-edge lines, spine bands, and a ribbon bookmark; cover colors distinguish the book types.
- **Right-click the pile while holding another book** to add it on top, up to **four books per pile**. Each book is 3/16 of a block thick; a full pile is ¾ of a block tall. Existing larger piles keep their bottom four books and drop the extras when their chunks load. You can mix book types, names, enchantments, and written contents. Clicking the supporting block also adds a book to the pile in the adjacent space. Bottles and books cannot share a space.
- Right-click with no book in either hand, or punch, to retrieve **one book from the top**. Hover labels show the top book’s tooltip and the number of books in the pile.
- Survival placement uses one held book. Every book retains its own pages, author, title, enchantments, and other metadata. Creative placement/removal follows the same no-cost/no-return behavior as bottles.
- Removing the support or obstructing any space occupied by the pile drops **all books in the pile**. Protection checks, full-inventory handling, and restart/chunk-load recovery work as they do for bottles.
- Books share the existing `plugins/LumiView/potions.yml` storage file, with an ordered list per pile. Existing bottle records remain compatible.

## Hover labels

Aim your crosshair at an item in a shelf or at a placed bottle/book pile from up to five blocks away to see a floating tooltip. Shelf labels follow the individual slot, including real stored items and container preview items. Names, enchantments, lore, and potion effects use the normal item tooltip, capped at six lines with an ellipsis for longer descriptions.

Each hologram is visible only to the player looking at it. Looking away, targeting an empty slot, moving out of range, or disconnecting removes it. Walls block targeting, and locked/unopened-loot containers cannot expose items through hover labels. Labels update as displayed items change.

## Dropped item labels

Dropped items show a small floating item name and stack count to viewers within 16 blocks. Labels follow moving items and refresh as stacks merge or are partly collected. Custom item names and translated vanilla names are preserved. Existing drops are found on startup and when their chunks load.

Labels appear only to players who can see the dropped item, are occluded by blocks, and disappear on pickup, despawn, chunk unload, or plugin disable. The actual dropped stack and its entity name are unchanged.

## Behavior and limits

- Shelf preview stacks are sent only to clients. They are never inserted into the shelf inventory, so breaking a shelf, hoppers, and redstone cannot extract preview items.
- A shelf has three display slots; a pair in front of a double chest shares six. Extra unique items beyond that capacity are not shown. Separate shelves on other containers each show the same first three unique items.
- Locked containers and containers with pending loot tables do not show previews or open through shelves. Open a loot container normally first. Both halves of a double chest are checked.
- Blocked chests cannot be opened through shelves. Shulker boxes require an unobstructed block in their opening direction, a conservative check that can reject partial obstructions too.
- Opening a shelf dispatches a linked-container interaction and the standard inventory-open event. Bottle/book placement/retrieval dispatches virtual block-place/block-break events at the bottle's air space, alongside normal interaction events. Protection plugins that require an actual block at that position may need additional integration; check compatibility on protected servers.
- Preview visibility is shared with anyone who can see the shelf, like a public storage label. Region permissions are checked when opening, not when viewing the preview.
- Designed for ordinary Paper servers, not Folia. Shelves placed by external world editors require a chunk reload or plugin restart to be discovered.

## Verification

Build: `./gradlew build` (requires Java 25; the wrapper downloads Gradle automatically). Automated tests cover preview selection, container linking and lock/loot checks, potion gestures, support, denied placement, shelf hover orientation, tooltip formatting, occlusion checks, private hologram cleanup, book model bounds, stack limits, and individual book metadata/order.

In-game checks (require a Paper 26.3 server): test shelves in all four directions with single/double/trapped chests and the supported containers; update contents via players and hoppers; test native furnace/brewing/crafter menus; right-click with both hands and while sneaking; break either block; reload chunks and restart; check locked/blocked containers and region protection. Power a shelf and confirm that no preview stacks enter player inventories. Place all three potion types, including named/custom potions, plus dragon’s breath, honey, experience, ominous, and empty bottles using either hand; test stacked bottles and empty bottles clicked on beehives; retrieve by click and punch; test creative mode, full inventories, duplicate placement, support removal/pistons/explosions, occupied spaces, protected regions, chunk reloads, world reloads, and server restarts.

API references: [Paper project setup](https://docs.papermc.io/paper/dev/project-setup/), [Shelf inventory](https://jd.papermc.io/paper/26.3/org/bukkit/inventory/ShelfInventory.html), [Containers](https://jd.papermc.io/paper/26.3/org/bukkit/block/Container.html).

Hover checks: aim at each slot in shelves facing all four directions, including empty slots, real stored items, locked containers, and both halves of a double chest; hover named and enchanted items and potions with effects; test walls, range, simultaneous viewers, item changes, disconnects, chunk unloads, and plugin disable.

Book checks: mix all book types in a pile, including signed books and differently enchanted books; add books with either hand, reach the four-book limit, retrieve in reverse order, check hover counts, unload/reload chunks and restart, and remove the support to recover every book. Check denied placement/retrieval, creative/adventure mode, full inventories, and that bottles cannot be added to book piles. Group one to four mixed bottles, aim at and retrieve each bottle individually, test the four-bottle limit, and verify grouped metadata and restart/chunk-load recovery.

Dropped-item checks: drop ordinary and named items, merge stacks, partially collect them with players and hoppers, move drops with water, test hidden items and multiple viewers, go beyond the 16-block range, unload/reload chunks, and verify pickup/despawn and plugin-disable cleanup.
