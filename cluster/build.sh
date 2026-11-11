#!/bin/bash
# Build gempba-mpi against this cluster's MPI, then the examples against it. Expects gempba checked out next to this
# repository. Run it on a login node:
#
#   bash cluster/build.sh
#
# Writes cluster/classpath.txt for cluster/synthetic_void.sbatch.

set -euo pipefail

examples=$(cd "$(dirname "$0")/.." && pwd)
gempba=$(cd "$examples/../gempba" && pwd)

# shellcheck source=modules.sh
source "$examples/cluster/modules.sh"
echo "gempba: $gempba on $(git -C "$gempba" branch --show-current)"
bash "$gempba/scripts/build_jar_host_linux.sh" MP

# shellcheck source=/dev/null
source "$gempba/scripts/_java_artifact.sh"
version=$(gempba_java_version "$gempba")

cd "$examples"
mvn -B package dependency:build-classpath -DskipTests -Dgempba.version="$version" -pl multiprocessing -am \
    -Dmdep.outputFile="$examples/cluster/classpath.txt" -DincludeScope=runtime
mkdir -p "$examples/report" "$examples/errors"
echo "Built against gempba $version; submit with: sbatch cluster/synthetic_void.sbatch"
