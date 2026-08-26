<p align="center">
  <img src="art/icon_playstore_512.png" width="120" height="120" alt="Blocky Logo" />
</p>

<h1 align="center">🛡️ Blocky — Smart On-Device Call Protection</h1>

<p align="center">
  <strong>A modern, lightweight, privacy-first Android call screening app built with Jetpack Compose and Material 3.</strong><br>
  <em>Silently intercepts and blocks any call that is not in your contacts list.</em>
</p>

---

## ✨ Key Features
- Dont receive any call you don't want, no spam, no fraud, no unknown callers.
- 🛑 **Automatic Call Screening:** Silently blocks any number not in your contacts.
- 🔒 **100% Privacy / On-Device Processing:** Zero data collection, all screening and contact lookups happen entirely on your phone.
- 📇 **Smart Contact Protection:** Integrates with your address book so friends, family, and known contacts are never blocked.
- 📝 **Whitelist & Blocklist:** Manually allow trusted numbers or permanently block specific persistent callers.
- 📊 **Daily History:** Keep track of blocked calls with a daily log.
- 🌐 **Bi-Language:** Toggle between English and Spanish.

---

## 📥 Download & Installation (Sideloading)

You can download and install the pre-signed, production-ready APK directly:

1. Go to the **[Releases](https://github.com/perritodev/Blocky/releases)** page.
2. Download the latest **`Blocky.apk`**.
3. Open the downloaded APK on your Android device.
4. If prompted, allow **"Install from unknown sources"** for your browser or file manager.
5. Follow the onboarding setup:
   - Set **Blocky** as your default **Call Screening App**.
   - Grant **Contacts** permission (to avoid blocking known numbers).
   - Disable battery optimization to ensure 24/7 background reliability.

---

## 🛠️ Tech Stack & Architecture

- **Language:** Kotlin
- **UI Toolkit:** Jetpack Compose (Material 3)
- **Architecture:** MVVM + Coroutines & Flow
- **Local Database:** Room Database
- **Framework API:** Android Telecom `CallScreeningService` + `RoleManager`
- **Min SDK:** Android 10 (API 29)
- **Target SDK:** Android 15 (API 35)

---

## 🚀 Building from Source

```bash
# Clone the repository
git clone https://github.com/perritodev/Blocky.git
cd Blocky

# Build debug APK
./gradlew assembleDebug

# Build signed release APK
./gradlew assembleRelease
```

The compiled release APK will be located in:
`app/build/outputs/apk/release/Blocky.apk`

---

## 📄 License

This project is open-source under the MIT License.
