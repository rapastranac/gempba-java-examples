#
# Launches one of the multiprocessing examples under N MPI ranks (mpiexec).
#
# Usage:
#   .\run.ps1 synthetic_void                # default: 2 ranks
#   .\run.ps1 synthetic_void -n 4           # 1 center + 3 workers
#
# The positional argument is the sub-package name under io.gempba.mp —
# e.g. `synthetic_void` → `io.gempba.mp.synthetic_void.Main`.
#
# Windows counterpart of run.sh.
#

param(
    [Parameter(Mandatory = $true, Position = 0)] [string] $example,
    [int] $n = 2
)

$ErrorActionPreference = 'Stop'

$module    = $PSScriptRoot
$cpFile    = Join-Path $module 'target\classpath.txt'
$classes   = Join-Path $module 'target\classes'
$mainClass = "io.gempba.mp.$example.Main"

Push-Location $module
try {
    # includeScope=runtime drops test-scope deps from the resolved classpath.
    mvn -q dependency:build-classpath `
        "-Dmdep.outputFile=$cpFile" `
        '-DincludeScope=runtime'
} finally {
    Pop-Location
}

$deps = (Get-Content $cpFile -Raw).Trim()
$cp   = "$classes;$deps"  # ';' classpath separator on Windows

mpiexec -n $n java `
    -XX:-OmitStackTraceInFastThrow `
    --enable-native-access=ALL-UNNAMED `
    -cp $cp `
    $mainClass
