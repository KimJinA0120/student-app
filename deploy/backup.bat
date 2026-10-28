@echo off
chcp 65001 > nul
setlocal
cd /d C:\student-app

rem 서버를 켠 채로 DB를 백업 (H2의 BACKUP TO 명령, 실행 중 파일을 그대로 복사하면 깨질 수 있음)
rem 결과: backup\appdb-날짜-시각.zip / 30일 지난 백업은 자동 삭제

set "PW="
for /f "usebackq tokens=1,* delims==" %%a in ("application.properties") do (
	if "%%a"=="spring.datasource.password" set "PW=%%b"
)
for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set "TS=%%t"
if not exist backup mkdir backup

java -cp app.jar -Dloader.main=org.h2.tools.Shell org.springframework.boot.loader.launch.PropertiesLauncher ^
	-url "jdbc:h2:file:C:/student-app/data/appdb;AUTO_SERVER=TRUE" -user sa -password "%PW%" ^
	-sql "BACKUP TO 'C:/student-app/backup/appdb-%TS%.zip'" > backup\last-backup.log 2>&1
if errorlevel 1 (
	echo [실패] 백업하지 못했습니다. backup\last-backup.log 를 확인하세요.
	if "%~1"=="" pause
	exit /b 1
)
echo [완료] backup\appdb-%TS%.zip

forfiles /p backup /m appdb-*.zip /d -30 /c "cmd /c del @path" > nul 2>&1
rem 작업 스케줄러에서 실행할 때는 "auto" 인자를 줘서 창이 멈추지 않게 함
if "%~1"=="" pause
exit /b 0
