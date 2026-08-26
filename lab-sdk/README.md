# ChronosX Lab SDK

The optional Android library helps a customer-owned or mock target report an explicit Lab scenario
assertion back to ChronosX Manager. It is not required for time virtualization itself.

Include `:lab-sdk` in a test build, parse launch metadata with `ScenarioLaunchConfiguration`, then
report a `BenchmarkResult` through `BenchmarkReporter`. `DateCapabilityProbe.capture()` produces a
concrete matrix of date, zone, legacy Calendar, chronology, Android ICU, and diagnostic adapter
observations for an authorized target. Date-valued observations include a virtual reference epoch,
local date, year, month, day, weekday, and zone so ChronosX can flag a surface that diverges from
the target's sampled wall clock. Echo the launch configuration's `runToken` in the result so the manager can
associate evidence with the intended run. See [../docs/benchmark-protocol.md](../docs/benchmark-protocol.md)
for the wire contract and evidence semantics.
