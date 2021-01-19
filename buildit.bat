@echo off 

call mvn clean

call mvn install -Dmaven.javadoc.skip=true -Denforcer.skip=true
 

:tryAgain
del C:\Automation\CastBatchWSServer_CGI\dependency\CastAPI-*.jar
if exist C:\Automation\CastBatchWSServer_CGI\dependency\CastAPI-*.jar goto cantDelete
copy CastAPI\target\CastAPI-1.*.jar C:\Automation\CastBatchWSServer_CGI\dependency


del C:\Automation\CastBatchWSServer_CGI\dependency\DMTExpore-*.jar
if exist C:\Automation\CastBatchWSServer_CGI\dependency\DMTExpore-*.jar goto cantDelete
copy DMTExplore\target\DMTExpore-*.jar C:\Automation\CastBatchWSServer_CGI\dependency

 
del C:\Automation\CastBatchWSServer_CGI\WSBatchServer-1.*.jar
if exist C:\Automation\CastBatchWSServer_CGI\WSBatchServer-1.*.jar goto cantDelete
copy WSBatchServer\target\WSBatchServer-1.*.jar C:\Automation\CastBatchWSServer_CGI


del C:\Automation\CastBatchWSServer_CGI\CastAIPWS.hpi
if exist C:\Automation\CastBatchWSServer_CGI\CastAIPWS.hpi goto cantDelete
copy CastAIPWS\target\CastAIPWS.hpi C:\Automation\CastBatchWSServer_CGI


del /q C:\Automation\CastBatchWSServer_CGI\log\*.*
del /q C:\Automation\CastBatchWSServer_CGI\*.log

del C:\Automation\CastBatchWSServer_CGI*.zip
7z a -r C:\Automation\CastBatchWSServer_CGI-1.5.zip C:\Automation\CastBatchWSServer_CGI\*.* > zip.log



exit /b 0

:cantDelete
echo unable to delete files, shut down the web service

goto tryAgain

