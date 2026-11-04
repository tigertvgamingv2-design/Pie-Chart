# PieChart

A Spigot plugin that shows players a colored bar/pie-style breakdown of nearby block entities (chests, signs, hoppers, ...) or entities (mobs, items, ...). Useful for finding lag sources.

## Requirements

- Spigot/Paper server, API version 1.21+
- Java 25
- A valid license key (verified on startup)

## Installation

1. Build with `mvn package` (or use a release jar) and place `target/PieChart-1.0.0-SNAPSHOT.jar` in your server's `plugins/` folder.
2. Start the server once to generate `plugins/PieChart/config.yml`.
3. Set `license.key` in the config and restart.

## Commands

Alias: `/pie`

| Command | Description |
|---|---|
| `/piechart` | Show a chart using the default radius (block entities) |
| `/piechart <radius> [blocks\|entities]` | Chart a specific radius and type |
| `/piechart <on\|off\|toggle> [radius] [blocks\|entities]` | Auto-update the chart when it changes |
| `/piechart status` | Show your auto-update settings |
| `/piechart help` | Show help |

Auto-update refreshes on an interval and when you move to a new chunk, and only prints when the results change.

## Permissions

| Permission | Default | Description |
|---|---|---|
| `piechart.use` | op | Use `/piechart` |
| `piechart.chunks.<n>` | none | Limit block scans to `n` chunks around the player (e.g. `piechart.chunks.1`) |

`piechart.chunks.<n>` can only lower the server-wide `chart.loaded-chunk-radius`, never raise it. If a player has several, the highest applies. Players without one use the config value.

## Configuration

Changes take effect after a server restart. Invalid values are reported in the console and the plugin will not enable.

```yaml
license:
  key: "YOUR_LICENSE_KEY"

chart:
  default-radius: 32               # radius when none is given
  max-radius: 128                  # largest radius a player may request (1-512)
  loaded-chunk-radius: 2           # max chunk distance scanned for block entities (0-32)
  auto-update-interval-seconds: 10 # auto-update check interval (1-3600)
  max-entries: 8                   # types shown individually; the rest are grouped as "Other"
  bar-length: 20                   # bar width in characters (5-40)
  colors: ["&c", "&6", "&e", "&a"] # legacy (&a) or hex (#12abef) codes, 1-16
```

### How the limits work together

Block scans apply both the block radius and the loaded-chunk limit, whichever is stricter. Only already-loaded chunks are scanned; the plugin never loads chunks. Entity scans use the block radius only.

## Building

```
mvn package
```

## License

Copyright CDMStudios. All rights reserved.
