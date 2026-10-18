# Runtime memory, startup diagnostics and precompute caches

Use JDK 17 with the project's Gradle wrapper. These commands do not choose a heap
based on total physical RAM: budget for the server, JVM native memory and other
processes, and respect the competition's memory limit.

## Launch settings

`launch` retains the existing 20G heap default. Override it with a Gradle property
or environment variable (the property wins):

```powershell
.\gradlew.bat launch -PagentMaxHeap=8G --args="adf.impl.DefaultLoader -all"
```

On Bash, the equivalent is `./gradlew launch -PagentMaxHeap=8G --args="adf.impl.DefaultLoader -all"`.
`HFUT_AGENT_MAX_HEAP=8G` can also be used with `launch.sh`. 8G is an example,
not a recommendation for a particular competition machine.

The Gradle task logs its requested heap. `Main` logs the agent JVM's actual
maximum, committed and used heap in MiB. The latter values are a startup snapshot,
not a measurement of peak total process memory.

## Startup milestones

Per-agent logs distinguish `CONNECTED`, `INITIALIZED`, `FIRST_ACTION_SUBMITTED`
and `INITIALIZATION_FAILED`, with counts per Java agent type. Initialization
failure is reported to the connector after the protocol handshake finishes, so
the framework connection latch is released; the failed agent is shut down and
cannot run its decision loop. Submitted means handed to the
transport, not confirmed as executed by the server. Center agents may communicate
without producing a platoon action, so their active-action count can remain zero.
When requesting all available agents (`-1`), a connection rejection may indicate
exhaustion or another failure: consult both client and server logs. An exact
requested count is needed to detect an automatic shortfall.

## Precompute cache policy

To skip cache reads without deleting files:

```powershell
.\gradlew.bat launch -PprecomputeCache=false --args="adf.impl.DefaultLoader -all"
```

Alternatively use `HFUT_PRECOMPUTE_CACHE=false`, or `-Dadf.precompute.read=false`
when directly launching the agent Java process. This is separate from `-pre`:
`-pre 0` still permits a valid cache to be restored; `-pre 1` explicitly generates
new data. Confirm the logged `NON_PRECOMPUTE`, `PRECOMPUTED` or
`PRECOMPUTATION_PHASE` mode when comparing runs.

Old cache versions are deliberately rejected. The new format validates structure
and a map/initial-agent-position fingerprint. HFUT cache restore also checks tool
completion markers; partition restore validates required values before applying
them. Invalid files take the normal preparation path when rejected at load time.
A failure during restore fails initialization; it does not run preparation on
already partially mutated tools. Disable reads or regenerate after fixing the
reported error. This is fail-fast behavior, not automatic recovery from OOM.

New generations start in memory, and only successful generations are published
through a temporary file and replacement move (atomic when supported). Failed
computations retain the previous file. Do not regenerate caches while another
match is running against the same cache directory. Keep caches separate between
maps, versions and configurations; the fingerprint does not encode every possible
strategy parameter or server setting.

## Verification

Run `./gradlew test` (or `.\gradlew.bat test`) on JDK 17. Regression tests cover
cache round trips, interrupted/failed generations, invalid files, wrong maps,
read bypass, startup milestone counts and drawing without another path search.
The test task's obsolete `UseCGroupMemoryLimitForHeap` option was removed because
it prevents Java 17 test JVM startup; the heap-dump-on-OOM option is retained.

A real server is still required to validate connection deadlines, initialization
on the competition map, total memory, GC pauses and scores. Compare identical
maps/agent counts with cache reads enabled and disabled. Record connected and
initialized counts separately, first-action times, peak process memory, decision
timeouts and completed transports before changing the heap or strategy again.

### When the original framework artifacts cannot be resolved

During this change, the configured repositories could not supply
`com.github.roborescue:rcrs-server:v2.0` or `adf-core-java:v4.0`. Verification used
the official tag archives from:

- https://codeload.github.com/roborescue/rcrs-server/zip/refs/tags/v2.0
- https://codeload.github.com/roborescue/adf-core-java/zip/refs/tags/v4.0

Extract them under `build/tools/upstream/`, retaining the archive directory names
`rcrs-server-2.0` and `adf-core-java-4.0`, then run on JDK 17:

```powershell
.\gradlew.bat test --no-daemon --init-script tools/verify-upstream.init.gradle
```

The optional init script compiles those tagged sources alongside this repository,
excluding upstream ADF files overridden locally. It supplies their third-party
dependencies and a JDK-17-compatible Lombok processor. The obsolete google-collect
dependency is omitted; Guava already supplies the collection APIs used here. This
validates source compilation and tests, not the unavailable published binary
artifacts. The regular build still needs those framework artifacts to be restored
to an accessible repository. Do not use this validation classpath as a competition
deployment without a separate server integration check.
