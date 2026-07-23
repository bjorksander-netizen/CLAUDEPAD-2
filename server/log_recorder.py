#!/usr/bin/env python3
"""
CLAUDEPAD - Structured session logger (JSONL format).
Mencatat semua input/output untuk diagnostic & pengembangan.

File disimpan di data_path("logs/session_YYYY-MM-DD_HHMMSS.jsonl").
Auto-rotate: max 5MB/file, simpan 10 file terakhir.
"""

import json
import os
import time
import threading
from queue import Queue, Empty
from paths import data_path

LOG_DIR = data_path("logs")
MAX_FILE_SIZE = 5 * 1024 * 1024  # 5 MB
MAX_FILES = 10

_write_queue: Queue = Queue()
_running = False
_current_path: str | None = None
_writer_thread: threading.Thread | None = None


def start():
    """Mulai async writer thread. Aman dipanggil berkali-kali."""
    global _running, _writer_thread
    if _running:
        return
    _running = True
    _writer_thread = threading.Thread(target=_writer_loop, daemon=True)
    _writer_thread.start()


def log(direction: str, cmd_type: str, payload_summary: str, peer: str = ""):
    """Queue satu log entry (non-blocking)."""
    entry = {
        "ts": time.time(),
        "dir": direction,       # "in" atau "out"
        "cmd": cmd_type,        # e.g. "move", "clipboard_sync"
        "payload": payload_summary[:200],
        "peer": peer,
    }
    try:
        _write_queue.put_nowait(entry)
    except Exception:
        pass


def _writer_loop():
    while _running:
        try:
            entry = _write_queue.get(timeout=1.0)
        except Empty:
            continue
        _write_entry(entry)


def _ensure_dir():
    os.makedirs(LOG_DIR, exist_ok=True)


def _get_current_path():
    global _current_path
    if _current_path is None or not os.path.exists(_current_path):
        _rotate()
    elif os.path.getsize(_current_path) > MAX_FILE_SIZE:
        _rotate()
    return _current_path


def _rotate():
    global _current_path
    _ensure_dir()
    stamp = time.strftime("%Y-%m-%d_%H%M%S")
    base = os.path.join(LOG_DIR, f"session_{stamp}.jsonl")
    if not os.path.exists(base):
        _current_path = base
        return
    for i in range(2, MAX_FILES + 1):
        candidate = f"_{i}.jsonl".join(os.path.splitext(base))
        if not os.path.exists(candidate):
            _current_path = candidate
            return
    _current_path = base


def _write_entry(entry):
    try:
        path = _get_current_path()
        if path is None:
            return
        with open(path, "a", encoding="utf-8") as f:
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")
    except OSError:
        pass


def export_logs():
    """Kembalikan list (filename, full_path) untuk semua file log."""
    if not os.path.exists(LOG_DIR):
        return []
    files = sorted(
        [(f, os.path.join(LOG_DIR, f)) for f in os.listdir(LOG_DIR) if f.endswith(".jsonl")],
        key=lambda x: x[1], reverse=True,
    )
    return files


def recent_entries(n: int = 50) -> list[dict]:
    """Baca n entry terakhir dari file session terbaru."""
    files = export_logs()
    if not files:
        return []
    entries = []
    try:
        with open(files[0][1], "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line:
                    try:
                        entries.append(json.loads(line))
                    except json.JSONDecodeError:
                        pass
    except OSError:
        pass
    return entries[-n:]


def clear_logs():
    """Hapus semua file log."""
    if not os.path.exists(LOG_DIR):
        return 0
    count = 0
    for f in os.listdir(LOG_DIR):
        if f.endswith(".jsonl"):
            try:
                os.remove(os.path.join(LOG_DIR, f))
                count += 1
            except OSError:
                pass
    global _current_path
    _current_path = None
    return count
