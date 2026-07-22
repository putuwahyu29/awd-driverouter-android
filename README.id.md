# Awd DriveRouter Android 📱☁️🔀

<p align="center">
  <img src="app/src/main/res/drawable/app_logo.png" width="130" height="130" alt="Awd DriveRouter Android Logo">
</p>

<p align="center">
  <a href="https://github.com/putuwahyu29/awd-driverouter-android/blob/main/LICENSE">
    <img src="https://img.shields.io/badge/Lisensi-MIT-yellow.svg?style=flat-square" alt="License Badge">
  </a>
  <img src="https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android" alt="Android Badge">
  <img src="https://img.shields.io/badge/Bahasa-Kotlin-purple?style=flat-square&logo=kotlin" alt="Kotlin Badge">
  <img src="https://img.shields.io/badge/Framework-Jetpack%20Compose-navy?style=flat-square&logo=jetpackcompose" alt="Jetpack Compose Badge">
  <img src="https://img.shields.io/badge/Arsitektur-MVVM--Clean-orange?style=flat-square" alt="MVVM Clean Architecture">
</p>

**Awd DriveRouter Android** adalah aplikasi manajemen file seluler canggih yang dirancang untuk menghubungkan berbagai layanan penyimpanan cloud ke dalam satu pengalaman yang terpadu. Dibangun dengan **Kotlin** dan **Jetpack Compose**, aplikasi ini menyediakan lapisan perutean cerdas untuk file Anda, mendukung penjelajahan yang lancar, manajemen multi-akun, dan sinkronisasi latar belakang di berbagai penyedia termasuk Google Drive, OneDrive, Dropbox, Box, WebDAV, dan SFTP.

---

## 🌐 Bahasa / Language
*   [Versi Bahasa Indonesia](README.id.md)
*   [English Version](README.md)

---

## 📌 Daftar Isi
- [✨ Fitur Utama](#-fitur-utama)
- [📷 Tangkapan Layar](#-screenshots)
- [☁️ Provider yang Didukung](#️-provider-yang-didukung)
- [📁 Arsitektur & Teknologi](#-arsitektur--teknologi)
- [🚀 Memulai](#-memulai)
  - [Prasyarat](#prasyarat)
  - [Instalasi](#instalasi)
  - [Panduan Setup](#panduan-setup)
- [🛠️ Panduan Pengembang](#️-panduan-pengembang)
  - [Struktur Proyek](#struktur-proyek)
  - [Membangun dari Sumber](#membangun-dari-sumber)
- [⚙️ Troubleshooting](#️-troubleshooting)
- [⚠️ Disclaimer](#️-disclaimer)
- [📄 Lisensi](#-lisensi)

---

## ✨ Fitur Utama

*   **🔀 Manajemen Cloud Terpadu**: Hubungkan dan navigasi berbagai akun cloud (Google Drive, OneDrive, dll.) dari satu antarmuka yang intuitif.
*   **📤 Perutean Cerdas**: Distribusikan unggahan secara otomatis di seluruh provider berdasarkan strategi cerdas seperti "Ruang Kosong Terbanyak" atau "Round Robin".
*   **📂 Explorer File Profesional**: Manajer file lengkap dengan fitur multi-seleksi, aksi batch, pratinjau PDF internal, dan integrasi aplikasi native.
*   **🔗 Berbagi & Kolaborasi Native**: Bagikan file menggunakan fitur kolaborasi asli provider, termasuk menambahkan orang melalui email dan mengatur perizinan.
*   **🔒 Penyimpanan Kredensial Aman**: Menggunakan **EncryptedSharedPreferences** Android untuk menyimpan token OAuth dan kredensial server dengan aman.
*   **🎨 Desain Material 3**: UI yang sepenuhnya responsif dengan dukungan Dynamic Color, mode Gelap/Terang, dan dukungan bilingual (EN/ID).

---

## 📷 Tangkapan Layar

| | | |
|:---:|:---:|:---:|
| <img src="docs/screenshots/files.png" width="230" alt="File Explorer"/><br/>**Explorer File** | <img src="docs/screenshots/accounts.png" width="230" alt="Multi-Account"/><br/>**Manajemen Akun** | <img src="docs/screenshots/preview.png" width="230" alt="PDF Preview"/><br/>**Pratinjau PDF** |
| <img src="docs/screenshots/sharing.png" width="230" alt="Native Sharing"/><br/>**UI Berbagi Native** | <img src="docs/screenshots/selection.png" width="230" alt="Multi-Select"/><br/>**Mode Multi-Seleksi** | <img src="docs/screenshots/settings.png" width="230" alt="Settings"/><br/>**Pengaturan & Strategi** |

---

## ☁️ Provider yang Didukung

| Provider | Koneksi | Fitur |
| :--- | :---: | :--- |
| **Google Drive** | OAuth 2.0 | Berbagi Native, Sinkronisasi, Kuota |
| **OneDrive** | OAuth 2.0 | Berbagi Native, Sinkronisasi, Kuota |
| **Dropbox** | OAuth 2.0 | Jelajah File, Berbagi Dasar |
| **Box** | OAuth 2.0 | Jelajah File, Berbagi Dasar |
| **WebDAV** | Basic Auth | Dukungan Multi-Akun |
| **SFTP (SSH)** | Password | Akses Remote Aman |

---

## 📁 Arsitektur & Teknologi

*   **UI**: Jetpack Compose (Material 3)
*   **Asinkron**: Kotlin Coroutines & Flow
*   **Dependency Injection**: Hilt
*   **Database Lokal**: Room (Caching metadata & akun)
*   **Keamanan**: EncryptedSharedPreferences (MasterKey)
*   **Networking**: Retrofit, OkHttp, Sardine (WebDAV), SSHJ (SFTP)
*   **Arsitektur**: MVVM dengan prinsip Clean Architecture

---

## 🚀 Memulai

### Prasyarat
*   Perangkat Android yang menjalankan Android 8.0 (API 26) atau lebih tinggi.
*   Kredensial API provider cloud (untuk setup Google/OneDrive).

### Instalasi
1. Unduh APK terbaru dari halaman [Releases](https://github.com/putuwahyu29/awd-driverouter-android/releases).
2. Instal APK di perangkat Anda (pastikan "Instal dari Sumber Tidak Dikenal" diaktifkan).

### Panduan Setup
*   **OAuth Providers**: Buka **Pengaturan**, konfigurasikan Client ID Anda, lalu buka **Akun** untuk menghubungkan.
*   **WebDAV/SFTP**: Login langsung melalui layar **Akun** menggunakan detail server Anda.

---

## 🛠️ Panduan Pengembang

### Struktur Proyek
```
awd-driverouter-android/
├── app/                    # Modul aplikasi Android utama
│   ├── src/main/java/com/awd/driverouter/
│   │   ├── data/           # Implementasi Repository, DAO, dan Provider
│   │   ├── di/             # Modul Hilt
│   │   ├── domain/         # Model bisnis dan interface provider
│   │   ├── ui/             # Layar dan viewmodel Jetpack Compose
│   │   └── util/           # Utilitas pemformatan dan pembantu
│   └── build.gradle.kts    # Dependensi modul
├── gradle/                 # Gradle wrapper
└── README.md               # Dokumentasi Bahasa Inggris
```

### Membangun dari Sumber
1. Klon repositori:
   ```bash
   git clone https://github.com/putuwahyu29/awd-driverouter-android.git
   ```
2. Buka di Android Studio (Iguana atau yang lebih baru).
3. Bangun proyek:
   ```bash
   ./gradlew assembleDebug
   ```

---

## ⚙️ Troubleshooting

*   **Masalah Callback OAuth**: Pastikan URI pengalihan di konsol provider Anda cocok dengan konfigurasi Anda.
*   **Kesalahan Kuota Penyimpanan**: Beberapa server WebDAV mungkin tidak melaporkan kuota dengan benar.
*   **Pemuatan PDF**: File PDF berukuran besar mungkin membutuhkan beberapa detik untuk dirender pada perangkat lama.

---

## ⚠️ Disclaimer

Proyek ini adalah pengembangan open-source independen. Tidak didukung secara resmi oleh Google, Microsoft, atau penyedia layanan lain yang disebutkan. Pengguna bertanggung jawab untuk memastikan kepatuhan terhadap Ketentuan Layanan masing-masing provider.

---

## 📄 Lisensi

Proyek ini dilisensikan di bawah Lisensi MIT. Lihat file [LICENSE](LICENSE) untuk detailnya.

---

<p align="center">Dibuat dengan ❤️ oleh <a href="mailto:aguswahyu@office.awd.my.id">I Putu Agus Wahyu Dupayana</a></p>
