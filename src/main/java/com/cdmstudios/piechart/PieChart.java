package com.cdmstudios.piechart;

import org.bukkit.plugin.java.JavaPlugin;
import com.cdmstudios.piechart.commands.PieChartCommand;
import com.cdmstudios.piechart.listeners.ChunkChangeListener;
import org.bukkit.entity.Player;
import com.cdmstudios.piechart.utils.LicenseGuard;
import org.bukkit.configuration.file.FileConfiguration;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.UUID;
import java.util.logging.Level;
import com.cdmstudios.piechart.managers.PluginManager;
import com.cdmstudios.piechart.listeners.PlayerListener;

public class PieChart extends JavaPlugin {
    private static final String LICENSE_API_URL = "https://lic.cloudrix.uk/api/verify";
    private static final String PRODUCT_SLUG = "piechart";
    private static final Pattern LEGACY_COLOR = Pattern.compile("&[0-9a-fk-or]", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEX_COLOR = Pattern.compile("#[a-f0-9]{6}", Pattern.CASE_INSENSITIVE);

    private record Settings(int defaultRadius, int maxRadius, int loadedChunkRadius,
                            int refreshIntervalSeconds, int maxEntries, int barLength, List<String> colors) {}

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        Settings settings;
        try {
            settings = loadSettings();
        } catch (IllegalArgumentException exception) {
            getLogger().severe("Invalid config.yml setting: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        String licenseKey = getConfig().getString("license.key", "YOUR_LICENSE_KEY");
        if (licenseKey.isBlank() || licenseKey.startsWith("YOUR_")) {
            getLogger().severe("Set license.key in config.yml before enabling this plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            String failureReason;
            try {
                failureReason = LicenseGuard.verify(LICENSE_API_URL, PRODUCT_SLUG, licenseKey);
            } catch (IOException exception) {
                getLogger().log(Level.WARNING, "License verification request failed.", exception);
                failureReason = exception.getMessage();
            }

            String reason = failureReason;
            getServer().getScheduler().runTask(this, () -> {
                if (reason != null) {
                    getLogger().severe("License verification failed: " + reason + "; disabling plugin.");
                    getServer().getPluginManager().disablePlugin(this);
                    return;
                }
                enableFeatures(settings);
            });
        });
    }

    private Settings loadSettings() {
        FileConfiguration config = getConfig();
        int maxRadius = readIntSetting(config, "chart.max-radius", 1, 512);
        int defaultRadius = readIntSetting(config, "chart.default-radius", 1, 512);
        if (defaultRadius > maxRadius) {
            throw new IllegalArgumentException("chart.default-radius must not exceed chart.max-radius.");
        }

        int loadedChunkRadius = readIntSetting(config, "chart.loaded-chunk-radius", 0, 32);
        int refreshInterval = readIntSetting(config, "chart.auto-update-interval-seconds", 1, 3600);
        int maxEntries = readIntSetting(config, "chart.max-entries", 1, 50);
        int barLength = readIntSetting(config, "chart.bar-length", 5, 40);

        Object configuredColors = config.get("chart.colors");
        if (!(configuredColors instanceof List<?> colorList) || colorList.isEmpty() || colorList.size() > 16) {
            throw new IllegalArgumentException("chart.colors must contain between 1 and 16 color codes.");
        }
        List<String> colors = new ArrayList<>();
        for (Object color : colorList) {
            if (!(color instanceof String colorCode)
                    || (!LEGACY_COLOR.matcher(colorCode).matches() && !HEX_COLOR.matcher(colorCode).matches())) {
                throw new IllegalArgumentException(
                        "every chart.colors entry must be a legacy color code (for example &a) or hex color (for example #12abef).");
            }
            colors.add(colorCode);
        }
        return new Settings(defaultRadius, maxRadius, loadedChunkRadius, refreshInterval,
                maxEntries, barLength, List.copyOf(colors));
    }

    private int readIntSetting(FileConfiguration config, String path, int minimum, int maximum) {
        if (!config.isInt(path)) {
            throw new IllegalArgumentException(path + " must be a whole number.");
        }
        int value = config.getInt(path);
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(path + " must be between " + minimum + " and " + maximum + ".");
        }
        return value;
    }

    private void enableFeatures(Settings settings) {
        // Initialize managers
        PluginManager.getInstance().initialize();
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(), this);
        
        PieChartCommand command = new PieChartCommand(settings.defaultRadius(), settings.maxRadius(),
                settings.loadedChunkRadius(), settings.barLength(), settings.maxEntries(), settings.colors());
        getCommand("piechart").setExecutor(command);
        getCommand("piechart").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new ChunkChangeListener(command), this);

        long refreshTicks = settings.refreshIntervalSeconds() * 20L;
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (UUID id : command.enabledPlayers()) {
                Player p = getServer().getPlayer(id);
                if (p != null) {
                    command.update(p);
                }
            }
        }, refreshTicks, refreshTicks);

        getLogger().info(getDescription().getName() + " has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info(getDescription().getName() + " has been disabled!");
    }
    
}
