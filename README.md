# Nexora Draw Studio

Editor Android native Kotlin + engine Java, offline, tanpa server/API key. Target Android 8.0+ (API 26), compile/target SDK 35. Fondasi MVP: satu brush smooth CiDrawing, eraser, layer bitmap/gambar/vector, shape vector yang tetap editable, SVG impor read-only, NXD, PNG dan Android Sharesheet. Status pengujian aktual ada di [VALIDATION.md](VALIDATION.md).

## Unduh APK dari HP

1. Pastikan commit proyek sudah tersedia di GitHub. Buka repository → **Actions → Android APK**. Workflow berjalan saat push ke `main`, `work`, `develop`, `codex/**`, atau **Run workflow**.
2. Pilih run yang selesai hijau. Di bagian **Artifacts**, unduh **nexora-debug-apk** (login GitHub mungkin diperlukan).
3. Ekstrak ZIP artifact melalui aplikasi Files. Buka `app-debug.apk`, izinkan instalasi dari aplikasi sumber tersebut bila Android meminta, lalu pasang.
4. Jika run gagal, buka step merah dan artifact **android-build-reports**. APK hanya diunggah setelah unit test, lint dan build berhasil.

Artifact memiliki masa simpan GitHub dan dapat kedaluwarsa. Debug APK menggunakan signing debug otomatis, bukan production key. Update dari mesin CI berbeda dapat memiliki kunci debug berbeda sehingga perlu uninstall; ekspor NXD dahulu agar data aman. Tidak ada biaya hosting; penggunaan Actions mengikuti kuota akun GitHub.

## Alur uji di HP

* Proyek baru → Shape → circle. Layer vector baru otomatis dipilih.
* Layer → + Bitmap → Tool → Brush. Gambar stroke, lalu Eraser untuk menghapusnya. Ukuran dan opacity ada di bawah kanvas; Warna menerima HEX.
* Tool → Select untuk memilih layer lewat kanvas, Move untuk menyeret layer. Layer → pilih nama → Transform untuk x/y/scale/rotasi, Stroke vector untuk warna/ukuran garis. Pinch dua jari untuk zoom/pan; Tool → Pan untuk satu jari; Fit mengembalikan tampilan.
* Layer panel mendukung tambah bitmap, urutan, rename, show/hide, lock, opacity dan delete. Tombol Undo/Redo menangani edit dokumen dasar dengan batas memori. Layer terkunci tidak dapat diedit.
* Menu → Save As NXD. Tutup/reopen lalu Buka NXD. Circle harus tetap vector dan stroke tetap bitmap. Home → Pulihkan autosave tersedia untuk recovery.
* Menu → Export PNG transparan/putih; Share NXD membuka Sharesheet. Buka/Impor menerima PNG, JPEG dan subset SVG. NXD dibuka sebagai dokumen pengganti; gambar/SVG ditambahkan sebagai layer. Simpan sebelum membuka proyek lain.

NXD memakai SAF tanpa izin seluruh penyimpanan. Export SVG hanya menerima dokumen vector murni; dokumen hybrid harus diekspor PNG/NXD agar bitmap tidak hilang.

## Batas versi awal

768×768, maksimal 8 layer. Satu brush smooth nyata; belum pencil/airbrush/marker/pressure/stabilization lanjutan. Native circle/rectangle/ellipse/line/predefined cubic path dapat diubah fill/stroke/transform; belum node editing, freeform vector pen, grouping, boolean, selection bitmap, bucket fill, text, symmetry, guides atau CDR. Color picker berupa HEX, belum hue wheel/RGB/HSL controls. SVG impor mempertahankan sumber vector tetapi tidak menyediakan edit node; XML yang tidak didukung ditolak dengan pesan. Tidak ada tombol fitur palsu.

## Build tanpa komputer lokal

Workflow: [.github/workflows/android-build.yml](.github/workflows/android-build.yml). Output: `app/build/outputs/apk/debug/app-debug.apk`.

Toolchain: JDK 17, Gradle Wrapper 8.11.1 (checksum pinned), AGP 8.9.2, Kotlin 2.1.20, SDK/platform/build-tools 35/35.0.0. Java source engine lama dipertahankan tanpa support-v7 runtime. CI menyiapkan Android SDK dan cache Gradle.

Untuk lingkungan pengembangan yang memiliki SDK/JDK:

```sh
./gradlew --no-daemon --console=plain testDebugUnitTest lintDebug assembleDebug
```

Tidak memerlukan signing key produksi. Tidak ada kode cloud/AI/subscription. Baca [ENGINE_AUDIT.md](ENGINE_AUDIT.md), [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), dan [NXD_FORMAT.md](NXD_FORMAT.md).
