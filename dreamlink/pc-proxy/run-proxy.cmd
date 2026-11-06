@echo off
REM Foreground run of the proxy (console window, prints sync/API logs).
REM For real use, run install-autostart.ps1 instead so it starts hidden at logon.
cd /d "%~dp0"
where pythonw >nul 2>nul
if %errorlevel%==0 (
    echo DreamLink proxy starting in background. API: http://127.0.0.1:8756/api/links
    echo Close it via Task Manager ^(pythonw^) or run uninstall-autostart.ps1 to stop autostart.
    start "" pythonw "%~dp0dreamlink_proxy.py"
    goto :eof
)
echo pythonw not found; running with python in this window.
python "%~dp0dreamlink_proxy.py"
pause
