#!/usr/bin/env python3
"""
CLAUDEPAD Server untuk Windows 10/11
------------------------------------
HP Android menjadi trackpad, keyboard, media control & volume untuk PC.
Koneksi: WiFi/Hotspot atau USB (adb reverse).

Jalankan:  pythonw pc_server.py     (GUI, tanpa jendela konsol)
           python  pc_server.py --nogui
Butuh:     pip install websockets pycaw comtypes pystray pillow
"""

import asyncio
import base64
import http
import json
import os
from paths import data_path
import secrets
import socket
import queue
import sys
import threading

try:
    import websockets
except ImportError:
    raise SystemExit("Modul 'websockets' belum terpasang. Jalankan: pip install websockets")

import input_core as core
import system_ctl
import crypto_box
import binary_protocol
from input_core import (CLIENTS, LOGQ, WS_PORT, HOSTNAME, log, local_ips,
                        local_ips_detailed, enable_usb_mode, discovery_loop,
                        handle_message, volume_get, firewall_status,
                        fix_firewall, check_rate_limit, record_failed_attempt,
                        reset_failed_attempts)

APP_VERSION = "3.3"

# RSA-2048 keypair: digenerate sekali saat server start.
# Public key dikirim ke HP di hello_ok supaya HP bisa mengenkripsi PIN.
# Private key hanya ada di server, tidak pernah dikirim.
_RSA_KEYPAIR = None  # (pub_pem, private_key) — diinisialisasi di start_server_thread


def _ensure_rsa_keypair():
    global _RSA_KEYPAIR
    if _RSA_KEYPAIR is None:
        _RSA_KEYPAIR = crypto_box.generate_rsa_keypair()
    return _RSA_KEYPAIR

# Token perangkat tepercaya: sekali dipasangkan, HP tidak perlu mengetik PIN
# lagi. v3.2: disimpan di Windows Credential Manager lewat keyring,
# bukan lagi di paired.txt (plain text).
try:
    import keyring as _keyring
    _KEYRING_AVAILABLE = True
except ImportError:
    _KEYRING_AVAILABLE = False

_KEYRING_SERVICE = "claudepad"
_KEYRING_USER = "paired_tokens"
_PAIR_FILE = data_path("paired.txt")  # fallback jika keyring tidak tersedia


def load_tokens():
    """Muat semua token pairing dari penyimpanan."""
    if _KEYRING_AVAILABLE:
        try:
            raw = _keyring.get_password(_KEYRING_SERVICE, _KEYRING_USER)
            if raw:
                return {t for t in raw.splitlines() if t.strip()}
        except Exception:
            pass
    # Fallback: baca dari paired.txt (migrasi dari v3.1 ke bawah)
    try:
        with open(_PAIR_FILE, "r", encoding="utf-8") as f:
            return {l.strip() for l in f if l.strip()}
    except OSError:
        return set()


def save_token(token):
    """Simpan token pairing baru. Migrasi otomatis dari paired.txt ke keyring."""
    tokens = load_tokens()
    tokens.add(token)
    if _KEYRING_AVAILABLE:
        try:
            _keyring.set_password(_KEYRING_SERVICE, _KEYRING_USER,
                                  "\n".join(sorted(tokens)))
            # Hapus paired.txt setelah migrasi berhasil
            try:
                if os.path.exists(_PAIR_FILE):
                    os.remove(_PAIR_FILE)
            except OSError:
                pass
            return True
        except Exception as e:
            log(f"[!] keyring gagal, fallback ke file: {e}")
    # Fallback ke paired.txt
    try:
        with open(_PAIR_FILE, "w", encoding="utf-8") as f:
            f.write("\n".join(sorted(tokens)))
        return True
    except OSError as e:
        log(f"[!] Gagal menyimpan token pairing: {e}")
        return False


def forget_tokens():
    """Hapus semua token pairing."""
    if _KEYRING_AVAILABLE:
        try:
            _keyring.delete_password(_KEYRING_SERVICE, _KEYRING_USER)
        except Exception:
            pass
    try:
        if os.path.exists(_PAIR_FILE):
            os.remove(_PAIR_FILE)
        log("[i] Semua perangkat tepercaya dilupakan")
        return True
    except OSError:
        return False


ACTIVE_SOCKETS = set()
MAIN_LOOP = None


def disconnect_clients():
    """Putuskan semua klien yang sedang terhubung (dipanggil dari GUI)."""
    loop = MAIN_LOOP
    if loop is None:
        return
    for ws in list(ACTIVE_SOCKETS):
        try:
            asyncio.run_coroutine_threadsafe(ws.close(code=1000, reason="disconnect"), loop)
        except Exception:
            pass
    log("[i] Semua klien diputus dari server")

# ---------------------------------------------------------------- Handler ----
async def handle(ws):
    authed = False
    crypto = None          # Session bila klien memilih jalur terenkripsi
    binary_enabled = False  # v3.3: binary protocol aktif
    peer = ws.remote_address[0] if ws.remote_address else "?"
    transport = "usb" if peer.startswith("127.") else "wifi"
    pending_salt = [None]      # garam handshake untuk koneksi ini
    ACTIVE_SOCKETS.add(ws)

    # Matikan algoritma Nagle. Tanpa ini paket gerakan kursor yang mungil
    # ditahan menunggu paket lain, dan kursor terasa tersendat.
    try:
        sock = None
        for attr in ("transport", "socket"):
            obj = getattr(ws, attr, None)
            if obj is not None:
                sock = obj.get_extra_info("socket") if hasattr(obj, "get_extra_info") else obj
                if sock is not None:
                    break
        if sock is not None:
            sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    except Exception:
        pass

    log(f"[+] Koneksi dari {peer} ({transport})")

    def reply(obj):
        data = json.dumps(obj)
        if crypto is not None:
            asyncio.create_task(ws.send(crypto.seal(data.encode("utf-8"))))
        else:
            asyncio.create_task(ws.send(data))

    try:
        async for raw in ws:
            # Setelah handshake, pesan datang sebagai blob biner terenkripsi.
            if crypto is not None and isinstance(raw, (bytes, bytearray)):
                try:
                    raw = crypto.open(bytes(raw))
                except Exception as e:
                    log(f"[!] {peer} paket ditolak: {e}")
                    continue
                # v3.3: coba decode binary protocol dulu
                if binary_enabled:
                    try:
                        m = binary_protocol.decode(raw)
                    except Exception:
                        m = None
                    if m is not None:
                        handle_message(m, reply)
                        continue
                # Fallback ke JSON
                try:
                    raw = raw.decode("utf-8")
                except UnicodeDecodeError:
                    continue
            try:
                m = json.loads(raw)
            except (ValueError, TypeError):
                continue

            # Handshake enkripsi. Klien v3.0+ meminta garam lebih dahulu.
            # Kunci sesi TIDAK pernah dikirim: kedua pihak menurunkannya
            # sendiri dari PIN atau token pairing yang sudah sama-sama
            # diketahui, memakai garam ini.
            # v3.2: server juga mengirim public key RSA supaya HP bisa
            # mengenkripsi PIN/token saat auth (proteksi transit).
            if not authed and m.get("t") == "hello":
                pending_salt[0] = crypto_box.new_salt()
                pub_pem, _ = _ensure_rsa_keypair()
                await ws.send(json.dumps({
                    "t": "hello_ok",
                    "salt": pending_salt[0].hex(),
                    "pubkey": crypto_box.rsa_pubkey_to_b64(pub_pem),
                    "version": APP_VERSION,
                }))
                continue

            if not authed:
                if m.get("t") == "auth":
                    # Rate limiting: blokir jika terlalu banyak gagal
                    if not check_rate_limit(peer):
                        await ws.send(json.dumps({
                            "t": "auth_fail", "reason": "rate_limit",
                        }))
                        log(f"[!] {peer} diblokir: terlalu banyak percobaan gagal")
                        continue

                    # Kunci versi: APK dan server harus sama persis.
                    app_ver = str(m.get("ver", ""))
                    if app_ver != APP_VERSION:
                        await ws.send(json.dumps({
                            "t": "auth_fail", "reason": "version",
                            "server": APP_VERSION, "app": app_ver,
                        }))
                        log(f"[!] {peer} ditolak: versi APK '{app_ver}' != server {APP_VERSION}")
                        continue

                # v3.2: Ekstrak PIN/token — terenkripsi (RSA) atau plain.
                # Default dari field plain, lalu override jika pin_enc/token_enc ada.
                pin_plain = str(m.get("pin", ""))
                token_plain = str(m.get("token", ""))

                if m.get("t") == "auth":
                    _, priv_key = _ensure_rsa_keypair()

                    pin_enc_b64 = m.get("pin_enc")
                    if pin_enc_b64:
                        try:
                            pin_plain = crypto_box.rsa_decrypt(
                                priv_key,
                                __import__("base64").b64decode(pin_enc_b64)
                            ).decode("utf-8")
                        except Exception as e:
                            log(f"[!] {peer} gagal dekripsi pin_enc: {e}")
                            record_failed_attempt(peer)
                            await ws.send(json.dumps({"t": "auth_fail", "reason": "pin"}))
                            continue

                    token_enc_b64 = m.get("token_enc")
                    if token_enc_b64:
                        try:
                            token_plain = crypto_box.rsa_decrypt(
                                priv_key,
                                __import__("base64").b64decode(token_enc_b64)
                            ).decode("utf-8")
                        except Exception as e:
                            log(f"[!] {peer} gagal dekripsi token_enc: {e}")
                            record_failed_attempt(peer)
                            await ws.send(json.dumps({"t": "auth_fail", "reason": "pin"}))
                            continue

                token = token_plain
                by_token = bool(token) and token in load_tokens()
                by_pin = pin_plain == core.PIN

                if m.get("t") == "auth" and (by_pin or by_token):
                    authed = True
                    binary_enabled = m.get("binary", False)
                    CLIENTS[peer] = transport
                    reset_failed_attempts(peer)

                    # Beri token baru saat masuk memakai PIN, supaya lain kali
                    # HP bisa langsung tersambung tanpa mengetik PIN lagi.
                    new_token = ""
                    if by_pin and not by_token:
                        new_token = secrets.token_hex(16)
                        save_token(new_token)

                    # Kunci sesi diturunkan dari rahasia yang sudah dimiliki
                    # kedua pihak, jadi tidak ada yang perlu dikirim.
                    if pending_salt[0] is not None:
                        secret = token if by_token else core.PIN
                        crypto = crypto_box.Session.derive(secret, pending_salt[0])
                        log(f"[+] {peer} lalu lintas terenkripsi")

                    await ws.send(json.dumps({
                        "t": "auth_ok",
                        "host": HOSTNAME,
                        "transport": transport,
                        "version": APP_VERSION,
                        "vol": volume_get(),
                        "mac": system_ctl.mac_address(),
                        "token": new_token,
                        "encrypted": pending_salt[0] is not None,
                        "binary": True,
                    }))
                    log(f"[+] {peer} terautentikasi"
                        + (" (token tersimpan)" if new_token else
                           " (token tepercaya)" if by_token else ""))
                elif m.get("t") == "auth":
                    record_failed_attempt(peer)
                    await ws.send(json.dumps({"t": "auth_fail", "reason": "pin"}))
                    log(f"[!] {peer} PIN salah")
                continue
            handle_message(m, reply)
    except websockets.ConnectionClosed:
        pass
    except Exception as e:
        log(f"[!] Error dari {peer}: {e}")
    finally:
        ACTIVE_SOCKETS.discard(ws)
        CLIENTS.pop(peer, None)
        log(f"[-] {peer} terputus")


def _health_body():
    return (
        "CLAUDEPAD OK\n"
        f"server  : v{APP_VERSION}\n"
        f"host    : {HOSTNAME}\n"
        f"port    : {WS_PORT}\n\n"
        "Halaman ini tampil berarti HP SUDAH bisa menjangkau PC.\n"
        "Kalau aplikasi tetap gagal, masalahnya bukan di firewall.\n"
    )


def _ws_api_generation():
    """
    2 = API baru (websockets >= 14, process_request(connection, request))
    1 = API lama (websockets < 14, process_request(path, headers))
    """
    try:
        major = int(str(websockets.__version__).split(".")[0])
    except Exception:
        major = 0
    return 2 if major >= 14 else 1


# --- API lama: process_request(path, request_headers) -> tuple | None ---
async def _health_legacy(path, request_headers):
    if path.startswith("/ws"):
        return None
    return (http.HTTPStatus.OK,
            [("Content-Type", "text/plain; charset=utf-8"),
             ("Access-Control-Allow-Origin", "*")],
            _health_body().encode())


# --- API baru: process_request(connection, request) -> Response | None ---
def _health_modern(connection, request):
    try:
        path = getattr(request, "path", "/") or "/"
        if path.startswith("/ws"):
            return None
        return connection.respond(http.HTTPStatus.OK, _health_body())
    except Exception as e:                 # jangan pernah jatuhkan handshake
        log(f"[!] health endpoint error: {e}")
        return None


def health_request():
    """Pilih implementasi sesuai versi websockets yang terpasang."""
    return _health_modern if _ws_api_generation() == 2 else _health_legacy


SERVER_READY = threading.Event()
SERVER_ERROR = [None]


def start_server_thread():
    def run():
        async def main():
            global MAIN_LOOP
            MAIN_LOOP = asyncio.get_running_loop()
            async with websockets.serve(handle, "0.0.0.0", WS_PORT,
                                        ping_interval=20, ping_timeout=20,
                                        process_request=health_request()):
                SERVER_READY.set()
                await asyncio.Future()
        try:
            asyncio.run(main())
        except OSError as e:
            SERVER_ERROR[0] = f"Port {WS_PORT} sudah dipakai program lain ({e})"
            log(f"[!] {SERVER_ERROR[0]}")
        except Exception as e:
            SERVER_ERROR[0] = f"Server gagal jalan: {type(e).__name__}: {e}"
            log(f"[!] {SERVER_ERROR[0]}")
    threading.Thread(target=discovery_loop, daemon=True).start()
    threading.Thread(target=run, daemon=True).start()


# ---------------------------------------------------------------- GUI --------
BG      = "#0e0e14"
CARD    = "#191922"
CARD2   = "#20202c"
FG      = "#f2f2f7"
MUTED   = "#8e8ea0"
ACCENT  = "#7c6cff"
GREEN   = "#4ade80"
AMBER   = "#fbbf24"
RED     = "#ff6b6b"
MONO    = "JetBrains Mono"


def _mono(size, weight="normal"):
    """JetBrains Mono kalau terpasang di Windows, kalau tidak pakai Consolas."""
    import tkinter.font as tkfont
    fams = set(tkfont.families())
    fam = MONO if MONO in fams else ("Consolas" if "Consolas" in fams else "Courier New")
    return (fam, size, weight)


def run_gui():
    import tkinter as tk

    root = tk.Tk()
    root.title("CLAUDEPAD")
    root.geometry("520x600")
    root.minsize(460, 540)
    root.configure(bg=BG)

    state = {"tray": None, "hidden": False}

    # ---- header ----
    header = tk.Frame(root, bg=BG)
    header.pack(fill="x", padx=22, pady=(20, 6))
    tk.Label(header, text="CLAUDEPAD", font=_mono(20, "bold"),
             bg=BG, fg=FG).pack(anchor="w")
    tk.Label(header, text=f"remote server  v{APP_VERSION}", font=_mono(9),
             bg=BG, fg=MUTED).pack(anchor="w")

    # ---- kartu PIN + IP ----
    def card(parent, pady=(0, 10)):
        outer = tk.Frame(parent, bg=CARD, highlightthickness=1,
                         highlightbackground=CARD2)
        outer.pack(fill="x", padx=22, pady=pady)
        return outer

    c1 = card(root, (10, 10))
    inner = tk.Frame(c1, bg=CARD)
    inner.pack(fill="x", padx=18, pady=16)

    left = tk.Frame(inner, bg=CARD)
    left.pack(side="left", anchor="n")
    tk.Label(left, text="PIN", font=_mono(9), bg=CARD, fg=MUTED).pack(anchor="w")
    pin_lbl = tk.Label(left, text=core.PIN, font=_mono(32, "bold"), bg=CARD, fg=ACCENT)
    pin_lbl.pack(anchor="w")

    right = tk.Frame(inner, bg=CARD)
    right.pack(side="right", anchor="n")
    tk.Label(right, text="ALAMAT UNTUK HP", font=_mono(9),
             bg=CARD, fg=MUTED).pack(anchor="e")

    detailed = local_ips_detailed()
    ips = local_ips()
    if not detailed:
        tk.Label(right, text="tidak ada jaringan", font=_mono(12),
                 bg=CARD, fg=FG).pack(anchor="e")
    else:
        for ip, name, virtual in detailed[:5]:
            row = tk.Frame(right, bg=CARD)
            row.pack(anchor="e")
            tk.Label(row, text=("  (virtual, jangan dipakai)" if virtual else ""),
                     font=_mono(8), bg=CARD, fg="#5a5a70").pack(side="left")
            tk.Label(row, text=ip, font=_mono(13 if not virtual else 10),
                     bg=CARD, fg=(FG if not virtual else "#5a5a70")).pack(side="left")
        tk.Label(right, text=f"port {WS_PORT}", font=_mono(9),
                 bg=CARD, fg=MUTED).pack(anchor="e", pady=(4, 0))

    # ---- status ----
    c2 = card(root)
    status_lbl = tk.Label(c2, text="  Menunggu koneksi", font=_mono(11),
                          bg=CARD, fg=AMBER, anchor="w")
    status_lbl.pack(fill="x", padx=18, pady=(14, 4))

    fw_lbl = tk.Label(c2, text="  Memeriksa firewall...", font=_mono(10),
                      bg=CARD, fg=MUTED, anchor="w")
    fw_lbl.pack(fill="x", padx=18, pady=(0, 14))

    def render_firewall():
        ok = firewall_status()
        if ok:
            fw_lbl.config(text="  Firewall: port terbuka", fg=GREEN)
        else:
            fw_lbl.config(text="  Firewall: port TERTUTUP - klik Perbaiki Firewall",
                          fg=AMBER)

    def do_fix_firewall():
        fw_lbl.config(text="  Meminta izin Administrator...", fg=MUTED)
        def work():
            fix_firewall()
            root.after(1200, render_firewall)
        threading.Thread(target=work, daemon=True).start()

    # ---- tombol ----
    btnbar = tk.Frame(root, bg=BG)
    btnbar.pack(fill="x", padx=22, pady=(2, 10))

    def flat_btn(parent, text, cmd, accent=False):
        b = tk.Label(parent, text=text, font=_mono(10), bg=ACCENT if accent else CARD2,
                     fg="#ffffff" if accent else FG, padx=14, pady=9, cursor="hand2")
        b.pack(side="left", padx=(0, 8))
        b.bind("<Button-1>", lambda e: cmd())
        hover = "#8f80ff" if accent else "#2b2b3a"
        base = ACCENT if accent else CARD2
        b.bind("<Enter>", lambda e: b.config(bg=hover))
        b.bind("<Leave>", lambda e: b.config(bg=base))
        return b

    def copy_ip():
        if ips:
            root.clipboard_clear()
            root.clipboard_append(ips[-1])
            log(f"[i] IP {ips[-1]} disalin")

    def regen_pin():
        pin_lbl.config(text=core.new_pin())
        log(f"[i] PIN baru dibuat")

    flat_btn(btnbar, "Salin IP", copy_ip)
    flat_btn(btnbar, "PIN Baru", regen_pin)
    flat_btn(btnbar, "Mode USB",
             lambda: threading.Thread(target=enable_usb_mode, daemon=True).start(),
             accent=True)
    flat_btn(btnbar, "Putuskan", disconnect_clients)
    flat_btn(btnbar, "Perbaiki Firewall", do_fix_firewall)

    # ---- log ----
    tk.Label(root, text="LOG", font=_mono(9), bg=BG, fg=MUTED,
             anchor="w").pack(fill="x", padx=22, pady=(6, 4))
    logframe = tk.Frame(root, bg=CARD)
    logframe.pack(fill="both", expand=True, padx=22, pady=(0, 12))
    scroll = tk.Scrollbar(logframe, bg=CARD, troughcolor=CARD,
                          activebackground=ACCENT, bd=0, highlightthickness=0)
    scroll.pack(side="right", fill="y")
    logbox = tk.Text(logframe, bg=CARD, fg="#c9c9d4", insertbackground=FG,
                     relief="flat", font=_mono(9), state="disabled",
                     yscrollcommand=scroll.set, padx=14, pady=12, wrap="word")
    logbox.pack(fill="both", expand=True)
    scroll.config(command=logbox.yview)

    # ---- tray ----
    def make_tray_icon():
        try:
            import pystray
            from PIL import Image, ImageDraw
        except ImportError:
            log("[!] pystray/pillow belum ada; tray dinonaktifkan.")
            return None
        img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        d.rounded_rectangle((6, 6, 58, 58), radius=14, fill=(124, 108, 255, 255))
        d.rounded_rectangle((20, 18, 44, 46), radius=6, fill=(255, 255, 255, 255))
        return pystray.Icon(
            "claudepad", img, "CLAUDEPAD",
            menu=pystray.Menu(
                pystray.MenuItem("Tampilkan", lambda: root.after(0, show_window),
                                 default=True),
                pystray.MenuItem("Keluar", lambda: root.after(0, quit_app)),
            ))

    def hide_to_tray():
        icon = state["tray"]
        if icon is None:
            icon = make_tray_icon()
            if icon is None:
                root.iconify()
                return
            state["tray"] = icon
            threading.Thread(target=icon.run, daemon=True).start()
        root.withdraw()
        state["hidden"] = True
        log("[i] Diminimalkan ke system tray")

    def show_window():
        root.deiconify()
        root.lift()
        root.focus_force()
        state["hidden"] = False

    def quit_app():
        if state["tray"]:
            try:
                state["tray"].stop()
            except Exception:
                pass
        root.destroy()

    flat_btn(btnbar, "Ke Tray", hide_to_tray)
    root.protocol("WM_DELETE_WINDOW", hide_to_tray)

    # ---- polling ----
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
            names = ", ".join(f"{ip} ({t})" for ip, t in sorted(CLIENTS.items()))
            status_lbl.config(text=f"  Terhubung: {names}", fg=GREEN)
        else:
            status_lbl.config(text="  Menunggu koneksi", fg=AMBER)
        root.after(300, poll)

    start_server_thread()

    def self_check():
        """Pastikan server benar-benar menerima koneksi, bukan cuma terlihat hidup."""
        ok = SERVER_READY.wait(timeout=6)
        if not ok or SERVER_ERROR[0]:
            msg = SERVER_ERROR[0] or "Server tidak siap dalam 6 detik"
            root.after(0, lambda: status_lbl.config(text=f"  GAGAL: {msg}", fg=RED))
            log(f"[!] {msg}")
            return
        try:
            import urllib.request
            body = urllib.request.urlopen(
                f"http://127.0.0.1:{WS_PORT}/", timeout=5).read().decode()
            if "CLAUDEPAD OK" in body:
                log(f"[i] Uji mandiri OK - websockets {websockets.__version__}")
            else:
                log("[!] Uji mandiri: balasan tak terduga")
        except Exception as e:
            SERVER_ERROR[0] = f"Uji mandiri gagal: {e}"
            log(f"[!] {SERVER_ERROR[0]}")
            root.after(0, lambda: status_lbl.config(
                text="  GAGAL: server tidak merespons - lihat log", fg=RED))

    threading.Thread(target=self_check, daemon=True).start()
    log(f"[i] Server aktif di port {WS_PORT} sebagai '{HOSTNAME}'")
    if ips:
        log(f"[i] Tes dari HP: buka http://{ips[0]}:{WS_PORT} di browser")
    render_firewall()
    poll()
    root.mainloop()


def run_console():
    start_server_thread()
    print("=" * 46)
    print(f"  CLAUDEPAD Server v{APP_VERSION} (konsol)")
    print(f"  PIN  : {core.PIN}")
    print(f"  Port : {WS_PORT}")
    for ip in local_ips():
        print(f"  IP   : {ip}")
    print("=" * 46)

    def printer():
        while True:
            print(LOGQ.get())
    threading.Thread(target=printer, daemon=True).start()
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
            print(f"GUI gagal ({e}), fallback ke konsol.")
            run_console()
