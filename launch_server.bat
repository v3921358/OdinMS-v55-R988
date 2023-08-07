@ECHO OFF
ECHO Press any key to begin.
pause >nul

ECHO Starting launch_world.bat
start launch_world.bat
ping localhost -w 10>nul

ECHO Starting launch_login.bat
start launch_login.bat
ping localhost -w 10>nul

ECHO Starting launch_channel.bat
start launch_channel.bat