# Ruang Putar Musik (Android) — v0.2.0

Versi Android dari Ruang Putar: **ringan, khusus musik**, tanpa mode video dan tanpa sambungan ke PC.
Dibuat oleh **@SuS_X_x**.

- Android 7.0 (API 24) sampai Android terbaru (target API 36)
- Kotlin + Jetpack Compose + Media3 ExoPlayer + NewPipeExtractor (tanpa ffmpeg, tanpa yt-dlp)

## Fitur v0.1
- Cari lagu (filter *Lagu* / *Semua video*) dengan tombol halaman ◀ ▶
- Putar di latar belakang: notifikasi, layar kunci, tombol headset/Bluetooth
- Antrean (naik/turun/hapus), acak, ulang, timer tidur
- Favorit, Riwayat, **Sering kamu putar**
- Beranda dengan rak + filter durasi (mis. < 5 menit), bisa tambah/ubah/geser rak
- Penyanyi: buka lagu lain dari channel, ikuti penyanyi
- Unduh ke Offline → folder `Music/RuangPutar` (M4A/Opus, langsung dari YouTube, tanpa konversi)
- Cache lagu (ukuran bisa diatur), batas kecepatan unduh (Rendah / Normal / Tanpa batas)
- Kalau YouTube mengira bot: aplikasi berhenti meminta selama 30 menit (tidak mengulang terus)
- Tema Gelap / Terang / Batik
- Bagikan link YouTube dari aplikasi lain → **Bagikan → Ruang Putar Musik** langsung diputar

## Cara membuat APK (lewat GitHub)
Repo **Public** + kunci tanda tangan disimpan di **GitHub Secrets** (tidak ikut terlihat).

1. Buat repository baru di GitHub (**Public**), tanpa README/.gitignore/license.
2. **Settings → Secrets and variables → Actions → New repository secret**
   - Name: `KUNCI_BASE64`
   - Secret: tempel seluruh isi file `kunci-base64.txt` (dari paket kunci terpisah).
3. Unggah seluruh isi folder ini **kecuali** folder `kunci` (jangan pernah unggah file `.jks` ke repo Public).
   Folder `.github` biasanya tidak ikut terunggah lewat web — buat manual:
   *+ → Create new file* → `.github/workflows/build-apk.yml` → tempel isinya.
4. Tab **Actions** → **Build APK** berjalan → hasilnya terbit di **Releases** (dan di Artifacts).

## Pembaruan otomatis
- Setiap kali ada perubahan kode (commit) → APK baru terbit di **Releases**.
- Setiap Senin 03.00 WIB GitHub mengecek versi baru **NewPipeExtractor** (mesin YouTube).
  Ada versi baru → otomatis build & terbit. Tidak ada → tidak terjadi apa-apa.
- Aplikasi mengecek Releases sekali sehari, langsung mengecek bila lagu sering gagal,
  dan bisa dicek manual: **Pengaturan → Pembaruan → Cek pembaruan**.
- Ada versi baru → *Perbarui* → APK diunduh → jendela pasang Android → *Perbarui*.
- Pertama kali, Android meminta izin "Instal aplikasi tidak dikenal" untuk Ruang Putar Musik (sekali saja).
- GitHub Actions gratis tanpa batas untuk repo Public.

## Memasang di HP
1. Unduh APK dari **Releases** (atau Artifacts), pindahkan ke HP, buka dengan File Manager.
2. Izinkan "Instal aplikasi tidak dikenal" bila diminta → Instal.
3. Buka **Ruang Putar Musik**, izinkan notifikasi.

**Simpan file `ruangputar.jks` baik-baik.** Selama kuncinya sama, versi baru selalu bisa
dipasang di atas versi lama tanpa kehilangan data.

## Kalau build gagal
Buka run yang merah di tab Actions → klik langkah yang gagal → salin log bagian error
(cari baris `e: ` atau `FAILURE`) lalu kirim ke Claude.

## Lisensi pihak ketiga
NewPipeExtractor (GPL-3.0), AndroidX / Media3 (Apache-2.0), OkHttp (Apache-2.0).
