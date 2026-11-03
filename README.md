# NYXVAULT AutoBuy — Minecraft 1.21.11 Fabric

Client-side auction listing scanner / click assistant.

## Build

Requires Java 21 and Gradle 9.2.1+:

```bash
gradle build
```

The JAR is produced in `build/libs/`. GitHub Actions builds it on every push and publishes it as a workflow artifact.

## Use

1. Install Fabric Loader for Minecraft 1.21.11 and Fabric API 0.141.4+1.21.11.
2. Put the built JAR into `.minecraft/mods`.
3. Open the server auction GUI and press **K** to enable/disable scanning.
4. Edit `.minecraft/config/nyxvault-autobuy.properties` to set item registry IDs and maximum prices, for example:

```properties
minecraft:netherite_ingot=100
minecraft:elytra=1000
```

The mod reads price text from item lore and clicks the matching listing. Auction GUI layouts and price wording are server-specific; this initial build needs testing against the actual HolyWorld GUI. It does not bypass server protections or send custom packets.
