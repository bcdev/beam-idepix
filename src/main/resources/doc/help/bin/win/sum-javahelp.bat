@echo off

set SOURCE_DIR=..\..\xml\sum\
set TARGET_DIR=..\..\mepix

rem clean up target directory
rem del %TARGET_DIR% /q
rem del %TARGET_DIR%*.png /q /s
del %TARGET_DIR%JavaHelpSearch /q

rem generate JavaHelp files
xsltproc -o %TARGET_DIR% %SOURCE_DIR%sum-javahelp.xsl %SOURCE_DIR%sum.xml

rem copy figures
rem copy %SOURCE_DIR%figures\*.png %TARGET_DIR%figures /s /i /y

rem create JavaHelp index
jhindexer -db %TARGET_DIR%JavaHelpSearch %TARGET_DIR%
