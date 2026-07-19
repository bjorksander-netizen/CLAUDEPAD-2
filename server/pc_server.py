#!/usr/bin/env python3
"""
CLAUDEPAD Server untuk Windows 10 — dengan UI desktop (tkinter)
---------------------------------------------------------------
HP Android menjadi trackpad, keyboard, clipboard sync & media control.
Koneksi: WiFi/Hotspot atau USB (adb reverse).

Jalankan:  python pc_server.py          (dengan GUI)
           python pc_server.py --nogui  (mode konsol)
Butuh:     pip install websockets
"""

import asyncio
import ctypes
import ctypes.wintypes as wt
import json
import queue
import random
import socket
import subprocess
import sys
import threading

try:
    import websockets
except ImportError:
    raise SystemExit("Modul 'websockets' belum terpasang. Jalankan: pip install websockets")

WS_PORT = 8765
DISCOVERY_PORT = 8766
PIN = f"{random.randint(0, 9999):04d}"
CLIENTS = set()
LOGQ = queue.Queue()


def log(msg):
    print(msg)
    LOGQ.put(msg)


user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32

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
    "printscreen": 0x2C,
    **{f"f{i}": 0x6F + i for i in range(1, 13)},
}

MEDIA = {
    "playpause": 0xB3, "next": 0xB0, "prev": 0xB1, "stop": 0xB2,
    "volup": 0xAF, "voldown": 0xAE, "mute": 0xAD,
}


def press_key(name, mods=None):
    mods = mods or []
    name = name.lower()
    if name in VK:
        vk = VK[name]
    elif len(name) == 1:
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


# ---------------------------------------------------------------- Clipboard --
CF_UNICODETEXT, GMEM_MOVEABLE = 13, 0x0002


def clipboard_get():
    text = ""
    if user32.OpenClipboard(None):
        try:
            h = user32.GetClipboardData(CF_UNICODETEXT)
            if h:
                kernel32.GlobalLock.restype = ctypes.c_void_p
                p = kernel32.GlobalLock(ctypes.c_void_p(h))
                if p:
                    text = ctypes.wstring_at(p)
                    kernel32.GlobalUnlock(ctypes.c_void_p(h))
        finally:
            user32.CloseClipboard()
    return text


def clipboard_set(text):
    if not user32.OpenClipboard(None):
        return
    try:
        user32.EmptyClipboard()
        data = text.encode("utf-16-le") + b"\x00\x00"
        kernel32.GlobalAlloc.restype = ctypes.c_void_p
        h = kernel32.GlobalAlloc(GMEM_MOVEABLE, len(data))
        kernel32.GlobalLock.restype = ctypes.c_void_p
        p = kernel32.GlobalLock(ctypes.c_void_p(h))
        ctypes.memmove(p, data, len(data))
        kernel32.GlobalUnlock(ctypes.c_void_p(h))
        user32.SetClipboardData(CF_UNICODETEXT, ctypes.c_void_p(h))
    finally:
        user32.CloseClipboard()


# ---------------------------------------------------------------- Handler ----
async def handle(ws):
    authed = False
    peer = ws.remote_address[0] if ws.remote_address else "?"
    log(f"[+] Koneksi dari {peer}")
    try:
        async for raw in ws:
            try:
                m = json.loads(raw)
            except (ValueError, TypeError):
                continue
            t = m.get("t")
            if not authed:
                if t == "auth" and str(m.get("pin", "")) == PIN:
                    authed = True
                    CLIENTS.add(peer)
                    await ws.send(json.dumps({"t": "auth_ok"}))
                    log(f"[+] {peer} terautentikasi")
                else:
                    await ws.send(json.dumps({"t": "auth_fail"}))
                    log(f"[!] {peer} PIN salah")
                continue
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
            elif t == "text":
                type_text(m.get("s", ""))
            elif t == "key":
                press_key(m.get("k", ""), m.get("mods"))
            elif t == "media":
                vk = MEDIA.get(m.get("a", ""))
                if vk:
                    key_vk(vk, True)
                    key_vk(vk, False)
            elif t == "clipset":
                clipboard_set(m.get("s", ""))
                log(f"[i] Clipboard HP -> PC ({len(m.get('s',''))} karakter)")
            elif t == "clipget":
                await ws.send(json.dumps({"t": "clip", "s": clipboard_get()}))
                log("[i] Clipboard PC -> HP")
            elif t == "ping":
                await ws.send(json.dumps({"t": "pong"}))
    except websockets.ConnectionClosed:
        pass
    finally:
        CLIENTS.discard(peer)
        log(f"[-] {peer} terputus")


# ---------------------------------------------------------------- Discovery --
def discovery_loop():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(("0.0.0.0", DISCOVERY_PORT))
    host = socket.gethostname()
    while True:
        try:
            data, addr = s.recvfrom(256)
            if data.strip() == b"DISCOVER_CLAUDEPAD":
                s.sendto(f"CLAUDEPAD|{host}|{WS_PORT}".encode(), addr)
        except OSError:
            break


def local_ips():
    ips = set()
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            ips.add(info[4][0])
    except socket.gaierror:
        pass
    try:
        probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        probe.connect(("8.8.8.8", 80))
        ips.add(probe.getsockname()[0])
        probe.close()
    except OSError:
        pass
    return sorted(ip for ip in ips if not ip.startswith("127."))


def start_server_thread():
    def run():
        async def main():
            async with websockets.serve(handle, "0.0.0.0", WS_PORT):
                await asyncio.Future()
        asyncio.run(main())
    threading.Thread(target=discovery_loop, daemon=True).start()
    threading.Thread(target=run, daemon=True).start()


def enable_usb_mode():
    """Jalankan 'adb reverse' supaya HP bisa konek via kabel USB."""
    try:
        subprocess.run(["adb", "start-server"], capture_output=True, timeout=15)
        r = subprocess.run(["adb", "reverse", f"tcp:{WS_PORT}", f"tcp:{WS_PORT}"],
                           capture_output=True, text=True, timeout=15)
        if r.returncode == 0:
            log("[USB] Aktif! Di aplikasi HP tekan tombol 'USB'.")
            return True
        log("[USB] Gagal: " + (r.stderr.strip() or "pastikan HP terhubung & USB debugging aktif"))
    except FileNotFoundError:
        log("[USB] adb.exe tidak ditemukan. Install SDK Platform Tools & tambahkan ke PATH.")
    except subprocess.TimeoutExpired:
        log("[USB] adb tidak merespons.")
    return False


# ---------------------------------------------------------------- GUI --------
def run_gui():
    import tkinter as tk
    from tkinter import ttk, scrolledtext

    BG, CARD, FG, ACCENT, MUT = "#12121a", "#1d1d2b", "#eeeef5", "#7c6cff", "#8a8aa0"

    root = tk.Tk()
    root.title("CLAUDEPAD Server")
    root.geometry("560x520")
    root.configure(bg=BG)
    root.minsize(480, 440)

    tk.Label(root, text="CLAUDEPAD", font=("Segoe UI", 22, "bold"),
             bg=BG, fg=ACCENT).pack(pady=(16, 0))
    tk.Label(root, text="HP Android → trackpad · keyboard · clipboard · media",
             font=("Segoe UI", 10), bg=BG, fg=MUT).pack()

    card = tk.Frame(root, bg=CARD)
    card.pack(fill="x", padx=16, pady=12)

    tk.Label(card, text="PIN", font=("Segoe UI", 10), bg=CARD, fg=MUT).grid(
        row=0, column=0, sticky="w", padx=14, pady=(12, 0))
    pin_lbl = tk.Label(card, text=PIN, font=("Consolas", 30, "bold"), bg=CARD, fg=FG)
    pin_lbl.grid(row=1, column=0, sticky="w", padx=14, pady=(0, 12))

    tk.Label(card, text="Alamat IP (WiFi/Hotspot)", font=("Segoe UI", 10),
             bg=CARD, fg=MUT).grid(row=0, column=1, sticky="w", padx=14, pady=(12, 0))
    ips = local_ips()
    ip_lbl = tk.Label(card, text="\n".join(ips) if ips else "-",
                      font=("Consolas", 13), bg=CARD, fg=FG, justify="left")
    ip_lbl.grid(row=1, column=1, sticky="w", padx=14, pady=(0, 12))

    status_lbl = tk.Label(card, text="● Menunggu koneksi...", font=("Segoe UI", 10),
                          bg=CARD, fg="#ffb454")
    status_lbl.grid(row=2, column=0, columnspan=2, sticky="w", padx=14, pady=(0, 12))
    card.columnconfigure(1, weight=1)

    btns = tk.Frame(root, bg=BG)
    btns.pack(fill="x", padx=16)

    style = ttk.Style()
    try:
        style.theme_use("clam")
    except tk.TclError:
        pass
    style.configure("A.TButton", font=("Segoe UI", 10), padding=6)

    def copy_ip():
        if ips:
            root.clipboard_clear()
            root.clipboard_append(ips[-1])
            log(f"[i] IP {ips[-1]} disalin ke clipboard")

    def new_pin():
        global PIN
        PIN = f"{random.randint(0, 9999):04d}"
        pin_lbl.config(text=PIN)
        log(f"[i] PIN baru: {PIN}")

    ttk.Button(btns, text="Salin IP", style="A.TButton", command=copy_ip).pack(
        side="left", padx=(0, 8))
    ttk.Button(btns, text="PIN Baru", style="A.TButton", command=new_pin).pack(
        side="left", padx=(0, 8))
    ttk.Button(btns, text="Aktifkan Mode USB", style="A.TButton",
               command=lambda: threading.Thread(target=enable_usb_mode, daemon=True).start()
               ).pack(side="left")

    tk.Label(root, text="Log", font=("Segoe UI", 10), bg=BG, fg=MUT,
             anchor="w").pack(fill="x", padx=16, pady=(12, 0))
    logbox = scrolledtext.ScrolledText(root, height=10, bg=CARD, fg=FG,
                                       insertbackground=FG, relief="flat",
                                       font=("Consolas", 9), state="disabled")
    logbox.pack(fill="both", expand=True, padx=16, pady=(4, 16))

    def poll():
        try:
            while True:
                msg = LOGQ.get_nowait()
                logbox.config(state="normal")
                logbox.insert("end", msg + "\n")
                logbox.see("end")
                logbox.config(state="disabled")
        except queue.Empty:
            pass
        if CLIENTS:
            status_lbl.config(text=f"● Terhubung: {', '.join(sorted(CLIENTS))}",
                              fg="#5eff8e")
        else:
            status_lbl.config(text="● Menunggu koneksi...", fg="#ffb454")
        root.after(300, poll)

    start_server_thread()
    log(f"[i] Server aktif di port {WS_PORT}")
    poll()
    root.mainloop()


def run_console():
    start_server_thread()
    print("=" * 46)
    print("  CLAUDEPAD Server aktif (mode konsol)")
    print(f"  PIN     : {PIN}")
    print(f"  Port    : {WS_PORT}")
    for ip in local_ips():
        print(f"  Alamat  : {ip}")
    print("=" * 46)
    try:
        threading.Event().wait()
    except KeyboardInterrupt:
        print("Server berhenti.")


if __name__ == "__main__":
    if "--nogui" in sys.argv:
        run_console()
    else:
        try:
            run_gui()
        except Exception as e:
            print(f"GUI gagal ({e}), fallback ke mode konsol.")
            run_console()
