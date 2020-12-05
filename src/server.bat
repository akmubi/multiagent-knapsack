@echo off
chcp 65001 > nul
echo [Серверная часть]
javac -classpath ../lib/jade.jar;. *.java -encoding utf-8
set /p in_file="Введите название входного файла (имена туристов): "
chcp 866 > nul
java -classpath ../lib/jade.jar;. Main %in_file%
pause