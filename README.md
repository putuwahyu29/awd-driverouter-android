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
- [☁️ Supported Providers](#️-supported-providers)
- [📁 Architecture & Tech Stack](#-architecture--tech-stack)
- [🚀 Getting Started](#-getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
  - [Setup Guide](#setup-guide)
- [🛠️ Developer Guide](#️-developer-guide)
  - [Project Structure](#project-directory-structure)
  - [Building from Source](#building-from-source)
- [⚙️ Troubleshooting](#️-troubleshooting)
- [⚠️ Disclaimer](#️-disclaimer)
- [📄 License](#-license)

---

## ✨ Key Features

*   **🔀 Unified Cloud Management**: Connect and navigate multiple cloud accounts (Google Drive, OneDrive, etc.) from a single intuitive interface.
*   **📤 Intelligent Routing**: Automatically distribute uploads across providers based on smart strategies like "Largest Free Space" or "Round Robin".
*   **📂 Professional File Explorer**: Full-featured manager with multi-selection, batch actions, internal PDF preview, and native app integration.
*   **🔗 Native Sharing & Collaboration**: Share files using the providers' native collaboration features, including adding people via email and setting permissions.
*   **🔒 Secure Credential Storage**: Uses Android's **EncryptedSharedPreferences** to store OAuth tokens and server credentials securely at rest.
*   **🎨 Material 3 Design**: Fully responsive UI with support for Dynamic Color, Dark/Light modes, and bilingual support (EN/ID).

---

## 📷 Screenshots

| | | |
|:---:|:---:|:---:|
| <img src="docs/screenshots/files.png" width="230" alt="File Explorer"/><br/>**File Explorer** | <img src="docs/screenshots/accounts.png" width="230" alt="Multi-Account"/><br/>**Accounts Management** | <img src="docs/screenshots/preview.png" width="230" alt="PDF Preview"/><br/>**Internal PDF Preview** |
| <img src="docs/screenshots/sharing.png" width="230" alt="Native Sharing"/><br/>**Native Sharing UI** | <img src="docs/screenshots/selection.png" width="230" alt="Multi-Select"/><br/>**Multi-Selection Mode** | <img src="docs/screenshots/settings.png" width="230" alt="Settings"/><br/>**Settings & Strategy** |

---

## ☁️ Supported Providers

| Provider | Connection | Features |
| :--- | :---: | :--- |
| **Google Drive** | OAuth 2.0 | Native Sharing, Sync, Quota |
| **OneDrive** | OAuth 2.0 | Native Sharing, Sync, Quota |
| **Dropbox** | OAuth 2.0 | File Browsing, Basic Sharing |
| **Box** | OAuth 2.0 | File Browsing, Basic Sharing |
| **WebDAV** | Basic Auth | Multi-Account Support |
| **SFTP (SSH)** | Password | Secure Remote Access |

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
*   Cloud provider API credentials (for Google/OneDrive setup).

### Installation
1. Download the latest APK from the [Releases](https://github.com/putuwahyu29/awd-driverouter-android/releases) page.
2. Install the APK on your device (ensure "Install from Unknown Sources" is enabled).

### Setup Guide
*   **OAuth Providers**: Go to **Settings**, configure your Client IDs, then go to **Accounts** to connect.
*   **WebDAV/SFTP**: Direct login via the **Accounts** screen using your server details.

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

*   **OAuth Callback Issues**: Ensure the redirect URI in your provider console matches your configuration.
*   **Storage Quota Errors**: Some WebDAV servers may not report quota correctly.
*   **PDF Loading**: Large PDF files may take a few seconds to render on older devices.

---

## ⚠️ Disclaimer

This project is an independent open-source development. It is not officially endorsed by Google, Microsoft, or any other service provider mentioned. Users are responsible for ensuring compliance with each provider's Terms of Service.

---

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

---

<p align="center">Made with ❤️ by <a href="mailto:aguswahyu@office.awd.my.id">I Putu Agus Wahyu Dupayana</a></p>
