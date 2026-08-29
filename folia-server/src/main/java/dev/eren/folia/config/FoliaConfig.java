package dev.eren.folia.config;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fork-local configuration for performance, networking, compatibility and
 * region-safety changes that are not part of upstream Paper/Folia.
 *
 * <p>Pure correctness fixes default to enabled. Changes that intentionally
 * alter event/protocol/gameplay semantics remain individually switchable.</p>
 */
public final class FoliaConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("FoliaConfig");
    private static final File FILE = new File("folia.yml");
    private static final int CURRENT_VERSION = 2;

    // Networking
    public static boolean alternativeKeepAlive = false;
    public static boolean reconcileRejectedEntityInteractions = true;
    public static boolean precomputeVarLongSizes = true;

    // Existing performance options
    public static boolean preventEndermanTeleportChunkLoads = true;
    public static boolean earlyTargetRangeCheck = true;
    public static boolean useBuiltInBlockRegistryForRandomTicks = true;
    public static int inWallCheckInterval = 1;
    public static int villagerItemRepickupDelay = -1;
    public static boolean disableVanillaDebugSubscribers = true;

    // Region-safety / correctness fixes
    public static boolean cancellableCactusAge = true;
    public static boolean fixSpawnRadiusZero = true;
    public static boolean queuedVanishRemovals = true;
    public static boolean aiSensorOwnershipChecks = true;
    public static boolean fixPlayerAutosave = true;
    public static boolean safePlayerRefresh = true;
    public static boolean fixEnderPearlDamager = true;
    public static boolean fixMaxPlayerCount = true;
    public static boolean fixDelayedLeashOwnership = true;
    public static boolean fixDragonPartRegistration = true;

    // Compatibility event bridges. These are kept switchable because they add
    // event behaviour that upstream Folia intentionally does not fully expose.
    public static boolean respawnEventBridge = true;
    public static boolean playerChangedWorldAfterAsyncTeleport = true;

    // Process-wide fluid work budget. Disabled by default because it is a load
    // shedding/fairness policy, not a semantics-free micro optimisation.
    public static boolean fluidTickBudgetEnabled = false;
    public static int fluidTickProcessMaximum = 2_000;
    public static int fluidTickRegionMinimum = 50;
    public static int fluidTickTargetAgeTicks = 20;
    public static int fluidTickMaxAgeWeight = 8;
    public static int fluidTickFairSharePercent = 75;

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
        add(config, "networking.reconcile-rejected-entity-interactions", true,
            "Resend authoritative inventory/container state when Folia rejects an entity interaction because ownership changed.",
            "Prevents client-prediction ghost item/equipment states without processing the interaction off-region.");
        add(config, "networking.precompute-varlong-sizes", true,
            "Use a precomputed VarLong byte-size table on packet encoding hot paths.",
            "Stateless Velocity/Gale/Leaf-style optimization; safe to leave enabled.");

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
        add(config, "performance.debug.disable-vanilla-debug-subscribers", true,
            "Disable vanilla debug subscriptions, which are not region-thread safe in upstream Folia.",
            "Recommended for production. Set false only when you intentionally need vanilla debug subscriptions.");

        add(config, "performance.fluid-ticks.process-budget.enabled", false,
            "Apply one fair process-wide fluid tick ceiling across all Folia regions.",
            "Work is delayed rather than discarded. Useful as overload protection for many simultaneous fluid-heavy regions.");
        add(config, "performance.fluid-ticks.process-budget.process-maximum", 2_000,
            "Maximum fluid ticks granted process-wide during each approximately 50 ms epoch.");
        add(config, "performance.fluid-ticks.process-budget.region-minimum", 50,
            "Minimum grant attempted for each active region before the remainder is shared.");
        add(config, "performance.fluid-ticks.process-budget.target-age-ticks", 20,
            "Queue age represented by each additional age weight.");
        add(config, "performance.fluid-ticks.process-budget.max-age-weight", 8,
            "Maximum fairness weight assigned to an old runnable fluid queue.");
        add(config, "performance.fluid-ticks.process-budget.fair-share-percent", 75,
            "Percentage of the remainder distributed equally before age weighting (0-100).");

        add(config, "fixes.block-growth.cancellable-cactus-age", true,
            "Paper #13480 compatibility fix: fire BlockGrowEvent for cactus AGE state changes as well as new-block growth.",
            "Cancelling that event can therefore stop the root cactus from aging. This intentionally strengthens BlockGrowEvent semantics.");
        add(config, "fixes.respawn.spawn-radius-zero", true,
            "Fix Folia spawnRadius=0 ignoring /setworldspawn and falling back to the world-border center.");
        add(config, "fixes.player.autosave", true,
            "Initialize Folia's per-player last-save timestamp on join so automatic player saving starts immediately.");
        add(config, "fixes.region-safety.queued-vanish-removals", true,
            "Queue CraftPlayer visibility-map removals to the owning player tick instead of mutating the map from another region.");
        add(config, "fixes.region-safety.ai-sensor-ownership-checks", true,
            "Reject stale AI sensor targets after they move to another Folia region.");
        add(config, "fixes.region-safety.safe-player-refresh", true,
            "Run advancement/recipe player refresh work through each player's owning scheduler.");
        add(config, "fixes.region-safety.delayed-leash-ownership", true,
            "Do not restore delayed leash data when its target entity or knot position is owned by another region.");
        add(config, "fixes.region-safety.dragon-part-registration", true,
            "Synchronize Ender Dragon part coordinates before region registration and after cross-dimension transforms.");
        add(config, "fixes.events.ender-pearl-damager", true,
            "Use the thrown ender pearl as EntityDamageByEntityEvent damager instead of the player in Folia's async teleport path.");
        add(config, "fixes.player.max-count-off-by-one", true,
            "Fix Folia's connection-limit comparison being offset by one.");

        add(config, "compatibility.events.respawn-bridge", true,
            "Enable this fork's region-safe PlayerRespawnEvent compatibility bridge.",
            "The bridge runs after Folia has completed its destination-region respawn phase; location changes are applied asynchronously afterwards.");
        add(config, "compatibility.events.player-changed-world-after-async-teleport", true,
            "Fire PlayerChangedWorldEvent after a successful cross-world async teleport on the player's owning scheduler.");

        alternativeKeepAlive = config.getBoolean("networking.alternative-keepalive", false);
        reconcileRejectedEntityInteractions = config.getBoolean("networking.reconcile-rejected-entity-interactions", true);
        precomputeVarLongSizes = config.getBoolean("networking.precompute-varlong-sizes", true);
        preventEndermanTeleportChunkLoads = config.getBoolean("performance.entity-ai.prevent-enderman-teleport-chunk-loads", true);
        earlyTargetRangeCheck = config.getBoolean("performance.entity-ai.early-target-range-check", true);
        useBuiltInBlockRegistryForRandomTicks = config.getBoolean("performance.random-tick.use-built-in-block-registry", true);
        inWallCheckInterval = Math.max(1, config.getInt("performance.entity-ticking.in-wall-check-interval", 1));
        villagerItemRepickupDelay = Math.max(-1, config.getInt("performance.villager.item-repickup-delay", -1));
        disableVanillaDebugSubscribers = config.getBoolean("performance.debug.disable-vanilla-debug-subscribers", true);

        fluidTickBudgetEnabled = config.getBoolean("performance.fluid-ticks.process-budget.enabled", false);
        fluidTickProcessMaximum = Math.max(1, config.getInt("performance.fluid-ticks.process-budget.process-maximum", 2_000));
        fluidTickRegionMinimum = Math.max(0, config.getInt("performance.fluid-ticks.process-budget.region-minimum", 50));
        fluidTickTargetAgeTicks = Math.max(1, config.getInt("performance.fluid-ticks.process-budget.target-age-ticks", 20));
        fluidTickMaxAgeWeight = Math.max(1, config.getInt("performance.fluid-ticks.process-budget.max-age-weight", 8));
        fluidTickFairSharePercent = Math.max(0, Math.min(100, config.getInt("performance.fluid-ticks.process-budget.fair-share-percent", 75)));

        cancellableCactusAge = config.getBoolean("fixes.block-growth.cancellable-cactus-age", true);
        fixSpawnRadiusZero = config.getBoolean("fixes.respawn.spawn-radius-zero", true);
        fixPlayerAutosave = config.getBoolean("fixes.player.autosave", true);
        queuedVanishRemovals = config.getBoolean("fixes.region-safety.queued-vanish-removals", true);
        aiSensorOwnershipChecks = config.getBoolean("fixes.region-safety.ai-sensor-ownership-checks", true);
        safePlayerRefresh = config.getBoolean("fixes.region-safety.safe-player-refresh", true);
        fixDelayedLeashOwnership = config.getBoolean("fixes.region-safety.delayed-leash-ownership", true);
        fixDragonPartRegistration = config.getBoolean("fixes.region-safety.dragon-part-registration", true);
        fixEnderPearlDamager = config.getBoolean("fixes.events.ender-pearl-damager", true);
        fixMaxPlayerCount = config.getBoolean("fixes.player.max-count-off-by-one", true);
        respawnEventBridge = config.getBoolean("compatibility.events.respawn-bridge", true);
        playerChangedWorldAfterAsyncTeleport = config.getBoolean("compatibility.events.player-changed-world-after-async-teleport", true);

        config.set("config-version", CURRENT_VERSION);
        config.set("performance.entity-ticking.in-wall-check-interval", inWallCheckInterval);
        config.set("performance.villager.item-repickup-delay", villagerItemRepickupDelay);
        config.set("performance.fluid-ticks.process-budget.process-maximum", fluidTickProcessMaximum);
        config.set("performance.fluid-ticks.process-budget.region-minimum", fluidTickRegionMinimum);
        config.set("performance.fluid-ticks.process-budget.target-age-ticks", fluidTickTargetAgeTicks);
        config.set("performance.fluid-ticks.process-budget.max-age-weight", fluidTickMaxAgeWeight);
        config.set("performance.fluid-ticks.process-budget.fair-share-percent", fluidTickFairSharePercent);

        try {
            config.save(FILE);
        } catch (final IOException ex) {
            LOGGER.error("Unable to save {}", FILE.getAbsolutePath(), ex);
        }

        LOGGER.info(
            "Loaded folia.yml v{} (alt-keepalive={}, interaction-resync={}, varlong-fastpath={}, debug-subscribers-disabled={}, cactus-age-fix={}, respawn-bridge={}, fluid-budget={})",
            CURRENT_VERSION,
            alternativeKeepAlive,
            reconcileRejectedEntityInteractions,
            precomputeVarLongSizes,
            disableVanillaDebugSubscribers,
            cancellableCactusAge,
            respawnEventBridge,
            fluidTickBudgetEnabled
        );
    }

    private static void add(final YamlConfiguration config, final String path, final Object defaultValue, final String... comments) {
        config.addDefault(path, defaultValue);
        if (config.getComments(path).isEmpty()) {
            config.setComments(path, List.of(comments));
        }
    }
}
