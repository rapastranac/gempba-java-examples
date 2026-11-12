#!/bin/bash
# Runs synthetic_void across nodes: 6 nodes x 2 ranks x 2 threads, about 9 minutes at depth 26 on Rorqual. Set your
# account, build with cluster/build.sh, then submit from this repository's root:
#
#   sbatch cluster/synthetic_void.sbatch
#
#SBATCH --job-name=gempba-java
#SBATCH --output=report/%x-%j.out
#SBATCH --error=errors/%x-%j.err
#SBATCH --account=def-someuser
#SBATCH --nodes=6
#SBATCH --ntasks-per-node=2
#SBATCH --cpus-per-task=2
#SBATCH --mem-per-cpu=2048M
#SBATCH --time=0-00:15:00

cd "$SLURM_SUBMIT_DIR" || exit
source cluster/modules.sh

# Each rank logs mca_pml_ucx_init when Open MPI runs over UCX.
export OMPI_MCA_pml_ucx_verbose=1

cp="$SLURM_SUBMIT_DIR/multiprocessing/target/classes:$(cat "$SLURM_SUBMIT_DIR/cluster/classpath.txt")"

echo "Starting run at: $(date)"
srun java -Xmx3g -Dgempba.depth=26 -Dgempba.threads="$SLURM_CPUS_PER_TASK" -Dgempba.telemetry.port=9000 \
    -XX:-OmitStackTraceInFastThrow --enable-native-access=ALL-UNNAMED -cp "$cp" io.gempba.mp.synthetic_void.Main
echo "Finishing run at: $(date)"
