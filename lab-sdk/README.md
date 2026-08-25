# ChronosX Lab SDK

The optional Android library helps a customer-owned or mock target report an explicit Lab scenario
assertion back to ChronosX Manager. It is not required for time virtualization itself.

Include `:lab-sdk` in a test build, parse launch metadata with `ScenarioLaunchConfiguration`, then
report a `BenchmarkResult` through `BenchmarkReporter`. `DateCapabilityProbe.capture()` produces a
concrete matrix of date, zone, legacy Calendar, chronology, and Android ICU observations for an
authorized target. Echo the launch configuration's `runToken` in the result so the manager can
associate evidence with the intended run. See [../docs/benchmark-protocol.md](../docs/benchmark-protocol.md)
for the wire contract and evidence semantics.
