#!/usr/bin/env python3
"""
Resolusi path yang sadar mode .exe (PyInstaller).

Saat dibungkus PyInstaller onefile:
  * Berkas data read-only (mis. fix_firewall.bat) diekstrak ke folder
    sementara sys._MEIPASS yang dihapus setelah program tutup.
  * Berkas yang perlu BERTAHAN (mis. paired.txt) harus diletakkan di
    sebelah .exe, bukan di folder sementara.

Saat dijalankan sebagai skrip Python biasa, keduanya berada di folder skrip.
"""

import os
import sys

FROZEN = getattr(sys, "frozen", False)


def resource_path(name: str) -> str:
    """Berkas bundel read-only. Di .exe diambil dari _MEIPASS."""
    if FROZEN:
        base = getattr(sys, "_MEIPASS", os.path.dirname(sys.executable))
    else:
        base = os.path.dirname(os.path.abspath(__file__))
    return os.path.join(base, name)


def data_path(name: str) -> str:
    """Berkas yang perlu bertahan. Di .exe diletakkan di sebelah executable."""
    if FROZEN:
        base = os.path.dirname(sys.executable)
    else:
        base = os.path.dirname(os.path.abspath(__file__))
    return os.path.join(base, name)
