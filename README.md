# CLAUDEPAD

HP Android menjadi **trackpad, keyboard, media control & volume** untuk PC Windows 10/11.
Koneksi lewat **WiFi / Hotspot** atau **kabel USB**.

Tampilan bergaya Control Center: panel kaca tembus pandang yang memperlihatkan wallpaper HP.

---

## Unduh

**APK:** [Releases › latest](../../releases/tag/latest) — dibangun otomatis oleh GitHub Actions.

---

## 1. Setup PC

1. Install Python 3 dari [python.org](https://python.org) (centang **Add Python to PATH**).
2. Dobel-klik `server/start_server.bat`.
   Dependency terpasang otomatis, lalu server jalan **tanpa jendela konsol**.
3. Jendela CLAUDEPAD menampilkan **PIN** dan **alamat IP**.
4. Saat Windows Firewall bertanya, pilih **Allow access** (Private network).

Tutup jendela = minimize ke **system tray**. Klik ikon tray untuk membuka lagi,
atau klik kanan → Keluar untuk benar-benar menutup.

## 2. Hubungkan HP

### WiFi / Hotspot
HP & PC harus satu jaringan. Buka app → **cari otomatis** (atau ketik IP) → isi **PIN** → **hubungkan**.

### USB
1. Di HP: aktifkan **Developer Options → USB Debugging**.
2. Colok kabel, setujui prompt di HP.
3. Di PC: klik **Mode USB** di jendela server (butuh [ADB platform-tools](https://developer.android.com/tools/releases/platform-tools) di PATH).
4. Di app: tekan **usb**, isi PIN.

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
| tap 2x lalu tahan | drag & drop |

## Tombol

| Simbol | Fungsi | | Simbol | Fungsi |
|---|---|---|---|---|
| ◐ | klik kiri | | ⧉ | salin (Ctrl+C) |
| ⊙ | klik tengah | | ⎘ | tempel (Ctrl+V) |
| ◑ | klik kanan | | ↩ | urungkan (Ctrl+Z) |
| ⏎ | Enter | | ⊞ | Win+Tab |
| ⋯ | buka menu Advance | | ⤢ | ubah orientasi |
| ⚙ | setting | | | |

Menu **Advance** (⋯) berisi: `esc` · `⇥` Tab · `❖` Win · `⌦` Delete.

Kolom bawah kiri: **slider volume** (absolut) + kontrol media.
Kolom bawah kanan: **D-Pad** tombol arah dengan auto-repeat saat ditahan.

## Setting

Koneksi (status, jalur, nama PC, versi server) · getaran haptic · scroll natural ·
layar tetap menyala · orientasi · sensitivitas kursor · change log · panduan gesture ·
bantuan (README).

---

## Build APK sendiri

1. Install [Android Studio](https://developer.android.com/studio).
2. **Open** → pilih folder `android/`, tunggu Gradle sync.
3. **Build → Build App Bundle(s)/APK(s) → Build APK(s)**.

Font JetBrains Mono diunduh otomatis oleh task Gradle `fetchFont`.
Kalau offline, aplikasi memakai monospace bawaan sistem — build tetap berhasil.

## Keamanan

- Server meminta **PIN** acak yang berubah tiap kali dijalankan.
- Hanya untuk jaringan lokal. Jangan buka port 8765 ke internet.

## Troubleshooting

| Masalah | Solusi |
|---|---|
| "cari otomatis" tidak ketemu | Cek firewall (izinkan Python), pastikan satu subnet, isi IP manual |
| Volume tidak berubah | `pip install pycaw comtypes` — tanpa itu slider tidak berfungsi |
| Tray tidak muncul | `pip install pystray pillow` |
| USB gagal | Cek `adb devices` menampilkan device (bukan `unauthorized`) |
| Wallpaper tidak terlihat | Sebagian launcher/mode hemat daya mematikan wallpaper di balik aplikasi |

## Roadmap

Bluetooth (RFCOMM) — protokol JSON sudah siap dipakai ulang, tinggal ganti transport.

---

## Struktur

```
CLAUDEPAD/
├── server/
│   ├── pc_server.py      GUI desktop + layer WebSocket
│   ├── input_core.py     injeksi input, volume, gesture, discovery
│   ├── start_server.bat  jalankan tanpa konsol
│   └── usb_mode.bat      adb reverse
├── android/              project Android Studio (Kotlin)
└── docs/
    └── bug-report.html   laporan pemeriksaan bug
```
