# APK build status

This package is Android Studio / GitHub Actions build-ready, but the ChatGPT sandbox used to generate it does not include the Android SDK, Gradle, aapt2, d8, zipalign, or apksigner, and external downloads are blocked. That prevents compiling the APK inside the sandbox.

Fastest APK path:

1. Create a GitHub repo.
2. Upload this folder.
3. Open the Actions tab.
4. Run **Build debug APK**.
5. Download the **PocketLab-debug-apk** artifact.

Local path:

1. Open this folder in Android Studio.
2. Let Gradle sync.
3. Build > Build Bundle(s) / APK(s) > Build APK(s).

The APK will be at:

```txt
app/build/outputs/apk/debug/app-debug.apk
```
