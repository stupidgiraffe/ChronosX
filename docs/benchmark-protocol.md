# Benchmark protocol

ChronosX does not require a companion application. An authorized mock or customer-owned target can
optionally return an explicit self-reported assertion to the installed ChronosX Manager.

## Launch metadata

When ChronosX launches a scenario target, its normal launcher intent carries these extras:

| Extra | Meaning |
| --- | --- |
| `dev.chronosx.extra.RUN_ID` | Unique Lab run identifier. |
| `dev.chronosx.extra.RUN_TOKEN` | Opaque per-run correlation token that the target must echo in its result. |
| `dev.chronosx.extra.SCENARIO_ID` | Selected built-in scenario identifier. |
| `dev.chronosx.extra.RULE_REVISION` | Immutable ChronosX rule revision. |
| `dev.chronosx.extra.FIXTURE_ID` | Optional controlled loopback/mock fixture identifier. |
| `dev.chronosx.extra.FIXTURE_RESPONSE_KIND` | Optional explicit response kind selected by the scenario. |
| `dev.chronosx.extra.FIXTURE_DELAY_MILLIS` | Optional fixture delay selected by the scenario. |

`ScenarioLaunchConfiguration.fromIntent(intent)` in `lab-sdk` parses these values for an
authorized mock target. Its `loopbackFixtureUrl()` helper points only to the local ChronosX Lab
fixture server; it is not a proxy or a production-traffic interceptor.

## Result broadcast

Send an explicit broadcast with action `dev.chronosx.action.BENCHMARK_RESULT` to package
`dev.chronosx`. Supply the run ID, source package, pass/fail result, and any observed values.

| Extra | Required | Meaning |
| --- | --- | --- |
| `dev.chronosx.extra.RUN_ID` | Yes | Run identifier supplied at launch. |
| `dev.chronosx.extra.RUN_TOKEN` | Yes | Per-run correlation token supplied at launch. |
| `dev.chronosx.extra.SOURCE_PACKAGE` | Yes | Package name of the reporting test app. |
| `dev.chronosx.extra.RULE_REVISION` | Yes | Rule revision the app observed. |
| `dev.chronosx.extra.PASSED` | Yes | Scenario assertion result. |
| `dev.chronosx.extra.PROCESS_NAME` | No | Reporting process. |
| `dev.chronosx.extra.OBSERVED_WALL_EPOCH_MILLIS` | No | Observed wall-clock epoch. |
| `dev.chronosx.extra.OBSERVED_ZONE_ID` | No | Observed default zone. |
| `dev.chronosx.extra.OBSERVED_SURFACES` | No | Comma-separated tested clock surfaces. |
| `dev.chronosx.extra.DATE_CAPABILITY_MATRIX` | No | Versioned `DateCapabilityMatrixCodec` payload sampled by the authorized target. |
| `dev.chronosx.extra.MESSAGE` | No | Concise assertion note. |

Results are deliberately labelled self-reported evidence. ChronosX can prove local rule delivery and
record a cooperative benchmark observation; it cannot infer arbitrary app behavior from injection
alone. The manager accepts an observation only when its declared source package matches the selected
run target, and marks a rule-revision mismatch as a failed observation.

## Date capability matrix

`DateCapabilityProbe.capture()` in `lab-sdk` samples the supported date paths directly from the
authorized target process. Each observation contains the API surface, state, epoch when applicable,
local date, zone, and any failure detail. Include the resulting matrix in `BenchmarkResult`:

```kotlin
val launch = ScenarioLaunchConfiguration.fromIntent(intent) ?: return
val matrix = DateCapabilityProbe.capture()

BenchmarkReporter(this).report(
BenchmarkResult(
    runId = launch.runId,
    runToken = launch.runToken,
        ruleRevision = launch.ruleRevision,
        passed = matrix.observations.all { it.state.name == "OBSERVED" },
        observedZoneId = matrix.defaultZoneId,
        dateCapabilityMatrix = matrix,
        message = "Authorized date-surface benchmark.",
    ),
)
```

The manager stores this payload with the run and exports its exact evidence. It checks both the
declared source package and the one-time run correlation token before accepting an observation; the
token prevents accidental cross-run broadcasts but is not an authentication or attestation scheme.
ChronosX does not guess that an unrelated target used one of these paths.
