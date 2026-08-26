# Contributing to Blocky

Thank you for your interest in contributing to **Blocky**! We welcome bug reports, feature suggestions, code contributions, and localization improvements.

## Code of Conduct

Please review and adhere to our [Code of Conduct](CODE_OF_CONDUCT.md) in all community interactions.

## How to Contribute

### 1. Reporting Bugs
- Search existing [Issues](https://github.com/perritodev/Blocky/issues) before opening a new one.
- Use the **Bug Report** template and include device model, Android version, and logs if applicable.

### 2. Suggesting Features
- Open a feature request issue describing the feature and its intended use case.

### 3. Pull Requests
1. Fork the repository and create a new feature branch from `main`:
   ```bash
   git checkout -b feature/my-new-feature
   ```
2. Ensure your changes follow standard Kotlin conventions and Jetpack Compose best practices.
3. Test locally using `./gradlew assembleDebug` to verify compilation.
4. Push to your fork and submit a Pull Request against `main`.

## Development Setup

- **IDE:** Android Studio Ladybug / Meerkat or newer
- **JDK:** Java 17 or Java 21 (bundled JBR)
- **Min SDK:** 29 (Android 10)
- **Target SDK:** 37
