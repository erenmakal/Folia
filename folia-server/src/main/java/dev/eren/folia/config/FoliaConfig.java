package dev.eren.folia.config;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fork-local configuration for performance and networking changes that are not
 * part of upstream Paper/Folia.
 *
 * <p>Safe optimisations default to enabled. Changes that intentionally alter
 * vanilla timing or protocol behaviour remain opt-in.</p>
 */
public final class FoliaConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("FoliaConfig");
    private static final File FILE = new File("folia.yml");
    private static final int CURRENT_VERSION = 1;

    public static boolean alternativeKeepAlive = false;
    public static boolean preventEndermanTeleportChunkLoads = true;
    public static boolean earlyTargetRangeCheck = true;
    public static boolean useBuiltInBlockRegistryForRandomTicks = true;
    public static int inWallCheckInterval = 1;
    public static int villagerItemRepickupDelay = -1;

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
            "Can be more tolerant of short stalls and high-latency clients.",
            "Disabled by default because it changes networking behaviour.");

        add(config, "performance.entity-ai.prevent-enderman-teleport-chunk-loads", true,
            "Do not load a chunk only because an Enderman is testing a teleport destination.",
            "This removes avoidable chunk loads while retaining valid teleports into already-loaded chunks.");
        add(config, "performance.entity-ai.early-target-range-check", true,
            "Reject out-of-range AI targets before the more expensive visibility calculation.");
        add(config, "performance.random-tick.use-built-in-block-registry", true,
            "Use the immutable built-in block registry during grass/mycelium random ticks instead of resolving RegistryAccess every tick.");
        add(config, "performance.entity-ticking.in-wall-check-interval", 1,
            "How often living entities perform the relatively expensive suffocation/in-wall test, in ticks.",
            "1 preserves vanilla behaviour. Values above 1 trade suffocation reaction latency for lower entity-tick cost.");
        add(config, "performance.villager.item-repickup-delay", -1,
            "Pickup delay applied to items thrown by villagers.",
            "-1 preserves vanilla behaviour; a positive delay can reduce rapid villager item re-pickup churn in dense farms.");

        alternativeKeepAlive = config.getBoolean("networking.alternative-keepalive", false);
        preventEndermanTeleportChunkLoads = config.getBoolean("performance.entity-ai.prevent-enderman-teleport-chunk-loads", true);
        earlyTargetRangeCheck = config.getBoolean("performance.entity-ai.early-target-range-check", true);
        useBuiltInBlockRegistryForRandomTicks = config.getBoolean("performance.random-tick.use-built-in-block-registry", true);
        inWallCheckInterval = Math.max(1, config.getInt("performance.entity-ticking.in-wall-check-interval", 1));
        villagerItemRepickupDelay = Math.max(-1, config.getInt("performance.villager.item-repickup-delay", -1));

        config.set("config-version", CURRENT_VERSION);
        config.set("performance.entity-ticking.in-wall-check-interval", inWallCheckInterval);
        config.set("performance.villager.item-repickup-delay", villagerItemRepickupDelay);

        try {
            config.save(FILE);
        } catch (final IOException ex) {
            LOGGER.error("Unable to save {}", FILE.getAbsolutePath(), ex);
        }

        LOGGER.info(
            "Loaded folia.yml (alternative-keepalive={}, enderman-no-load={}, early-target-range={}, random-tick-registry={}, in-wall-interval={}, villager-repickup-delay={})",
            alternativeKeepAlive,
            preventEndermanTeleportChunkLoads,
            earlyTargetRangeCheck,
            useBuiltInBlockRegistryForRandomTicks,
            inWallCheckInterval,
            villagerItemRepickupDelay
        );
    }

    private static void add(final YamlConfiguration config, final String path, final Object defaultValue, final String... comments) {
        config.addDefault(path, defaultValue);
        if (config.getComments(path).isEmpty()) {
            config.setComments(path, List.of(comments));
        }
    }
}
