# NYXVAULT AutoBuy — Minecraft 1.21.11 Fabric

Client-side auction listing scanner with configurable per-item purchase mode.

## Build

Requires Java 21 and Gradle 9.5.0:

```bash
gradle build
```

The JAR is produced in `build/libs/`. GitHub Actions builds it on every push and publishes it as a workflow artifact.

## Use

1. Install Fabric Loader for Minecraft 1.21.11 and Fabric API 0.141.4+1.21.11.
2. Put the built JAR into `.minecraft/mods`.
3. Open the server auction GUI and press **K** to enable/disable scanning.
4. Edit `.minecraft/config/nyxvault-autobuy.properties`.

Each item has its own maximum price and purchase mode:

```properties
# Buy the whole listing with left click
minecraft:elytra=1000,full

# Buy one item from the listing with right click
minecraft:potion=500,single

# Backward-compatible format: omitted mode means full lot
minecraft:netherite_ingot=100
```

Modes:
- `full` — left click (ЛКМ), buy the whole listing.
- `single` — right click (ПКМ), buy one item, as indicated by the HolyWorld item tooltip.

The mod reads price text from item lore and clicks matching listings. Auction GUI layouts and price wording are server-specific; test with a low-value listing first. The current implementation does not automatically open the auction, change pages, verify completed purchases, or bypass server protections.
