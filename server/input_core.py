#!/usr/bin/env python3
"""
CLAUDEPAD - core input injection & protocol handling (Windows).
Dipisah dari GUI supaya mudah diuji dan dipakai ulang.
"""

import ctypes
import json
import os
import time
from paths import resource_path, data_path
import queue
import random
import socket
import subprocess

IS_WINDOWS = hasattr(ctypes, "windll")

if IS_WINDOWS:
    import ctypes.wintypes as wt
else:
    # Non-Windows: modul tetap bisa di-import (untuk pengujian di CI).
    class _StubTypes:
        LONG = ctypes.c_long
        DWORD = ctypes.c_ulong
        WORD = ctypes.c_ushort
    wt = _StubTypes()

import system_ctl

WS_PORT = 8765
DISCOVERY_PORT = 8766

PIN = f"{random.randint(0, 99999999):08d}"
CLIENTS = {}          # peer -> transport ("wifi" / "usb")
LOGQ = queue.Queue()
HOSTNAME = socket.gethostname()

# ── Rate limiting: anti brute-force PIN ──
FAILED_ATTEMPTS = {}  # ip -> [(waktu_gagal, ...)]
MAX_FAILED = 3        # maksimal percobaan gagal dalam jendela waktu
LOCKOUT_SECONDS = 30  # durasi blokir setelah MAX_FAILED
WINDOW_SECONDS = 60   # jendela waktu untuk menghitung gagal


def new_pin():
    global PIN
    PIN = f"{random.randint(0, 99999999):08d}"
    return PIN


def check_rate_limit(ip):
    """Kembalikan True jika IP boleh mencoba autentikasi, False jika diblokir."""
    now = time.time()
    attempts = FAILED_ATTEMPTS.get(ip, [])

    # Bersihkan percobaan luar jendela waktu
    attempts = [t for t in attempts if now - t < WINDOW_SECONDS]
    FAILED_ATTEMPTS[ip] = attempts

    if len(attempts) >= MAX_FAILED:
        elapsed = now - attempts[0]
        if elapsed < LOCKOUT_SECONDS:
            remaining = int(LOCKOUT_SECONDS - elapsed)
            log(f"[!] {ip} diblokir {remaining}s lagi ({len(attempts)} gagal)")
            return False
        # Jendela sudah expired, bersihkan
        FAILED_ATTEMPTS[ip] = []
        return True
    return True


def record_failed_attempt(ip):
    """Catat percobaan gagal untuk IP tertentu."""
    now = time.time()
    attempts = FAILED_ATTEMPTS.get(ip, [])
    attempts.append(now)
    FAILED_ATTEMPTS[ip] = attempts
    log(f"[!] {ip} gagal autentikasi ({len(attempts)}/{MAX_FAILED})")


def reset_failed_attempts(ip):
    """Reset counter gagal setelah berhasil autentikasi."""
    FAILED_ATTEMPTS.pop(ip, None)


def log(msg):
    LOGQ.put(msg)


if IS_WINDOWS:
    user32 = ctypes.windll.user32
    kernel32 = ctypes.windll.kernel32
else:
    user32 = kernel32 = None


# ---------------------------------------------------------------- SendInput --
class MOUSEINPUT(ctypes.Structure):
    _fields_ = [("dx", wt.LONG), ("dy", wt.LONG), ("mouseData", wt.DWORD),
                ("dwFlags", wt.DWORD), ("time", wt.DWORD),
                ("dwExtraInfo", ctypes.c_void_p)]


class KEYBDINPUT(ctypes.Structure):
    _fields_ = [("wVk", wt.WORD), ("wScan", wt.WORD), ("dwFlags", wt.DWORD),
                ("time", wt.DWORD), ("dwExtraInfo", ctypes.c_void_p)]


class _INPUTunion(ctypes.Union):
    _fields_ = [("mi", MOUSEINPUT), ("ki", KEYBDINPUT)]


class INPUT(ctypes.Structure):
    _fields_ = [("type", wt.DWORD), ("u", _INPUTunion)]


INPUT_MOUSE, INPUT_KEYBOARD = 0, 1
MOUSEEVENTF_MOVE = 0x0001
MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP = 0x0002, 0x0004
MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP = 0x0008, 0x0010
MOUSEEVENTF_MIDDLEDOWN, MOUSEEVENTF_MIDDLEUP = 0x0020, 0x0040
MOUSEEVENTF_WHEEL, MOUSEEVENTF_HWHEEL = 0x0800, 0x1000
KEYEVENTF_KEYUP, KEYEVENTF_UNICODE = 0x0002, 0x0004


def _send(*inputs):
    if not IS_WINDOWS:
        return
    arr = (INPUT * len(inputs))(*inputs)
    user32.SendInput(len(inputs), arr, ctypes.sizeof(INPUT))


def mouse_event(flags, dx=0, dy=0, data=0):
    inp = INPUT(type=INPUT_MOUSE)
    inp.u.mi = MOUSEINPUT(dx, dy, data, flags, 0, None)
    _send(inp)


def mouse_move(dx, dy):
    mouse_event(MOUSEEVENTF_MOVE, int(dx), int(dy))


BUTTONS = {
    "left": (MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP),
    "right": (MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP),
    "middle": (MOUSEEVENTF_MIDDLEDOWN, MOUSEEVENTF_MIDDLEUP),
}


def mouse_click(btn="left", double=False):
    down, up = BUTTONS.get(btn, BUTTONS["left"])
    for _ in range(2 if double else 1):
        mouse_event(down)
        mouse_event(up)


def mouse_button(btn, press):
    down, up = BUTTONS.get(btn, BUTTONS["left"])
    mouse_event(down if press else up)


def mouse_scroll(dy=0, dx=0):
    if dy:
        mouse_event(MOUSEEVENTF_WHEEL, data=ctypes.c_ulong(int(dy)).value)
    if dx:
        mouse_event(MOUSEEVENTF_HWHEEL, data=ctypes.c_ulong(int(dx)).value)


def key_vk(vk, press):
    inp = INPUT(type=INPUT_KEYBOARD)
    inp.u.ki = KEYBDINPUT(vk, 0, 0 if press else KEYEVENTF_KEYUP, 0, None)
    _send(inp)


def type_text(text):
    for ch in text:
        if ch == "\n":
            key_vk(0x0D, True)
            key_vk(0x0D, False)
            continue
        code = ord(ch)
        for flags in (KEYEVENTF_UNICODE, KEYEVENTF_UNICODE | KEYEVENTF_KEYUP):
            inp = INPUT(type=INPUT_KEYBOARD)
            inp.u.ki = KEYBDINPUT(0, code, flags, 0, None)
            _send(inp)


VK = {
    "enter": 0x0D, "esc": 0x1B, "tab": 0x09, "backspace": 0x08, "delete": 0x2E,
    "space": 0x20, "up": 0x26, "down": 0x28, "left": 0x25, "right": 0x27,
    "home": 0x24, "end": 0x23, "pgup": 0x21, "pgdn": 0x22, "win": 0x5B,
    "ctrl": 0x11, "alt": 0x12, "shift": 0x10, "insert": 0x2D, "capslock": 0x14,
    "printscreen": 0x2C, "d": 0x44,
    **{f"f{i}": 0x6F + i for i in range(1, 13)},
}

MEDIA = {
    "playpause": 0xB3, "next": 0xB0, "prev": 0xB1, "stop": 0xB2,
    "volup": 0xAF, "voldown": 0xAE, "mute": 0xAD,
}


def press_key(name, mods=None):
    mods = mods or []
    name = (name or "").lower()
    if name in VK:
        vk = VK[name]
    elif len(name) == 1:
        if not IS_WINDOWS:
            return
        res = user32.VkKeyScanW(ord(name))
        if res == -1:
            type_text(name)
            return
        vk = res & 0xFF
    else:
        return
    for m in mods:
        key_vk(VK.get(m, 0x11), True)
    key_vk(vk, True)
    key_vk(vk, False)
    for m in reversed(mods):
        key_vk(VK.get(m, 0x11), False)


# ---------------------------------------------------------------- Volume -----
_volume_iface = None


def _get_volume_iface():
    """Interface pycaw untuk kontrol volume absolut. None kalau tidak tersedia."""
    global _volume_iface
    if _volume_iface is not None:
        return _volume_iface
    try:
        from ctypes import cast, POINTER
        import comtypes
        from comtypes import CLSCTX_ALL
        from pycaw.pycaw import AudioUtilities, IAudioEndpointVolume
        # WAJIB: server jalan di thread asyncio sendiri, dan COM harus
        # diinisialisasi per-thread. Tanpa ini Activate() selalu gagal
        # dan slider volume mati total.
        try:
            comtypes.CoInitialize()
        except OSError:
            pass  # sudah terinisialisasi di thread ini
        devices = AudioUtilities.GetSpeakers()
        iface = devices.Activate(IAudioEndpointVolume._iid_, CLSCTX_ALL, None)
        _volume_iface = cast(iface, POINTER(IAudioEndpointVolume))
        return _volume_iface
    except Exception as e:                       # pycaw tidak ada / audio error
        log(f"[!] Volume absolut tidak tersedia ({e}); pakai tombol media.")
        return None


def volume_get():
    """Kembalikan volume 0..100, atau None kalau tidak bisa dibaca."""
    iface = _get_volume_iface()
    if iface is None:
        return None
    try:
        return int(round(iface.GetMasterVolumeLevelScalar() * 100))
    except Exception:
        return None


def volume_set(percent):
    percent = max(0, min(100, int(percent)))
    iface = _get_volume_iface()
    if iface is None:
        return False
    try:
        iface.SetMasterVolumeLevelScalar(percent / 100.0, None)
        return True
    except Exception:
        return False


# ---------------------------------------------------------------- Gestures ---
def gesture(name):
    """Gesture Windows Precision Touchpad."""
    if name == "taskview":                       # 3 jari ke atas
        press_key("tab", ["win"])
    elif name == "showdesktop":                  # 3 jari ke bawah
        press_key("d", ["win"])
    elif name == "appnext":                      # 3 jari ke kanan
        press_key("right", ["ctrl", "win"])
    elif name == "appprev":                      # 3 jari ke kiri
        press_key("left", ["ctrl", "win"])


def zoom(direction):
    """Pinch: Ctrl + scroll."""
    key_vk(VK["ctrl"], True)
    mouse_scroll(120 if direction > 0 else -120)
    key_vk(VK["ctrl"], False)


# ---------------------------------------------------------------- Dispatch ---
def handle_message(m, reply):
    """
    Proses satu pesan protokol. `reply` adalah callable(dict) untuk balasan.
    Dipisah dari layer WebSocket supaya bisa diuji tanpa jaringan.
    """
    t = m.get("t")
    if t == "move":
        mouse_move(m.get("dx", 0), m.get("dy", 0))
    elif t == "click":
        mouse_click(m.get("b", "left"), m.get("double", False))
    elif t == "down":
        mouse_button(m.get("b", "left"), True)
    elif t == "up":
        mouse_button(m.get("b", "left"), False)
    elif t == "scroll":
        mouse_scroll(m.get("dy", 0), m.get("dx", 0))
    elif t == "zoom":
        zoom(m.get("dir", 1))
    elif t == "gesture":
        gesture(m.get("g", ""))
    elif t == "text":
        type_text(m.get("s", ""))
    elif t == "key":
        press_key(m.get("k", ""), m.get("mods"))
    elif t == "media":
        vk = MEDIA.get(m.get("a", ""))
        if vk:
            key_vk(vk, True)
            key_vk(vk, False)
    elif t == "volset":
        if not volume_set(m.get("v", 50)):
            reply({"t": "volerr"})
    elif t == "volget":
        reply({"t": "vol", "v": volume_get()})
    elif t == "bright":
        ok, msg = system_ctl.brightness_step(int(m.get("d", 10)))
        reply({"t": "bright_result", "ok": ok, "msg": msg})
    elif t == "power":
        act = m.get("a", "")
        ok, msg = system_ctl.power_action(act)
        if ok:
            log(f"[i] Aksi daya: {act}")
        reply({"t": "power_result", "a": act, "ok": ok, "msg": msg})
    elif t == "radio":
        which = m.get("d", "")
        ok, msg = toggle_radio(which)
        reply({"t": "radio_result", "d": which, "ok": ok, "msg": msg})
    elif t == "ping":
        reply({"t": "pong"})
    elif t == "clipboard_request":
        try:
            import pyperclip
            text = pyperclip.paste()
            if text:
                from binary_protocol import CLIPBOARD_MAX
                reply({"t": "clipboard_sync", "s": text[:CLIPBOARD_MAX]})
        except Exception:
            pass
    return t


# ---------------------------------------------------------------- Discovery --
def discovery_loop():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        s.bind(("0.0.0.0", DISCOVERY_PORT))
    except OSError as e:
        log(f"[!] Discovery gagal bind: {e}")
        return
    while True:
        try:
            data, addr = s.recvfrom(256)
            if data.strip() == b"DISCOVER_CLAUDEPAD":
                s.sendto(f"CLAUDEPAD|{HOSTNAME}|{WS_PORT}".encode(), addr)
        except OSError:
            break


# Rentang/nama adapter virtual yang HARUS diabaikan: WSL, Hyper-V, Docker,
# VirtualBox, VMware. Menampilkan IP mereka membuat pengguna mengetik alamat
# yang mustahil dijangkau dari HP.
VIRTUAL_HINTS = (
    "wsl", "hyper-v", "vethernet", "docker", "virtualbox", "vmware",
    "loopback", "bluetooth", "vpn", "tailscale", "zerotier", "radmin",
)


def _ipconfig_adapters():
    """[(nama_adapter, ipv4)] dari ipconfig. [] kalau bukan Windows/gagal."""
    if not IS_WINDOWS:
        return []
    try:
        out = subprocess.run(["ipconfig"], capture_output=True, text=True, timeout=10,
                             creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        text = out.stdout
    except Exception:
        return []
    adapters, current = [], None
    for line in text.splitlines():
        if line and not line.startswith(" "):
            current = line.strip().rstrip(":")
        elif current and "IPv4" in line and ":" in line:
            ip = line.split(":", 1)[1].strip().rstrip("(Preferred)").strip()
            if ip.count(".") == 3:
                adapters.append((current, ip))
    return adapters


def _is_virtual(name):
    low = name.lower()
    return any(h in low for h in VIRTUAL_HINTS)


def _score(ip, name):
    """Makin kecil makin diprioritaskan untuk ditampilkan ke pengguna."""
    if _is_virtual(name):
        return 100
    # 192.168.43.x / 192.168.x.x = hotspot Android & WiFi rumah -> paling relevan
    if ip.startswith("192.168.43."):
        return 0
    if ip.startswith("192.168."):
        return 1
    if ip.startswith("10."):
        return 2
    # 172.16-31.x sering dipakai WSL/Docker -> turunkan walau namanya tak dikenali
    if ip.startswith("172."):
        try:
            second = int(ip.split(".")[1])
            if 16 <= second <= 31:
                return 90
        except ValueError:
            pass
    return 50


def local_ips_detailed():
    """
    [(ip, nama_adapter, virtual?)] terurut dari yang paling mungkin dipakai HP.
    Adapter virtual tetap dikembalikan tapi ditandai, supaya GUI bisa meredupkannya.
    """
    found = []
    seen = set()

    for name, ip in _ipconfig_adapters():
        if ip.startswith("127.") or ip in seen:
            continue
        seen.add(ip)
        found.append((ip, name, _is_virtual(name) or _score(ip, name) >= 90))

    if not found:                       # non-Windows / ipconfig gagal
        try:
            for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
                ip = info[4][0]
                if not ip.startswith("127.") and ip not in seen:
                    seen.add(ip)
                    found.append((ip, "network", _score(ip, "") >= 90))
        except socket.gaierror:
            pass

    found.sort(key=lambda t: _score(t[0], t[1]))
    return found


def local_ips():
    """Hanya IP yang layak dipakai (adapter virtual dibuang)."""
    real = [ip for ip, _n, virt in local_ips_detailed() if not virt]
    return real if real else [ip for ip, _n, _v in local_ips_detailed()]


def firewall_status():
    """True kalau aturan firewall CLAUDEPAD sudah ada."""
    if not IS_WINDOWS:
        return True
    try:
        r = subprocess.run(
            ["netsh", "advfirewall", "firewall", "show", "rule", "name=CLAUDEPAD TCP"],
            capture_output=True, text=True, timeout=10,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        return r.returncode == 0 and "CLAUDEPAD" in r.stdout
    except Exception:
        return False


def fix_firewall():
    """
    Pasang aturan firewall lewat UAC dengan menjalankan fix_firewall.bat.
    Memakai file .bat terpisah (bukan perintah panjang inline) karena kutipan
    bersarang PowerShell -> cmd -> netsh sangat rawan salah; versi sebelumnya
    memakai ';' sebagai pemisah perintah yang TIDAK dikenal cmd.exe sehingga
    tombol perbaiki firewall tidak pernah bekerja.
    """
    if not IS_WINDOWS:
        return False
    script = resource_path("fix_firewall.bat")
    if not os.path.exists(script):
        log("[!] fix_firewall.bat tidak ditemukan di folder server")
        return False
    try:
        subprocess.run([
            "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command",
            f"Start-Process -FilePath '{script}' -Verb RunAs -WindowStyle Hidden -Wait"
        ], capture_output=True, timeout=120,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    except Exception as e:
        log(f"[!] Gagal menjalankan fix_firewall.bat: {e}")
        return False

    if firewall_status():
        log("[i] Aturan firewall terpasang.")
        return True
    log("[!] Aturan firewall masih belum ada - prompt Administrator ditolak?")
    return False


# ---------------------------------------------------------------- Radio -----
# Kontrol WiFi & Bluetooth memakai Windows Radio Management API lewat WinRT.
# Cara ini TIDAK membutuhkan hak Administrator, berbeda dengan
# Enable-NetAdapter/Disable-NetAdapter yang selalu meminta elevasi.
_PS_RADIO = r"""
$ErrorActionPreference = 'Stop'
try {
  Add-Type -AssemblyName System.Runtime.WindowsRuntime
  $awaiters = [System.WindowsRuntimeSystemExtensions].GetMethods() |
    Where-Object { $_.Name -eq 'GetAwaiter' -and $_.GetParameters().Count -eq 1 }

  function Await($task, $type) {
    $m = $awaiters | Where-Object {
      $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    } | Select-Object -First 1
    $m = $m.MakeGenericMethod($type)
    $m.Invoke($null, @($task)).GetResult()
  }

  [Windows.Devices.Radios.Radio, Windows.System.Devices, ContentType=WindowsRuntime] | Out-Null
  [Windows.Devices.Radios.RadioAccessStatus, Windows.System.Devices, ContentType=WindowsRuntime] | Out-Null

  $access = Await ([Windows.Devices.Radios.Radio]::RequestAccessAsync()) `
                  ([Windows.Devices.Radios.RadioAccessStatus])
  if ($access -ne 'Allowed') { Write-Output 'DENIED'; exit }

  $radios = Await ([Windows.Devices.Radios.Radio]::GetRadiosAsync()) `
                  ([System.Collections.Generic.IReadOnlyList[Windows.Devices.Radios.Radio]])
  $kind = '__KIND__'
  $r = $radios | Where-Object { $_.Kind -eq $kind } | Select-Object -First 1
  if ($null -eq $r) { Write-Output 'NOTFOUND'; exit }

  $target = if ($r.State -eq 'On') { 'Off' } else { 'On' }
  $res = Await ($r.SetStateAsync($target)) ([Windows.Devices.Radios.RadioAccessStatus])
  if ($res -eq 'Allowed') { Write-Output "OK:$target" } else { Write-Output "FAIL:$res" }
} catch {
  Write-Output "ERR:$($_.Exception.Message)"
}
"""

# Hotspot memakai NetworkOperatorTetheringManager (WinRT). Lebih rapuh:
# butuh profil koneksi aktif dan tidak semua adapter mendukungnya.
_PS_HOTSPOT = r"""
$ErrorActionPreference = 'Stop'
try {
  Add-Type -AssemblyName System.Runtime.WindowsRuntime
  $awaiters = [System.WindowsRuntimeSystemExtensions].GetMethods() |
    Where-Object { $_.Name -eq 'GetAwaiter' -and $_.GetParameters().Count -eq 1 }
  function Await($task, $type) {
    $m = $awaiters | Where-Object {
      $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    } | Select-Object -First 1
    $m = $m.MakeGenericMethod($type)
    $m.Invoke($null, @($task)).GetResult()
  }

  [Windows.Networking.NetworkOperators.NetworkOperatorTetheringManager, Windows.Networking, ContentType=WindowsRuntime] | Out-Null
  [Windows.Networking.Connectivity.NetworkInformation, Windows.Networking, ContentType=WindowsRuntime] | Out-Null

  $profile = [Windows.Networking.Connectivity.NetworkInformation]::GetInternetConnectionProfile()
  if ($null -eq $profile) { Write-Output 'NOPROFILE'; exit }

  $mgr = [Windows.Networking.NetworkOperators.NetworkOperatorTetheringManager]::CreateFromConnectionProfile($profile)
  if ($mgr.TetheringOperationalState -eq 'On') {
    $op = Await ($mgr.StopTetheringAsync()) ([Windows.Networking.NetworkOperators.NetworkOperatorTetheringOperationResult])
    $target = 'Off'
  } else {
    $op = Await ($mgr.StartTetheringAsync()) ([Windows.Networking.NetworkOperators.NetworkOperatorTetheringOperationResult])
    $target = 'On'
  }
  if ($op.Status -eq 'Success') { Write-Output "OK:$target" }
  else { Write-Output "FAIL:$($op.Status) $($op.AdditionalErrorMessage)" }
} catch {
  Write-Output "ERR:$($_.Exception.Message)"
}
"""


def _run_powershell(script, timeout=45):
    try:
        r = subprocess.run(
            ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script],
            capture_output=True, text=True, timeout=timeout,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        return (r.stdout or r.stderr or "").strip()
    except subprocess.TimeoutExpired:
        return "ERR:timeout"
    except FileNotFoundError:
        return "ERR:powershell tidak ditemukan"
    except Exception as e:
        return f"ERR:{e}"


def toggle_radio(which):
    """
    Nyalakan/matikan radio PC: 'wifi', 'bluetooth', atau 'hotspot'.
    Mengembalikan (berhasil, pesan) untuk ditampilkan di HP.
    """
    if not IS_WINDOWS:
        return False, "hanya tersedia di Windows"

    if which == "hotspot":
        out = _run_powershell(_PS_HOTSPOT, timeout=60)
    elif which in ("wifi", "bluetooth"):
        kind = "WiFi" if which == "wifi" else "Bluetooth"
        out = _run_powershell(_PS_RADIO.replace("__KIND__", kind))
    else:
        return False, "perangkat tidak dikenal"

    line = out.splitlines()[-1] if out else ""
    if line.startswith("OK:"):
        state = "menyala" if line.split(":", 1)[1] == "On" else "mati"
        log(f"[i] {which} PC kini {state}")
        return True, f"{which} {state}"
    if line == "DENIED":
        return False, f"{which}: akses radio ditolak - izinkan di Pengaturan Windows"
    if line == "NOTFOUND":
        return False, f"{which}: perangkat tidak ada di PC ini"
    if line == "NOPROFILE":
        return False, "hotspot: PC tidak punya koneksi internet aktif"
    msg = line.replace("FAIL:", "").replace("ERR:", "").strip() or "gagal"
    log(f"[!] Gagal mengubah {which}: {msg}")
    return False, f"{which}: {msg[:90]}"


def enable_usb_mode():
    """adb reverse supaya HP bisa konek lewat kabel USB."""
    try:
        subprocess.run(["adb", "start-server"], capture_output=True, timeout=20,
                       creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        r = subprocess.run(["adb", "reverse", f"tcp:{WS_PORT}", f"tcp:{WS_PORT}"],
                           capture_output=True, text=True, timeout=20,
                           creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        if r.returncode == 0:
            log("[USB] Aktif. Di aplikasi HP tekan tombol USB.")
            return True
        log("[USB] Gagal: " + (r.stderr.strip() or "cek kabel & USB debugging"))
    except FileNotFoundError:
        log("[USB] adb.exe tidak ditemukan. Install SDK Platform Tools.")
    except subprocess.TimeoutExpired:
        log("[USB] adb tidak merespons.")
    return False
