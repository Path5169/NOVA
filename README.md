# NOVA — Phase 3

A futuristic, offline-first Android utility app. Phase 1 shipped **Home**, **Device**,
**Lab**, and **Tools**. Phase 2 added **Network** diagnostics, on-device **Vision**
(QR/barcode scanning + OCR), three new Lab instruments, and a Device Camera test.
Phase 3 ships **Privacy** (the last planned module), a more advanced Deep Scan report,
restrained haptics, and better animation throughout — with no fake data anywhere.

## What's new in Phase 3

### Privacy (new)
A permission and app-privacy dashboard built entirely from what Android legitimately
exposes to a normal app, and nothing more:
- **NOVA's own permissions** — exactly what NOVA has asked for and whether it's been
  granted, in plain language.
- **Apps with notification access** and **accessibility services enabled** — genuine,
  system-wide, per-app lists read from `Settings.Secure`. No special permission needed;
  these settings are designed to be readable.
- **Apps with sensitive permissions** — a scan of installed apps' granted dangerous
  permissions (camera, microphone, location, contacts, SMS, calendar, body sensors,
  storage), grouped and tagged per app.
- **An honest limitation, stated plainly**: since Android 11 (API 30), a normal app can
  only see a subset of what's installed — itself, apps it's interacted with, and some
  system packages — unless it requests `QUERY_ALL_PACKAGES`. NOVA deliberately does
  **not** request that permission; a privacy dashboard that demands "see everything on
  this phone" to function would work against its own purpose. The screen says this
  outright instead of pretending its app list is complete.

Nothing in this module bypasses Android security or reads data a normal app can't.

### Deep Scan — advanced reporting
Deep Scan now folds in a live network-connection check (via `ConnectivityManager` —
connected / validated, not a full ping test, which stays a deliberate user-initiated
action in NOVA Network) and a Privacy summary (notification/accessibility grant counts).
Every line in the report still traces back to something read from an Android API during
that run — never a fabricated "health score."

### Feel
- Cards give a small physical scale-down on press.
- Screen transitions fade/slide instead of hard-cutting.
- Deep Scan and Privacy findings reveal progressively instead of appearing all at once.
- Restrained, instrument-panel haptics (not game-controller rumble) on module taps, tab
  switches, running a scan, and scan completion — success is two light pulses, a finding
  worth checking is a firmer double pulse. Uses the `VIBRATE` permission already declared
  (a normal, not dangerous, permission — no prompt).

## What's real in this build

### Shield (new)
A local, DNS-only ad/tracker filter built on `VpnService` — no proxy server, no paid
backend, nothing leaves the device. It advertises its own tun interface as the system DNS
server and routes **only** traffic to that single address (not a 0.0.0.0/0 catch-all), so
regular app traffic never passes through NOVA at all. Matched domains get a synthesized
NXDOMAIN reply; everything else is forwarded unmodified to a public resolver (1.1.1.1) and
relayed back. Ships with a small hand-curated starter blocklist (`assets/shield_blocklist.txt`,
~85 known ad/tracker domains — explicitly not exhaustive), a user-editable custom blocklist,
an allowlist, per-app exclusion, and cumulative (all-time, on-device only) counters. The
Shield screen states its own limitation plainly: DNS filtering can't touch ads served from a
domain an app also needs to function, and it never inspects encrypted content.

### Privacy (Phase 3)
See above — self-permissions, notification listeners, accessibility services, and a
package-visibility-limited sensitive-app scan, explained honestly.

### Network (Phase 2)
Live connection type/metered/validated status and Wi-Fi details (SSID, signal dBm/bars,
link speed, frequency) straight from `ConnectivityManager` / `WifiManager`. Local
IPv4/IPv6, gateway, and DNS servers from `LinkProperties`. **Run Diagnostics**: real ping
series to 1.1.1.1, real DNS resolution timing, a real HTTP 204 connectivity check, and
real download/upload throughput against Cloudflare's public speed-test endpoint. **LAN
Scan**: derives your own `/24` subnet from your real local IP and TCP-connect-probes it —
only ever scans the network you're already on.

### Vision (Phase 2)
QR/Barcode Scanner and Text Scanner (OCR): live CameraX preview analyzed on-device by ML
Kit. Nothing is uploaded or saved.

### Lab
`SensorManager`-backed live instruments — Motion Graph, Rotation, Magnetic Field, Light,
Sound level, Proximity, Barometer (with a clearly-labeled sea-level-formula altitude
estimate), GPS (permission-gated), and a full Sensor Availability list.

### Device
Live RAM/storage/battery/display/CPU/camera readouts, plus interactive Touch,
Display/Color, Vibration, Flashlight, and Camera tests.

### Tools
Calculator (hand-written parser, no `eval`), Base64, URL encode/decode, JSON
formatter/validator, UUID generator, Hash generator, Timestamp converter, Unit converter.

### Home
Live status header + Deep Scan, now covering Device, Network, and Privacy in one report.

## Opening the project
1. Open this folder in Android Studio (Koala/2024.1+ recommended).
2. Let Gradle sync — it will fetch the wrapper jar automatically on first sync
   (only `gradle-wrapper.properties` is included in this zip; Android Studio
   regenerates the wrapper jar/script, or run `gradle wrapper` once if building
   from the command line without Android Studio).
3. Run on a device/emulator with API 26+ (minSdk 26, targetSdk/compileSdk 34).

## Permissions
Every permission is requested **contextually**, only when you open the screen that needs
it. Nothing is requested on first launch. Privacy's app-list scan and settings reads need
no additional permission at all — `VIBRATE`, `ACCESS_NETWORK_STATE`, and
`ACCESS_WIFI_STATE` are "normal" permissions granted automatically at install, not
runtime prompts.

## Architecture
Kotlin + Jetpack Compose + Material 3, single-activity, `feature/<module>` packages,
`ViewModel` + `StateFlow` for state, a small shared `core/` package for the haptics
helper, no backend, no analytics, no ads.

## What's intentionally NOT built yet
All modules from the original spec (Lab, Device, Tools, Network, Vision, Privacy) are now
shipped. Phase 4 is polish: crash/edge-case hardening across permission states,
accessibility passes, performance profiling on mid-range hardware, and screen-size /
Android-version coverage — no new modules planned.
