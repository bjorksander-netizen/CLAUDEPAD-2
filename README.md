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
| tap 2× lalu tahan | drag & drop |

## Tombol

| | | | | |
|---|---|---|---|---|
| ◐ | klik kiri | | ⧉ | salin (Ctrl+C) |
| ⊙ | klik tengah | | ⎘ | tempel (Ctrl+V) |
| ◑ | klik kanan | | ↩ | urungkan (Ctrl+Z) |
| ⏎ | Enter | | ⊞ | Win+Tab |
| ⋯ | panel Advance | | ⚙ | setting |
| ⤢ ⤡ ⤣ | rotasi input 0° / 90° / 270° | | ⏻ | putuskan koneksi |

Panel **Advance** (⋯) muncul sebagai pop-up berisi `esc` · `⇥` Tab · `❖` Win · `⌦` Delete.

**Baris bawah** terbagi dua kolom seimbang: slider volume dan kontrol media di
kiri, **D-Pad** tombol arah dengan auto-repeat di kanan.

### Mengetik

Sentuh kolom **"ketik di sini"** untuk membuka panel ketik di tengah layar
dengan latar diburamkan. Panel melebar ke atas mengikuti panjang teks, dan
bertahan sampai keyboard ditutup. Huruf dikirim langsung ke PC saat diketik,
termasuk backspace.

### Rotasi input

Tombol **⤢ / ⤡ / ⤣** memutar arah input trackpad bergiliran antara **0°, 90°,
dan 270°** — layout tidak berubah sama sekali. Pada 90°, geser ke kanan
menggerakkan kursor ke atas; pada 270° ke bawah. Tulisan di dalam kotak
trackpad ikut berputar mengikuti orientasi aktif. Berguna bila HP diletakkan
menyamping.

### Indikator ping

Saat memakai WiFi/Hotspot, latensi tampil di samping nama PC dan berganti
warna mengikuti kualitas koneksi — hijau berarti responsif, merah berarti
lambat.

## Setting

- **Koneksi** — status, jalur (WiFi/USB), nama PC, versi server
- **Perilaku** — haptic, scroll natural, layar tetap menyala, rotasi input
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
- **Kunci versi** — APK dengan versi berbeda ditolak.
- Hanya untuk jaringan lokal. Jangan buka port 8765 ke internet.

---

## Struktur

```
CLAUDEPAD/
├── server/                 jalan di PC Windows
│   ├── pc_server.py        GUI desktop + layer WebSocket
│   ├── input_core.py       injeksi input, volume, gesture, discovery, firewall
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
