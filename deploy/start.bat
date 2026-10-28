@echo off
chcp 65001 > nul
title 수강생 관리 서버 - 이 창을 닫으면 사이트가 꺼집니다
cd /d C:\student-app
rem 현재 교시 자동 판정에 서버 PC 시계를 쓰므로 시간대를 한국으로 고정
java -Duser.timezone=Asia/Seoul -jar app.jar
echo.
echo 서버가 종료되었습니다. 오류 내용이 위에 있으면 확인하세요.
pause
