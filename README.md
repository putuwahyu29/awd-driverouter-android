# Awd DriveRouter Android 📱☁️🔀

<p align="center">
  <img src="app/src/main/res/drawable/app_logo.png" width="130" height="130" alt="Awd DriveRouter Android Logo">
</p>

<p align="center">
  <a href="https://github.com/putuwahyu29/awd-driverouter-android/blob/main/LICENSE">
    <img src="https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square" alt="License Badge">
  </a>
  <img src="https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android" alt="Android Badge">
  <img src="https://img.shields.io/badge/Language-Kotlin-purple?style=flat-square&logo=kotlin" alt="Kotlin Badge">
  <img src="https://img.shields.io/badge/Framework-Jetpack%20Compose-navy?style=flat-square&logo=jetpackcompose" alt="Jetpack Compose Badge">
  <img src="https://img.shields.io/badge/Architecture-MVVM--Clean-orange?style=flat-square" alt="MVVM Clean Architecture">
</p>

**Awd DriveRouter Android** is a sophisticated mobile file management application designed to bridge multiple cloud storage services into a single, cohesive experience. Built with **Kotlin** and **Jetpack Compose**, it provides an intelligent routing layer for your files, supporting seamless browsing, multi-account management, and background synchronization across various providers including Google Drive, OneDrive, Dropbox, Box, WebDAV, and SFTP.

---

## 🌐 Language / Bahasa
*   [English Version (Main)](README.md)
*   [Versi Bahasa Indonesia](README.id.md)

---

## 📌 Table of Contents
- [✨ Key Features](#-key-features)
- [📷 Screenshots](#-screenshots)
- [☁️ Supported Providers & Setup Guide](#️-supported-providers--setup-guide)
- [📁 Architecture & Tech Stack](#-architecture--tech-stack)
- [🚀 Getting Started](#-getting-started)
- [🛠️ Developer Guide](#️-developer-guide)
- [⚙️ Troubleshooting](#️-troubleshooting)
- [⚠️ Disclaimer](#️-disclaimer)
- [📄 License](#-license)

---

## ✨ Key Features

*   **🔀 Unified Cloud Management**: Connect and navigate multiple cloud accounts (Google Drive, OneDrive, Dropbox, Box, WebDAV, SFTP) from a single intuitive interface.
*   **📤 Intelligent Routing**: Automatically distribute uploads across providers based on smart strategies like "Largest Free Space" or "Round Robin".
*   **📂 Professional File Explorer**: Full-featured manager with multi-selection, batch actions, internal PDF preview, and native app integration.
*   **🔄 Automatic Background Backup**: Folder sync running reliably in the background via Android **Foreground Services**.
*   **🔒 Secure Credential Storage**: Uses Android's **EncryptedSharedPreferences** to store OAuth tokens and server credentials securely at rest.
*   **🎨 Material 3 Design**: Fully responsive UI with support for Dynamic Color, Dark/Light modes, and bilingual support (EN/ID).

---

## 📷 Screenshots

| Home File Explorer | Navigation Drawer | Auto Backup |
|:---:|:---:|:---:|
| <img src="docs/screenshots/home.jpg" width="240" alt="Home File Explorer"/><br/>**Home File Explorer** | <img src="docs/screenshots/drawer.jpg" width="240" alt="Navigation Drawer"/><br/>**Navigation Drawer** | <img src="docs/screenshots/backup.jpg" width="240" alt="Auto Backup"/><br/>**Auto Backup** |

| Upload Strategy | Cloud Accounts | File Upload |
|:---:|:---:|:---:|
| <img src="docs/screenshots/strategy.jpg" width="240" alt="Upload Strategy"/><br/>**Upload Strategy** | <img src="docs/screenshots/account.jpg" width="240" alt="Cloud Accounts"/><br/>**Cloud Accounts** | <img src="docs/screenshots/upload.jpg" width="240" alt="File Upload"/><br/>**File Upload** |

---

## ☁️ Supported Providers & Setup Guide

In the Android app, go to **Settings → Cloud Provider Settings** to view your app identity details (**Package Name**, **SHA-1 (HEX)**, **SHA-1 (Base64)**, and **Redirect URIs**) with 1-click copy buttons.

| Provider | Type | Provider Console Link | Setup & Credentials Flow |
| :--- | :---: | :--- | :--- |
| **Google Drive** | OAuth 2.0 | [Google Cloud Console](https://console.cloud.google.com/) | **1.** Create **OAuth Client ID #1 (Web application)** -> Copy Client ID to App Settings.<br/>**2.** Create **OAuth Client ID #2 (Android)** -> Register your app's **Package Name** & **SHA-1 (HEX)** from Settings. |
| **OneDrive** | OAuth 2.0 | [Microsoft Entra](https://entra.microsoft.com/) | **1.** Register App in Entra admin center.<br/>**2.** Under *Authentication*, add **Android Platform** with your **Package Name** & **SHA-1 (Base64)** Signature Hash.<br/>**3.** Copy **Application (client) ID** into App Settings. |
| **Dropbox** | OAuth 2.0 | [Dropbox Console](https://www.dropbox.com/developers/apps) | **1.** Create app with Scoped Access (`files.content.read/write`).<br/>**2.** Register Redirect URI: `awd-driverouter://dropbox-auth`<br/>**3.** Copy **App Key** into App Settings. |
| **Box** | OAuth 2.0 | [Box Console](https://app.box.com/developers/console) | **1.** Create Custom App (User Authentication OAuth 2.0).<br/>**2.** Register Redirect URI: `awd-driverouter://box-auth`<br/>**3.** Copy **Client ID** & **Client Secret** into App Settings. |
| **WebDAV** | Protocol | - | Direct connection. Enter **Server URL**, **Username**, and **Password** directly in the *Connect Account* dialog. |
| **SFTP (SSH)** | Protocol | - | Direct connection. Enter **Host/IP**, **Port** (default 22), **Username**, and **Password** in the *Connect Account* dialog. |

---

## 📁 Architecture & Tech Stack

*   **UI**: Jetpack Compose (Material 3)
*   **Asynchronous**: Kotlin Coroutines & Flow
*   **Dependency Injection**: Hilt
*   **Local Database**: Room (Caching metadata & account records)
*   **Security**: EncryptedSharedPreferences (MasterKey)
*   **Networking**: Retrofit, OkHttp, Sardine (WebDAV), SSHJ (SFTP)
*   **Architecture**: MVVM with Clean Architecture principles

---

## 🚀 Getting Started

### Prerequisites
*   Android device running Android 8.0 (API 26) or higher.
*   Cloud provider API credentials (for Google, OneDrive, Dropbox, or Box).

### Installation
1. Download the latest APK from the [Releases](https://github.com/putuwahyu29/awd-driverouter-android/releases) page.
2. Install the APK on your device (ensure "Install from Unknown Sources" is enabled).

---

## 🛠️ Developer Guide

### Project Directory Structure
```
awd-driverouter-android/
├── app/                    # Main Android application module
│   ├── src/main/java/com/awd/driverouter/
│   │   ├── data/           # Repository, DAO, and Provider implementations
│   │   ├── di/             # Hilt modules
│   │   ├── domain/         # Business models and provider interfaces
│   │   ├── ui/             # Jetpack Compose screens and viewmodels
│   │   └── util/           # Formatting and helper utilities
│   └── build.gradle.kts    # Module dependencies
├── docs/screenshots/       # Screenshots for documentation
├── gradle/                 # Gradle wrapper
└── README.md               # English Documentation
```

### Building from Source
1. Clone the repository:
   ```bash
   git clone https://github.com/putuwahyu29/awd-driverouter-android.git
   ```
2. Open in Android Studio (Iguana or newer).
3. Build the project:
   ```bash
   ./gradlew assembleDebug
   ```

---

## ⚙️ Troubleshooting

*   **OAuth Callback Issues**: Ensure the custom scheme redirect URI (`awd-driverouter://dropbox-auth` or `awd-driverouter://box-auth`) is registered in your provider console.
*   **Google Drive Error 10**: Verify that your SHA-1 (HEX) fingerprint and Package Name in Google Cloud Console match your APK signature.
*   **OneDrive MSAL Errors**: Verify that your Package Name and SHA-1 (Base64) Signature Hash in Microsoft Entra match your app build.

---

## ⚠️ Disclaimer

This project is an independent open-source development. It is not officially endorsed by Google, Microsoft, Dropbox, Box, or any other service provider mentioned. Users are responsible for ensuring compliance with each provider's Terms of Service.

---

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

---

<p align="center">Made with ❤️ by <a href="mailto:aguswahyu@office.awd.my.id">I Putu Agus Wahyu Dupayana</a></p>
