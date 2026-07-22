# -*- mode: python ; coding: utf-8 -*-
# PyInstaller spec untuk CLAUDEPAD Server (onefile, tanpa jendela konsol).
block_cipher = None

a = Analysis(
    ['pc_server.py'],
    pathex=[],
    binaries=[],
    # fix_firewall.bat dibundel sebagai data read-only (dicari via _MEIPASS)
    datas=[('fix_firewall.bat', '.')],
    hiddenimports=[
        'input_core', 'system_ctl', 'crypto_box', 'paths', 'binary_protocol',
        'pycaw', 'comtypes', 'pystray', 'PIL', 'websockets',
    ],
    hookspath=[],
    runtime_hooks=[],
    excludes=[],
    cipher=block_cipher,
)
pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz, a.scripts, a.binaries, a.zipfiles, a.datas, [],
    name='CLAUDEPAD-Server',
    debug=False,
    strip=False,
    upx=True,
    runtime_tmpdir=None,
    console=False,           # tanpa jendela konsol
    icon='icon.ico',
)
