# Mystic AI for Android

Native Android companion app built with Kotlin and Jetpack Compose. It uses the Mystic AI production API to create web apps, shows the five agent results, previews generated HTML in a network-restricted WebView, and saves up to eight projects on-device.

## Open and run

Open this `android` directory in Android Studio. Use JDK 17, Android SDK 35, and allow Gradle to sync. Select the `app` run configuration and launch an Android emulator or device running Android 8.0 (API 26) or newer.

## Download

The `Build Android APK` GitHub Actions workflow publishes a debug-signed APK at the [`android-latest` release](https://github.com/uzzirulzz-cyber/mysticai/releases/tag/android-latest). The APK is suitable for direct device installation and is not a Play Store release.

## What it does

- Sends a project prompt and optional references to `https://mysticai-tawny.vercel.app/api/build`.
- Supports up to five image, PDF, or text references with a combined size of 3 MB.
- Previews returned HTML in an isolated WebView. Local file access is disabled and HTTP(S) page and resource loads are blocked.
- Exports the generated standalone HTML using Android's document picker.
- Keeps up to eight projects in the app's private files directory.

The Android app does not contain a Gemini API key. The Vercel API must have `GEMINI_API_KEY` configured before AI builds can succeed. Never place provider keys in the Android client.
