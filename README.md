# AppLocker

React Native Android app that locks selected apps behind a PIN/biometric using Accessibility Service.

## Features

- **App locking** — Select apps to protect from a list of installed apps
- **PIN authentication** — 4+ digit PIN with salted SHA-256 storage
- **Biometric fallback** — Fingerprint/face unlock (Android 9+)
- **Accessibility-based detection** — Instantly shows lock screen when protected app opens
- **Session persistence** — Unlocked apps stay unlocked until screen turns off

## Architecture

```
┌─────────────────┐     ┌──────────────────────┐     ┌─────────────────────┐
│  React Native   │────▶│  Native Module (Kotlin)  │────▶│  Accessibility Service │
│  (HomeScreen)   │     │  AppLockerModule      │     │  (AppLockerAccessibilityService) │
└─────────────────┘     └──────────────────────┘     └─────────────────────┘
                                                         │
                                                         ▼
                                              ┌─────────────────────┐
                                              │  LockScreenActivity │
                                              │  (PIN + Biometric)  │
                                              └─────────────────────┘
```

## Requirements

- Android 7.0+ (API 24+)
- React Native 0.85+
- Node.js 22+
- Accessibility Service permission (user must enable in Settings)

## Installation

```bash
# Install dependencies
npm install

# Build and run on Android
npm run android
```

## Building Release APK

```bash
cd android
./gradlew assembleRelease
# Output: android/app/build/outputs/apk/release/app-release.apk
```

## Security

| Aspect | Implementation |
|--------|----------------|
| PIN Storage | Salted SHA-256 (16-byte random salt per PIN) |
| Biometric | Android BiometricPrompt API (Strong auth) |
| Session | In-memory `unlockedPackages` set, cleared on `ACTION_SCREEN_OFF` |
| Back button | Blocked on lock screen |

## Permissions

| Permission | Purpose |
|------------|---------|
| `BIND_ACCESSIBILITY_SERVICE` | Detect app launches |
| `SYSTEM_ALERT_WINDOW` | Show lock screen over other apps |
| `INTERNET` | React Native debugging (dev only) |

## Project Structure

```
AppLocker/
├── android/
│   └── app/src/main/
│       ├── java/com/applocker/
│       │   ├── AppLockerModule.kt       # React Native bridge
│       │   ├── AppLockerAccessibilityService.kt
│       │   ├── LockScreenActivity.kt
│       │   └── MainActivity.kt
│       ├── res/
│       │   ├── layout/lock_screen.xml
│       │   ├── xml/accessibility_service_config.xml
│       │   └── values/strings.xml
│       └── AndroidManifest.xml
├── src/
│   ├── native/AppLocker.ts              # TS interface to native module
│   └── screens/HomeScreen.tsx           # Main UI
├── App.tsx
└── package.json
```

## Enabling the Accessibility Service

1. Open AppLocker
2. Tap "OFF — Tap to enable" under Accessibility Service
3. Find "AppLocker" in the list and toggle it ON
4. Confirm the permission dialog

## Development

```bash
# Start Metro bundler
npm start

# Run linting
npm run lint

# Run tests
npm test
```

## Known Limitations

- **Android only** — iOS doesn't allow overlaying lock screens on other apps
- **Accessibility Service required** — User must manually enable it
- **No cloud sync** — Locked apps stored locally only

## License

MIT