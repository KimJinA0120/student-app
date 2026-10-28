@echo off
chcp 65001 > nul
setlocal
cd /d C:\student-app

rem 백업 zip으로 DB를 되돌림. 반드시 서버 창(start.bat)을 먼저 닫고 실행
rem 사용: restore.bat backup\appdb-20261110-220000.zip

if "%~1"=="" (
	echo 사용법: restore.bat backup\appdb-날짜-시각.zip
	echo.
	echo 백업 목록 ^(최근순^):
	dir /b /o-d backup\appdb-*.zip
	pause
	exit /b 1
)
if not exist "%~1" (
	echo 파일이 없습니다: %~1
	pause
	exit /b 1
)

echo 서버 창^(start.bat^)을 닫았는지 확인하세요.
set /p OK=지금 DB를 "%~1" 내용으로 바꿉니다. 계속하려면 Y 입력 후 Enter: 
if /i not "%OK%"=="Y" (
	echo 취소했습니다.
	pause
	exit /b 1
)

for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set "TS=%%t"
rem 지금 DB는 지우지 않고 backup\before-restore-시각 폴더로 옮겨 둠 (서버가 켜져 있으면 파일이 잠겨 실패함)
if exist data\appdb.mv.db (
	mkdir "backup\before-restore-%TS%"
	move data\appdb.mv.db "backup\before-restore-%TS%\" > nul
	if errorlevel 1 (
		echo [중단] DB 파일을 옮기지 못했습니다. 서버 창이 켜져 있으면 닫고 다시 실행하세요.
		pause
		exit /b 1
	)
)
if not exist data mkdir data
powershell -NoProfile -Command "Expand-Archive -LiteralPath '%~1' -DestinationPath 'data' -Force"
if errorlevel 1 (
	echo [실패] 압축을 풀지 못했습니다. 원래 DB는 backup\before-restore-%TS% 에 있습니다.
	pause
	exit /b 1
)
echo [완료] 복구했습니다. start.bat으로 서버를 다시 켜고 화면을 확인하세요.
echo        복구 전 DB: backup\before-restore-%TS%\appdb.mv.db
pause
