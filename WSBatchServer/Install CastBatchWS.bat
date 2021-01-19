SET INSTALL_FOLDER=C:\Dev\Delivery\WebServiceServer

SET SERVICE_NAME=CastBatchWSServer
SET SERVICE_DESCRIPTION=Cast Web Service Batch Server
SET PRUNSRV=%INSTALL_FOLDER%\CastBatchWSServer.exe
SET CLASSPATH="%INSTALL_FOLDER%\dependency\*;WSBatchServer-1.2.jar"
SET START_STOP_CLASS=com.castsoftware.batch.CastBatchWebServiceServer
SET LOG=%INSTALL_FOLDER%\Log

CastBatchWSServer.exe //IS//%SERVICE_NAME% --Install="%PRUNSRV%" --Description="%SERVICE_DESCRIPTION%" --Jvm=auto --Classpath=%CLASSPATH% --StartMode=jvm --StartClass=%START_STOP_CLASS% --StartParams=start --StopMode=jvm --StopClass=%START_STOP_CLASS% --StopParams=stop --LogPath="%LOG%" --StdOutput=auto --StdError=auto

pause