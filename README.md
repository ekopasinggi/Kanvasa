# Kanvasa

**Studio Catatan Kanvas Tak Terbatas untuk Android & Tablet Stylus**  
*Infinite Canvas Note-Taking Studio for Android & Stylus Tablets*

[![Platform](https://img.shields.io/badge/Android-7.0%2B%20(API%2024%2B)-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-2563EB.svg)](./LICENSE)

[🇮🇩 Bahasa Indonesia](#bahasa-indonesia) · [🇬🇧 English](#english) · [📥 Unduh APK / Download APK](./releases/Kanvasa-v1.0.apk)

---

<a id="bahasa-indonesia"></a>
## 🇮🇩 Bahasa Indonesia

### Tentang Kanvasa

**Kanvasa** adalah aplikasi catatan **kanvas tak terbatas (*infinite canvas*)** untuk perangkat Android—dirancang khusus agar terasa cepat, presisi, dan nyaman di **tablet + stylus (pencil)** sekaligus tetap praktis digunakan di ponsel.

Semua catatan, coretan tulisan tangan, teks, *sticky note*, bentuk geometris, dan gambar referensi disimpan **100% secara lokal di perangkat Anda tanpa perlu login atau akun**. Ketika selesai mencatat, Anda dapat langsung mengekspor seluruh isi kanvas maupun tampilan layar aktif ke **PDF** atau **PNG** beresolusi tinggi.

---

### 📥 Unduh & Pasang APK (Siap Pakai)

Ingin langsung mencoba Kanvasa tanpa perlu kompilasi kode?

- **Unduh Langsung dari Repositori**: [**`Kanvasa-v1.0.apk`**](./releases/Kanvasa-v1.0.apk) *(klik lalu pilih **Download raw file** di GitHub)*
- **Build Terbaru Otomatis**: Tersedia juga di tab **Actions** (Artifact APK) dan halaman **Releases** setiap kali tag versi baru (`v*`) diterbitkan.

#### Langkah Instalasi di Perangkat Android
1. Unduh file **`Kanvasa-v1.0.apk`** ke HP atau tablet Android Anda (Android 7.0 / API 24 atau lebih baru).
2. Buka file `.apk` tersebut melalui pengelola berkas (*File Manager*) atau notifikasi unduhan.
3. Jika diminta sistem, aktifkan izin **Instal aplikasi tidak dikenal (*Install unknown apps*)** untuk browser atau pengelola berkas yang Anda gunakan.
4. Ketuk **Instal (*Install*)**, lalu buka **Kanvasa** dan mulai mencatat.

---

### ✨ Fitur Utama

| Kategori | Kemampuan |
| --- | --- |
| **Kanvas Tak Terbatas** | Ruang kerja bebas batas halaman dengan *pan* dan *pinch-to-zoom* (`25%` – `400%`), lencana *zoom* cepat untuk memusatkan catatan, serta 3 pilihan pola latar: **Titik**, **Kotak**, dan **Polos**. |
| **Mesin Tinta Stylus Presisi** | Menangkap seluruh sampel historis frekuensi tinggi (*historical stylus points*), penghalusan tipis penjaga sudut tajam huruf (*corner-preserving*), serta **ketebalan garis dinamis mengikuti tekanan stylus** (`0,45×` – `1,35×`). |
| **Palm Rejection & Geser 1 Jari** | 3 mode sentuhan (**Otomatis saat pakai Stylus**, **Hanya Stylus**, **Bebas**), dukungan ujung penghapus stylus (*eraser tail*), pengaman **Hover-Lock** otomatis agar telapak tangan tidak menggeser kanvas saat ujung stylus dekat layar, dan **geser kanvas 1 jari** begitu pena dijauhkan. |
| **Penghapus Sebagian (*Partial Eraser*)** | Menghapus hanya bagian garis/bentuk yang tersentuh ujung penghapus—bukan seluruh rangkaian goresan—lengkap dengan 4 pilihan ukuran simbol tanpa teks label. |
| **Gambar & Pemotongan (*Crop*)** | Impor gambar tanpa izin penyimpanan luas via Android Photo Picker, lalu potong gambar dalam mode **Persegi Panjang** maupun **Custom (Bebas)** untuk membuat potongan stiker transparan. |
| **Teks, Sticky Note & Bentuk** | Tambahkan kotak teks ketik, *sticky note* berwarna, serta bentuk **Persegi Panjang**, **Lingkaran**, **Garis**, dan **Panah**. |
| **Alat Seleksi & Manipulasi** | Pilih, pindahkan, ubah ukuran dengan pegangan sudut, duplikat, bawa ke lapisan paling depan, edit teks, potong gambar, atau hapus elemen dengan *Undo/Redo*. |
| **Bilah Atas Terpadu Adaptif** | Seluruh alat dipusatkan di bagian atas agar area bawah layar lapang bagi telapak tangan: otomatis **1 baris penuh di tablet (≥ 840dp)** dan **2 baris rapi di HP (< 840dp)** dengan sub-bar gaya warna & simbol ukuran tipis tepat di bawahnya. |
| **Ekspor PDF & PNG** | Pratinjau langsung sebelum ekspor, pilihan area **Seluruh Isi Catatan** atau **Tampilan Layar Aktif**, simpan langsung ke perangkat (SAF) atau bagikan ke aplikasi lain. |
| **Galeri Catatan Lokal** | Kelola banyak papan catatan, cari judul secara instan, ubah nama/pola latar, dan simpan otomatis (*autosave*) di *background thread* tanpa mengganggu kelancaran menulis. |

---

### ✍️ Panduan Gestur & Mode Stylus

Kanvasa menyediakan tombol **Mode Sentuhan & Stylus** di bilah atas (pilihan tersimpan otomatis):

| Mode | Stylus / Pencil | 1 Jari | 2 Jari |
| --- | --- | --- | --- |
| **Otomatis** *(bawaan)* | Menggambar / menghapus (otomatis mengaktifkan proteksi telapak tangan saat stylus *hover* atau menyentuh layar). | Sebelum stylus terdeteksi: memakai alat aktif. Setelah stylus terdeteksi: **menggeser kanvas** (otomatis terkunci saat stylus *hover* dekat layar). | *Pinch-to-zoom* & geser kanvas. |
| **Hanya Stylus** | Menggambar / menghapus di semua alat (ujung belakang stylus otomatis menjadi penghapus). | **Selalu menggeser kanvas** (tidak pernah mencoret; otomatis terkunci saat stylus *hover* dekat layar). Pada alat *Seleksi*, 1 jari tetap bisa memilih/memindah elemen. | *Pinch-to-zoom* & geser kanvas. |
| **Bebas** | Menggambar / menghapus. | Menggambar / memakai alat aktif secara bebas. | *Pinch-to-zoom* & geser kanvas. |

---

### 🏗️ Arsitektur & Tumpukan Teknologi

- **Bahasa & UI**: Kotlin 2.2, Jetpack Compose, Material Design 3, *Edge-to-Edge WindowInsets*, tipografi lokal (*Space Grotesk* & *Plus Jakarta Sans*).
- **Performa Rendering (*Dual-Layer Canvas*)**:
  - **Lapisan Bawah (GPU Cached)**: Latar pola *Titik* / *Kotak* dirender menggunakan *tiling* `BitmapShader` (1 kali panggilan gambar GPU), elemen tersimpan diseleksi lewat *viewport culling*, dan `Path` goresan disimpan di `LruCache`.
  - **Lapisan Atas (Live Ink Overlay)**: Hanya menggambar 1 goresan aktif dan kursor penghapus tanpa memicu render ulang seluruh papan catatan.
- **Penyimpanan Lokal**: AndroidX Room Database + penyimpanan gambar internal privat + `SharedPreferences` untuk preferensi mode stylus. Serialisasi JSON berjalan sepenuhnya di `Dispatchers.Default` / `Dispatchers.IO` agar tidak pernah memblokir *main thread*.
- **Privasi & Izin Minimal**: Tidak memerlukan izin penyimpanan global (`READ_EXTERNAL_STORAGE`/`READ_MEDIA_IMAGES`). Pemilihan gambar memakai Android Photo Picker (`PickVisualMedia`) dan ekspor berkas memakai Storage Access Framework (`CreateDocument`) serta `FileProvider`.

---

### 🛠️ Panduan Build untuk Pengembang

#### Prasyarat
- **JDK 17**
- **Android SDK** (API 36 / Android 16, `minSdk` 24)
- **Gradle 9.3.1** (atau Android Studio terbaru)

#### Langkah Build Lokal
1. Kloning repositori ini:
   ```bash
   git clone <url-repositori-anda>
   cd <folder-repositori>
   ```
2. Siapkan `debug.keystore` di direktori utama proyek (jika belum ada):
   ```bash
   keytool -genkeypair -v -keystore debug.keystore -storepass android \
     -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 \
     -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
   ```
3. Jalankan *unit test*:
   ```bash
   gradle :app:testDebugUnitTest --tests "com.example.ExampleUnitTest"
   ```
4. Bangun file APK:
   ```bash
   gradle :app:assembleDebug
   ```
   File APK hasil build akan berada di `app/build/outputs/apk/debug/app-debug.apk`.

#### Otomatisasi Rilis via GitHub Actions
Setiap *push* ke branch `main`/`master` otomatis menjalankan *unit test* dan menghasilkan artifact APK. Untuk menerbitkan APK ke halaman **GitHub Releases**, cukup buat dan kirim *tag* versi:
```bash
git tag v1.0.1
git push origin v1.0.1
```

---

### 📄 Lisensi

Proyek ini dilisensikan di bawah **MIT License** — silakan lihat berkas [`LICENSE`](./LICENSE) untuk rincian lengkap.

---

<a id="english"></a>
## 🇬🇧 English

### About Kanvasa

**Kanvasa** is an **infinite canvas note-taking studio** for Android—crafted to feel fast, accurate, and natural on **tablets with an active stylus/pencil** while remaining ergonomic on smartphones.

All notes, handwritten strokes, typed text boxes, sticky notes, geometric shapes, and reference images are stored **100% locally on your device with no account or sign-in required**. When ready, you can export either the entire note canvas or just the active viewport to high-resolution **PDF** or **PNG**.

---

### 📥 Download & Install APK (Ready to Use)

Want to try Kanvasa right away without building from source?

- **Direct Repository Download**: [**`Kanvasa-v1.0.apk`**](./releases/Kanvasa-v1.0.apk) *(click the link and choose **Download raw file** on GitHub)*
- **Automated CI Builds**: Fresh APKs are also uploaded under the **Actions** tab on every push and published automatically to **GitHub Releases** on version tags (`v*`).

#### Android Installation Steps
1. Download **`Kanvasa-v1.0.apk`** onto your Android phone or tablet (Android 7.0 / API 24 or higher).
2. Open the `.apk` file from your file manager or downloads notification.
3. If prompted by Android, allow **Install unknown apps** for your browser or file manager.
4. Tap **Install**, launch **Kanvasa**, and start taking notes.

---

### ✨ Key Features

| Category | Capabilities |
| --- | --- |
| **Infinite Canvas** | Boundless note workspace with smooth pan and pinch-to-zoom (`25%` – `400%`), quick fit-to-content zoom badge, and 3 minimal background patterns: **Dots**, **Grid**, and **Blank**. |
| **Precision Stylus Ink Engine** | Captures full high-frequency **historical stylus samples**, applies ultra-light **corner-preserving smoothing** so sharp handwriting angles stay true, and renders **dynamic pressure-sensitive pen width** (`0.45×` – `1.35×`). |
| **Smart Palm Rejection & 1-Finger Pan** | 3 touch modes (**Auto on Stylus**, **Stylus Only**, **Free**), hardware stylus eraser-tail support, automatic **Hover-Lock** so resting palms never shift the canvas while the pen hovers nearby, and effortless **1-finger canvas panning** once the pen lifts away. |
| **Partial Segment Eraser** | Erases only the exact portion of strokes or shapes touched by the eraser tip (not the whole stroke), with 4 symbol-only tip size presets. |
| **Image Import & Cropping** | Zero-permission image import via Android Photo Picker, plus built-in **Rectangle** and **Custom Freehand** image cropping with transparent cutout support. |
| **Text, Sticky Notes & Shapes** | Place typed text boxes, colored sticky notes, and crisp **Rectangle**, **Circle**, **Line**, and **Arrow** shapes. |
| **Selection & Manipulation** | Select, drag, corner-resize, duplicate, bring to front, edit text, crop images, or delete elements with multi-step Undo/Redo. |
| **Adaptive Unified Top Bar** | Consolidates all tools at the top to leave the lower screen completely clear for your resting palm: automatically **1 unified row on tablets (≥ 840dp)** and **2 tidy rows on phones (< 840dp)** with a slim color/symbol-size sub-bar directly underneath. |
| **PDF & PNG Export** | Live export preview, **Full Content** or **Active Viewport** scope selection, direct save via Storage Access Framework (SAF), or instant share sheet. |
| **Local Notes Gallery** | Create and organize multiple infinite boards, search by title, customize patterns, and enjoy background-thread autosave with zero UI pauses. |

---

### ✍️ Stylus & Gesture Guide

Switch modes anytime from the **Touch & Stylus Mode** menu in the top bar (persisted locally):

| Mode | Stylus / Pencil | 1 Finger | 2 Fingers |
| --- | --- | --- | --- |
| **Auto** *(default)* | Draws / erases (automatically enables palm protection as soon as stylus hover or touch is detected). | Before stylus is detected: uses active tool. After stylus is detected: **pans canvas** (automatically locked while stylus hovers near glass). | Pinch-to-zoom & pan. |
| **Stylus Only** | Draws / erases across all tools (stylus eraser tail automatically switches to Eraser). | **Always pans canvas** (never leaves stray strokes; automatically locked while stylus hovers near glass). With *Select* tool, 1 finger still selects/moves items. | Pinch-to-zoom & pan. |
| **Free** | Draws / erases. | Draws / uses active tool freely. | Pinch-to-zoom & pan. |

---

### 🏗️ Architecture & Tech Stack

- **Language & UI**: Kotlin 2.2, Jetpack Compose, Material Design 3, Edge-to-Edge `WindowInsets`, bundled *Space Grotesk* & *Plus Jakarta Sans* typography.
- **High-FPS Dual-Layer Rendering**:
  - **Committed Base Layer (GPU Cached)**: Repeating `BitmapShader` tile pattern (1 GPU draw call), viewport culling, and `LruCache`-backed `Path` geometry for committed strokes.
  - **Live Ink Overlay**: Draws only the active in-progress stroke and eraser cursor with a zero-copy point buffer, avoiding full-board redraws while writing.
- **Local Persistence**: AndroidX Room Database + private internal image storage + `SharedPreferences` for stylus mode. All JSON serialization runs on `Dispatchers.Default` / `Dispatchers.IO` so autosave never stalls the UI thread.
- **Privacy-First Permissions**: Requires no broad storage permissions. Uses Android Photo Picker (`PickVisualMedia`), SAF (`CreateDocument`), and `FileProvider`.

---

### 🛠️ Building from Source

#### Prerequisites
- **JDK 17**
- **Android SDK** (API 36 / Android 16, `minSdk` 24)
- **Gradle 9.3.1** (or latest Android Studio)

#### Local Build Steps
1. Clone this repository:
   ```bash
   git clone <your-repository-url>
   cd <repository-folder>
   ```
2. Generate `debug.keystore` in the project root (if not already present):
   ```bash
   keytool -genkeypair -v -keystore debug.keystore -storepass android \
     -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 \
     -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
   ```
3. Run unit tests:
   ```bash
   gradle :app:testDebugUnitTest --tests "com.example.ExampleUnitTest"
   ```
4. Assemble the APK:
   ```bash
   gradle :app:assembleDebug
   ```
   The generated APK is output to `app/build/outputs/apk/debug/app-debug.apk`.

#### Automated Releases via GitHub Actions
Every push to `main`/`master` runs unit tests and uploads a build artifact APK. To publish an official APK to **GitHub Releases**, push a version tag:
```bash
git tag v1.0.1
git push origin v1.0.1
```

---

### 📄 License

This project is licensed under the **MIT License** — see the [`LICENSE`](./LICENSE) file for details.
