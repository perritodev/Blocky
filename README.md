# 🛡️ Blocky — Smart On-Device Call Protection

**Blocky** is a modern, lightweight, privacy-first Android call screening app built with Jetpack Compose and Material 3. It silently intercepts and blocks any call that is not in your contacts list.

---

## ✨ Key Features

- 🚫 **Total Peace of Mind:** Don\'t receive any call you don\'t want — no spam, no fraud, no unknown callers.
- 🛑 **Automatic Call Screening:** Silently blocks calls from unknown numbers not in your contacts.
- 🔒 **100% Privacy / On-Device Processing:** Zero data collection, zero telemetry, no cloud servers. All screening and contact lookups happen entirely on your phone.
- 📇 **Smart Contact Protection:** Integrates with your address book so friends, family, and known contacts are never blocked.
- 📝 **Whitelist & Blocklist:** Manually allow trusted numbers or permanently block specific persistent callers.
- 📊 **Daily History:** Keep track of blocked calls with a daily log.
- 🌐 **Multilingual:** Seamless toggle between English and Spanish.

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
