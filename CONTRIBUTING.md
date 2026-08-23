# Contributing to ChronosX

Thanks for helping keep ChronosX useful for Android time-behavior research and application testing.

## Before you start

- Work only with applications and devices you are authorized to test.
- Keep changes narrowly scoped and avoid adding global hooks or system-package scope.
- Do not add legacy `de.robv.android.xposed` APIs; ChronosX targets libxposed API 102.
- Read the current [architecture notes](docs/architecture.md) and [security policy](SECURITY.md).

## Development setup

1. Use Android Studio with JDK 17.
2. Install the Android SDK platform and build tools declared in `app/build.gradle.kts`.
3. Run the verification baseline before making a change:

   ```bash
   ./gradlew test
   ./gradlew assembleDebug
   ```

4. On-device hook verification should use a disposable test app and a supported API 102 framework. Never use a production account as a test fixture.

## Change guidelines

- Keep core arithmetic pure and add JVM tests for every new mode or overflow edge case.
- Add hook surfaces through `HookRegistry`; hooks must use `ExceptionMode.PROTECTIVE` and preserve the original result on transformation failure.
- Preserve the construction/bypass model whenever a new API delegates to another hooked clock.
- Treat remote preference values as untrusted input and fail closed to real time.
- Update README, architecture docs, and `CHANGELOG.md` when behavior or compatibility changes.

## Pull requests

Use a focused title and include:

- the problem being solved;
- affected hook surfaces and Android/API assumptions;
- test output from `./gradlew test` and `./gradlew assembleDebug`;
- on-device validation notes when changing module lifecycle or scope behavior;
- documentation updates where applicable.

Keep commits reviewable. Do not commit APKs, keystores, local SDK paths, framework logs containing private data, or generated build output.

## Reporting bugs

Use the bug-report issue form and include the framework name/version, API level, target package (redact it if needed), configured rule, expected time surface, actual result, and relevant sanitized logs. Do not attach credentials or private application data.
