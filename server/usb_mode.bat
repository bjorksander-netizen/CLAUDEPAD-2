@echo off
title CLAUDEPAD - Mode USB
where adb >nul 2>nul || (echo ADB tidak ditemukan. Download "SDK Platform Tools" dari developer.android.com lalu taruh adb.exe di folder ini atau tambahkan ke PATH. & pause & exit /b 1)
adb start-server
adb reverse tcp:8765 tcp:8765
adb reverse tcp:8767 tcp:8767
if %errorlevel%==0 (echo Mode USB aktif + audio port. Di aplikasi HP tekan tombol USB.) else (echo Gagal. Cek kabel, USB debugging, dan izin ADB di layar HP.)
pause
