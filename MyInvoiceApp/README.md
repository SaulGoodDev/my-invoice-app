# MyInvoiceApp

Android WebView wrapper for `https://saulgooddev.github.io/my-invoice-app`.

## Requirements
- Android Studio (Giraffe or newer recommended)
- Android SDK Platform 24+ (minSdk 24), compileSdk 35

## Run
1. Open this folder (`/workspace/MyInvoiceApp`) in Android Studio.
2. Let Android Studio sync Gradle and install any missing SDKs.
3. Run the `app` configuration on a device or emulator (Android 7.0+).

## Notes
- Internet permission is declared in `app/src/main/AndroidManifest.xml`.
- `MainActivity` loads the app URL in a fullscreen `WebView` and supports back navigation.