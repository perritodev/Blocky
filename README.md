<p align="center">
  <img src="art/icon_playstore_512.png" width="120" height="120" alt="Blocky Logo" />
</p>

# 🛡️ Blocky

<p align="center">
  <a href="https://github.com/perritodev/Blocky/releases/latest"><img src="https://img.shields.io/github/v/release/perritodev/Blocky?color=3DDC84&label=Release&logo=android" alt="Latest Release" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License: MIT" /></a>
  <img src="https://img.shields.io/badge/Platform-Android%2010%2B-brightgreen.svg?logo=android" alt="Platform" />
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF.svg?logo=kotlin" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20On--Device-success.svg" alt="Privacy" />
</p>

A private, quiet call screening app for Android. Blocky stops unknown numbers, spam, and telemarketers before your phone ever rings, while making sure your contacts and trusted numbers always get through.

Everything happens directly on your device. There are no accounts, no cloud servers, no analytics, and no third-party tracking.

[English](#-english) | [Español](#-español)

---

## 🇬🇧 English

### 💡 Why Blocky?

Many call blocker apps upload your address book to remote servers, require accounts, or show ads. Blocky is designed with a simpler, privacy-first approach:

- It runs entirely on your device. Your contacts and call logs never leave your phone.
- It protects your peace of mind. Only numbers you know or choose to allow can ring your phone.
- It is lightweight, open source, and completely free of ads.

### 📱 What it does

- Screens incoming calls quietly: When someone calls, Blocky checks if the number is in your contacts or your allowed list. If it is not, the call is rejected silently without interrupting you.
- Protects your contacts: Anyone saved in your phone book will always ring through normally.
- Allowed list: Add numbers you expect to receive calls from, such as a doctor's office, delivery driver, or bank, without needing to save them as personal contacts.
- Blocked list and daily history: Check which numbers were stopped each day and when. You can unblock numbers or clear logs whenever you want.
- Emergency repeated calls: If you worry about missing urgent calls from an unknown number, you can set a repeat threshold. For example, you can allow a call through if the same number calls back within 15 minutes.
- Optional sound alert: You can enable a subtle retro sound effect that plays whenever an unwanted call is blocked, so you know Blocky did its job.
- English and Spanish: Switch the app interface between English and Spanish directly from the settings screen.

### 📥 How to install

1. Go to the Releases page on GitHub: https://github.com/perritodev/Blocky/releases
2. Download the latest Blocky.apk file.
3. Open the downloaded file on your Android device to install it. If Android asks, allow installation from unknown sources for your browser or file manager.
4. Open Blocky and complete the quick initial setup:
   - Set Blocky as your default Call Screening app. This lets Android route incoming calls through Blocky for checking.
   - Allow Contacts access. This allows Blocky to recognize people you know so they are never blocked.
   - Turn off battery optimization for Blocky. This ensures Android does not put the background protection to sleep when your phone is locked.

### 🛠️ Building from source

If you want to build the project yourself using Android Studio or the command line:

```bash
git clone https://github.com/perritodev/Blocky.git
cd Blocky
./gradlew assembleRelease
```

The compiled APK will be located at:
`app/build/outputs/apk/release/Blocky.apk`

### Requirements

- Android 10 (API level 29) or higher.

### 📄 License

Blocky is open-source software released under the MIT License. See the LICENSE file for details.

---

## 🇲🇽 Español

### 💡 ¿Por qué Blocky?

Muchas aplicaciones para bloquear llamadas suben tu lista de contactos a servidores externos, te piden crear cuentas o muestran anuncios molestos. Blocky funciona de manera diferente y respetuosa con tu privacidad:

- Funciona completamente en tu teléfono. Tus contactos y registros de llamadas nunca salen de tu dispositivo.
- Protege tu tranquilidad. Solo los números que conoces o decides permitir pueden hacer sonar tu teléfono.
- Es ligera, de código abierto y totalmente libre de publicidad.

### 📱 ¿Qué hace la aplicación?

- Filtra llamadas en segundo plano: Cuando entra una llamada, Blocky revisa si el número está en tus contactos o en tu lista de permitidos. Si no está registrado, la llamada se rechaza silenciosamente sin molestarte.
- Protege a tus contactos: Todas las personas guardadas en tu agenda telefónica siempre podrán llamarte con total normalidad.
- Lista de permitidos: Agrega números específicos de los que esperas llamadas (como paqueterías, consultorios médicos o tu banco) sin necesidad de guardarlos en tus contactos personales.
- Historial diario y lista de bloqueados: Consulta qué números fueron interceptados cada día y a qué hora. Puedes desbloquear números o borrar el historial cuando lo desees.
- Intentos de llamada para emergencias: Si te preocupa perder una llamada urgente de un número que no conoces, puedes configurar un umbral de intentos. Por ejemplo, permitir que suene si el mismo número marca varias veces seguidas en un lapso de 15 minutos.
- Alerta de sonido opcional: Puedes activar un efecto de sonido estilo retro que suena brevemente cuando se bloquea una llamada no deseada, para saber que la app hizo su trabajo.
- Inglés y Español: Cambia el idioma de la aplicación entre inglés y español directamente desde la pantalla de ajustes.

### 📥 Cómo instalarla

1. Ve a la sección de Releases en GitHub: https://github.com/perritodev/Blocky/releases
2. Descarga el archivo más reciente llamado Blocky.apk.
3. Abre el archivo descargado en tu teléfono Android para instalarlo. Si el sistema te lo solicita, permite la instalación desde fuentes desconocidas en tu navegador o gestor de archivos.
4. Abre Blocky y completa los pasos iniciales de configuración:
   - Configura Blocky como tu aplicación predeterminada de Filtrado de Llamadas. Esto le permite a Android consultar a Blocky cada vez que entra una llamada.
   - Concede acceso a tus Contactos. Así Blocky identifica a tus conocidos y nunca los bloqueará.
   - Desactiva la optimización de batería para Blocky. Esto evita que Android suspenda el servicio de protección cuando el teléfono entra en reposo.

### 🛠️ Compilación desde el código fuente

Si prefieres compilar la aplicación por tu cuenta usando Android Studio o la terminal:

```bash
git clone https://github.com/perritodev/Blocky.git
cd Blocky
./gradlew assembleRelease
```

El archivo APK generado se encontrará en:
`app/build/outputs/apk/release/Blocky.apk`

### Requisitos

- Android 10 (nivel de API 29) o superior.

### 📄 Licencia

Blocky es un proyecto de código abierto publicado bajo la Licencia MIT. Consulta el archivo LICENSE para más detalles.
