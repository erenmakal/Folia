# Linux tuning for Folia Performance Pack

This directory contains **conservative** host-side tuning for dedicated Folia
servers. The goal is to remove avoidable OS scheduling/socket/file-descriptor
friction without applying benchmark-blog sysctl cargo cult.

## Recommended baseline for a 16-core / 32-thread dedicated host

For a Ryzen 9 5950X-class machine with 64 GiB RAM and a large survival server:

- run a current 64-bit Linux distribution and Java 25+;
- keep roughly 25-30 GiB outside the Java heap for native memory, chunk I/O,
  filesystem cache, Netty, the proxy/database and the OS;
- start with a 30 GiB fixed heap and ZGC;
- do not manually force ZGC worker counts unless GC logs prove the JVM's
  topology-based sizing is wrong for the workload;
- use an NVMe SSD and keep world/player/plugin databases off slow network or
  consumer SMR storage;
- keep Velocity/database/background compression from competing for every CPU.

The supplied launcher defaults to:

```bash
HEAP=30G GC=zgc JAR=folia-server.jar ./scripts/linux/start-folia.sh
```

## Install conservative host tuning

```bash
sudo ./scripts/linux/install-tuning.sh
```

It applies only:

- `vm.swappiness=10`;
- larger listen/SYN queues for login bursts;
- SYN cookies;
- a larger system file-descriptor ceiling;
- per-user nofile limits;
- the `performance` CPU governor when the kernel exposes it.

The script intentionally does **not** turn on BBR, force IRQ affinity, bind Java
to one CCD/NUMA node, change transparent-huge-page policy, replace the I/O
scheduler or alter kernel scheduler internals. Those changes can help a
specific machine but can also make Folia worse by starving Netty, chunk workers
or GC. Treat them as separate A/B tests.

## systemd

Copy the example unit and edit paths/user names:

```bash
sudo cp scripts/linux/folia.service.example /etc/systemd/system/folia.service
sudo systemctl daemon-reload
sudo systemctl enable --now folia
```

The unit raises `LimitNOFILE`, uses `SIGINT` for a clean shutdown and gives the
server up to three minutes to flush worlds before systemd kills it.

## CPU layout

Do **not** equate Folia region threads with logical CPU count. A 5950X also has
to run:

- Netty event loops;
- chunk generation/loading workers;
- region-file I/O;
- ZGC/G1 workers;
- Velocity and possibly MariaDB/Redis;
- plugin async executors;
- the kernel/NIC interrupt work.

For a real production tune, record peak-hour scheduler utilisation and worst
region MSPT first. Increase region threads only while there are simultaneously
runnable independent regions **and** host CPU still has headroom. More region
threads do not split one overloaded region into smaller work.

## Network

Linux/Netty already uses the native epoll transport when the server/runtime has
it available; do not replace it with a custom JNI networking stack merely for
this fork. The fork-level networking changes are focused on avoiding needless
packet work, correcting rejected client prediction and optionally removing a
blocking protocol-state transition.

`networking.async-switch-connection-state` remains disabled by default until it
has passed join/reconnect/proxy soak testing. Enable it only after validating
Velocity, ViaVersion, Geyser/Floodgate and packet-inspection plugins together.

## Disk and region files

For large survival worlds, CPU-saving compression such as LZ4 can be valuable
when supported by the active Paper/Folia configuration, at the cost of larger
region files. Do not change an existing world compression format without a
backup and a tested conversion path.

Avoid global `ionice -c1` / realtime I/O priorities. They can starve logs,
databases and other server processes. If chunk generation is the bottleneck,
profile chunk workers and storage latency rather than hiding it behind extreme
kernel priorities.

## What to monitor

At peak load keep these separate:

1. worst-region and p95 region MSPT;
2. region scheduler runnable/idle time;
3. host per-core CPU and run queue;
4. chunk-generation and region-file I/O latency;
5. GC allocation rate, concurrent cycle time and pauses;
6. Netty/event-loop CPU and packet backlog;
7. player/entity counts per hot region.

A global TPS number cannot tell which of those is saturated on Folia.
