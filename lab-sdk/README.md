# ChronosX Lab SDK

The optional Android library helps a customer-owned or mock target report an explicit Lab scenario
assertion back to ChronosX Manager. It is not required for time virtualization itself.

Include `:lab-sdk` in a test build, read the launch extras supplied by ChronosX, then report a
`BenchmarkResult` through `BenchmarkReporter`. See [../docs/benchmark-protocol.md](../docs/benchmark-protocol.md)
for the wire contract and evidence semantics.
