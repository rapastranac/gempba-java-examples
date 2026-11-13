#!/usr/bin/env bash
#
# Launches one of the multiprocessing examples under N MPI ranks (mpirun).
#
# Usage:
#   ./run.sh synthetic_void                 # default: 2 ranks
#   ./run.sh synthetic_void -n 4            # 1 center + 3 workers
#   ./run.sh synthetic_void -port 9001      # telemetry on port 9001 instead of 9000
#
# The positional argument is the sub-package name under io.gempba.mp —
# e.g. `synthetic_void` → `io.gempba.mp.synthetic_void.Main`.
#
# Linux/macOS counterpart of run.ps1.
#

set -euo pipefail

if [[ $# -lt 1 ]]; then
    echo "usage: $0 <example> [-n <ranks>] [-port <telemetry port>]" >&2
    exit 1
fi

example=$1
shift

n=2
port=9000
while [[ $# -gt 0 ]]; do
    case "$1" in
        -n) n=$2; shift 2 ;;
        -port) port=$2; shift 2 ;;
        *)  echo "unknown arg: $1" >&2; exit 1 ;;
    esac
done

module=$(cd "$(dirname "$0")" && pwd)
cpFile="$module/target/classpath.txt"
classes="$module/target/classes"
mainClass="io.gempba.mp.$example.Main"

(
    cd "$module"
    # includeScope=runtime drops test-scope deps from the resolved classpath.
    mvn -q dependency:build-classpath \
        "-Dmdep.outputFile=$cpFile" \
        -DincludeScope=runtime
)

deps=$(cat "$cpFile")
cp="$classes:$deps"  # ':' classpath separator on Linux/macOS

mpirun -n "$n" java \
    "-Dgempba.telemetry.port=$port" \
    -XX:-OmitStackTraceInFastThrow \
    --enable-native-access=ALL-UNNAMED \
    -cp "$cp" \
    "$mainClass"
