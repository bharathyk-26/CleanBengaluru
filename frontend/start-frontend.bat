@echo off
cd /d "%~dp0"
echo Starting CleanBengaluru HTML frontend on http://localhost:5173
npx --yes http-server . -p 5173
pause
