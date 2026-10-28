@echo off
chcp 65001 > nul
rem 관리자 권한으로 실행 (마우스 오른쪽 → 관리자 권한으로 실행)
rem 같은 내부망(로컬 서브넷)에서만 3000 포트 접속 허용
netsh advfirewall firewall add rule name="student-app 3000" dir=in action=allow protocol=TCP localport=3000 remoteip=localsubnet profile=any
if errorlevel 1 (
	echo [실패] 관리자 권한으로 다시 실행하세요.
) else (
	echo [완료] 3000 포트 인바운드 허용 규칙을 추가했습니다.
)
pause
