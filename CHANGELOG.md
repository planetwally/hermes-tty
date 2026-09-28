# Changelog

## 0.3.0 — 2026-09-28

First public release.

- Renamed to **Hermes TTY** (`com.planetwally.hermestty`); pairing links are now `hermestty://connect`.
- Pairing links always ask for confirmation before changing the server.
- API key is encrypted at rest with the Android Keystore.
- Settings and pairing screens warn when the server is reached over unencrypted http outside a
  private network or tailnet.
- QR scanning uses ZXing instead of Google Play Services, so the app works on de-Googled phones.
- New about screen (`/about`) with the non-affiliation notice and third-party licenses.
- Release builds are signed with a real key when `keystore.properties` is present.

## 0.2.0

Private sideload build: Runs API streaming, steering, approvals, session browser, QR pairing,
fingerprint lock.
