@echo off
chcp 65001 > nul
rem 매일 22:00에 backup.bat 자동 실행 (서버 PC에 로그인되어 있을 때)
schtasks /create /tn "student-app backup" /tr "C:\student-app\backup.bat auto" /sc daily /st 22:00 /f
if errorlevel 1 (
	echo [실패] 작업을 등록하지 못했습니다.
) else (
	echo [완료] 매일 22:00 자동 백업을 등록했습니다. 작업 스케줄러에서 "student-app backup"으로 확인할 수 있습니다.
)
pause
