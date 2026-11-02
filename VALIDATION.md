# Validasi — 2 November 2026, 16:24 WIB

**Build lokal cloud berhasil. APK debug tersedia. Pengujian perangkat nyata belum dilakukan.**

Perintah inti yang dijalankan melalui Gradle Wrapper:

```sh
./gradlew --no-daemon --console=plain testDebugUnitTest lintDebug assembleDebug
python3 tools/verify_vendored.py
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --verbose app/build/outputs/apk/debug/app-debug.apk
$ANDROID_HOME/build-tools/35.0.0/aapt dump badging app/build/outputs/apk/debug/app-debug.apk
```

Di cloud ini Gradle dijalankan dengan JAVA_HOME JDK 17 lengkap, cache di `/workspace/toolchains/gradle-home`, proxy HTTP/HTTPS platform, dan truststore `/etc/ssl/certs/java/cacerts`. Pengaturan proxy diteruskan ke JVM Robolectric melalui init script lokal di luar repository. TLS/checksum tidak dinonaktifkan. Saved install_script telah dieksekusi ulang dan berhasil; konfigurasi cloud tersimpan sebagai draft, belum merupakan publikasi/snapshot task baru.

| Pemeriksaan | Hasil |
|---|---|
| Wrapper official SHA-256 + distribusi Gradle checksum | PASS; wrapper diregenerasi dari Gradle resmi |
| Kompilasi Kotlin/Java + resource/manifest | PASS |
| Unit/Robolectric test | **12 executed, 0 failed, 0 errors, 0 skipped** |
| Android lint app | PASS, 0 errors, 12 warnings |
| Android lint engine | PASS, 0 errors, 18 warnings |
| assembleDebug | PASS, APK 1,125,713 bytes |
| APK signature | PASS, debug APK Signature Scheme v2 |
| Package metadata | `com.nexora.studio`, version 0.1.0, minSdk 26 / targetSdk 35 |
| Audit source provenance | PASS, 127 upstream source/resource files unchanged |
| GitHub Actions remote run | Workflow disiapkan; hasil run remote belum diverifikasi |
| Emulator / HP fisik | BELUM DIJALANKAN; tidak ada perangkat/emulator terpasang, `/dev/kvm` tidak tersedia |

Tes mencakup round-trip gabungan circle vector + stroke bitmap, ID/order/transform/opacity/lock/visibility, pixel PNG transparan dan isi kanvas, export yang tidak mengubah dokumen, eraser, undo/redo, PNG/JPEG import, corrupt image, SVG holes/clipping/transform dan preservasi XML, penolakan SVG berbahaya/tidak didukung, bentrok ID resource saat export SVG, ZIP traversal/arsip rusak/ZIP bomb, recovery write terputus, input sentuhan brush/eraser pada Android View, serta penolakan path traversal/write oleh share provider.

Tes UI adalah Robolectric native graphics SDK 28, bukan uji install/gesture/lifecycle di perangkat Android 15. Sharesheet Intent dan provider terimplementasi; pengiriman ke aplikasi penerima nyata belum diuji. Recovery internal teruji; atomic replacement pada semua SAF document provider tidak dijamin. Selection memakai bounding box layer, belum hit testing kontur presisi. Node editing, grouping, boolean, pressure, pencil, text, dan color wheel belum tersedia.

Warnings app terutama teks UI Indonesia belum diekstrak ke resource terjemahan dan constructor custom View programatik. Warnings engine meliputi API deprecated/legacy, reflection di serialisasi CiPaint yang tidak dipakai oleh codec Nexora, dan AsyncTask pada SVGImageView yang tidak dipakai compositor. Tidak ada lint error yang dinonaktifkan. Log native decoder untuk fixture gambar rusak adalah jalur penolakan yang diharapkan; tesnya lulus.

Kegagalan awal yang diselesaikan: sandbox mencegah akses proxy; Java bawaan hanya JRE tanpa compiler; JDK unduhan memerlukan truststore CA platform; Robolectric memerlukan pewarisan proxy. Tidak ada failure wajib yang masih belum terdiagnosis pada build lokal. Dokumentasi toolchain eksternal sempat diblokir policy jaringan; versi pinned dibuktikan lewat build nyata, bukan klaim bahwa versi tersebut selalu yang paling baru.

Bukti: [log build](audit/build-validation.log), [ringkasan mesin](audit/validation-summary.json), [signature](audit/apk-signature.txt), [dependency runtime](audit/runtime-dependencies.txt), dan laporan lengkap lokal `app/build/reports/`, `engines/build/reports/`. Modul engine tidak memiliki unit test sendiri (`NO-SOURCE`); 12 tes aplikasi menjalankan engine nyata. APK: `app/build/outputs/apk/debug/app-debug.apk`.

SHA-256 APK: `c7c1662c06d9f986863445f2002c586e748aa0e4667f48eedab030c1d3545b52`.
