# Changelog

All notable changes to ChronosX are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-08-23

### Added

- Dynamic per-package libxposed API 102 module entry and remote-preference rule contract.
- Kotlin/Compose manager with Dashboard, Installed Applications, App Time Rules, Debug Logs, and Settings screens.
- Room-backed local storage for rules and manager diagnostics.
- `REAL_TIME`, `OFFSET`, and `FIXED_TIME` rule modes with rule preview.
- Guarded hooks for requested Java, `java.time`, and Android clock APIs.
- Package safety policy, framework compatibility checks, and scoped process diagnostics.
- JVM tests for time arithmetic, fixed monotonic behavior, configuration codec, and package filtering.
