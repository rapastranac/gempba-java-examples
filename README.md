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

**One-time setup.** GitHub Packages requires authentication even for public artifacts. Create a GitHub Personal Access Token with the **`read:packages`** scope and add it to `~/.m2/settings.xml`:

```xml
<settings>
    <servers>
        <server>
            <id>github-gempba</id>
            <username>YOUR_GITHUB_USERNAME</username>
            <password>YOUR_PAT</password>
        </server>
    </servers>
</settings>
```

The `<id>` must match the repository id declared in the parent pom (`github-gempba`).

Then:

```sh
mvn install
```

**Pointing at a different owner.** The pom resolves `io.gempba:gempba` from `rapastranac/gempba` by default. To resolve from a fork or org mirror, override the owner property — no pom edit needed:

```sh
mvn install -Dgempba.github.owner=my-org
```

Or set `gempba.github.owner` once in a profile in `~/.m2/settings.xml` to make it permanent for your checkout.

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

## License

MIT — see [LICENSE](LICENSE).
