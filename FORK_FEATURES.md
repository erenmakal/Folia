# Folia Performance & Compatibility Pack

This fork keeps Folia's region-ownership model intact while adding profiling-driven
optimisations, missing compatibility bridges, diagnostics and fixes for
Folia-specific races. The goal is lower per-region MSPT, fewer cross-region
crashes/desyncs and a more usable plugin API without hiding illegal cross-region
access.

The fork-local configuration is generated as `folia.yml` on first start.
Paper/Folia configuration files remain authoritative for settings already owned
by upstream.

> Build status matters: do not deploy a branch merely because patches look
> correct in review. `applyAllPatches`, the test suite and a full Gradle build
> must pass before production use.

## Default `folia.yml` (config version 3)

```yaml
config-version: 3

networking:
  alternative-keepalive: false
  reconcile-rejected-entity-interactions: true
  precompute-varlong-sizes: true
  async-switch-connection-state: false

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

  teleport:
    preserve-relative-velocity: true

  events:
    ender-pearl-damager: true

compatibility:
  events:
    respawn-bridge: true
    player-changed-world-after-async-teleport: true
```

The generated file includes comments explaining each option. Correctness and
region-safety fixes default on. Changes that alter protocol timing or deliberately
shed/delay work are conservative/opt-in.

## Plugin compatibility

### Region-safe PlayerRespawnEvent bridge

Upstream Folia cannot safely fire Paper's legacy `PlayerRespawnEvent` at exactly
the same pre-placement point because the destination region is not owned at that
moment. This fork uses a two-phase bridge:

1. Folia completes normal async destination-region placement.
2. `PlayerRespawnEvent` fires while the player is owned by that region.
3. If a plugin changes `event.getRespawnLocation()`, the change is applied with
   `teleportAsync` rather than touching a foreign region.
4. `PlayerPostRespawnEvent` fires at the final owned location.

This restores useful respawn observation/location control for Folia plugins
without pretending Paper's original pre-placement timing is region-safe.

### Async teleport relative velocity

Folia's Bukkit `teleportAsync` bridge discarded `TeleportFlag.Relative` and
always forwarded zero velocity. `VELOCITY_X`, `VELOCITY_Y`, `VELOCITY_Z` and
`VELOCITY_ROTATION` are now reconstructed using Paper/vanilla relative-delta
semantics before entering the Folia async teleport path. This targets Folia
issue #441 and is controlled by:

```yaml
fixes:
  teleport:
    preserve-relative-velocity: true
```

### Teleport/world-change observation

A successful cross-world async player teleport can optionally schedule
`PlayerChangedWorldEvent` on the owning player scheduler.

### Safe player data refresh

Recipe and advancement refresh operations are redirected through each player's
owning scheduler. Runtime server/plugin/data reload and runtime datapack toggling
are explicitly rejected because upstream does not have a safe region ownership
model for mutating those global registries while players are ticking.

## Diagnostics API

The existing Folia location-aware TPS API is extended with location-aware MSPT:

```java
double[] mspt = Bukkit.getRegionAverageTickTimes(location);
```

The returned windows are 5s, 15s, 1m, 5m and 15m. This is much more useful than
a fake global MSPT value when diagnosing one overloaded survival region.

Region scheduler failures also create normal `crash-reports/crash-...-server.txt`
files with system data and region context instead of only logging a scheduler
stack trace.

## Included correctness and race fixes

| Area | Change | Default | Origin / issue |
| --- | --- | --- | --- |
| Respawn | Region-safe `PlayerRespawnEvent` + `PlayerPostRespawnEvent` bridge | On | This fork / Folia #105 |
| Respawn | `spawnRadius=0` respects configured world spawn | On | Folia #401 / PR #475 |
| Block growth | Cactus AGE changes use cancellable `BlockGrowEvent` | On | Paper #13480 |
| Visibility | Queue `CraftPlayer` vanish-map removals on player-owning context | On | Folia #406 / PR #448 |
| Player lookup | Use `ConcurrentHashMap` for `playersByName` | Always | Folia PR #497 |
| AI | Validate stale sensor targets against current region ownership | On | Folia PR #491 |
| Villager portal | Defer brain schedule init until destination world ownership is valid | On | Folia #431 / PR #446 |
| Leashes | Reject delayed leash targets owned by another region | On | Folia PR #495 |
| Ender Dragon | Sync multipart positions before region registration | On | Folia #427 / PR #504 |
| Block entities | Correct region `tickingBlockEntities` setter typo | Always | Folia PR #462 |
| Player saves | Initialize/repair Folia player autosave timing | On | Folia PR #459 |
| Player refresh | Schedule recipes/advancements on player owner | On | Folia PR #498 |
| Runtime reload | Reject unsafe reload/data/datapack mutations | Always | Folia PR #498 |
| Ender pearl | Correct Bukkit damage-event damager | On | Folia #438 / PR #442 |
| Async teleport | Preserve Paper relative-velocity flags | On | Folia #441 / this fork |
| Connection limit | Correct player-limit comparison offset | On | Folia PR #500 |
| Debug | Disable unsafe vanilla debug subscriptions in production | On | Folia #432/#472 / PR #499 |
| Interaction | Resync inventory after ownership-rejected predicted interaction | On | Folia #502 |
| Shutdown | Stop chat handling once region shutdown begins | Always | Folia PR #461 |
| Crash handling | Write crash report for scheduler/region failures | Always | Folia PR #463 |
| Startup | Exit completed bootstrap thread; remove unused region-server instance | Always | Folia PR #465 |
| Health command | Force `minecraft:tp` namespace in hotspot navigation | Always | Folia PR #494 |
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
| Entity math | Inline squared entity-distance hot path | On | Stateless hot-path optimization |
| Packet encoding | Precomputed VarLong byte-size table | On | Velocity/Gale/Leaf |
| Client prediction | Resync rejected entity interactions | On | Avoid ghost inventory/equipment state |
| Keepalive | Multiple outstanding keepalive mode | Off | Purpur/Canvas; protocol behavior change |
| Protocol state | Async Netty pipeline state switch | Off | Leaf 26.2 / Folia #454; needs proxy/plugin soak testing |
| Fluids | Fair process-wide fluid tick budget | Off | Folia PR #503-derived overload controller |

## Process-wide fluid budget

Folia's normal fluid limit is per region. Many independent fluid-heavy regions
can therefore multiply the same local maximum across the process. The optional
controller shares one process allowance between active regions using a per-region
floor, fair-share pool, bounded queue-age weighting and rotating tie order. Work
is delayed rather than deleted.

Upstream PR #503 reported a four-region stress test at a process maximum of
2,000 fluid ticks / 50 ms where median regional MSPT fell from 5.350 to 2.110,
allocation estimate fell by about 67%, and network traffic also fell sharply.
Those are stress-test results, not a promise for every survival workload.

Leave the controller disabled initially. Enable it when fluid abuse/farms are
actually visible in profiling or when you intentionally want overload
protection. Monitor queue age as well as MSPT.

## Linux production pack

See [`LINUX_TUNING.md`](LINUX_TUNING.md). The repository includes:

- `scripts/linux/start-folia.sh` — environment-driven Java launcher, fixed heap,
  ZGC/G1 selection and rotating GC logs;
- `scripts/linux/install-tuning.sh` — conservative sysctl/nofile tuning and
  `performance` CPU governor where supported;
- `scripts/linux/folia.service.example` — systemd service with clean SIGINT
  shutdown, high file-descriptor limit and restart-on-failure.

The Linux installer deliberately does not force BBR, IRQ/RPS/XPS affinity,
CCD/NUMA pinning, huge-page policy or an I/O scheduler. Those are host-specific
A/B tests, not universal Minecraft optimisations.

## 500-player production target

No fork can guarantee 500 concurrent players independently of workload. Plugin
behavior, player distribution, view/simulation distance, chunk generation, mob
density and cross-region mechanics determine the real ceiling.

For a 16-core / 32-thread Ryzen-class dedicated host:

1. Keep enough CPU headroom for Netty, chunk workers/I/O, GC, Velocity and the
   database; do not assign every hardware thread to region ticking.
2. Use the location-aware TPS/MSPT API and profile worst-region/p95 MSPT rather
   than trusting one global TPS number.
3. Pre-generate large worlds where practical; runtime terrain generation can
   dominate independently of region tick optimisations.
4. Keep `in-wall-check-interval: 1` until profiling proves it is material.
5. Keep debug subscribers disabled on production.
6. Keep alternative keepalive and async connection-state switch disabled until
   tested with the actual Velocity/ViaVersion/Geyser/PacketEvents stack.
7. Enable the fluid process budget only when fluid load needs a process-wide
   safety valve.
8. Profile plugins independently. Global locks, synchronous SQL and cross-region
   scans can erase server-side gains.

## Scheduler policy

This production branch does **not** replace Folia's core scheduler with an
unvalidated custom scheduler. A scheduler rewrite changes fairness, ordering,
starvation behavior and shutdown semantics. It belongs on a separate benchmark
branch until repeatable stress and soak tests prove it is an improvement.

More region threads also do not split one overloaded region across CPUs. They
only help when enough independent regions are runnable concurrently.

## Changes deliberately rejected/deferred

Leaf/Canvas patches are not copied blindly. This branch avoids:

- mutable static world/entity scratch caches shared by region threads;
- async world/entity reads that bypass ownership checks;
- a second multithreaded entity tracker layered over Folia's own parallelism;
- direct cross-region entity/chunk reads just to save a lookup;
- scheduler rewrites without starvation/deadlock testing;
- Leaf's non-flush Netty `lazyExecute` patch in the conservative branch because
  the related Paper experiment was not merged and has connectivity-risk surface.

Open Folia issues such as complete teleport/portal event parity, owner-bound
projectiles across dimensions and portal-linked region backpressure require
larger semantic/architectural work and should not be papered over with unsafe
cross-region calls.

## Build validation

Before deployment, both stages must pass:

```bash
./gradlew applyAllPatches --stacktrace
./gradlew build --stacktrace
```

The repository workflow uploads built server JARs on success and Gradle reports
on failure. Keep the PR in draft until patch application, compilation and tests
are actually green. After that, run a clean-world smoke test covering login,
death/respawn, async teleports, portals, inventory/entity interactions,
villagers, vanish/hideEntity, Ender Dragon spawning, fluids, reload rejection
and shutdown, then run a realistic multi-hour plugin soak test before production.
