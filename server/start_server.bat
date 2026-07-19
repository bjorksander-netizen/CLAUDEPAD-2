@echo off
title CLAUDEPAD
cd /d "%~dp0"

where python >nul 2>nul || (echo Python belum terpasang. Install dari python.org lalu centang "Add to PATH". & pause & exit /b 1)

REM ---- Firewall: pasang sekali lewat Administrator ----
netsh advfirewall firewall show rule name="CLAUDEPAD TCP" >nul 2>nul
if errorlevel 1 (
  echo [i] Memasang aturan firewall - setujui prompt Administrator...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath '%~dp0fix_firewall.bat' -Verb RunAs -WindowStyle Hidden -Wait"
)

REM ---- Dependency: cek VERSI, bukan sekadar ada/tidak ----
REM Versi websockets 14+ memakai API process_request berbeda; server sudah
REM menangani keduanya, tapi paket lain tetap harus lengkap.
python -c "import websockets, pycaw, pystray, PIL" 2>nul
if errorlevel 1 (
  echo Menyiapkan dependency, mohon tunggu...
  python -m pip install --quiet --disable-pip-version-check -r requirements.txt
)

start "" pythonw pc_server.py
exit
