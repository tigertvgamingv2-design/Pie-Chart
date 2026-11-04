package com.cdmstudios.piechart.commands;

import com.cdmstudios.piechart.utils.Utils;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PieChartCommand implements CommandExecutor, TabCompleter {

    private static final String CHUNK_PERMISSION_PREFIX = "piechart.chunks.";

    private final int defaultRadius;
    private final int maxRadius;
    private final int loadedChunkRadius;
    private final int barLength;
    private final int maxChartEntries;
    private final List<String> colors;

    public PieChartCommand(int defaultRadius, int maxRadius, int loadedChunkRadius, int barLength,
                           int maxChartEntries, List<String> colors) {
        this.defaultRadius = defaultRadius;
        this.maxRadius = maxRadius;
        this.loadedChunkRadius = loadedChunkRadius;
        this.barLength = barLength;
        this.maxChartEntries = maxChartEntries;
        this.colors = List.copyOf(colors);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length == 1 && (args[0].equalsIgnoreCase("help") || args[0].equals("?"))) {
            sendHelp(player, label);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            sendStatus(player);
            return true;
        }

        int radius = defaultRadius;
        boolean entities = false;
        String action = null;
        boolean radiusProvided = false;
        boolean modeProvided = false;
        for (String arg : args) {
            if (arg.equalsIgnoreCase("on") || arg.equalsIgnoreCase("off") || arg.equalsIgnoreCase("toggle")) {
                if (action != null) {
                    sendUsage(player, label);
                    return true;
                }
                action = arg.toLowerCase(Locale.ROOT);
            } else if (arg.equalsIgnoreCase("entities")) {
                if (modeProvided) {
                    sendUsage(player, label);
                    return true;
                }
                entities = true;
                modeProvided = true;
            } else if (arg.equalsIgnoreCase("blocks")) {
                if (modeProvided) {
                    sendUsage(player, label);
                    return true;
                }
                entities = false;
                modeProvided = true;
            } else {
                if (radiusProvided) {
                    sendUsage(player, label);
                    return true;
                }
                try {
                    radius = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    sendUsage(player, label);
                    return true;
                }
                radiusProvided = true;
            }
        }
        if (radius < 1 || radius > maxRadius) {
            player.sendMessage(Utils.colorize("&cRadius must be between 1 and " + maxRadius + " blocks."));
            return true;
        }

        if (action != null) {
            boolean enable = action.equals("on") || (action.equals("toggle") && !enabled.containsKey(player.getUniqueId()));
            if (enable) {
                enabled.put(player.getUniqueId(), new Settings(radius, entities));
                player.sendMessage(Utils.colorize("&aPie chart auto-updates &lON&a &8| &7"
                        + (entities ? "Entities" : "Block entities") + ", " + radius + "-block radius"
                        + (entities ? "" : ", up to " + chunkLimit(player) + " chunks from you")));
                update(player);
            } else {
                enabled.remove(player.getUniqueId());
                lastAutoCharts.remove(player.getUniqueId());
                player.sendMessage(Utils.colorize("&cPie chart auto-updates &lOFF&c."));
            }
            return true;
        }

        sendChart(player, entities ? scanEntities(player, radius) : scanBlocks(player, radius), radius, entities);
        return true;
    }

    private record Settings(int radius, boolean entities) {}
    private record ChartSnapshot(Map<String, Integer> counts, int radius, boolean entities) {}

    private final Map<UUID, Settings> enabled = new ConcurrentHashMap<>();
    private final Map<UUID, ChartSnapshot> lastAutoCharts = new ConcurrentHashMap<>();

    public boolean isEnabled(Player player) {
        return enabled.containsKey(player.getUniqueId());
    }

    public void remove(Player player) {
        enabled.remove(player.getUniqueId());
        lastAutoCharts.remove(player.getUniqueId());
    }

    public Collection<UUID> enabledPlayers() {
        return enabled.keySet();
    }

    private void sendHelp(Player player, String label) {
        player.sendMessage(Utils.colorize("&6&lPie Chart &8» &7Nearby block entities or entities"));
        player.sendMessage(Utils.colorize("&e/" + label + " &7- Show a chart (default: " + defaultRadius
                + " blocks, block entities)"));
        player.sendMessage(Utils.colorize("&e/" + label + " <radius> [blocks|entities] &7- Set chart range and type"));
        player.sendMessage(Utils.colorize("&e/" + label + " <on|off|toggle> [radius] [blocks|entities] &7- Auto-update"));
        player.sendMessage(Utils.colorize("&e/" + label + " status &7- Show your auto-update settings"));
        player.sendMessage(Utils.colorize("&7Radius: 1-" + maxRadius + " blocks. Use &f/" + label
                + " help &7for help."));
        player.sendMessage(Utils.colorize("&7Block scans use both limits: radius and loaded-chunk radius ("
                + chunkLimit(player) + " chunks). Unloaded chunks are never loaded."));
    }

    // The config value is the server-wide cap; piechart.chunks.<n> permissions can only lower it.
    public int chunkLimit(Player player) {
        int granted = -1;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            String node = info.getPermission().toLowerCase(Locale.ROOT);
            if (!info.getValue() || !node.startsWith(CHUNK_PERMISSION_PREFIX)) {
                continue;
            }
            try {
                granted = Math.max(granted, Integer.parseInt(node.substring(CHUNK_PERMISSION_PREFIX.length())));
            } catch (NumberFormatException ignored) {
                // Not a numeric limit node.
            }
        }
        return granted < 0 ? loadedChunkRadius : Math.min(granted, loadedChunkRadius);
    }

    private void sendStatus(Player player) {
        Settings settings = enabled.get(player.getUniqueId());
        if (settings == null) {
            player.sendMessage(Utils.colorize("&6&lPie Chart &8» &7Auto-update: &cOFF"));
            return;
        }
        String type = settings.entities() ? "entities" : "block entities";
        player.sendMessage(Utils.colorize("&6&lPie Chart &8» &7Auto-update: &aON &8| &7Type: &f"
                + type + " &8| &7Radius: &f" + settings.radius() + " blocks"
                + (settings.entities() ? "" : " &8| &7Chunk limit: &f" + chunkLimit(player))));
    }

    private void sendUsage(Player player, String label) {
        player.sendMessage(Utils.colorize("&cInvalid arguments. Use &f/" + label + " help&c for usage."));
    }

    // Sends the chart using the player's saved toggle settings.
    public void update(Player player) {
        Settings s = enabled.get(player.getUniqueId());
        if (s == null) {
            return;
        }
        Map<String, Integer> counts = s.entities() ? scanEntities(player, s.radius()) : scanBlocks(player, s.radius());
        ChartSnapshot snapshot = new ChartSnapshot(Map.copyOf(counts), s.radius(), s.entities());
        ChartSnapshot previous = lastAutoCharts.put(player.getUniqueId(), snapshot);
        if (!snapshot.equals(previous)) {
            sendChart(player, counts, s.radius(), s.entities());
        }
    }

    private Map<String, Integer> scanBlocks(Player player, int radius) {
        Map<String, Integer> counts = new HashMap<>();
        Location origin = player.getLocation();
        World world = origin.getWorld();
        int minCx = (origin.getBlockX() - radius) >> 4;
        int maxCx = (origin.getBlockX() + radius) >> 4;
        int minCz = (origin.getBlockZ() - radius) >> 4;
        int maxCz = (origin.getBlockZ() + radius) >> 4;
        int playerCx = origin.getBlockX() >> 4;
        int playerCz = origin.getBlockZ() >> 4;
        int chunkLimit = chunkLimit(player);
        minCx = Math.max(minCx, playerCx - chunkLimit);
        maxCx = Math.min(maxCx, playerCx + chunkLimit);
        minCz = Math.max(minCz, playerCz - chunkLimit);
        maxCz = Math.min(maxCz, playerCz + chunkLimit);
        double radiusSq = (double) radius * radius;

        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }
                Chunk chunk = world.getChunkAt(cx, cz);
                for (BlockState state : chunk.getTileEntities()) {
                    if (state.getLocation().distanceSquared(origin) <= radiusSq) {
                        counts.merge(pretty(state.getType().name()), 1, Integer::sum);
                    }
                }
            }
        }
        return counts;
    }

    private Map<String, Integer> scanEntities(Player player, int radius) {
        Map<String, Integer> counts = new HashMap<>();
        Location origin = player.getLocation();
        double radiusSq = (double) radius * radius;
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity.getLocation().distanceSquared(origin) <= radiusSq) {
                counts.merge(pretty(entity.getType().name()), 1, Integer::sum);
            }
        }
        return counts;
    }

    private void sendChart(Player player, Map<String, Integer> counts, int radius, boolean entities) {
        String what = entities ? "entities" : "block entities";
        if (counts.isEmpty()) {
            player.sendMessage(Utils.colorize("&6&lPie Chart &8» &eNo " + what + " found within &f"
                    + radius + " blocks&e."));
            return;
        }

        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> {
            int countOrder = b.getValue().compareTo(a.getValue());
            return countOrder != 0 ? countOrder : a.getKey().compareTo(b.getKey());
        });

        player.sendMessage(Utils.colorize("&6&lPie Chart &8» &7Nearby " + what + " &8| &f" + radius
                + " blocks" + (entities ? "" : " &8|                 &7Chunk limit: &f" + chunkLimit(player))                + " &8| &7Total: &f" + total));
        int visibleEntries = Math.min(sorted.size(), maxChartEntries);
        for (int i = 0; i < visibleEntries; i++) {
            sendChartEntry(player, sorted.get(i).getKey(), sorted.get(i).getValue(), total,
                    colors.get(i % colors.size()));
        }
        if (sorted.size() > maxChartEntries) {
            int otherCount = 0;
            for (int i = maxChartEntries; i < sorted.size(); i++) {
                otherCount += sorted.get(i).getValue();
            }
            sendChartEntry(player, "Other (" + (sorted.size() - maxChartEntries) + " types)",
                    otherCount, total, "&7");
        }
    }

    private void sendChartEntry(Player player, String name, int count, int total, String color) {
        double percent = count * 100.0 / total;
        int filled = Math.max(1, (int) Math.round(percent / 100.0 * barLength));
        String bar = color + "|".repeat(filled) + "&8" + "|".repeat(Math.max(0, barLength - filled));
        player.sendMessage(Utils.colorize(bar + " &f" + name + " &8» &f" + count
                + String.format(Locale.ROOT, " &7(%.1f%%)", percent)));
    }

    private String pretty(String name) {
        StringBuilder sb = new StringBuilder();
        for (String part : name.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> candidates = new ArrayList<>();
        if (args.length == 1) {
            candidates.addAll(List.of("help", "status", "on", "off", "toggle", "blocks", "entities"));
        } else {
            boolean hasAction = false;
            boolean hasMode = false;
            for (int i = 0; i < args.length - 1; i++) {
                String previous = args[i].toLowerCase(Locale.ROOT);
                hasAction |= previous.equals("on") || previous.equals("off") || previous.equals("toggle");
                hasMode |= previous.equals("blocks") || previous.equals("entities");
            }
            if (!hasAction) {
                candidates.addAll(List.of("on", "off", "toggle"));
            }
            if (!hasMode) {
                candidates.addAll(List.of("blocks", "entities"));
            }
        }
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.startsWith(prefix)) {
                matches.add(candidate);
            }
        }
        return matches;
    }
}
