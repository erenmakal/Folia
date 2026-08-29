#!/usr/bin/env bash
set -Eeuo pipefail

# Conservative host tuning for a dedicated Folia machine.
# Supports systemd-based distributions such as AlmaLinux/Rocky/RHEL/Fedora,
# Debian and Ubuntu. It deliberately avoids BBR, IRQ pinning, NUMA pinning,
# huge-page policy changes and scheduler sysctls because those require host-
# specific benchmarking and can regress latency on some systems.

if [[ ${EUID:-$(id -u)} -ne 0 ]]; then
  echo "Run as root: sudo $0" >&2
  exit 1
fi

SYSCTL_FILE="/etc/sysctl.d/99-folia-performance.conf"
LIMITS_FILE="/etc/security/limits.d/99-folia-performance.conf"

cat >"$SYSCTL_FILE" <<'EOF'
# Folia Performance Pack - conservative dedicated-server tuning

# Keep swap as an emergency mechanism, but strongly prefer RAM/page cache.
vm.swappiness = 10

# Increase accept/SYN queues for large join bursts. This does not increase
# Minecraft's own player limit and is intentionally far below absurd values.
net.core.somaxconn = 4096
net.ipv4.tcp_max_syn_backlog = 8192

# Keep SYN cookies available under queue pressure.
net.ipv4.tcp_syncookies = 1

# Plenty of descriptors for player sockets, region files, plugins and logs.
fs.file-max = 2097152
EOF

cat >"$LIMITS_FILE" <<'EOF'
# Folia Performance Pack
* soft nofile 1048576
* hard nofile 1048576
EOF

sysctl --system >/dev/null

echo "Installed $SYSCTL_FILE"
echo "Installed $LIMITS_FILE"

# Prefer the performance governor on dedicated hosts when cpufreq exposes it.
# This only changes CPUs that advertise a 'performance' governor.
changed=0
for governor in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do
  [[ -e "$governor" ]] || continue
  available="${governor%/scaling_governor}/scaling_available_governors"
  if [[ -r "$available" ]] && grep -qw performance "$available"; then
    current="$(cat "$governor")"
    if [[ "$current" != performance ]]; then
      echo performance >"$governor"
      changed=$((changed + 1))
    fi
  fi
done

if (( changed > 0 )); then
  echo "Set performance CPU governor on $changed logical CPUs."
else
  echo "CPU governor unchanged (performance governor unavailable or already active)."
fi

cat <<'EOF'

Not changed automatically:
- TCP congestion control / BBR
- NIC IRQ/RPS/XPS affinity
- CPU/CCD pinning
- NUMA policy
- Transparent/explicit huge pages
- I/O scheduler

Those are hardware/workload-specific. Benchmark them separately rather than
turning them into global defaults for a 500-player server.
EOF
