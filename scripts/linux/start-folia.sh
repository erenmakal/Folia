#!/usr/bin/env bash
set -Eeuo pipefail

# Production launcher for Folia Performance Pack on Linux.
# Override values through environment variables instead of editing the script.
# Example:
#   HEAP=30G JAR=folia-server.jar REGION_THREADS=12 ./scripts/linux/start-folia.sh

JAR="${JAR:-folia-server.jar}"
HEAP="${HEAP:-30G}"
JAVA_BIN="${JAVA_BIN:-java}"
GC="${GC:-zgc}"
EXTRA_JAVA_FLAGS="${EXTRA_JAVA_FLAGS:-}"
SERVER_FLAGS="${SERVER_FLAGS:---nogui}"

if [[ ! -f "$JAR" ]]; then
  echo "ERROR: server jar not found: $JAR" >&2
  exit 1
fi

if ! command -v "$JAVA_BIN" >/dev/null 2>&1; then
  echo "ERROR: Java executable not found: $JAVA_BIN" >&2
  exit 1
fi

java_version="$($JAVA_BIN -version 2>&1 | awk -F'[\".]' '/version/ {print $2; exit}')"
if [[ -z "${java_version:-}" || "$java_version" -lt 25 ]]; then
  echo "WARNING: Java 25+ is recommended for this 26.2 fork; detected: ${java_version:-unknown}" >&2
fi

common_flags=(
  "-Xms${HEAP}"
  "-Xmx${HEAP}"
  "-XX:+AlwaysPreTouch"
  "-XX:+DisableExplicitGC"
  "-Dfile.encoding=UTF-8"
  "--add-modules=jdk.incubator.vector"
  "-Xlog:gc*:file=logs/gc.log:time,uptime,level,tags:filecount=5,filesize=20M"
)

gc_flags=()
case "${GC,,}" in
  zgc)
    # On current JDKs ZGC is generational; let the JVM size concurrent workers
    # from the real CPU topology instead of hard-coding GC thread counts.
    gc_flags+=("-XX:+UseZGC")
    ;;
  g1|g1gc)
    gc_flags+=(
      "-XX:+UseG1GC"
      "-XX:MaxGCPauseMillis=${G1_PAUSE_TARGET_MS:-150}"
      "-XX:+ParallelRefProcEnabled"
    )
    ;;
  *)
    echo "ERROR: GC must be 'zgc' or 'g1' (got '$GC')" >&2
    exit 1
    ;;
esac

mkdir -p logs

# shellcheck disable=SC2206
extra_flags=( $EXTRA_JAVA_FLAGS )
# shellcheck disable=SC2206
server_flags=( $SERVER_FLAGS )

exec "$JAVA_BIN" \
  "${common_flags[@]}" \
  "${gc_flags[@]}" \
  "${extra_flags[@]}" \
  -jar "$JAR" \
  "${server_flags[@]}"
