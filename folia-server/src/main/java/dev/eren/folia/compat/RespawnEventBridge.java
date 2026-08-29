package dev.eren.folia.compat;

import ca.spottedleaf.moonrise.common.util.TickThread;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import dev.eren.folia.config.FoliaConfig;
import java.util.concurrent.CompletionException;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compatibility bridge for respawn observers on Folia.
 *
 * <p>Upstream Folia cannot safely expose Paper's pre-placement respawn event:
 * the destination region is not owned while that legacy event normally runs.
 * This bridge therefore fires {@link PlayerRespawnEvent} only after Folia has
 * placed the player on the destination region. Listeners can safely inspect the
 * player and destination world. If a listener changes the respawn location, the
 * requested location is applied with Folia's async teleport path immediately
 * after the event. A {@link PlayerPostRespawnEvent} follows at the final owned
 * location.</p>
 *
 * <p>This intentionally favors region safety over byte-for-byte Paper event
 * timing. Plugins that only observe respawns get the expected event again;
 * plugins that change the location are supported through the async follow-up.</p>
 */
public final class RespawnEventBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("FoliaRespawnBridge");

    private RespawnEventBridge() {
    }

    public static void fire(
        final ServerPlayer player,
        final PlayerRespawnEvent.RespawnReason reason,
        final boolean bedSpawn,
        final boolean anchorSpawn,
        final boolean missingRespawnBlock
    ) {
        if (!FoliaConfig.respawnEventBridge) {
            return;
        }

        if (!TickThread.isTickThreadFor(player)) {
            player.getBukkitEntity().taskScheduler.scheduleOrExecute(
                (ServerPlayer ownedPlayer) -> fire(ownedPlayer, reason, bedSpawn, anchorSpawn, missingRespawnBlock)
            );
            return;
        }

        final CraftPlayer bukkitPlayer = player.getBukkitEntity();
        final Location placedLocation = bukkitPlayer.getLocation().clone();
        final PlayerRespawnEvent event = new PlayerRespawnEvent(
            bukkitPlayer,
            placedLocation,
            bedSpawn,
            anchorSpawn,
            missingRespawnBlock,
            reason
        );
        Bukkit.getPluginManager().callEvent(event);

        final Location requestedLocation = event.getRespawnLocation().clone();
        if (requestedLocation.equals(placedLocation)) {
            firePost(player, reason, bedSpawn, anchorSpawn, missingRespawnBlock);
            return;
        }

        bukkitPlayer.teleportAsync(requestedLocation, PlayerTeleportEvent.TeleportCause.PLUGIN)
            .whenComplete((success, throwable) -> {
                if (throwable != null) {
                    final Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                        ? throwable.getCause()
                        : throwable;
                    LOGGER.error("Failed to apply PlayerRespawnEvent location for {}", bukkitPlayer.getName(), cause);
                }
                bukkitPlayer.taskScheduler.scheduleOrExecute(
                    (ServerPlayer ownedPlayer) -> firePost(ownedPlayer, reason, bedSpawn, anchorSpawn, missingRespawnBlock)
                );
            });
    }

    private static void firePost(
        final ServerPlayer player,
        final PlayerRespawnEvent.RespawnReason reason,
        final boolean bedSpawn,
        final boolean anchorSpawn,
        final boolean missingRespawnBlock
    ) {
        if (!TickThread.isTickThreadFor(player)) {
            player.getBukkitEntity().taskScheduler.scheduleOrExecute(
                (ServerPlayer ownedPlayer) -> firePost(ownedPlayer, reason, bedSpawn, anchorSpawn, missingRespawnBlock)
            );
            return;
        }

        final CraftPlayer bukkitPlayer = player.getBukkitEntity();
        Bukkit.getPluginManager().callEvent(new PlayerPostRespawnEvent(
            bukkitPlayer,
            bukkitPlayer.getLocation().clone(),
            bedSpawn,
            anchorSpawn,
            missingRespawnBlock,
            reason
        ));
    }
}
