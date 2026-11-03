# Spec

Android design QA overlay for inspecting UI sizes, spacing, fonts, colors, and corners on a real device.

Floating button · element inspection · design overlay comparison.

## Requirements

- Android 9+ (API 28)
- Accessibility permission (for inspection)

## Build

```bash
./gradlew :app:assembleDebug
```

Install the debug APK from `app/build/outputs/apk/debug/`.

## Modules

| Path | Purpose |
|------|---------|
| `app/` | Spec main app (`com.legs.designqa`) |
| `spec-inspector/` | Optional debug library for exact font weight in target apps |
| `spec-sample/` | Sample app for testing inspection |

## License

MIT
