# CLAUDEPAD

HP Android menjadi **trackpad, keyboard, media control, dan pengatur volume**
untuk PC Windows 10/11. Terhubung lewat **WiFi / Hotspot** atau **kabel USB**.

Tampilan bergaya Control Center: panel kaca dengan wallpaper HP yang menembus
di belakangnya, warna aksen mengikuti wallpaper, dan font monospace.

**[⬇ Unduh APK terbaru](../../releases/tag/latest)** — dibangun otomatis oleh GitHub Actions.

> **Versi APK dan server harus sama.** Server menolak koneksi bila berbeda.
> Setiap kali memperbarui APK, perbarui juga folder `server/`.

---

## 1. Menyiapkan PC

**Cara termudah (tanpa Python):** unduh **CLAUDEPAD-Server.exe** dari
[Releases](../../releases/tag/latest), dobel-klik. Python dan semua dependensi
sudah terbungkus di dalamnya. Setujui prompt Administrator saat firewall
diminta, dan (bila muncul) klik "Run anyway" pada peringatan SmartScreen —
wajar untuk aplikasi tak bersertifikat.

**Cara lama (untuk yang mau mengoprek kode):**

1. Install **Python 3** dari [python.org](https://python.org) — centang **Add Python to PATH**.
2. Dobel-klik **`server/start_server.bat`**.
   - Saat pertama kali, Windows meminta izin **Administrator** untuk membuka
     firewall. Setujui — tanpa ini koneksi WiFi tidak akan bisa.
   - Dependency terpasang otomatis, lalu server jalan **tanpa jendela konsol**.
3. Jendela CLAUDEPAD menampilkan **PIN**, **alamat IP**, dan **status firewall**.

**Bacalah alamat IP dengan benar.** Alamat yang diberi label *virtual, jangan
dipakai* berasal dari adapter WSL/Hyper-V/Docker dan **tidak bisa** dijangkau HP.
Pakai alamat yang tampil terang di baris paling atas.

Menutup jendela = minimize ke **system tray**. Klik ikon tray untuk membukanya
lagi, klik kanan → **Keluar** untuk benar-benar menutup.

## 2. Menghubungkan HP

### WiFi / Hotspot
HP dan PC harus berada di jaringan yang sama — boleh lewat router yang sama,
hotspot HP, atau hotspot PC.

Buka aplikasi → **cari otomatis** (atau ketik IP dari jendela server) →
isi **PIN** → **hubungkan**.

### USB
1. Di HP: aktifkan **Developer Options → USB Debugging**.
2. Colok kabel, setujui prompt di HP.
3. Di PC: klik **Mode USB** di jendela server.
   Butuh [ADB platform-tools](https://developer.android.com/tools/releases/platform-tools)
   di PATH atau di folder `server/`.
4. Di aplikasi: tekan **usb**, isi PIN.

---

## Gesture trackpad

Mengikuti standar **Windows Precision Touchpad**:

| Gesture | Fungsi |
|---|---|
| 1 jari geser | gerakkan kursor |
| 1 jari tap | klik kiri |
| 2 jari tap | klik kanan |
| 2 jari geser | scroll |
| 2 jari cubit | zoom (Ctrl + scroll) |
| 3 jari ke atas | Task View |
| 3 jari ke bawah | Show Desktop |
| 3 jari kiri / kanan | ganti aplikasi |
| 2 jari geser mendatar | scroll horizontal |
| 3 jari ketuk | klik tengah |
| 2 jari geser mendatar | scroll horizontal (sumbu terkunci) |
| tap 2× lalu tahan | drag & drop |

## Tombol

| | | | | |
|---|---|---|---|---|
| ◐ | klik kiri | | ⧉ | grup salin & tempel |
| ⊙ | klik tengah | | ↩ | grup urungkan & ulangi |
| ◑ | klik kanan | | ⇌ | Win+Tab |
| ⏎ | Enter | | ⊗ | Ctrl+W tutup tab |
| ⋯ | panel Advance | | ⚙ | setting |
| ⤢ ⤡ ⤣ | rotasi input 0° / 90° / 270° | | ⏻ | putuskan koneksi |

Tombol bertanda **grup** membuka pop-up kecil di atasnya:
**⧉** berisi `copy` dan `paste`, **↩** berisi `undo` dan `redo`, dan
**SIGNAL** berisi `wifi`, `bluetooth`, serta `hotspot` untuk PC.

Panel **Advance** juga memuat sleep layar dan lock PC.

**Mute** dilakukan dengan mengetuk ikon speaker pada slider volume.

Panel **Advance** (⋯) muncul sebagai pop-up berisi `esc` · `⇥` Tab · `❖` Win ·
`⌦` Delete · `⚙` Ctrl+, (buka pengaturan).

**Baris bawah** terbagi dua kolom seimbang: slider volume dan kontrol media di
kiri, **D-Pad** tombol arah dengan auto-repeat di kanan.

### Mengetik

Sentuh kolom **"ketik di sini"** untuk membuka panel ketik di tengah layar
dengan latar diburamkan. Posisinya tetap terpusat dan tidak bergeser saat
keyboard muncul. Panel melebar ke atas mengikuti panjang teks, dan bertahan
sampai keyboard ditutup. Huruf dikirim langsung ke PC saat diketik,
termasuk backspace.

### Orientasi tampilan

Tombol **⤢ / ⤡ / ⤣** memutar seluruh tampilan bergiliran antara **0°, 90°,
dan 270°**, memakai tata letak lanskap tersendiri saat diputar. Arah gestur
selalu mengikuti layar apa adanya — geser ke atas berarti kursor ke atas, di
orientasi mana pun.

### Umpan balik visual trackpad

**Show taps** menampilkan riak lingkaran di titik sentuhan (default aktif),
sedangkan **pointer location** menambahkan garis bidik dan koordinat setiap
jari (default mati). Keduanya diatur di ⚙ Setting.

### Indikator & log ping

Saat memakai WiFi/Hotspot, latensi tampil di samping nama PC dan berganti
warna mengikuti kualitas koneksi — hijau berarti responsif, merah berarti
lambat.

Setiap pengukuran juga direkam. Di **⚙ Setting → LOG PING** kamu bisa melihat
laporan lengkap (rata-rata, median, jitter, jumlah sampel lambat), lalu
menyimpannya sebagai berkas teks dan membagikannya lewat lembar berbagi
Android ke aplikasi mana pun.

### Daya PC & Wake-on-LAN

Di puncak halaman **⚙ Setting** ada kontrol daya: matikan, mulai ulang,
tidurkan, hibernasi, dan keluar sesi. Aksi berisiko selalu dikonfirmasi.

**Wake-on-LAN bersifat eksperimental.** Fitur ini menyalakan PC dari HP, tapi
keberhasilannya di luar kendali aplikasi: WoL harus diaktifkan di BIOS/UEFI
dan pada properti adapter jaringan Windows, dan umumnya hanya bekerja lewat
kabel LAN — banyak adapter WiFi tidak mendukungnya.

Menyalakan PC **lewat kabel USB dari HP tidak dimungkinkan**. Android tidak
mengizinkan aplikasi biasa menyamar sebagai perangkat USB yang mampu
membangunkan komputer, dan port USB PC umumnya tidak menyalurkan daya saat
komputer benar-benar mati.

### Kontrol koneksi PC

Tombol **📶** menyalakan atau mematikan **WiFi**, **Bluetooth**, dan
**hotspot** di PC. WiFi dan Bluetooth memakai Windows Radio Management API
sehingga tidak butuh hak Administrator. Hotspot memakai WinRT tethering dan
memerlukan koneksi internet aktif di PC — bila tidak didukung, alasannya
ditampilkan di HP.

## Setting

- **Koneksi** — status, jalur (WiFi/USB), nama PC, versi server
- **Perilaku** — haptic, scroll natural, layar tetap menyala, show taps,
  pointer location, rotasi input
- **Sensitivitas & intensitas** — kecepatan kursor, blur background, kekuatan haptic
- **Tentang** — versi APK, change log, panduan gesture, bantuan
- **Diagnosa koneksi** — lihat bagian berikutnya

---

## Kalau koneksi bermasalah

### Langkah 1 — Diagnosa koneksi
Buka **⚙ Setting → diagnosa koneksi**. Laporannya menunjukkan interface HP
(mana hotspot, mana seluler), rute yang dipilih, hasil tes TCP, balasan server,
dan tes pencarian — lengkap dengan kesimpulan. Laporan bisa disalin.

### Langkah 2 — Tes lewat browser
Buka **`http://<ip-pc>:8765`** di browser HP.

- **Muncul halaman status** → jaringan dan firewall sudah benar; masalahnya di aplikasi.
- **Tidak bisa dijangkau** → firewall atau alamat IP-nya salah.

### Tabel masalah umum

| Masalah | Penyebab & solusi |
|---|---|
| Cari otomatis tidak ketemu | Klik **Perbaiki Firewall** di jendela server, setujui prompt Administrator |
| Koneksi timeout | IP yang dipakai kemungkinan adapter virtual — pakai alamat yang tidak berlabel virtual |
| Jendela server berstatus merah | Server gagal jalan; baca pesan di kotak log |
| "versi tidak cocok" | Perbarui APK dan folder `server/` ke versi yang sama |
| Volume tidak berubah | `pip install pycaw comtypes` — slider otomatis beralih ke mode bertingkat |
| Tray tidak muncul | `pip install pystray pillow` |
| USB gagal | Cek `adb devices` menampilkan device, bukan `unauthorized` |
| Wallpaper tidak terlihat | Sebagian launcher/mode hemat daya mematikannya; naikkan slider blur di setting |

---

## Build APK sendiri

1. Install [Android Studio](https://developer.android.com/studio).
2. **Open** → pilih folder `android/`, tunggu Gradle sync.
3. **Build → Build App Bundle(s)/APK(s) → Build APK(s)**.

Font JetBrains Mono opsional: jalankan `android/fetch-font.sh` (atau `.bat`).
Tanpa file font, aplikasi memakai monospace bawaan sistem dan build tetap berhasil.

## Keamanan

- Server meminta **PIN acak** yang berubah setiap kali dijalankan.
- Setelah berhasil sekali, server memberi **token pairing** sehingga koneksi
  berikutnya tidak perlu mengetik PIN. Token disimpan di `server/paired.txt`
  dan bisa dihapus lewat Setting atau dengan menghapus berkas itu.
- **Enkripsi lalu lintas (v3.0+).** Semua perintah dienkripsi ChaCha20 dengan
  tag HMAC-SHA256; kunci sesi diturunkan dari PIN/token memakai PBKDF2 dan
  tidak pernah dikirim. Menutup penyadapan dan pengubahan lalu lintas di
  jaringan lokal.
- **Kunci versi** — APK dengan versi berbeda dari server ditolak.

## Notifikasi kontrol

Sejak v3.0 muncul notifikasi permanen selama aplikasi berjalan. Saat terhubung
berisi kontrol media dan volume plus info koneksi; saat belum terhubung berisi
tombol untuk menyambung langsung. Bisa dipanggil ulang lewat **⚙ Setting →
tampilkan notifikasi kontrol**.

## Tombol makro kustom

Di **⚙ Setting → TOMBOL MAKRO** kamu bisa membuat sampai enam pintasan sendiri
— tentukan label, tombol, dan modifier (Ctrl/Shift/Alt/Win). Tombolnya muncul
di barisnya sendiri di layar utama.
- **Kunci versi** — APK dengan versi berbeda ditolak.
- Hanya untuk jaringan lokal. Jangan buka port 8765 ke internet.

---

## Struktur

```
CLAUDEPAD/
├── server/                 jalan di PC Windows
│   ├── pc_server.py        GUI desktop + layer WebSocket
│   ├── input_core.py       injeksi input, volume, gesture, discovery, firewall
│   ├── crypto_box.py       enkripsi ChaCha20 + HMAC (pasangan CryptoBox.kt)
│   ├── system_ctl.py       kecerahan, daya, MAC untuk WoL
│   ├── start_server.bat    jalankan tanpa konsol
│   ├── fix_firewall.bat    pasang aturan firewall (via UAC)
│   └── usb_mode.bat        adb reverse
├── android/                project Android Studio (Kotlin)
└── docs/
    └── bug-report.html     laporan pemeriksaan bug
```

### Catatan teknis

Server mendukung **kedua generasi API `websockets`** (di bawah 14 dan 14 ke
atas) karena keduanya memakai bentuk `process_request` yang berbeda. Setiap
build CI mengujinya di websockets **12.0, 13.1, dan 15.0.1**.

Saat HP menjadi hotspot sambil data seluler menyala, Android mengikat socket
aplikasi ke jaringan seluler. Aplikasi mengatasinya dengan mencari alamat lokal
yang **satu subnet** dengan PC lalu mengikat socket ke alamat tersebut.

## Roadmap

Bluetooth (RFCOMM) — protokol JSON sudah siap dipakai ulang, tinggal mengganti
lapisan transport.
