# Changelog

Semua perubahan penting CLAUDEPAD. Versi APK dan server selalu dinaikkan
bersamaan, dan server menolak koneksi bila keduanya berbeda.

| Versi | Inti perubahan |
|---|---|
| **v2.8** | D-Pad kembali ke bentuk salib; tombol digabung jadi grup pop-up; kontrol koneksi PC; log ping |
| v2.6 | Panel ketik terkunci di tengah; D-Pad bundar faset; pointer location & show taps |
| v2.5 | Panel ketik pop-up dengan latar blur; indikator ping; rotasi input 3 arah |
| v2.4 | Perbaikan fatal HTTP 500 di semua koneksi; firewall benar; logo aplikasi |
| v2.3 | Koneksi WiFi/Hotspot: adapter virtual disaring, socket diikat ke interface hotspot; mode diagnosa |
| v2.2 | Slider blur & haptic, backspace kembali, kunci versi, tombol putuskan koneksi |
| v2.1 | Perbaikan trackpad hilang & D-Pad raksasa; slider volume; pop-up Advance |
| v2.0 | Tema Control Center, gesture Precision Touchpad, D-Pad, haptic, halaman Setting |
| v1.0 | Rilis pertama |

---

## v2.8

### Revisi
- **D-Pad dikembalikan** ke bentuk salib seperti sebelumnya. Sorot arah yang
  ditekan tetap memakai warna aksen yang mengikuti wallpaper.
- **Salin & tempel digabung** menjadi satu tombol grup yang membuka pop-up
  berisi tombol `copy` dan `paste`.
- **Win+Tab dipindah** ke posisi tombol urungkan sebelumnya.

### Fitur baru
- **Grup urungkan & ulangi** — tombol undo dan redo dalam satu pop-up,
  menempati posisi tombol tempel sebelumnya. Redo dikirim sebagai Ctrl+Y.
- **Kontrol koneksi PC** — satu tombol grup membuka pop-up berisi wifi,
  bluetooth, dan hotspot, menempati posisi tombol Win+Tab sebelumnya.
  WiFi dan Bluetooth memakai Windows Radio Management API sehingga
  **tidak memerlukan hak Administrator**; hotspot memakai WinRT tethering.
  Setiap hasil dilaporkan kembali ke HP, termasuk alasan bila gagal.
- **Log ping** — latensi direkam otomatis saat memakai WiFi (maksimum 600
  sampel). Di halaman setting tersedia ringkasan, laporan lengkap dengan
  rata-rata, median, jitter dan jumlah sampel lambat, serta tombol
  **simpan & bagikan** yang menulis berkas teks lalu membuka lembar berbagi
  Android sehingga tujuannya bisa kamu pilih sendiri.

## v2.6

### Revisi
- **Panel ketik terkunci di tengah layar** — jendela panel tidak lagi digeser
  atau diperkecil saat keyboard bawaan muncul (`SOFT_INPUT_ADJUST_NOTHING`),
  jadi posisinya tetap terpusat secara horizontal dan vertikal. Pertumbuhan
  ke atas tetap dipertahankan.
- **Simbol Win+Tab** diganti menjadi **⇌**.
- **D-Pad digambar ulang** menjadi piringan bundar bergaya faset: gradasi
  gelap mengilap, empat belas potongan segi yang memantulkan cahaya
  berbeda-beda, tepi berkilau, dan dua kaki kecil di bawah.

### Fitur baru
- **Pintasan Ctrl+,** (buka pengaturan aplikasi Windows) di panel Advance,
  memakai ikon gir.
- **Pointer location** — garis bidik dan koordinat setiap jari di trackpad.
  Default **mati**.
- **Show taps** — riak lingkaran yang meredup di titik sentuhan, termasuk
  untuk jari kedua dan ketiga. Default **aktif**.
- **Nama berkas APK memuat versi**, misalnya `CLAUDEPAD-v2.6.apk`.

## v2.5

### Revisi aplikasi
- **Panel ketik pop-up.** Menyentuh kolom "ketik di sini" kini membuka panel
  di tengah layar dengan latar diburamkan (mode fokus). Panel **melebar ke
  atas** mengikuti panjang teks — tepi bawahnya dikunci setelah pengukuran
  pertama. Tombol Enter tetap tersedia, dan panel bertahan sampai keyboard
  ditutup sehingga nyaman untuk mengetik beberapa baris berturut-turut.
  Teks dikirim per huruf, jadi langsung tampil di PC.
- **Tombol ⌫ dihapus** dari baris utama; fungsi backspace tetap bekerja lewat
  penghapusan di panel ketik.
- **Indikator ping** muncul di samping status saat memakai WiFi/Hotspot,
  diukur tiap 2 detik lewat ping/pong. Warnanya mengikuti kualitas koneksi:
  hijau di bawah 40 ms, hijau kekuningan sampai 90 ms, kuning sampai 180 ms,
  jingga sampai 350 ms, merah di atasnya.
- **Rotasi input tiga arah** — 0°, 90°, dan 270°, berputar bergiliran lewat
  tombol yang sama. Tulisan di dalam kotak trackpad ikut berputar mengikuti
  orientasi yang sedang aktif. Gerakan kursor, scroll dua jari, dan gesture
  tiga jari semuanya menyesuaikan.

## v2.4

### Perbaikan fatal
- **Semua koneksi ditolak `500 Internal Server Error` sejak v2.3** — termasuk
  USB yang sebelumnya normal. Halaman tes (`process_request`) ditulis memakai
  API `websockets` lama `(path, headers)`, sedangkan websockets 14+ memakai
  `(connection, request)`. Di PC dengan versi baru, setiap handshake gagal.
  Pin versi di `requirements.txt` tidak pernah menolong karena
  `start_server.bat` hanya mengecek modul *ada*, bukan versinya.
  Server kini **mendeteksi generasi API dan menyesuaikan diri**, diuji lulus
  di websockets **12.0, 13.1, dan 15.0.1**.
- **Tombol Perbaiki Firewall tidak berefek** — perintah memakai `;` sebagai
  pemisah, padahal `cmd.exe` memakai `&`. Kini memanggil `fix_firewall.bat`
  terpisah lewat UAC, lalu **memverifikasi** hasilnya. Aturan juga dipasang
  per-program untuk `python`/`pythonw`, bukan hanya per-port.

### Ketahanan
- Server melakukan **uji mandiri** setelah start. Bila tidak bisa dihubungi,
  jendela server menampilkan status merah dan alasannya — tidak lagi terlihat
  sehat padahal mati.

### Tampilan
- **Logo aplikasi baru**: adaptive icon dengan latar gradasi ungu dan lambang
  trackpad + kursor.


## v2.3

### Perbaikan koneksi WiFi/Hotspot (akar masalah ditemukan)
Pesan error pengguna membongkar dua bug nyata:
`failed to connect to /172.25.224.1 ... from /10.240.31.97`

- **Server menampilkan IP adapter virtual.** `172.25.224.1` adalah adapter
  WSL/Hyper-V, bukan alamat hotspot — alamat itu mustahil dijangkau HP.
  Deteksi IP kini membaca nama adapter dari `ipconfig`, menyaring
  WSL/Hyper-V/Docker/VirtualBox/VPN, dan mengurutkan alamat hotspot
  (192.168.43.x) paling atas. Adapter virtual tetap ditampilkan tetapi
  diredupkan dan diberi label "jangan dipakai".
- **Socket HP keluar lewat jaringan seluler.** `10.240.31.97` adalah IP data
  seluler: saat HP jadi hotspot sambil 4G menyala, Android mengikat socket
  aplikasi ke jaringan default (seluler), sehingga paket menuju PC dikirim ke
  internet. Kini aplikasi mencari alamat lokal yang **satu subnet** dengan PC
  dan mengikat socket ke alamat itu, memaksa lalu lintas lewat interface
  hotspot. Pencarian otomatis juga memakai socket terikat per-interface.

### Diagnostik & firewall
- **Tombol Diagnosa koneksi** di setting: melaporkan interface HP, rute yang
  dipilih, tes TCP, tes balasan server, dan tes pencarian — lengkap dengan
  kesimpulan. Laporan bisa disalin.
- **Tes lewat browser**: buka `http://<ip-pc>:8765` di HP. Server kini
  membalas halaman status, jadi jaringan bisa diuji tanpa aplikasi.
- **Perbaiki Firewall** di jendela server: memasang aturan lewat UAC untuk
  semua profil termasuk Public (jaringan hotspot selalu dianggap Public).
  Status firewall ditampilkan langsung di jendela server.
- `start_server.bat` kini meminta hak Administrator otomatis saat aturan
  firewall belum ada.


## v2.2

### Perbaikan (laporan pengguna)
- **WiFi/Hotspot & cari otomatis** — `start_server.bat` kini menambah aturan
  Windows Firewall otomatis (TCP 8765 & UDP 8766); pencarian di APK memakai
  broadcast global + per-interface dengan 3 percobaan
- **Blur background** — cross-window blur sering dimatikan vendor, kini
  ditambah lapisan frost berbasis intensitas yang bekerja di semua perangkat
- **Warna aksen wallpaper** — sumber diganti ke `WallpaperColors`
  (Android 8.1+, tanpa izin), dengan penyesuaian saturasi/luminance

### Fitur baru
- Slider **intensitas blur** dan slider **kekuatan haptic** di setting
- **Backspace** dikembalikan: tombol ⌫ dan penghapusan di kolom ketik
- **Kunci versi**: server menolak koneksi bila versi APK ≠ versi server
- Tombol **putuskan koneksi**: ⏻ di bar atas APK dan tombol Putuskan
  di jendela server


## v2.1

### Perbaikan bug (laporan pengguna)
- **Trackpad hilang** — panel baris bawah memakai weight di parent wrap_content
  sehingga melahap seluruh tinggi layar; tinggi baris bawah kini tetap (190dp)
- **D-Pad terlalu besar** — panel D-Pad kini berukuran sama dengan panel media
- **Slider volume mati** — server memanggil COM audio dari thread tanpa
  CoInitialize sehingga pycaw selalu gagal; ditambah mode cadangan bertingkat
  bila server memang tanpa pycaw

### Revisi & fitur
- Menu Advance kini **pop-up persegi** (bukan dropdown baris)
- **Wallpaper diblur** di belakang aplikasi (Android 12+)
- Fitur orientasi diluruskan: hanya **arah input trackpad** yang diputar 90° —
  layout tidak berubah; swipe kanan = kursor ke atas saat aktif
- **Warna aksen mengikuti wallpaper** perangkat (Material You, Android 12+)


## v2.0

### Tampilan
- Tema baru bergaya Control Center: panel kaca tembus pandang
- Background aplikasi memperlihatkan **wallpaper HP** (bukan warna solid)
- Font monospace **JetBrains Mono** di seluruh UI
- Semua tombol memakai **simbol fungsi** (Ctrl+Z → ↩, Tab → ⇥, dst)
- GUI desktop dirombak: layout lebih rapi, tema gelap senada

### Layout
- Keyboard dipindah ke **bawah tombol kontrol mouse**
- Esc, Tab, Win, Del dikelompokkan ke dropdown **Advance** (⋯)
- Baris bawah dibagi 2 kolom seimbang: volume + media (kiri), D-Pad (kanan)
- Tombol arah berbentuk **D-Pad ala DualShock** dengan auto-repeat

### Fitur baru
- **Slider volume absolut** menggantikan tombol mute (via pycaw)
- **Gesture Windows Precision Touchpad**: 2 jari scroll & pinch zoom,
  3 jari untuk Task View / Show Desktop / ganti aplikasi
- **Getaran haptic bertingkat** — kekuatan menyesuaikan bobot aksi
- **Tombol ubah orientasi** vertikal ↔ horizontal tanpa mengubah layout
- **Halaman Setting**: status koneksi, jalur koneksi, nama PC, versi server,
  toggle haptic / scroll natural / layar menyala, sensitivitas kursor,
  change log, panduan gesture, bantuan
- Nama PC yang terhubung tampil di tengah trackpad
- Server: **minimize to system tray**, jalan **tanpa jendela konsol**

### Perubahan
- Alt+Tab → **Win+Tab**
- Fitur **clipboard sync dihapus**
- Fitur **backspace dihapus**

## v1.0
- Rilis pertama: trackpad, keyboard, clipboard sync, media control,
  koneksi WiFi/Hotspot & USB
