"""
CLAUDEPAD v3.5 - Audio Streaming Module via USB/WiFi (Port 8767)
Menyediakan streaming audio dua arah:
1. PC Loopback Capture -> HP Speaker (PC to Phone)
2. HP Mic Capture -> PC Audio Input / Virtual Cable (Phone to PC)
Menggunakan `pyaudiowpatch` (dengan PyAudio sebagai fallback) dan `numpy`.
"""

import asyncio
import socket
import threading
import time
import sys
import numpy as np

try:
    import pyaudiowpatch as pyaudio
except ImportError:
    try:
        import pyaudio
    except ImportError:
        pyaudio = None

AUDIO_PORT = 8767
CHUNK = 1024
FORMAT = pyaudio.paInt16 if pyaudio else 16
CHANNELS = 2
RATE = 44100

class AudioStreamServer:
    def __init__(self, port=AUDIO_PORT):
        self.port = port
        self.server_socket = None
        self.running = False
        self.thread = None
        self.p = None
        
    def start(self):
        if self.running:
            return
        if pyaudio is None:
            print("[!] PyAudio / pyaudiowpatch tidak terpasang. Audio streaming dinonaktifkan.")
            return
        
        self.running = True
        self.thread = threading.Thread(target=self._server_loop, daemon=True)
        self.thread.start()
        print(f"[*] Audio stream server berjalan di port {self.port}")

    def stop(self):
        self.running = False
        if self.server_socket:
            try:
                self.server_socket.close()
            except Exception:
                pass
        print("[*] Audio stream server dihentikan.")

    def _server_loop(self):
        try:
            self.p = pyaudio.PyAudio()
        except Exception as e:
            print(f"[!] Gagal menginisialisasi PyAudio: {e}")
            self.running = False
            return

        self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            self.server_socket.bind(("0.0.0.0", self.port))
            self.server_socket.listen(1)
        except Exception as e:
            print(f"[!] Gagal bind audio socket ke port {self.port}: {e}")
            self.running = False
            return

        while self.running:
            try:
                self.server_socket.settimeout(1.0)
                try:
                    conn, addr = self.server_socket.accept()
                except socket.timeout:
                    continue
                except Exception:
                    break

                print(f"[+] Koneksi audio diterima dari {addr}")
                self._handle_client(conn)
            except Exception as e:
                if self.running:
                    print(f"[!] Error pada audio loop: {e}")
                break

        if self.p:
            try:
                self.p.terminate()
            except Exception:
                pass

    def _handle_client(self, conn):
        conn.setblocking(True)
        # Sesi dua arah:
        # Thread 1: Tangkap WASAPI loopback PC -> Kirim ke HP
        # Thread 2: Terima data mic dari HP -> Putar/salin ke speaker PC atau virtual audio
        
        stop_event = threading.Event()

        def stream_pc_to_phone():
            try:
                # Cari loopback device jika menggunakan pyaudiowpatch
                try:
                    wasapi_info = self.p.get_host_api_info_by_type(pyaudio.paWASAPI)
                    default_speakers = self.p.get_device_info_by_index(wasapi_info["defaultOutputDevice"])
                    if not default_speakers["isLoopbackDevice"]:
                        for loopback in self.p.get_loopback_device_info_generator():
                            if default_speakers["name"] in loopback["name"]:
                                default_speakers = loopback
                                break
                    device_index = default_speakers["index"]
                except Exception:
                    device_index = None # Default device

                stream = self.p.open(
                    format=pyaudio.paInt16,
                    channels=CHANNELS,
                    rate=RATE,
                    input=True,
                    input_device_index=device_index,
                    frames_per_buffer=CHUNK
                )
                
                while not stop_event.is_set() and self.running:
                    try:
                        data = stream.read(CHUNK, exception_on_overflow=False)
                        conn.sendall(data)
                    except Exception:
                        break
                stream.stop_stream()
                stream.close()
            except Exception as e:
                print(f"[!] PC->Phone stream error: {e}")

        def stream_phone_to_pc():
            try:
                # Buka output stream untuk memutar audio mic dari HP ke PC (atau virtual cable)
                stream = self.p.open(
                    format=pyaudio.paInt16,
                    channels=1,
                    rate=RATE,
                    output=True,
                    frames_per_buffer=CHUNK
                )
                while not stop_event.is_set() and self.running:
                    try:
                        data = conn.recv(CHUNK * 2)
                        if not data:
                            break
                        stream.write(data)
                    except Exception:
                        break
                stream.stop_stream()
                stream.close()
            except Exception as e:
                print(f"[!] Phone->PC stream error: {e}")

        t1 = threading.Thread(target=stream_pc_to_phone, daemon=True)
        t2 = threading.Thread(target=stream_phone_to_pc, daemon=True)

        t1.start()
        t2.start()

        while t1.is_alive() and t2.is_alive() and self.running:
            time.sleep(0.5)

        stop_event.set()
        try:
            conn.close()
        except Exception:
            pass
        print("[-] Koneksi audio ditutup.")

_server_instance = None

def start_audio_server():
    global _server_instance
    if _server_instance is None:
        _server_instance = AudioStreamServer()
        _server_instance.start()

def stop_audio_server():
    global _server_instance
    if _server_instance is not None:
        _server_instance.stop()
        _server_instance = None

if __name__ == "__main__":
    print("[*] Menjalankan server audio mandiri...")
    start_audio_server()
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        stop_audio_server()
