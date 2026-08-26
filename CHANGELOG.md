# Changelog

All notable changes to ChronosX are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.2.0] - Unreleased

### Added

- Date Capability Matrix protocol and `DateCapabilityProbe` for authorized mock/customer targets. The matrix captures observed epoch, local date, year, month, day, weekday, zone, and error state for supported legacy, `java.time`, chronology, Android ICU, and diagnostic adapter paths.
- Manager-side date-matrix divergence analysis, so reports and the Lab UI identify a date source that disagrees with the target's virtual wall-clock reference instead of merely listing raw observations.
- Process-scoped runtime telemetry transported through libxposed remote preferences: rule loaded, hook installation, observed surfaces, and failures are visible in the manager.
- Date-focused hook surfaces for `GregorianCalendar`, Android ICU Calendar/TimeZone, `ZoneId` overloads of Java time factories, and ISO/Japanese/Hijrah/Minguo/Thai chronology factories.
- Best-effort ART deoptimization for documented wall-clock and timezone hooks.
- Custom Scenario Builder with manual time, fixed instant, timezone, process, monotonic, controlled-fixture, and assertion settings.
- Immutable scenario snapshots and date-matrix evidence in JSON/Markdown exports.
- Launch metadata and Lab SDK parsing for controlled loopback fixture choice and delay.
- Per-run correlation tokens so an authorized benchmark result cannot be accidentally attached to
  another Lab run.

### Changed

- Dashboard, applications, debug, and settings now distinguish saved/scope/restart states from installed, observed, stale, and failed runtime evidence.
- Custom Lab fixtures can explicitly select an owned Lab-server response kind instead of relying on a preset fixture name.

## [1.1.0] - 2026-08-25

### Added

- ChronosX Lab scenario runner with durable evidence records and Markdown/JSON report sharing.
- Portable versioned temporal profile import/export.
- IANA default-zone virtualization policy, process policy, rule revisions, and explicit monotonic-clock policy.
- Expanded public clock capability registry and runtime coverage for default zone, `OffsetDateTime`, `ZonedDateTime`, system `Clock` factories, and nanosecond Android monotonic surfaces.
- Long-press application actions, Lab scenario UI, date/time pickers, timezone picker, and process/monotonic controls.
- Optional benchmark result broadcast protocol for mock and customer-owned test targets.
- Loopback-only `lab-server` fixture service and JVM tests.

### Changed

- Fixed wall time no longer seeds monotonic clocks with Unix-epoch values; physical interval clocks are preserved by default.
- Remote rule payload now carries schema version, immutable revision, zone, process, and monotonic policies.

## [1.0.0] - 2026-08-23

### Added

- Dynamic per-package libxposed API 102 module entry and remote-preference rule contract.
- Kotlin/Compose manager with Dashboard, Installed Applications, App Time Rules, Debug Logs, and Settings screens.
- Room-backed local storage for rules and manager diagnostics.
- `REAL_TIME`, `OFFSET`, and `FIXED_TIME` rule modes with rule preview.
- Guarded hooks for requested Java, `java.time`, and Android clock APIs.
- Package safety policy, framework compatibility checks, and scoped process diagnostics.
- JVM tests for time arithmetic, fixed monotonic behavior, configuration codec, and package filtering.
