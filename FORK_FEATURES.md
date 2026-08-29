# Folia Performance & Compatibility Pack

This fork keeps Folia's region-ownership model intact while adding profiling-driven
optimisations, missing compatibility bridges and fixes for Folia-specific races.
The goal is lower per-region MSPT, fewer cross-region crashes/desyncs and a more
usable plugin API without hiding illegal cross-region access.

The fork-local configuration is generated as `folia.yml` on first start.
Paper/Folia configuration files remain authoritative for settings already owned
by upstream.

> Build status matters: do not deploy a branch merely because all patches look
> correct in review. Run `applyAllPatches` and a full Gradle build first.

## Default `folia.yml` (config version 2)

```yaml
config-version: 2

networking:
  alternative-keepalive: false
  reconcile-rejected-entity-interactions: true
  precompute-varlong-sizes: true

performance:
  entity-ai:
    prevent-enderman-teleport-chunk-loads: true
    early-target-range-check: true

  random-tick:
    use-built-in-block-registry: true

  entity-ticking:
    in-wall-check-interval: 1

  villager:
    item-repickup-delay: -1

  debug:
    disable-vanilla-debug-subscribers: true

  fluid-ticks:
    process-budget:
      enabled: false
      process-maximum: 2000
      region-minimum: 50
      target-age-ticks: 20
      max-age-weight: 8
      fair-share-percent: 75

fixes:
  block-growth:
    cancellable-cactus-age: true

  respawn:
    spawn-radius-zero: true

  player:
    autosave: true
    max-count-off-by-one: true

  region-safety:
    queued-vanish-removals: true
    ai-sensor-ownership-checks: true
    safe-player-refresh: true
    delayed-leash-ownership: true
    dragon-part-registration: true
    villager-async-portal-brain: true

  events:
    ender-pearl-damager: true

compatibility:
  events:
    respawn-bridge: true
    player-changed-world-after-async-teleport: true
```

The generated file includes comments explaining each option. Correctness and
region-safety fixes default on. Options that deliberately change networking or
load-shedding policy remain opt-in when appropriate.

## Plugin compatibility fixes

### Region-safe PlayerRespawnEvent bridge

Upstream Folia cannot safely fire Paper's legacy `PlayerRespawnEvent` at exactly
the same pre-placement point because the destination region is not owned at that
moment. This fork therefore uses a two-phase bridge:

1. Folia completes its normal async destination-region placement.
2. `PlayerRespawnEvent` is fired while the player is owned by the destination
   region.
3. If a plugin changes `event.getRespawnLocation()`, the changed location is
   applied through `teleportAsync` rather than by touching a foreign region.
4. `PlayerPostRespawnEvent` is fired at the final owned location.

This restores useful respawn observation and location control for Folia plugins
without pretending that legacy Paper timing is thread-safe. Plugins that rely on
pre-placement side effects should be tested against this documented timing.

### Teleport/world-change observation

A successful cross-world async teleport can optionally schedule
`PlayerChangedWorldEvent` on the owning player scheduler. This restores an event
many plugins use to refresh per-world state without reading the player from an
unowned region.

### Safe player data refresh

Recipe and advancement refresh operations are redirected through each player's
owning scheduler. This is aimed at plugin/API reload paths that otherwise touch
many players from a global or unrelated region context.

## Included correctness and race fixes

| Area | Change | Default | Origin / issue |
| --- | --- | --- | --- |
| Respawn | Region-safe `PlayerRespawnEvent` + `PlayerPostRespawnEvent` bridge | On | This fork / Folia #105 |
| Respawn | `spawnRadius=0` respects configured world spawn | On | Folia #401 / PR #475 |
| Block growth | Cactus AGE changes use cancellable `BlockGrowEvent` | On | Paper #13480 |
| Visibility | Queue `CraftPlayer` vanish-map removals on player-owning context | On | Folia #406 / PR #448 |
| AI | Validate stale sensor targets against current region ownership | On | Folia PR #491 |
| Villager portal | Defer brain schedule init until async portal copy owns destination world | On | Folia #431 / PR #446 |
| Leashes | Reject delayed leash targets owned by another region | On | Folia PR #495 |
| Ender Dragon | Sync multipart positions before region registration | On | Folia #427 / PR #504 |
| Block entities | Correct region `tickingBlockEntities` setter typo | Always | Folia PR #462 |
| Player saves | Initialize/repair Folia player autosave timing | On | Folia PR #459 |
| Player refresh | Schedule recipes/advancements on player owner | On | Folia PR #498 |
| Ender pearl | Correct Bukkit damage-event damager | On | Folia #438 / PR #442 |
| Connection limit | Correct player-limit comparison offset | On | Folia PR #500 |
| Debug | Disable unsafe vanilla debug subscriptions in production | On | Folia #432/#472 / PR #499 |
| Interaction | Resync player inventory after ownership-rejected predicted interaction | On | Folia #502 |
| Explosion | Do not create fire for a cancelled explosion | On | Leaf / Paper-related fix |

## Performance and network changes

| Area | Change | Default | Notes |
| --- | --- | --- | --- |
| Entity AI | Enderman teleport target checks never force destination chunk loads | On | Gale/Airplane |
| Entity AI | Reject targets outside follow range before visibility work | On | Gale/Airplane |
| Random tick | Use immutable built-in block registry on spreading-block hot path | On | Leaf |
| Block entity | Direct block/type association for `BlockEntityType#isValid` | On | Leaf |
| Tracking | Default 100% tracking-distance fast path | On | Leaf |
| Entity tick | Skip effect iterator when entity has no effects | On | Leaf |
| Entity tick | Configurable suffocation/in-wall interval | `1` | Higher values trade response latency for CPU |
| Entity network | Skip unchanged relative movement packet construction | On | Gale/Airplane |
| ItemStack | Identity fast path for same-stack comparisons | On | Gale/Leaf |
| Entity math | Inline squared entity-distance hot path | On | Leaf-inspired/stateless |
| Packet encoding | Precomputed VarLong byte-size table | On | Velocity/Gale/Leaf |
| Keepalive | Multiple outstanding keepalive mode | Off | Purpur/Canvas; protocol behavior change |
| Fluids | Fair process-wide fluid tick budget | Off | Folia PR #503-derived overload controller |

## Process-wide fluid budget

Folia's ordinary fluid limit is per region. With many independent fluid-heavy
regions, the process can therefore execute the local maximum many times in the
same 50 ms window. The optional controller shares one bounded process allowance
between active regions with a per-region floor, fair-share pool, bounded queue
age weighting and rotating tie order. Work is delayed rather than deleted.

Upstream PR #503 reported a four-region stress test at a process maximum of
2,000 fluid ticks / 50 ms where median regional MSPT fell from 5.350 to 2.110,
allocation estimate fell by about 67%, and network traffic also fell sharply.
Those are stress-test results, not a promise for every survival workload.

Leave this controller disabled initially. Enable it when fluid abuse/farms are
actually visible in profiling or when you intentionally want overload
protection. Monitor queue age as well as MSPT; an unrealistically low budget can
make fluids lag behind even while tick time looks excellent.

## 500-player production target

There is no code patch that can guarantee 500 concurrent players. Hardware,
plugin behavior, player distribution, view/simulation distance, chunk
generation, mob density and cross-region mechanics determine the real ceiling.
For a 500-player target, use this fork as a way to remove known correctness and
hot-path costs, then tune from actual peak-load measurements.

Recommended starting policy:

1. Pre-generate the main worlds when possible. Runtime generation can dominate
   chunk workers regardless of region tick optimisations.
2. Keep enough CPU headroom for Netty, chunk I/O/workers, plugins and GC; do not
   assign every hardware thread to `threaded-regions.threads`.
3. Measure worst-region MSPT, median region MSPT and scheduler utilisation; one
   global TPS number is not sufficient for Folia.
4. Keep `in-wall-check-interval: 1` until profiling proves it matters. Try `5`
   or `10` only as an explicit gameplay/performance tradeoff.
5. Keep vanilla debug subscribers disabled on production unless you explicitly
   use client debug subscriptions.
6. Leave alternative keepalive off until tested with the actual proxy/client
   mix. It is not a generic throughput switch.
7. Enable the fluid process budget only when fluid load needs a process-wide
   safety valve. Start with the documented 2000/50 defaults and profile queue
   age before lowering it.
8. Profile plugins separately. A plugin doing cross-region scans, synchronous
   database work or global locks can erase the benefit of server-side micro
   optimisations.

## Scheduler policy

This production patch set deliberately does **not** replace Folia's core region
scheduler with Canvas AFFINITY/work-stealing. A scheduler rewrite changes region
ordering, fairness and failure modes and requires dedicated contention,
starvation and shutdown testing. Combining an unvalidated scheduler rewrite with
many compatibility patches would make failures harder to isolate.

The current strategy instead removes avoidable region work and adds bounded
process-wide control where regional independence itself multiplies load. A
scheduler experiment should live on a separate branch and only move into the
production branch after repeatable benchmark and soak-test evidence.

## Changes deliberately rejected/deferred

Leaf/Canvas patches are not copied blindly. This branch avoids:

- mutable static world/entity scratch caches shared by region threads;
- async world/entity reads that bypass ownership checks;
- a second multithreaded entity tracker layered over Folia's own parallelism;
- cross-region direct entity/chunk reads just to save a lookup;
- scheduler rewrites without starvation/deadlock testing;
- Leaf's non-flush Netty `lazyExecute` patch in the conservative branch because
  the related Paper experiment was not merged and had connectivity-testing
  concerns.

## Build validation

Before deployment, both stages must pass:

```bash
./gradlew applyAllPatches --stacktrace
./gradlew build --stacktrace
```

The repository workflow is prepared to upload built server JARs and failure
reports. Keep the PR in draft until both patch application and compilation pass.
After that, run a clean-world smoke test covering login, death/respawn,
teleport/portal, inventory/entity interactions, villagers, vanish/hideEntity,
Ender Dragon spawning, fluids and shutdown; then run a realistic multi-hour
plugin soak test before production rollout.
