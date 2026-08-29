# Folia Performance Pack

This fork keeps Folia's region-ownership model intact and adds a small set of
profiling-driven optimisations, fixes and optional networking/gameplay tuning.
The goal is **lower per-region MSPT without turning Folia into a collection of
unsafe global/async hacks**.

The extra configuration is written to `folia.yml` on first start. Upstream
Paper/Folia configuration files remain authoritative for settings already
provided upstream.

## Default `folia.yml`

```yaml
config-version: 1

networking:
  # Purpur/Canvas-style multiple outstanding keepalive packets.
  # Changes networking behaviour, therefore disabled by default.
  alternative-keepalive: false

performance:
  entity-ai:
    # Do not load a chunk just because an Enderman tests a teleport target.
    prevent-enderman-teleport-chunk-loads: true

    # Reject targets outside raw follow range before doing visibility work.
    early-target-range-check: true

  random-tick:
    # Avoid RegistryAccess resolution on grass/mycelium random ticks.
    use-built-in-block-registry: true

  entity-ticking:
    # 1 = vanilla/Paper behaviour. Higher values run the suffocation test less
    # often. Keep this at 1 unless profiling shows Entity#isInWall is expensive.
    in-wall-check-interval: 1

  villager:
    # -1 = vanilla/Paper behaviour. Positive values set a pickup delay on items
    # thrown by villagers and can reduce rapid item re-pickup in dense farms.
    item-repickup-delay: -1
```

Comments are generated into the real file as well. Values that materially
change behaviour are deliberately conservative by default.

## Included changes

| Area | Change | Default | Origin / notes |
| --- | --- | --- | --- |
| Config | Fork-local `folia.yml` loaded during server bootstrap | Enabled | This fork |
| Entity AI | Enderman teleport checks do not force chunk loads | On | Gale / Airplane |
| Entity AI | Distance rejection before expensive target visibility work | On | Gale / Airplane |
| Random tick | Use built-in block registry instead of resolving RegistryAccess for spreading blocks | On | Leaf |
| Block entities | `BlockEntityType#isValid` uses a direct block/type association instead of a set lookup | On | Leaf |
| Entity tracking | Fast path when entity broadcast range is the default 100% | On | Leaf |
| Entity ticking | Configurable suffocation / in-wall check interval | Vanilla (`1`) | Gale / Pufferfish |
| Villagers | Configurable pickup delay for items thrown by villagers | Vanilla (`-1`) | Gale / EmpireCraft |
| Explosions | Do not create explosion fire after the explosion was cancelled | On | Leaf / Paper-related fix |
| Networking | Alternative multiple-outstanding keepalive implementation | Off | Purpur / Canvas |
| Mob effects | Skip active-effect iterator setup when an entity has no effects | On | Leaf |
| Entity network | Skip relative movement-packet construction when neither position nor rotation changed | On | Gale / Airplane |

Patch headers preserve upstream attribution and license notes where applicable.

## Why some Leaf/Canvas changes are intentionally not included

Folia is not a normal single-main-thread Paper fork. An optimisation that is
safe on Paper/Leaf can be incorrect on Folia if it introduces shared mutable
scratch state or reads another region's world/entity data.

The first performance pack intentionally rejects or defers changes such as:

- mutable static `BlockPos`/entity/chunk scratch caches used by multiple regions;
- async world/entity access that bypasses Folia ownership checks;
- parallel world ticking or entity tracking layered on top of Folia's own
  region parallelism;
- direct cross-region chunk/entity reads merely to save a lookup;
- replacing Folia scheduler/ownership checks with single-thread assumptions;
- large scheduler rewrites (for example Canvas AFFINITY/work-stealing) without
  isolated soak testing and race/deadlock validation.

Canvas's scheduler work is interesting, but it belongs in a separate
experimental branch rather than the conservative production patch set.

## Production tuning guidance

Start with the defaults. For a survival server, change one option at a time and
profile the same workload before and after.

Suggested procedure:

1. Keep the world pre-generated when possible.
2. Capture 5-10 minutes of region profiling during a real peak period.
3. Compare the worst-region MSPT, median region MSPT and scheduler utilisation.
4. Check entity ticking, chunk loading/tickets, packet processing and GC
   separately instead of assuming one global TPS number describes the server.
5. Only raise `in-wall-check-interval` if suffocation checks are visible in the
   hot path. Values such as `5` or `10` are reasonable experiments; they are
   not the default because they alter reaction timing.
6. Only set `villager.item-repickup-delay` if villager farms show rapid item
   pickup/drop churn in profiling.
7. Leave alternative keepalive disabled unless you have a concrete latency or
   short-stall problem and have tested affected clients/proxies.

## Thread-count policy

Do not treat `threaded-regions.threads` as "more is always faster". Region tick
threads share CPU time with Netty, chunk I/O/workers, plugins, the JVM/GC and
the operating system. A healthy pool should have headroom and should not spend
all of its time inside `RegionScheduleHandle#runTick`.

The upstream Folia guidance of leaving CPU capacity for non-region work still
applies. Tune from real scheduler utilisation rather than player count alone.

## Build validation

The repository workflow validates the same two stages used by upstream Folia:

```bash
./gradlew applyAllPatches --stacktrace
./gradlew build
```

The fork workflow also uploads built JARs when GitHub Actions is enabled for the
repository. A change should not be merged to the production branch until both
patch application and compilation pass.

## Upstream maintenance

Keep this fork as a small patch stack on top of `PaperMC/Folia` rather than
copying hundreds of Leaf/Canvas patches. When upstream Folia changes region
ownership, scheduler or chunk code, re-evaluate affected patches instead of
blindly resolving conflicts.
