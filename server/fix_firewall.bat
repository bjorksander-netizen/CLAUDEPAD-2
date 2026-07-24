@echo off
REM Dijalankan sebagai Administrator (dipanggil dari start_server.bat / GUI).
REM Aturan dipasang untuk SEMUA profil: jaringan hotspot HP selalu
REM dikategorikan Public oleh Windows, dan profil Public paling ketat.

netsh advfirewall firewall delete rule name="CLAUDEPAD TCP" >nul 2>&1
netsh advfirewall firewall delete rule name="CLAUDEPAD UDP" >nul 2>&1

netsh advfirewall firewall add rule name="CLAUDEPAD TCP" dir=in action=allow protocol=TCP localport=8765 profile=any
netsh advfirewall firewall add rule name="CLAUDEPAD UDP" dir=in action=allow protocol=UDP localport=8766 profile=any
REM v3.5: port audio streaming
netsh advfirewall firewall delete rule name="CLAUDEPAD AUDIO" >nul 2>&1
netsh advfirewall firewall add rule name="CLAUDEPAD AUDIO" dir=in action=allow protocol=TCP localport=8767 profile=any

REM Izinkan juga program Python secara eksplisit (sebagian sistem memblokir
REM per-aplikasi, bukan per-port).
for /f "delims=" %%P in ('where pythonw 2^>nul') do (
  netsh advfirewall firewall delete rule name="CLAUDEPAD pythonw" >nul 2>&1
  netsh advfirewall firewall add rule name="CLAUDEPAD pythonw" dir=in action=allow program="%%P" enable=yes profile=any
)
for /f "delims=" %%P in ('where python 2^>nul') do (
  netsh advfirewall firewall delete rule name="CLAUDEPAD python" >nul 2>&1
  netsh advfirewall firewall add rule name="CLAUDEPAD python" dir=in action=allow program="%%P" enable=yes profile=any
)
exit /b 0
