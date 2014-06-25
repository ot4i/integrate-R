@echo off
set WORKPATH=%~1
set EXTPATH=%~2
if exist "%WORKPATH%\common\profiles\RNode.cmd" (
	del /f /q "%WORKPATH%\common\profiles\RNode.cmd"
)
copy /y "%EXTPATH%\samples\RNode.cmd" "%WORKPATH%\common\profiles\RNode.cmd" >nul
