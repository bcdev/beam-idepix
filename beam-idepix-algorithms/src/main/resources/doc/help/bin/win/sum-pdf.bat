@echo off

set SOURCE_DIR=..\..\xml\sum\
set TARGET_DIR=..\..\..\..\..\..\..\target\

fop -xml %SOURCE_DIR%sum.xml -xsl %SOURCE_DIR%sum-fo.xsl -pdf %TARGET_DIR%sum.pdf
