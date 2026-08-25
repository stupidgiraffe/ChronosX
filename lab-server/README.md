# ChronosX Lab Server

`lab-server` is a loopback-only fixture service for mock applications, staging builds, and other
authorized test targets. It is not a traffic proxy and does not intercept unrelated applications.

```bash
./gradlew :lab-server:run
```

It listens at `http://127.0.0.1:8787` by default.

| Endpoint | Purpose |
| --- | --- |
| `GET /health` | Verify that the controlled fixture service is running. |
| `GET /v1/time-policy?fixture=valid` | Returns a valid policy fixture. |
| `GET /v1/time-policy?fixture=expired` | Returns an expiry/re-authentication fixture. |
| `GET /v1/time-policy?fixture=stale` | Returns a stale-cache refresh fixture. |
| `GET /v1/time-policy?fixture=denied` | Returns a deny fixture. |
| `GET /v1/time-policy?fixture=retryable` | Returns a retryable failure fixture. |
| `GET /v1/time-policy?fixture=malformed` | Returns deliberately malformed JSON for parser testing. |

Append `delayMillis=<0..30000>` to simulate a bounded response delay. The server binds only to
loopback; use a customer-owned development topology such as `adb reverse` when a physical test
device needs to reach it.

Custom scenarios may pass an explicit `kind=VALID|EXPIRED|STALE|DENIED|RETRYABLE_FAILURE|MALFORMED_CONTRACT`
alongside any fixture ID. That lets a mock target label a fixture run however it needs while still
selecting a deterministic owned response. The server remains loopback-only and does not alter
production traffic.
