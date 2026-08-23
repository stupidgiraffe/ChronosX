# Benchmark protocol

ChronosX does not require a companion application. An authorized mock or customer-owned target can
optionally return an explicit self-reported assertion to the installed ChronosX Manager.

## Launch metadata

When ChronosX launches a scenario target, its normal launcher intent carries these extras:

| Extra | Meaning |
| --- | --- |
| `dev.chronosx.extra.RUN_ID` | Unique Lab run identifier. |
| `dev.chronosx.extra.SCENARIO_ID` | Selected built-in scenario identifier. |
| `dev.chronosx.extra.RULE_REVISION` | Immutable ChronosX rule revision. |

## Result broadcast

Send an explicit broadcast with action `dev.chronosx.action.BENCHMARK_RESULT` to package
`dev.chronosx`. Supply the run ID, source package, pass/fail result, and any observed values.

| Extra | Required | Meaning |
| --- | --- | --- |
| `dev.chronosx.extra.RUN_ID` | Yes | Run identifier supplied at launch. |
| `dev.chronosx.extra.SOURCE_PACKAGE` | Yes | Package name of the reporting test app. |
| `dev.chronosx.extra.RULE_REVISION` | Yes | Rule revision the app observed. |
| `dev.chronosx.extra.PASSED` | Yes | Scenario assertion result. |
| `dev.chronosx.extra.PROCESS_NAME` | No | Reporting process. |
| `dev.chronosx.extra.OBSERVED_WALL_EPOCH_MILLIS` | No | Observed wall-clock epoch. |
| `dev.chronosx.extra.OBSERVED_ZONE_ID` | No | Observed default zone. |
| `dev.chronosx.extra.OBSERVED_SURFACES` | No | Comma-separated tested clock surfaces. |
| `dev.chronosx.extra.MESSAGE` | No | Concise assertion note. |

Results are deliberately labelled self-reported evidence. ChronosX can prove local rule delivery and
record a cooperative benchmark observation; it cannot infer arbitrary app behavior from injection
alone.
