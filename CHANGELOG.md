# Changelog

Semua perubahan penting CLAUDEPAD. Versi APK dan server selalu dinaikkan
bersamaan, dan server menolak koneksi bila keduanya berbeda.

| Versi | Inti perubahan |
|---|---|
| **v2.9.3** | Arah kursor melenceng 90° saat lanskap diperbaiki |
| v2.9.2 | Bar atas lanskap diperbaiki; kecerahan & mute mandiri dihapus; Ctrl+W |
| v2.9.1 | Perbaikan layout lanskap & tombol Enter; kecerahan jadi opsional |
| v2.9 | Perbaikan kursor patah-patah; layout lanskap; kecerahan & daya PC; pairing; auto-reconnect |
| v2.8 | D-Pad kembali ke bentuk salib; tombol digabung jadi grup pop-up; kontrol koneksi PC; log ping |
| v2.6 | Panel ketik terkunci di tengah; D-Pad bundar faset; pointer location & show taps |
| v2.5 | Panel ketik pop-up dengan latar blur; indikator ping; rotasi input 3 arah |
| v2.4 | Perbaikan fatal HTTP 500 di semua koneksi; firewall benar; logo aplikasi |
| v2.3 | Koneksi WiFi/Hotspot: adapter virtual disaring, socket diikat ke interface hotspot; mode diagnosa |
| v2.2 | Slider blur & haptic, backspace kembali, kunci versi, tombol putuskan koneksi |
| v2.1 | Perbaikan trackpad hilang & D-Pad raksasa; slider volume; pop-up Advance |
| v2.0 | Tema Control Center, gesture Precision Touchpad, D-Pad, haptic, halaman Setting |
| v1.0 | Rilis pertama |

---

## v2.9.3

### Perbaikan
- **Arah kursor melenceng 90° pada mode lanskap.** Geser ke atas membuat
  kursor bergerak menyamping, geser ke kanan membuatnya naik, dan seterusnya.

  Penyebabnya: fitur rotasi input dibuat pada v2.1, ketika layar tetap potret
  meski HP dimiringkan — saat itu arah gerakan memang perlu diputar sendiri
  oleh aplikasi. Sejak v2.9 seluruh layar ikut berputar, dan Android sudah
  memutar koordinat sentuhan mengikuti orientasi layar. Aplikasi tetap
  memutarnya sekali lagi, sehingga totalnya berputar dua kali.

  Transformasi arah di aplikasi kini dihapus sepenuhnya. Gerakan kursor,
  scroll dua jari, dan gestur tiga jari semuanya memakai koordinat layar apa
  adanya — geser ke atas berarti kursor ke atas, di orientasi mana pun.
- Tombol orientasi kini murni memutar **tampilan**; namanya di Setting diubah
  menjadi "orientasi tampilan" agar tidak menyesatkan.

## v2.9.2

### Perbaikan
- **Bar kontrol pada mode lanskap** sebelumnya hanya membentang selebar kolom
  trackpad sehingga tombolnya menggantung di tengah layar. Kini bar itu
  membentang penuh di bagian atas, dengan status di kiri dan tombol di kanan.
- **Teks di dalam trackpad terbaca menyamping** saat rotasi aktif, karena
  diputar dua kali: sekali oleh rotasi layar, sekali lagi oleh kanvas. Rotasi
  kanvas dihapus — layar sudah menanganinya.

### Perubahan
- **Kontrol kecerahan dihapus** sepenuhnya, beserta pengaturannya. Fitur ini
  hanya bekerja pada layar internal laptop dan menyulitkan lebih banyak
  pengguna daripada yang dibantunya.
- **Tombol mute mandiri dihapus.** Mute tetap tersedia dengan mengetuk ikon
  speaker pada slider volume.
- **Tombol grup koneksi PC pindah** ke posisi tombol mute, dengan label teks
  **SIGNAL** menggantikan simbol.
- **Tombol Ctrl+W** (tutup tab/jendela) ditambahkan di posisi tombol koneksi
  yang lama, memakai simbol ⊗.

## v2.9.1

### Perbaikan bug
- **Layout lanskap tidak pernah dimuat.** Manifest mencantumkan
  `orientation|screenSize|screenLayout` pada `configChanges`, yang menyuruh
  Android **tidak** membuat ulang Activity saat berputar — akibatnya folder
  `layout-land` tak pernah dibaca dan yang tampil adalah layout potret yang
  dipaksa melar. Kini hanya `keyboardHidden` yang dipertahankan.
- **Tombol Enter di baris utama mati.** Sejak panel ketik menjadi pop-up di
  v2.5, `kEnter` hanya dipakai untuk mewarnai aksen dan tidak pernah diberi
  listener. Sekarang bisa ditekan tanpa membuka panel ketik.

### Perubahan
- **Kontrol kecerahan jadi opsional** dan default **mati**, karena hanya
  bekerja pada laptop (layar internal). Saat dimatikan, tombol **mute**
  kembali menempati tempatnya. Diatur lewat Setting.
- **Pop-up kontrol daya** kini bergaya sama dengan panel Advance —
  pop-up kaca dua kolom, bukan dialog.
- **Panduan gestur diperbarui**: ketuk tiga jari, scroll horizontal,
  penguncian sumbu scroll, dan catatan bahwa semua gestur mengikuti rotasi
  input yang aktif.
- Catatan Wake-on-LAN diperjelas: menyalakan PC lewat kabel USB dari HP
  tidak dimungkinkan.


### Perbaikan performa (kursor patah-patah)
Log ping pengguna menunjukkan median 14 ms tetapi jitter 16,8 ms dengan
lonjakan sampai 59 ms — jaringan cepat namun sangat tidak stabil. Dua akar
masalah diperbaiki:
- **Penggabungan gerakan (coalescing).** Sebelumnya satu pesan WebSocket
  dikirim untuk **setiap** event sentuhan — 60–120 paket mungil per detik.
  Saat jaringan tersendat, pesan menumpuk lalu dilepas sekaligus sehingga
  kursor melompat menyusul. Kini pergeseran dijumlahkan dan dikirim sekali
  per frame tampilan.
- **WifiLock mode latensi rendah** selama aplikasi dipakai, mencegah radio
  WiFi tertidur di antara paket — penyebab pola cepat-lambat berselang-seling
  pada log.
- **TCP_NODELAY** di sisi server agar paket kecil tidak ditahan Nagle.

### Revisi
- Tombol **Enter kini selalu bisa ditekan**, tidak hanya di mode mengetik.
- Simbol grup koneksi menjadi **🖧**, isi pop-upnya kini teks saja.
- Tombol mute diganti **kontrol kecerahan layar PC** (− ☀ +). Mute dipindah
  ke ketukan pada ikon speaker di slider volume.
- Fitur **change log dihapus** dari halaman Setting.

### Fitur baru
- **Layout lanskap tersendiri.** Saat rotasi input 90°/270° aktif, layar ikut
  berputar dan seluruh tombol memakai tata letak khusus lanskap — bukan
  sekadar diputar — sehingga teks tetap tajam dan proporsi tombol pas.
- **Sleep layar & lock PC** di panel Advance.
- **Gestur scroll horizontal** dua jari, dengan sumbu terkunci setelah arah
  dominan terdeteksi agar tidak berganti-ganti di tengah gulungan.
- **Ketuk tiga jari = klik tengah.**
- **Kontrol daya PC** di puncak halaman Setting: matikan, mulai ulang,
  tidurkan, hibernasi, keluar sesi — aksi berisiko dikonfirmasi dahulu.
- **Wake-on-LAN (eksperimental)** — menyalakan PC dari HP. Keberhasilannya
  bergantung pengaturan BIOS/UEFI dan adapter jaringan; umumnya hanya bekerja
  lewat kabel LAN.
- **Pairing** — server memberi token saat pertama masuk dengan PIN, sehingga
  koneksi berikutnya tidak perlu mengetik PIN lagi. Bisa dilupakan lewat
  Setting.
- **Auto-reconnect** — menyambung ulang otomatis sampai 5 kali dengan jeda
  menaik saat koneksi putus sesaat.

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
