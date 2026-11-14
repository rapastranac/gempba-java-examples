# The modules cluster/build.sh and cluster/synthetic_void.sbatch load: a JDK 25, Maven, CMake, a C++23 compiler and
# MPI. This is the Digital Research Alliance of Canada clusters' (Fir, Nibi, Rorqual), where StdEnv/2026 brings GCC and
# Open MPI over UCX; change it for another cluster.

module --force purge
module load StdEnv/2026 java/25.0.5 maven/3.9.10 cmake
module list
