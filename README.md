# GemPBA Java examples

Consumer-side examples for the Java bindings of [GemPBA](https://github.com/rapastranac/gempba) — the Java mirror of the `gempba-examples` C++ repo. Each example exercises the public API exactly as a real downstream project would.

## Modules

| Module            | What it contains                                                            |
|-------------------|-----------------------------------------------------------------------------|
| `commons`         | Variant-agnostic helpers (busy-wait, jitter, deterministic noise).          |
| `sequential`      | Plain-Java reference oracles. No GemPBA dependency.                         |
| `mutithreading`   | GemPBA MT examples. Self-contained — no external runtime needed.            |
| `multiprocessing` | GemPBA MP examples. Require an MPI runtime (MS-MPI / OpenMPI / MPICH).      |

## Build

Requires **Java 25** and **Maven 3.9+**.

The examples depend on `io.github.rapastranac:gempba` and `gempba-mpi` from Maven Central, so there's nothing to configure:

```sh
mvn install
```

**Against a local gempba checkout.** gempba's `scripts/build_all_jars_linux.sh` (or `_macos.sh`, `_windows.sh`) builds both JARs and installs them into `~/.m2` under the same coordinates, which is what this repo's CI does. Pass `-Dgempba.version=<version>` if it differs from the one in `pom.xml`.

## Run an example

The simplest path: open the project in **IntelliJ IDEA** (File → Open → pick the root `pom.xml`), let Maven resolve, then run any `Main` class directly with the green ▶ gutter icon next to `main(...)`. IntelliJ wires up the classpath and JVM args automatically.

**Multiprocessing is the one exception** — MP examples must be launched under `mpiexec`/`mpirun` rather than plain `java`, so the gutter icon won't do. The repo ships platform-matched launchers:

- Windows: `multiprocessing/run.ps1`
- Linux / macOS: `multiprocessing/run.sh`

```sh
./multiprocessing/run.ps1 synthetic_void -n 4   # Windows
./multiprocessing/run.sh  synthetic_void -n 4   # Linux / macOS
```

For one-click runs from IntelliJ, **right-click the script in the Project tree → Modify Run Configuration…**. IntelliJ creates the run config for you, pointing at the right interpreter; the only thing left is to fill in **Script options** with `<example> -n <ranks>`, where `<example>` is the sub-package name under `io.gempba.mp` (e.g. `synthetic_void`, not `synthetic`).

If you prefer the command line for MT / sequential:

```sh
mvn -pl sequential exec:java -Dexec.mainClass=io.gempba.seq.synthetic_void.Main
mvn -pl mutithreading exec:java -Dexec.mainClass=io.gempba.mt.synthetic_void.Main
```

## Example layout

Examples live as sibling sub-packages under `io.gempba.<module>`, one per variant:

```
mutithreading/src/main/java/io/gempba/mt/
  synthetic_void/        # synthetic-tree benchmark, framework Score path
  synthetic_leafsum/     # synthetic-tree benchmark, typed Node#getResult() path
```

Sub-package names follow `<problem>_<variant>`, lowercase with underscores — mirrors the filename convention in the C++ `gempba-examples` repo. Each sub-package is one self-contained `Main` plus its `Benchmark` helper. Adding a new example means a new sub-package, never a new module.

## What's here today

Only the **synthetic-tree benchmark** — a generated binary tree with simulated per-node CPU cost, used to measure framework overhead end-to-end.

## Stress tests

The test sources hold two synthetic stress tests that need no input files. Each one walks a full binary tree and checks two answers that don't depend on how the work was split: the number of leaves (`2^depth`) and the best leaf (`2^depth - 1`). A run fails when either answer is wrong, or when it doesn't finish within 60 s.

- `io.gempba.mt.stress.LeafVisitor`: one process on the quasi-horizontal load balancer, on 2, 8 and 16 threads.
- `io.gempba.mp.stress.LeafVisitor`: MPI ranks with 2 threads each, on the semi-centralized scheduler and the quasi-horizontal load balancer, on 2, 4 and 8 ranks. The Java binding has no MPI reduce, so each rank prints its leaf count and the test adds them up.

They oversubscribe small machines on purpose, so that hand-offs race. The hybrid runs need an MPI launcher on `PATH`: Open MPI's `mpirun` on Linux and macOS, where `--oversubscribe` is added for you, and MS-MPI's `mpiexec` on Windows. Override it with `-Dgempba.mpi.launcher=<launcher>` and `-Dgempba.mpi.args=<args>`.

```sh
mvn verify -Pstress
```

Add `-Dgempba.stress.repeat=20` for a longer soak. Each run's output lands in `<module>/target/stress-logs/`.

## License

MIT — see [LICENSE](LICENSE).
