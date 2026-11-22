# Android Torrent Client (Galaxy A06 5G / Android 17 / One UI 9.0)

Ready-to-build Kotlin + Jetpack Compose BitTorrent client powered by `libtorrent4j` (`arm64-v8a`), configured for **Android 17 (API 37)** and **Samsung One UI 9.0**.

## Build an APK Without a PC (GitHub Actions)
1. Create a new repository on [GitHub](https://github.com) (e.g., `android-torrent-client`).
2. Extract this ZIP and upload all files—including the hidden `.github/workflows/build-apk.yml` folder—to the root of your repository.
3. Open the **Actions** tab in your GitHub repository. The **Build Android APK** workflow will run automatically (~3 minutes).
4. Once complete, click the workflow run, scroll down to **Artifacts**, and download **app-debug** to get `app-debug.apk`.

## Build Locally in Android Studio
1. Open this folder in **Android Studio** (Ladybug / Narwhal or newer with JDK 17+).
2. Let Gradle sync, then select **Build > Build App(s) / APK(s) > Build APK(s)**.
3. Find the output APK at `app/build/outputs/apk/debug/app-debug.apk`.

## Installing & Configuring on Samsung Galaxy A06 5G (One UI 9.0)
1. **Auto Blocker:** Temporarily disable **Settings > Security and privacy > Auto Blocker** before installing the APK.
2. **Allow All Files Access (for public Downloads folder):**
   - Open the app and tap **Grant** on the storage banner.
   - If the toggle is grayed out as a *Restricted setting*, go to **Settings > Apps > Torrent Client**, tap the **three-dot menu (`⋮`) > Allow restricted settings**, then return and enable **All files access**.
3. **Prevent Background Killing:**
   - Long-press the **Torrent Client** icon > **(i) App info > Battery > Unrestricted**.
   - Open **Settings > Device care > Battery > Background usage limits > Never auto sleeping apps** and add **Torrent Client**.
