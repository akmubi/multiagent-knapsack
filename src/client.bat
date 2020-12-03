@echo off
chcp 65001 > nul
echo [Клиентская часть]
javac -classpath ../lib/jade.jar;. *.java -encoding utf-8
set /p host="Введите адрес главного контейнера: "
set /p port="Введите порт главного контейнера: "
set /p in_file="Введите название входного файла (предметы): "
set /p out_file="Введите название выходного файла: "
chcp 866 > nul
java -classpath ../lib/jade.jar;. Main %host% %port% %in_file% %out_file%