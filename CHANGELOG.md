# Changelog

Semua perubahan penting pada proyek ini akan dicatat di file ini.

## [1.1.1] - 2026-07-23

### Ditambahkan
- **Lokasi Unduhan Kustom**: Menambahkan kemampuan untuk memilih folder penyimpanan unduhan menggunakan Storage Access Framework (SAF).
- **Izin Startup**: Permintaan izin penyimpanan di awal aplikasi untuk pengalaman pengguna yang lebih lancar.

### Diperbaiki
- **Crash Android 14+**: Perbaikan `InvalidForegroundServiceTypeException` saat memulai transfer file.
- **Pratinjau File**: Peningkatan logika deteksi tipe file untuk mendukung file `application/octet-stream` berdasarkan ekstensi.
- **Pratinjau Gambar**: Perbaikan layar kosong saat membuka pratinjau gambar tanpa thumbnail (WebDAV/SFTP) dengan menambahkan tombol unduh ke pratinjau.
- **Strategi Unggah Manual**: Perbaikan logika agar benar-benar menggunakan akun yang ditandai sebagai "MAIN".

### Diperbarui
- **Lokasi Unduhan Default**: Dipindahkan dari penyimpanan privat aplikasi ke folder Downloads publik sistem.
- **UI Strategi**: Penyempurnaan antarmuka pemilihan strategi unggah agar lebih intuitif.

---

## [1.1.0] - 2026-07-23

### Ditambahkan
- **Peningkatan Pencarian**: Menambahkan kemampuan pencarian yang lebih mendalam dan responsif di seluruh provider cloud.
- **Navigasi Folder**: Implementasi penjelajahan file berbasis folder (*folder-scoped browsing*) untuk manajemen file yang lebih teratur.

### Diperbarui
- **Optimasi UI**: Penyesuaian antarmuka untuk mendukung tampilan *edge-to-edge* yang lebih modern dan imersif.
- Versi aplikasi ditingkatkan ke 1.1.0 (versionCode 2).

---

## [1.0.0] - 2026-07-23
- Rilis awal Awd DriveRouter Android.
- Dukungan untuk Google Drive, OneDrive, Dropbox, Box, WebDAV, dan SFTP.
- Manajemen multi-akun dan strategi unggah cerdas.
