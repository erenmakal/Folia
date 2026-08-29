package dev.eren.folia.config;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Small fork-local configuration layer for options that do not belong in
 * upstream Paper/Folia configuration files.
 *
 * <p>Defaults intentionally favour upstream-compatible behaviour. Performance
 * options that alter gameplay or protocol timing are opt-in.</p>
 */
public final class FoliaConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("FoliaConfig");
    private static final File FILE = new File("folia.yml");
    private static final int CURRENT_VERSION = 1;

    public static boolean alternativeKeepAlive = false;
    public static boolean flushLocationWhileKnockback = false;
    public static boolean preventEndermanTeleportChunkLoads = true;
    public static boolean earlyTargetRangeCheck = true;
    public static boolean useBuiltInBlockRegistryForRandomTicks = true;
    public static int inWallCheckInterval = 1;
    public static int villagerItemRepickupDelay = -1;
    public static boolean disableVanillaDebugSubscriptions = false;
    public static int debugSubscriberRefreshInterval = 20;

    private FoliaConfig() {
    }

    public static synchronized void load() {
        final YamlConfiguration config = YamlConfiguration.loadConfiguration(FILE);

        config.options().copyDefaults(true);
        config.options().parseComments(true);

        add(config, "config-version", CURRENT_VERSION,
            "Configuration version. Do not change this manually.");

        add(config, "networking.alternative-keepalive", false,
            "Purpur/Canvas-style multiple outstanding keepalive mode.",
            "Can be more tolerant of short stalls and high-latency clients. Disabled by default for upstream compatibility.");
        add(config, "networking.flush-location-while-knockback", false,
            "Flushes player position/tracking state immediately around player-vs-player knockback.",
            "May improve combat position consistency at the cost of extra packet flushes. Disabled by default.");

        add(config, "performance.entity-ai.prevent-enderman-teleport-chunk-loads", true,
            "Avoid loading a chunk only because an Enderman is testing a teleport destination.");
        add(config, "performance.entity-ai.early-target-range-check", true,
            "Reject out-of-range AI targets before the more expensive visibility calculation.");
        add(config, "performance.random-tick.use-built-in-block-registry", true,
            "Use the built-in block registry directly during grass/mycelium random ticks instead of resolving it every tick.");
        add(config, "performance.entity-ticking.in-wall-check-interval", 1,
            "How often living entities perform the relatively expensive suffocation/in-wall test, in ticks.",
            "1 keeps vanilla behaviour. 20 is a performance-oriented value used by Pufferfish/Gale-style optimisations.");
        add(config, "performance.villager.item-repickup-delay", -1,
            "Pickup delay applied to items thrown by villagers.",
            "-1 keeps vanilla behaviour; a positive delay can reduce rapid villager re-pickup churn in farms.");
        add(config, "performance.debug-subscribers.disable-vanilla-debug-subscriptions", false,
            "Completely disables vanilla debug subscription processing. Leave false if you use client debug subscription features.");
        add(config, "performance.debug-subscribers.refresh-interval", 20,
            "Refresh interval, in ticks, for vanilla debug subscriber membership.",
            "Only affects debug tooling; 20 substantially reduces per-tick player scanning overhead.");

        alternativeKeepAlive = config.getBoolean("networking.alternative-keepalive", false);
        flushLocationWhileKnockback = config.getBoolean("networking.flush-location-while-knockback", false);
        preventEndermanTeleportChunkLoads = config.getBoolean("performance.entity-ai.prevent-enderman-teleport-chunk-loads", true);
        earlyTargetRangeCheck = config.getBoolean("performance.entity-ai.early-target-range-check", true);
        useBuiltInBlockRegistryForRandomTicks = config.getBoolean("performance.random-tick.use-built-in-block-registry", true);
        inWallCheckInterval = Math.max(1, config.getInt("performance.entity-ticking.in-wall-check-interval", 1));
        villagerItemRepickupDelay = Math.max(-1, config.getInt("performance.villager.item-repickup-delay", -1));
        disableVanillaDebugSubscriptions = config.getBoolean("performance.debug-subscribers.disable-vanilla-debug-subscriptions", false);
        debugSubscriberRefreshInterval = Math.max(1, config.getInt("performance.debug-subscribers.refresh-interval", 20));

        config.set("config-version", CURRENT_VERSION);
        config.set("performance.entity-ticking.in-wall-check-interval", inWallCheckInterval);
        config.set("performance.villager.item-repickup-delay", villagerItemRepickupDelay);
        config.set("performance.debug-subscribers.refresh-interval", debugSubscriberRefreshInterval);

        try {
            config.save(FILE);
        } catch (final IOException ex) {
            LOGGER.error("Unable to save {}", FILE.getAbsolutePath(), ex);
        }

        LOGGER.info(
            "Loaded folia.yml (keepalive={}, knockback-flush={}, in-wall-interval={}, debug-refresh={})",
            alternativeKeepAlive,
            flushLocationWhileKnockback,
            inWallCheckInterval,
            debugSubscriberRefreshInterval
        );
    }

    private static void add(final YamlConfiguration config, final String path, final Object defaultValue, final String... comments) {
        config.addDefault(path, defaultValue);
        if (config.getComments(path).isEmpty()) {
            config.setComments(path, List.of(comments));
        }
    }
}
