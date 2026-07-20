#!/usr/bin/env python3
"""
CLAUDEPAD — kontrol sistem PC: kecerahan layar, daya, dan identitas jaringan.
Dipisah dari input_core agar mudah diuji sendiri.
"""

import ctypes
import os
import re
import subprocess
import uuid

try:
    IS_WINDOWS = hasattr(ctypes, "windll")
except Exception:
    IS_WINDOWS = False

_NO_WINDOW = getattr(subprocess, "CREATE_NO_WINDOW", 0)


def _ps(script, timeout=25):
    try:
        r = subprocess.run(
            ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script],
            capture_output=True, text=True, timeout=timeout, creationflags=_NO_WINDOW)
        return (r.stdout or "").strip(), (r.stderr or "").strip()
    except Exception as e:
        return "", str(e)


# ============================================================== KECERAHAN ====
# Dua jalur, dicoba berurutan:
#   1. WMI  — hanya bekerja pada layar internal (laptop).
#   2. DDC/CI lewat dxva2.dll — untuk monitor eksternal, asal monitornya
#      mendukung dan kabelnya meneruskan jalur DDC.

def _wmi_get():
    out, _ = _ps("(Get-CimInstance -Namespace root/WMI "
                 "-ClassName WmiMonitorBrightness -ErrorAction Stop).CurrentBrightness")
    m = re.search(r"\d+", out)
    return int(m.group()) if m else None


def _wmi_set(level):
    _, err = _ps("(Get-CimInstance -Namespace root/WMI "
                 "-ClassName WmiMonitorBrightnessMethods -ErrorAction Stop)"
                 f".WmiSetBrightness(1, {int(level)})")
    return not err


# --- DDC/CI ---
class _PHYSICAL_MONITOR(ctypes.Structure):
    _fields_ = [("handle", ctypes.c_void_p), ("description", ctypes.c_wchar * 128)]


def _ddc_monitors():
    """Kembalikan daftar handle monitor fisik. Kosong kalau tidak didukung."""
    if not IS_WINDOWS:
        return []
    user32 = ctypes.windll.user32
    dxva2 = ctypes.windll.dxva2
    handles = []
    monitors = []

    MONITORENUMPROC = ctypes.WINFUNCTYPE(
        ctypes.c_int, ctypes.c_void_p, ctypes.c_void_p,
        ctypes.POINTER(ctypes.c_long), ctypes.c_double)

    def cb(hmon, hdc, rect, data):
        monitors.append(hmon)
        return 1

    user32.EnumDisplayMonitors(None, None, MONITORENUMPROC(cb), 0)

    for hmon in monitors:
        count = ctypes.c_uint32()
        if not dxva2.GetNumberOfPhysicalMonitorsFromHMONITOR(
                ctypes.c_void_p(hmon), ctypes.byref(count)):
            continue
        if count.value == 0:
            continue
        arr = (_PHYSICAL_MONITOR * count.value)()
        if dxva2.GetPhysicalMonitorsFromHMONITOR(
                ctypes.c_void_p(hmon), count.value, arr):
            for pm in arr:
                handles.append(pm.handle)
    return handles


def _ddc_get():
    for h in _ddc_monitors():
        cur = ctypes.c_uint32()
        mn = ctypes.c_uint32()
        mx = ctypes.c_uint32()
        try:
            if ctypes.windll.dxva2.GetMonitorBrightness(
                    ctypes.c_void_p(h), ctypes.byref(mn),
                    ctypes.byref(cur), ctypes.byref(mx)):
                span = max(1, mx.value - mn.value)
                return int(round((cur.value - mn.value) * 100.0 / span))
        except Exception:
            pass
    return None


def _ddc_set(percent):
    ok = False
    for h in _ddc_monitors():
        cur = ctypes.c_uint32()
        mn = ctypes.c_uint32()
        mx = ctypes.c_uint32()
        try:
            if ctypes.windll.dxva2.GetMonitorBrightness(
                    ctypes.c_void_p(h), ctypes.byref(mn),
                    ctypes.byref(cur), ctypes.byref(mx)):
                span = mx.value - mn.value
                target = mn.value + int(round(span * percent / 100.0))
                if ctypes.windll.dxva2.SetMonitorBrightness(
                        ctypes.c_void_p(h), target):
                    ok = True
        except Exception:
            pass
    return ok


def brightness_get():
    """Kecerahan 0..100, atau None kalau tidak terbaca."""
    if not IS_WINDOWS:
        return None
    v = _wmi_get()
    if v is not None:
        return v
    return _ddc_get()


def brightness_step(delta):
    """
    Naik/turunkan kecerahan sebesar delta persen.
    Mengembalikan (berhasil, pesan_untuk_hp).
    """
    if not IS_WINDOWS:
        return False, "kecerahan hanya tersedia di Windows"

    cur = brightness_get()
    if cur is None:
        return False, ("kecerahan tidak didukung — monitor tidak menyediakan "
                       "WMI maupun DDC/CI")

    target = max(0, min(100, cur + delta))
    if target == cur:
        return True, f"kecerahan {cur}%"

    if _wmi_get() is not None and _wmi_set(target):
        return True, f"kecerahan {target}%"
    if _ddc_set(target):
        return True, f"kecerahan {target}%"
    return False, "gagal mengubah kecerahan — monitor menolak perintah"


# ==================================================================== DAYA ===
_POWER = {
    "shutdown": ("shutdown", ["/s", "/t", "0"], "PC dimatikan"),
    "restart": ("shutdown", ["/r", "/t", "0"], "PC dimulai ulang"),
    "logoff": ("shutdown", ["/l"], "keluar dari sesi"),
    "hibernate": ("shutdown", ["/h"], "PC hibernasi"),
}


def power_action(action):
    """shutdown / restart / logoff / hibernate / sleep / lock / screenoff."""
    if not IS_WINDOWS:
        return False, "hanya tersedia di Windows"

    try:
        if action == "lock":
            ctypes.windll.user32.LockWorkStation()
            return True, "PC dikunci"

        if action == "screenoff":
            # matikan layar tanpa menidurkan PC
            HWND_BROADCAST, WM_SYSCOMMAND, SC_MONITORPOWER = 0xFFFF, 0x0112, 0xF170
            ctypes.windll.user32.SendMessageW(
                HWND_BROADCAST, WM_SYSCOMMAND, SC_MONITORPOWER, 2)
            return True, "layar dimatikan"

        if action == "sleep":
            # Hibernate=False, ForceCritical=False, DisableWakeEvent=False
            ctypes.windll.powrprof.SetSuspendState(0, 0, 0)
            return True, "PC ditidurkan"

        if action in _POWER:
            exe, args, msg = _POWER[action]
            subprocess.Popen([exe] + args, creationflags=_NO_WINDOW)
            return True, msg

        return False, "aksi daya tidak dikenal"
    except Exception as e:
        return False, f"gagal: {e}"


# ============================================================ IDENTITAS ======
def mac_address():
    """MAC adapter aktif dalam format AA:BB:CC:DD:EE:FF, untuk Wake-on-LAN."""
    try:
        node = uuid.getnode()
        # bit multicast menyala berarti uuid.getnode() mengarang alamat
        if (node >> 40) % 2:
            return ""
        return ":".join(f"{(node >> b) & 0xFF:02X}" for b in range(40, -8, -8))
    except Exception:
        return ""
