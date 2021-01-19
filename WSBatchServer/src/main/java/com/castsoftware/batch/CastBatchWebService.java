package com.castsoftware.batch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Type;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.xml.rpc.ServiceException;
import javax.xml.soap.SOAPException;

import org.apache.log4j.Logger;

import com.castsoftware.batch.data.AnalysisInfo;
import com.castsoftware.batch.task.BackupTask;
import com.castsoftware.batch.task.BatchTask;
import com.castsoftware.batch.task.BatchTask83;
import com.castsoftware.batch.task.DeliveryReportTask;
import com.castsoftware.batch.task.DMTLogsTask;
import com.castsoftware.batch.task.ReportGeneratorTask;
import com.castsoftware.batch.util.CentralDB;
import com.castsoftware.batch.util.ManagmentDB;
import com.castsoftware.batches.BatchHelper;
import com.castsoftware.delivery.DeliveryManager;
import com.castsoftware.dmtexplore.DmtExplore;
import com.castsoftware.dmtexplore.XmlData;
import com.castsoftware.dmtexplore.data.DeliveryData;
import com.castsoftware.exception.OSException;
import com.castsoftware.exception.ReportException;
import com.castsoftware.jenkins.data.Snapshot;
import com.castsoftware.profiles.ConnectionProfilesHelper;
import com.castsoftware.reporting.Report;
import com.castsoftware.reporting.ReportHelper;
import com.castsoftware.reporting.ReportParams;
import com.castsoftware.reporting.ReportType;
import com.castsoftware.restapi.JsonResponse;
import com.castsoftware.taskengine.ErrorMessageList;
import com.castsoftware.taskengine.ErrorMessageManager;
import com.castsoftware.taskengine.TaskList;
import com.castsoftware.taskengine.TaskManager;
import com.castsoftware.util.CastUtil;
import com.castsoftware.util.GlobalProperties;
import com.castsoftware.util.GlobalPropertiesManager;
import com.castsoftware.vps.ValidationProbesService;
import com.castsoftware.vps.vo.ValidationResults;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import hudson.EnvVars;

@WebService
public class CastBatchWebService {
	private static final Logger logger = Logger.getLogger(CastBatchWebServiceServer.class);
	static public final DateFormat castDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
	static public final DateFormat backupDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
	private static final ErrorMessageList errorMessageList = ErrorMessageManager.getErrorMessageList();
	private static final GlobalProperties globalProperties = GlobalPropertiesManager.getGlobalProperties();
	private static final TaskList taskList = TaskManager.getTaskList();
	private static String OS = System.getProperty("os.name").toLowerCase();
	private static int taskCount = 0;

	public static boolean isWindows() {
		return (OS.indexOf("win") >= 0);
	}

	public static boolean isMac() {
		return (OS.indexOf("mac") >= 0);
	}

	public static boolean isUnix() {
		return (OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") > 0);
	}

	public static boolean isSolaris() {
		return (OS.indexOf("sunos") >= 0);
	}

	@WebMethod
	public String getServerVersion() {
		logger.info(String.format("getServerVersion"));

		Gson gson = new Gson();
		return gson.toJson(CastBatchWebServiceServer.getFullVersion());
	}

	@WebMethod
	public String ping() {
		logger.info(String.format("Ping"));

		return "pong";
	}

	@WebMethod
	public boolean isTaskRunning(int taskId) {
		try {
			return taskList.isTaskRunning(taskId);
		} catch (InterruptedException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			return false;
		}
	}

	@WebMethod
	public int getTaskExitValue(int taskId) {
		try {
			return taskList.getTaskExitValue(taskId);
		} catch (InterruptedException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			return -1;
		}
	}

	@WebMethod
	public String getTaskFullOutput(int taskId) {
		return getTaskOutput(taskId, 0);
	}

	@WebMethod
	public String getTaskOutput(int taskId, int fromIndex) {
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("Get Task %d Output from %s", taskId, fromIndex));
		}
		List<String> output;
		try {
			output = taskList.getTaskOutput(taskId, fromIndex);
			if (output == null)
				return "";
			if (logger.isDebugEnabled()) {
				logger.debug((String.format("Output lines: %d", output.size())));
			}
			Gson gson = new Gson();

			return gson.toJson(output);
		} catch (InterruptedException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			return "";
		}
	}

	@WebMethod
	public String getErrorMessage(int errorId) {
		logger.info(String.format("Get Error Message"));
		logger.info(String.format("  - errorId: %s", errorId));
		try {
			return errorMessageList.retrieveMessage(errorId);
		} catch (InterruptedException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			return "Unable to retrieve message. See web service server log for more details";
		}
	}

	@WebMethod
	public int automateDeliveryJNLP(String appId, String appName, String referenceVersion, String versionName,
			Date releaseDate) {
		int returnValue = 0;
		DateFormat dateFormatCLI = new SimpleDateFormat("yyyyMMddHHmmss");
		String releaseDateStr = dateFormatCLI.format(releaseDate);

		logger.info(String.format("Delivery Manager Tool (JNLP) - New delivery automation"));
		logger.info(String.format("  - appId: %s", appId));
		logger.info(String.format("  - appName: %s", appName));
		logger.info(String.format("  - referenceVersion: %s", referenceVersion));
		logger.info(String.format("  - versionName: %s", versionName));
		logger.info(String.format("  - releaseDate: %s", releaseDateStr));

		try {
			String aicPortalUrl = globalProperties.getPropertyValue("aicPortal.url");
			if ((aicPortalUrl != null) && (!aicPortalUrl.isEmpty()))
				logger.info(String.format("  - aicPortal.url: %s", aicPortalUrl));

			String dmtClient = globalProperties.getPropertyValue("dmt.client");
			if ((dmtClient != null) && (!dmtClient.isEmpty()))
				logger.info(String.format("  - dmt.client: %s", dmtClient));

			String aicPortalUser = globalProperties.getPropertyValue("aicPortal.user");
			if ((aicPortalUser != null) && (!aicPortalUser.isEmpty()))
				logger.info(String.format("  - aicPortal.user: %s", aicPortalUser));

			String aicPortalPassword = globalProperties.getPropertyValue("aicPortal.password");
			if ((aicPortalPassword != null) && (!aicPortalPassword.isEmpty()))
				logger.info(String.format("  - aicPortal.password: %s", aicPortalUser));

			String javaPath = globalProperties.getPropertyValue("java.path");
			if ((javaPath != null) && (!javaPath.isEmpty())) {
				if (!javaPath.endsWith("/"))
					javaPath = javaPath + "/";
				logger.info(String.format("  - java.path: %s", javaPath));
			} else {
				throw new IllegalArgumentException("No java path in properties file");
			}

			String java = String.format("%sbin/java.exe -cp ", javaPath);
			logger.info(String.format("  - javaWs: %s", java));

			List<String> paramsRefresh = new ArrayList<String>();
			List<String> paramsAutomateVersion = new ArrayList<String>();

			// DMT Refresh
			paramsRefresh.add(dmtClient);
			paramsRefresh.add("Refresh");
			paramsRefresh.add("-application");
			paramsRefresh.add(appName);
			paramsRefresh.add("-oneApplicationMode");
			paramsRefresh.add(appId);
			paramsRefresh.add("-serverURL");
			paramsRefresh.add(aicPortalUrl);

			if (aicPortalUser != null && !aicPortalUser.isEmpty()) {
				paramsRefresh.add("-username");
				paramsRefresh.add(aicPortalUser);
				paramsRefresh.add("-password");
				paramsRefresh.add(aicPortalPassword);
			}

			// DMT AutomateVersion
			paramsAutomateVersion.add(dmtClient);
			paramsAutomateVersion.add("AutomateVersion");
			paramsAutomateVersion.add("-application");
			paramsAutomateVersion.add(appName);
			paramsAutomateVersion.add("-oneApplicationMode");
			paramsAutomateVersion.add(appId);
			paramsAutomateVersion.add("-serverURL");
			paramsAutomateVersion.add(aicPortalUrl);
			paramsAutomateVersion.add("-version");
			paramsAutomateVersion.add(referenceVersion);
			paramsAutomateVersion.add("-name");
			paramsAutomateVersion.add(versionName);
			paramsAutomateVersion.add("-releaseDate");
			paramsAutomateVersion.add(releaseDateStr);

			if (aicPortalUser != null && !aicPortalUser.isEmpty()) {
				paramsAutomateVersion.add("-username");
				paramsAutomateVersion.add(aicPortalUser);
				paramsAutomateVersion.add("-password");
				paramsAutomateVersion.add(aicPortalPassword);
			}

			// add log file - if needed
			String dmtLog = globalProperties.getPropertyValue("dmt.log");
			if ((dmtLog != null) && (!dmtLog.equals(""))) {
				paramsRefresh.add("-logFilePath");
				paramsRefresh.add(dmtLog);
				paramsAutomateVersion.add("-logFilePath");
				paramsAutomateVersion.add(dmtLog);
			}

			returnValue = taskList.getNextId();
			BatchTask task = new BatchTask(returnValue, taskList);

			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Refresh: %s", task.toString()));
			}
			task.AddProcess(new ProcessBuilder(paramsRefresh));
			task.AddProcess(new ProcessBuilder(paramsAutomateVersion));
			taskList.submitTask(task);
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Task Id: %d", returnValue));
			}

			// this delivery should not apply for the DMT Polling process
			// since it is already being performed via the automation framework.
			AlertDMTChange dmtAlerts = CastBatchWebServiceServer.getAlertProcess();
			dmtAlerts.addIgnoreAppl(appName);

		} catch (InterruptedException | IOException | IllegalArgumentException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			try {
				returnValue = -errorMessageList.storeMessage(e.getMessage());
			} catch (InterruptedException | IllegalArgumentException e1) {
				logger.error(e1.getClass().getName());
				logger.error(e1);
				returnValue = -1;
			}
		}

		return returnValue;
	}

	@WebMethod
	public int automateDeliveryJNLPsave(String appId, String appName, String referenceVersion, String versionName,
			Date releaseDate) {
		int returnValue;
		DateFormat dateFormatCLI = new SimpleDateFormat("yyyyMMddHHmmss");

		try {
			String aicPortalUrl = globalProperties.getPropertyValue("aicPortal.url");

			logger.info(String.format("Delivery Manager Tool (JNLP) - New delivery automation"));
			logger.info(String.format("  - appId: %s", appId));
			logger.info(String.format("  - appName: %s", appName));
			logger.info(String.format("  - referenceVersion: %s", referenceVersion));
			logger.info(String.format("  - versionName: %s", versionName));
			logger.info(String.format("  - releaseDate: %s", dateFormatCLI.format(releaseDate)));
			logger.info(String.format("  - aicPortalUrl: %s", aicPortalUrl));

			String javaPath = globalProperties.getPropertyValue("java.path");
			if ((javaPath != null) && (!javaPath.isEmpty())) {
				if (!javaPath.endsWith("/"))
					javaPath = javaPath + "/";
				logger.info(String.format("  - java.path: %s", javaPath));
			} else {
				throw new IllegalArgumentException("java.path is not set in properties file");
			}
			String javaWs = String.format("%sbin/javaws.exe", javaPath);
			logger.info(String.format("  - javaWs: %s", javaWs));

			String commandLineAutomateVersion = "AutomateVersion -application \"#APPLICATION#\" -version \"#REFERENCE_VERSION#\" -name \"#VERSION_NAME#\" -releaseDate \"#RELEASE_DATE#\"#LOG#";
			String commandLineRefresh = "Refresh -application \"#APPLICATION#\" -version \"#REFERENCE_VERSION#\"#LOG#";
			String commandLineUrl = "#AICPORTAL_URL#/#APPLICATION_ID#/dmt-launcher.jnlp?url=#AICPORTAL_URL#";

			String logFilePathOption = "";
			String dmtLogFilePath = globalProperties.getPropertyValue("dmt.log");
			if ((dmtLogFilePath != null) && (!dmtLogFilePath.equals(""))) {
				logger.info(String.format("DMT log path: %s", dmtLogFilePath));
				logFilePathOption = " -logFilePath \"#LOG_FILE_PATH#\"";
				logFilePathOption = logFilePathOption.replaceAll("#LOG_FILE_PATH#", dmtLogFilePath);
			}

			commandLineAutomateVersion = commandLineAutomateVersion.replaceAll("#APPLICATION_ID#", appId);
			commandLineAutomateVersion = commandLineAutomateVersion.replaceAll("#APPLICATION#", appName);
			commandLineAutomateVersion = commandLineAutomateVersion.replaceAll("#REFERENCE_VERSION#", referenceVersion);
			commandLineAutomateVersion = commandLineAutomateVersion.replaceAll("#VERSION_NAME#", versionName);
			commandLineAutomateVersion = commandLineAutomateVersion.replaceAll("#RELEASE_DATE#",
					dateFormatCLI.format(releaseDate));
			commandLineAutomateVersion = commandLineAutomateVersion.replaceAll("#AICPORTAL_URL#", aicPortalUrl);
			commandLineAutomateVersion = commandLineAutomateVersion.replaceAll("#LOG#", logFilePathOption);

			commandLineRefresh = commandLineRefresh.replaceAll("#APPLICATION_ID#", appId);
			commandLineRefresh = commandLineRefresh.replaceAll("#APPLICATION#", appName);
			commandLineRefresh = commandLineRefresh.replaceAll("#REFERENCE_VERSION#", referenceVersion);
			commandLineRefresh = commandLineRefresh.replaceAll("#VERSION_NAME#", versionName);
			commandLineRefresh = commandLineRefresh.replaceAll("#RELEASE_DATE#", dateFormatCLI.format(releaseDate));
			commandLineRefresh = commandLineRefresh.replaceAll("#AICPORTAL_URL#", aicPortalUrl);
			commandLineRefresh = commandLineRefresh.replaceAll("#LOG#", logFilePathOption);

			commandLineAutomateVersion = commandLineAutomateVersion.replaceAll("\"", "\\\\\"");
			commandLineRefresh = commandLineRefresh.replaceAll("\"", "\\\\\"");

			commandLineUrl = commandLineUrl.replaceAll("#APPLICATION_ID#", appId);
			commandLineUrl = commandLineUrl.replaceAll("#APPLICATION#", appName);
			commandLineUrl = commandLineUrl.replaceAll("#REFERENCE_VERSION#", referenceVersion);
			commandLineUrl = commandLineUrl.replaceAll("#VERSION_NAME#", versionName);
			commandLineUrl = commandLineUrl.replaceAll("#RELEASE_DATE#", dateFormatCLI.format(releaseDate));
			commandLineUrl = commandLineUrl.replaceAll("#AICPORTAL_URL#", aicPortalUrl);
			commandLineUrl = commandLineUrl.replaceAll("#LOG#", logFilePathOption);

			List<String> paramsRefresh = new ArrayList<String>();
			List<String> paramsAutomateVersion = new ArrayList<String>();
			// Exe
			paramsRefresh.add(javaWs);
			paramsAutomateVersion.add(javaWs);

			// Java Params
			paramsRefresh.add("-Xnosplash");
			paramsAutomateVersion.add("-Xnosplash");
			paramsRefresh.add("-silent");
			paramsAutomateVersion.add("-silent");
			paramsRefresh.add("-wait");
			paramsAutomateVersion.add("-wait");
			paramsRefresh.add("-open");
			paramsAutomateVersion.add("-open");

			// DMT Params
			paramsRefresh.add(commandLineRefresh);
			paramsAutomateVersion.add(commandLineAutomateVersion);

			// URL
			paramsRefresh.add(commandLineUrl);
			paramsAutomateVersion.add(commandLineUrl);

			returnValue = taskList.getNextId();
			BatchTask task = new BatchTask(returnValue, taskList);
			task.AddProcess(new ProcessBuilder(paramsRefresh));
			task.AddProcess(new ProcessBuilder(paramsAutomateVersion));
			taskList.submitTask(task);
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Task Id: %d", returnValue));
			}
		} catch (InterruptedException | IOException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			try {
				returnValue = -errorMessageList.storeMessage(e.getMessage());
			} catch (InterruptedException e1) {
				logger.error(e1.getClass().getName());
				logger.error(e1);
				returnValue = -1;
			}
		}
		return returnValue;
	}

	@WebMethod
	public int automateDeliveryCharmJNLP(String appId, String appName, String versionName, String aicPortalUrl) {
		int returnValue;

		try {

			logger.info(String.format("Delivery Manager Tool (JNLP) - CHARM automation"));
			logger.info(String.format("  - appId: %s", appId));
			logger.info(String.format("  - appName: %s", appName));
			logger.info(String.format("  - versionName: %s", versionName));
			logger.info(String.format("  - aicPortalUrl: %s", aicPortalUrl));

			String javaPath = globalProperties.getPropertyValue("java.path");
			if ((javaPath != null) && (!javaPath.equals("")))
				logger.info(String.format("Java path: %s", javaPath));

			String logFilePathOption = "";
			String dmtLogFilePath = globalProperties.getPropertyValue("dmt.log");
			if ((dmtLogFilePath != null) && (!dmtLogFilePath.equals(""))) {
				logger.info(String.format("DMT log path: %s", dmtLogFilePath));
				logFilePathOption = " -logFilePath \"#LOG_FILE_PATH#\"";
				logFilePathOption = logFilePathOption.replaceAll("#LOG_FILE_PATH#", dmtLogFilePath);
			}
			String commandLineGenerateVersion = "Generate -application \"#APPLICATION#\" -version \"#VERSION#\" -reset true#LOG#";
			String commandLineRefresh = "Refresh -application \"#APPLICATION#\" -version \"#VERSION#\"#LOG#";
			String commandLineDeliver = "Deliver -application \"#APPLICATION#\" -version \"#VERSION#\" -close true#LOG#";
			String commandLineUrl = "#AICPORTAL_URL#/#APPLICATION_ID#/dmt-launcher.jnlp?url=#AICPORTAL_URL#";

			commandLineGenerateVersion = commandLineGenerateVersion.replaceAll("#APPLICATION_ID#", appId);
			commandLineGenerateVersion = commandLineGenerateVersion.replaceAll("#APPLICATION#", appName);
			commandLineGenerateVersion = commandLineGenerateVersion.replaceAll("#VERSION#", versionName);
			commandLineGenerateVersion = commandLineGenerateVersion.replaceAll("#AICPORTAL_URL#", aicPortalUrl);
			commandLineGenerateVersion = commandLineGenerateVersion.replaceAll("#LOG#", logFilePathOption);

			commandLineRefresh = commandLineRefresh.replaceAll("#APPLICATION_ID#", appId);
			commandLineRefresh = commandLineRefresh.replaceAll("#APPLICATION#", appName);
			commandLineRefresh = commandLineRefresh.replaceAll("#VERSION#", versionName);
			commandLineRefresh = commandLineRefresh.replaceAll("#AICPORTAL_URL#", aicPortalUrl);
			commandLineRefresh = commandLineRefresh.replaceAll("#LOG#", logFilePathOption);

			commandLineDeliver = commandLineDeliver.replaceAll("#APPLICATION_ID#", appId);
			commandLineDeliver = commandLineDeliver.replaceAll("#APPLICATION#", appName);
			commandLineDeliver = commandLineDeliver.replaceAll("#VERSION#", versionName);
			commandLineDeliver = commandLineDeliver.replaceAll("#AICPORTAL_URL#", aicPortalUrl);
			commandLineDeliver = commandLineDeliver.replaceAll("#LOG#", logFilePathOption);

			commandLineGenerateVersion = commandLineGenerateVersion.replaceAll("\"", "\\\\\"");
			commandLineRefresh = commandLineRefresh.replaceAll("\"", "\\\\\"");
			commandLineDeliver = commandLineDeliver.replaceAll("\"", "\\\\\"");

			commandLineUrl = commandLineUrl.replaceAll("#APPLICATION_ID#", appId);
			commandLineUrl = commandLineUrl.replaceAll("#APPLICATION#", appName);
			commandLineUrl = commandLineUrl.replaceAll("#VERSION#", versionName);
			commandLineUrl = commandLineUrl.replaceAll("#AICPORTAL_URL#", aicPortalUrl);
			commandLineUrl = commandLineUrl.replaceAll("#LOG#", logFilePathOption);

			List<String> paramsRefresh = new ArrayList<String>();
			List<String> paramsGenerateVersion = new ArrayList<String>();
			List<String> paramsDeliverVersion = new ArrayList<String>();
			// Exe
			if ((javaPath != null) && (!javaPath.equals(""))) {
				if (!javaPath.endsWith("/"))
					javaPath = javaPath + "/";

				paramsRefresh.add(javaPath + "javaws");
				paramsGenerateVersion.add(javaPath + "javaws");
				paramsDeliverVersion.add(javaPath + "javaws");
			} else {
				paramsRefresh.add("javaws");
				paramsGenerateVersion.add("javaws");
				paramsDeliverVersion.add("javaws");
			}

			// Java Params
			paramsRefresh.add("-Xnosplash");
			paramsGenerateVersion.add("-Xnosplash");
			paramsDeliverVersion.add("-Xnosplash");
			paramsRefresh.add("-silent");
			paramsGenerateVersion.add("-silent");
			paramsDeliverVersion.add("-silent");
			paramsRefresh.add("-wait");
			paramsGenerateVersion.add("-wait");
			paramsDeliverVersion.add("-wait");
			paramsRefresh.add("-open");
			paramsGenerateVersion.add("-open");
			paramsDeliverVersion.add("-open");

			// DMT Params
			logger.debug("dmtCharmJNLP refresh:" + commandLineRefresh);
			paramsRefresh.add(commandLineRefresh);
			logger.debug("dmtCharmJNLP generate:" + commandLineGenerateVersion);
			paramsGenerateVersion.add(commandLineGenerateVersion);
			logger.debug("dmtCharmJNLP deliver:" + commandLineDeliver);
			paramsDeliverVersion.add(commandLineDeliver);

			if (logger.isDebugEnabled()) {
				logger.debug("dmtCharmJNLP refresh:" + commandLineRefresh);
				logger.debug("dmtCharmJNLP generate:" + commandLineGenerateVersion);
				logger.debug("dmtCharmJNLP deliver:" + commandLineDeliver);
			}

			// URL
			paramsRefresh.add(commandLineUrl);
			paramsGenerateVersion.add(commandLineUrl);
			paramsDeliverVersion.add(commandLineUrl);

			returnValue = taskList.getNextId();
			BatchTask task = new BatchTask(returnValue, taskList);
			task.AddProcess(new ProcessBuilder(paramsRefresh));
			task.AddProcess(new ProcessBuilder(paramsGenerateVersion));
			task.AddProcess(new ProcessBuilder(paramsDeliverVersion));
			taskList.submitTask(task);
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Task Id: %d", returnValue));
			}

			// this delivery should not apply for the DMT Polling process
			// since it is already being performed via the automation framework.
			AlertDMTChange dmtAlerts = CastBatchWebServiceServer.getAlertProcess();
			dmtAlerts.addIgnoreAppl(appName);

		} catch (InterruptedException | IOException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			try {
				returnValue = -errorMessageList.storeMessage(e.getMessage());
			} catch (InterruptedException e1) {
				logger.error(e1.getClass().getName());
				logger.error(e1);
				returnValue = -1;
			}
			;
		}
		return returnValue;
	}

	@WebMethod
	public int deliveryManagerTool(String appId, String appName, String referenceVersion, String versionName,
			Calendar cal) {
		int returnValue;
		DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
		Date releaseDate = cal.getTime();

		logger.info(String.format("Delivery Manager Tool"));
		logger.info(String.format("  - appId: %s", appId));
		logger.info(String.format("  - appName: %s", appName));
		logger.info(String.format("  - referenceVersion: %s", referenceVersion));
		logger.info(String.format("  - versionName: %s", versionName));
		logger.info(String.format("  - releaseDate: %s", castDateFormat.format(releaseDate)));

		try {
			if (!isWindows())
				throw new OSException(String.format("Unsupported Operating System - %s", OS));

			String dmtClient = globalProperties.getPropertyValue("dmt.client");
			String deliveryFolder = globalProperties.getPropertyValue("aicPortal.deliveryFolder");
			String dmtLog = globalProperties.getPropertyValue("dmt.log");
			// String strPrevVersion = getPrevDmtVersion(appName);

			if (dmtLog != null && !dmtLog.isEmpty()) {
				String nowString = dateFormat.format(new Date());
				dmtLog = String.format("%s_%s", dmtLog, nowString);
			}

			String testVersionName = getlatestDmtVersion(deliveryFolder, appName, versionName);
			int versionIdx = -1;
			if (!testVersionName.isEmpty()) {
				String idxStr = testVersionName.substring(testVersionName.length() - 3, testVersionName.length() - 1);
				versionIdx = Integer.parseInt(idxStr);
			}
			versionIdx++;
			versionName = String.format("%s(%02d)", versionName, versionIdx);
			logger.info(String.format("  - final versionName: %s", versionName));

			List<String> paramsAddVersion = new ArrayList<String>();
			List<String> paramsGenerate = new ArrayList<String>();
			// List<String> paramsDeliver = new ArrayList<String>();
			List<String> paramsCloseVersion = new ArrayList<String>();

			// Exe
			paramsAddVersion.add(dmtClient);
			paramsGenerate.add(dmtClient);
			// paramsDeliver.add(dmtClient);
			paramsCloseVersion.add(dmtClient);

			// Action
			paramsAddVersion.add("AddVersion");
			paramsGenerate.add("Generate");
			// paramsDeliver.add("Deliver");
			paramsCloseVersion.add("CloseVersion");
			paramsCloseVersion.add("-audience");
			paramsCloseVersion.add("Integrator");

			// Application Parameter
			paramsAddVersion.add("-application");
			paramsGenerate.add("-application");
			// paramsDeliver.add("-application");
			paramsCloseVersion.add("-application");

			paramsAddVersion.add(appName);
			paramsGenerate.add(appName);
			// paramsDeliver.add(appName);
			paramsCloseVersion.add(appName);

			// Application Id Parameter
			paramsAddVersion.add("-oneApplicationMode");
			paramsGenerate.add("-oneApplicationMode");
			// paramsDeliver.add("-oneApplicationMode");
			paramsCloseVersion.add("-oneApplicationMode");

			paramsAddVersion.add(appId);
			paramsGenerate.add(appId);
			// paramsDeliver.add(appId);
			paramsCloseVersion.add(appId);

			// Reference Version
			paramsAddVersion.add("-version");
			paramsAddVersion.add(referenceVersion);

			// New Version
			paramsAddVersion.add("-name");
			paramsGenerate.add("-version");
			// paramsDeliver.add("-version");
			paramsCloseVersion.add("-version");

			paramsAddVersion.add(versionName);
			paramsGenerate.add(versionName);
			// paramsDeliver.add(versionName);
			paramsCloseVersion.add(versionName);

			// Release Date
			paramsAddVersion.add("-releaseDate");
			paramsAddVersion.add(dateFormat.format(releaseDate));

			// Delivery Folder
			paramsAddVersion.add("-storagePath");
			paramsGenerate.add("-storagePath");
			// paramsDeliver.add("-storagePath");
			paramsCloseVersion.add("-storagePath");

			paramsAddVersion.add(deliveryFolder);
			paramsGenerate.add(deliveryFolder);
			// paramsDeliver.add(deliveryFolder);
			paramsCloseVersion.add(deliveryFolder);

			if ((dmtLog != null) && (!dmtLog.equals(""))) {
				paramsAddVersion.add("-logFilePath");
				paramsGenerate.add("-logFilePath");
				// paramsDeliver.add("-logFilePath");
				paramsCloseVersion.add("-logFilePath");

				paramsAddVersion.add(dmtLog);
				paramsGenerate.add(dmtLog);
				// paramsDeliver.add(dmtLog);
				paramsCloseVersion.add(dmtLog);
			}

			returnValue = taskList.getNextId();
			BatchTask task = new BatchTask(returnValue, taskList, appName, versionName, releaseDate, "DMT");
			task.AddProcess(new ProcessBuilder(paramsAddVersion), "DMT Add ");
			task.AddProcess(new ProcessBuilder(paramsGenerate), "DMT Generate ");
			task.AddProcess(new ProcessBuilder(paramsCloseVersion), "DMT Close ");

			taskList.submitTask(task);

			// this delivery should not apply for the DMT Polling process
			// since it is already being performed via the automation framework.
			AlertDMTChange dmtAlerts = CastBatchWebServiceServer.getAlertProcess();
			dmtAlerts.addIgnoreAppl(appName);

			logger.debug(String.format("Task Id: %d", returnValue));
			if (logger.isDebugEnabled())
				logger.debug(String.format("Task Id: %d", returnValue));

		} catch (IOException | InterruptedException | OSException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			try {
				returnValue = -errorMessageList.storeMessage(e.getMessage());
			} catch (InterruptedException e1) {
				logger.error(e1.getClass().getName());
				logger.error(e1);
				returnValue = -1;
			}
		}
		return returnValue;
	}

	@WebMethod
	public int deliveryReport(String appId, String appName, String referenceVersion, String versionName, Calendar cal) {
		int returnValue = 0;

		logger.info(String.format("Delivery Report"));
		logger.info(String.format("  - appId: %s", appId));
		logger.info(String.format("  - appName: %s", appName));
		logger.info(String.format("  - versionName: %s", versionName));

		try {
			String deliveryFolder = globalProperties.getPropertyValue("aicPortal.deliveryFolder");
			String javaPath = globalProperties.getPropertyValue("java.path");
			String javaExe = new StringBuffer().append(javaPath).append("/bin/java.exe").toString();
			String deliveryReportJar = globalProperties.getPropertyValue("dmt.delivery.report.jar");

			boolean drj = new File(deliveryReportJar).exists();
			boolean jexist = new File(javaExe).exists();
			if (jexist && drj && deliveryFolder != null && !deliveryFolder.isEmpty()) {
				String latestDeliveryVersion = getlatestDmtVersion(deliveryFolder, appName, versionName);
				logger.info(String.format("  - latest DeliveryVersion : %s", latestDeliveryVersion));
				String prevDeliveryVersion = referenceVersion;

				if (!prevDeliveryVersion.isEmpty()) {
					List<String> paramsDeliveryReport = new ArrayList<String>();
					paramsDeliveryReport.add(javaExe);
					paramsDeliveryReport.add("-jar");
					paramsDeliveryReport.add(deliveryReportJar);
					paramsDeliveryReport.add("-delivery");
					paramsDeliveryReport.add(deliveryFolder);
					paramsDeliveryReport.add("-application");
					paramsDeliveryReport.add(appName);
					paramsDeliveryReport.add("-version");
					paramsDeliveryReport.add(latestDeliveryVersion);
					paramsDeliveryReport.add("-previousversion");
					paramsDeliveryReport.add(prevDeliveryVersion);

					ProcessBuilder pb = new ProcessBuilder(paramsDeliveryReport);

					returnValue = taskList.getNextId();
					DeliveryReportTask task = new DeliveryReportTask(returnValue, taskList, appName, versionName,
							cal.getTime(), "DMT");
					task.AddProcess(pb, "Delivery Report ");

					taskList.submitTask(task);
					if (logger.isDebugEnabled())
						logger.debug(String.format("Task Id: %d", returnValue));
				} else {
					logger.info("This is the baseline version, no comparison required");
				}

			} else {
				if (!jexist) {
					logger.error(String.format("Can't find java executer: %s", javaExe));
					returnValue = -2;
				} else if (!drj) {
					logger.error(String.format("Delivery Report Jar File Not Found: %s", deliveryReportJar));
					returnValue = -3;
				} else if (!(deliveryFolder != null && !deliveryFolder.isEmpty())) {
					logger.error("no delivery folder");
					returnValue = -4;
				}
			}

		} catch (InterruptedException | IOException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			try {
				returnValue = -errorMessageList.storeMessage(e.getMessage());
			} catch (InterruptedException e1) {
				logger.error(e1.getClass().getName());
				logger.error(e1);
				returnValue = -1;
			}
		}
		return returnValue;
	}

	@WebMethod
	public int DMTLogs(String appId, String appName, String referenceVersion, String versionName, Calendar cal) {
		int returnValue = 0;

		logger.info(String.format("DMT Logs"));
		logger.info(String.format("  - appId: %s", appId));
		logger.info(String.format("  - appName: %s", appName));
		logger.info(String.format("  - versionName: %s", versionName));

		try {
			String deliveryFolder = globalProperties.getPropertyValue("aicPortal.deliveryFolder");
			String javaPath = globalProperties.getPropertyValue("java.path");
			String javaExe = new StringBuffer().append(javaPath).append("/bin/java.exe").toString();
			String deliveryReportJar = globalProperties.getPropertyValue("dmt.delivery.report.jar");

			boolean drj = new File(deliveryReportJar).exists();
			boolean jexist = new File(javaExe).exists();
			if (jexist && drj && deliveryFolder != null && !deliveryFolder.isEmpty()) {
				String latestDeliveryVersion = getlatestDmtVersion(deliveryFolder, appName, versionName);
				logger.info(String.format("  - latest DeliveryVersion : %s", latestDeliveryVersion));
				String prevDeliveryVersion = referenceVersion;

				if (!prevDeliveryVersion.isEmpty()) {
					List<String> paramsDeliveryReport = new ArrayList<String>();
					paramsDeliveryReport.add(javaExe);
					paramsDeliveryReport.add("-jar");
					paramsDeliveryReport.add(deliveryReportJar);
					paramsDeliveryReport.add("-delivery");
					paramsDeliveryReport.add(deliveryFolder);
					paramsDeliveryReport.add("-application");
					paramsDeliveryReport.add(appName);
					paramsDeliveryReport.add("-version");
					paramsDeliveryReport.add(latestDeliveryVersion);
					paramsDeliveryReport.add("-previousversion");
					paramsDeliveryReport.add(prevDeliveryVersion);

					ProcessBuilder pb = new ProcessBuilder(paramsDeliveryReport);

					returnValue = taskList.getNextId();
					DMTLogsTask task = new DMTLogsTask(999, taskList, appName, latestDeliveryVersion, cal.getTime(),
							deliveryFolder);
					task.AddProcess(pb, "DMT");

					taskList.submitTask(task);
					logger.debug(String.format("Task Id: %d", returnValue));
				} else {
					logger.info("This is the baseline version, no comparison required");
				}

			} else {
				if (!jexist) {
					logger.error(String.format("Can't find java executer: %s", javaExe));
					returnValue = -2;
				} else if (!drj) {
					logger.error(String.format("Delivery Report Jar File Not Found: %s", deliveryReportJar));
					returnValue = -3;
				} else if (!(deliveryFolder != null && !deliveryFolder.isEmpty())) {
					logger.error("no delivery folder");
					returnValue = -4;
				}
			}

		} catch (InterruptedException | IOException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			try {
				returnValue = -errorMessageList.storeMessage(e.getMessage());
			} catch (InterruptedException e1) {
				logger.error(e1.getClass().getName());
				logger.error(e1);
				returnValue = -1;
			}
		}
		return returnValue;
	}

	private Connection getConnection() throws SQLException, InterruptedException, IOException {
		String url = globalProperties.getPropertyValue("cast.database");
		String user = globalProperties.getPropertyValue("db.user");
		String password = globalProperties.getPropertyValue("db.password");

		Properties props = new Properties();
		props.setProperty("user", user);
		props.setProperty("password", password);
		return DriverManager.getConnection(url, props);
	}

	@WebMethod
	public int renameSnapshot(String centralDbName, int snapshotId, String newSnapshotName) {
		logger.info("Renaming snapshot");
		logger.info(String.format("  - centralDbName: %s", centralDbName));
		logger.info(String.format("  - snapshotId: %d", snapshotId));
		logger.info(String.format("  - snapshotName: %s", newSnapshotName));

		CentralDB cdb = new CentralDB();
		return cdb.renameSnapshot(centralDbName, snapshotId, newSnapshotName);
	}

	@WebMethod
	public String getSnapshotList(String centralDbName, String appName) {
		logger.info("Getting Snapshot list");
		logger.info(String.format("  - centralDbName: %s", centralDbName));
		logger.info(String.format("  - findApp: %s", appName));

		Gson gson = new Gson();
		String snapshotList = "";

		CentralDB cdb = new CentralDB();
		List<Snapshot> snapshots = cdb.getSnapshotInfo(centralDbName, appName);
		Collections.sort(snapshots);
		snapshotList = gson.toJson(snapshots);

		return snapshotList;
	}

	@WebMethod
	public String getDmtDeliveryList(String findApp) {
		logger.info("Getting DMT delivery list");
		logger.info(String.format("  - findApp: %s", findApp));

		Gson gson = new Gson();
		String dmtDeliveryList = "";

		String dmtFolder;
		try {
			dmtFolder = globalProperties.getPropertyValue("aicPortal.deliveryFolder");
			logger.info(String.format("  - dmtFolder: %s", dmtFolder));

			DmtExplore dmtExplore = new DmtExplore(dmtFolder, findApp);
			List<XmlData> dmtData = dmtExplore.getDmtData();
			for (XmlData appData : dmtData) { // get the applications
				List<DeliveryData> deliveryData = appData.getDeliveries();
				Collections.sort(deliveryData);
				dmtDeliveryList = gson.toJson(deliveryData);
			}
		} catch (InterruptedException | IOException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
		}
		return dmtDeliveryList;
	}

	@WebMethod
	public String getPrevDmtVersion(String findApp) {
		logger.info("Getting previouse delivery version");
		logger.info(String.format("  - findApp: %s", findApp));

		String dmtFolder = getDMTDeliveryFolder();
		logger.info(String.format("  - dmtFolder: %s", dmtFolder));

		DmtExplore dmtExplore = new DmtExplore(dmtFolder, findApp);
		List<XmlData> dmtData = dmtExplore.getDmtData();
		String prevVersion = "";
		for (XmlData appData : dmtData) { // get the applications
			List<DeliveryData> deliveryData = appData.getDeliveries();
			Collections.sort(deliveryData);

			for (int idx = deliveryData.size() - 1; idx >= 0; idx--) {
				DeliveryData delivery = deliveryData.get(idx);
				String deliveryStatus = delivery.getDeliveryStatus();
				if ("delivery.StatusReadyForAnalysis".equals(deliveryStatus)
						|| "delivery.StatusReadyForAnalysisAndDeployed".equals(deliveryStatus)) {
					prevVersion = delivery.getDeliveryName();
					break;

					// if (rescanType.equals("QA")) {
					// if (DelvName.startsWith("QA")) {
					// prevVersion = delivery.getDeliveryName();
					// break;
					// }
					// } else {
					// if (!DelvName.startsWith("QA")) {
					// prevVersion = delivery.getDeliveryName();
					// break;
					// }
					// }
				}
			}
		}

		// if (prevVersion.equals("")) {
		// for (XmlData appData : dmtData) { // get the applications
		// List<DeliveryData> deliveryData = appData.getDeliveries();
		// Collections.sort(deliveryData);
		//
		// for (int idx = deliveryData.size() - 1; idx >= 0; idx--) {
		// DeliveryData delivery = deliveryData.get(idx);
		// String deliveryStatus = delivery.getDeliveryStatus();
		// if ("delivery.StatusReadyForAnalysis".equals(deliveryStatus)
		// ||
		// "delivery.StatusReadyForAnalysisAndDeployed".equals(deliveryStatus))
		// {
		// String DelvName = delivery.getDeliveryName();
		// if (rescanType.equals("QA")) {
		// if (DelvName.startsWith("QA")) {
		// prevVersion = delivery.getDeliveryName();
		// break;
		// }
		// } else {
		// if (!DelvName.startsWith("QA")) {
		// prevVersion = delivery.getDeliveryName();
		// break;
		// }
		// }
		// }
		// }
		// }
		// }
		return prevVersion;
	}

	private String getlatestDmtVersion(String dmtFolder, String appName, String versionName) {
		logger.info("Getting previouse delivery version");
		logger.info(String.format("  - dmtFolder: %s", dmtFolder));
		logger.info(String.format("  - appName: %s", appName));
		logger.info(String.format("  - versionName: %s", versionName));

		DmtExplore dmtExplore = new DmtExplore(dmtFolder, appName);
		List<XmlData> dmtData = dmtExplore.getDmtData();
		String latestVersion = "";
		
		for (XmlData appData : dmtData) { // get the applications
			List<DeliveryData> deliveryData = appData.getDeliveries();
			if (null != deliveryData) {
				Collections.sort(deliveryData);
				for (int idx = deliveryData.size() - 1; idx >= 0; idx--) {
					DeliveryData delivery = deliveryData.get(idx);
					if (delivery.getDeliveryName().startsWith(versionName)) {
						latestVersion = delivery.getDeliveryName();
						break;
					}
				}
			}
		}
		return latestVersion;
	}

	@WebMethod
	public int acceptDeliveryDMT(String appId, String appName, String versionName, Calendar cal) {
		int returnValue;
		Date releaseDate = cal.getTime();

		logger.info(String.format("Accept Delivery(DMT)"));
		logger.info(String.format("  - appId: %s", appId));
		logger.info(String.format("  - appName: %s", appName));
		logger.info(String.format("  - versionName: %s", versionName));
		logger.info(String.format("  - releaseDate: %s", castDateFormat.format(cal)));

		try {
			if (!isWindows())
				throw new OSException(String.format("Unsupported Operating System - %s", OS));

			String dmtClient = globalProperties.getPropertyValue("dmt.client");
			String deliveryFolder = globalProperties.getPropertyValue("aicPortal.deliveryFolder");
			String dmtLog = globalProperties.getPropertyValue("dmt.log");

			// AcceptDelivery
			List<String> params = new ArrayList<String>();
			params.add(dmtClient);
			params.add("AcceptDelivery");

			params.add("-application");
			params.add(appName);

			params.add("-oneApplicationMode");
			params.add(appId);

			params.add("-version");
			params.add(versionName);

			// Delivery Folder
			params.add("-storagePath");
			params.add(deliveryFolder);

			if ((dmtLog != null) && (!dmtLog.equals(""))) {
				params.add("-logFilePath");
				params.add(dmtLog);
			}

			ProcessBuilder pb = new ProcessBuilder(params);

			returnValue = taskList.getNextId();
			BatchTask task = new BatchTask(returnValue, taskList, appName, versionName, releaseDate, "DMT");
			task.AddProcess(pb, "Accept Delivery");
			taskList.submitTask(task);
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Task Id: %d", returnValue));
			}
		} catch (IOException | InterruptedException | OSException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			try {
				returnValue = -errorMessageList.storeMessage(e.getMessage());
			} catch (InterruptedException e1) {
				logger.error(e1.getClass().getName());
				logger.error(e1);
				returnValue = -1;
			}
			;
		}
		return returnValue;
	}

	@WebMethod
	public int rejectDeliveryDMT(String appId, String appName, String versionName) {
		int returnValue;

		logger.info(String.format("Reject Delivery(DMT)"));
		logger.info(String.format("  - appId: %s", appId));
		logger.info(String.format("  - appName: %s", appName));
		logger.info(String.format("  - versionName: %s", versionName));

		try {
			if (!isWindows())
				throw new OSException(String.format("Unsupported Operating System - %s", OS));

			String dmtClient = globalProperties.getPropertyValue("dmt.client");
			String deliveryFolder = globalProperties.getPropertyValue("aicPortal.deliveryFolder");
			String dmtLog = globalProperties.getPropertyValue("dmt.log");

			// AcceptDelivery
			List<String> params = new ArrayList<String>();
			params.add(dmtClient);
			params.add("RejectDelivery");

			params.add("-application");
			params.add(appName);

			params.add("-oneApplicationMode");
			params.add(appId);

			params.add("-version");
			params.add(versionName);

			// Delivery Folder
			params.add("-storagePath");
			params.add(deliveryFolder);

			if ((dmtLog != null) && (!dmtLog.equals(""))) {
				params.add("-logFilePath");
				params.add(dmtLog);
			}

			ProcessBuilder pb = new ProcessBuilder(params);

			returnValue = taskList.getNextId();
			BatchTask task = new BatchTask(returnValue, taskList);
			task.AddProcess(pb);
			taskList.submitTask(task);
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Task Id: %d", returnValue));
			}
		} catch (IOException | InterruptedException | OSException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			try {
				returnValue = -errorMessageList.storeMessage(e.getMessage());
			} catch (InterruptedException e1) {
				logger.error(e1.getClass().getName());
				logger.error(e1);
				returnValue = -1;
			}
			;
		}
		return returnValue;
	}

	@WebMethod
	public int acceptDelivery(String appName, String versionName, String castMSConnectionProfile, Calendar cal) {
		int returnValue;
		Date releaseDate = cal.getTime();

		logger.info(String.format("Accept Delivery"));
		logger.info(String.format("  - appName: %s", appName));
		logger.info(String.format("  - versionName: %s", versionName));
		logger.info(String.format("  - castMSConnectionProfile: %s", castMSConnectionProfile));
		logger.info(String.format("  - releaseDate: %s", castDateFormat.format(releaseDate)));

		try {
			if (!isWindows())
				throw new OSException(String.format("Unsupported Operating System - %s", OS));

			String castMSClient = globalProperties.getPropertyValue("castms.client");
			String deliveryFolder = globalProperties.getPropertyValue("aicPortal.deliveryFolder");

			String latestDeliveryVersion = getlatestDmtVersion(deliveryFolder, appName, versionName);
			logger.info(String.format("  - updated versionName: %s", latestDeliveryVersion));

			// AcceptDelivery
			ProcessBuilder pb = new ProcessBuilder(castMSClient, "AcceptDelivery", "-connectionProfile",
					castMSConnectionProfile, "-appli", appName, "-version", latestDeliveryVersion);

			returnValue = taskList.getNextId();
			BatchTask task = new BatchTask(returnValue, taskList, appName, versionName, releaseDate, "DMT");
			task.AddProcess(pb, "AcceptDelivery");
			taskList.submitTask(task);
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Task Id: %d", returnValue));
			}
		} catch (IOException | InterruptedException | OSException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			try {
				returnValue = -errorMessageList.storeMessage(e.getMessage());
			} catch (InterruptedException e1) {
				logger.error(e1.getClass().getName());
				logger.error(e1);
				returnValue = -1;
			}
			;
		}
		return returnValue;
	}

	@WebMethod
	public String sendAnalysisLogs(String mngtDbName, String appName, String castDate) {
		logger.info("Sending Analysis Logs to Application Operations Portal: ");
		logger.info(String.format("  - mngtDbName: %s", mngtDbName));
		logger.info(String.format("  - appName: %s", appName));
		logger.info(String.format("  - castDate: %s", castDate));

		Gson gson = new Gson();

		String validationProbService;
		int rsltCode = 0;
		String msg = "Logs successfully sent";
		try {
			validationProbService = globalProperties.getPropertyValue("validation.prob.service");
			if (validationProbService != null && validationProbService.isEmpty()) {
				msg = "Info: Connection to AOP is not configured";
			} else {
				List<AnalysisInfo> anaInfo = new ManagmentDB().getAnalysisRunInfo(mngtDbName);
				if (anaInfo.size() == 0) {
					msg = "No data returned, invalid configuration";
					rsltCode = -1;
				} else {
					processAnaLogFiles(anaInfo);
					ValidationProbesService vps = new ValidationProbesService(validationProbService);
					String analysisLogs = gson.toJson(anaInfo);
					logger.info("Starting to send Analysis Logs");
					vps.sendAnalysisLogs(appName, castDate, analysisLogs);
					logger.info("Completed sending Analysis Logs");
				}
			}
		} catch (InterruptedException | IOException | UnsupportedOperationException | SOAPException e) {
			logger.error("Error sending logs", e);
			msg = String.format("Error sending logs: %s", e.getMessage());
			rsltCode = -1;
		}
		logger.info("Response to plugin stating: initiate");
		JsonResponse response = new JsonResponse(rsltCode, msg);
		logger.info("Response to plugin stating: success");
		return gson.toJson(response);
	}

	private void processAnaLogFiles(List<AnalysisInfo> anaInfo) {
		for (AnalysisInfo info : anaInfo) {
			String fName = info.getLog();

			// Open the file
			FileInputStream fstream;
			try {
				fstream = new FileInputStream(fName);
				BufferedReader br = new BufferedReader(new InputStreamReader(fstream));

				String strLine;
				String lastLine = "";
				// Read File Line By Line
				while ((strLine = br.readLine()) != null) {
					// Print the content on the console
					lastLine = strLine;
				}
				String[] ary = lastLine.split("\t");
				for (String part : ary) {
					if (part.contains("fatal error")) {
						String[] msgAry = part.split(";");
						for (String msgPart : msgAry) {
							for (String msgPart2 : msgPart.split(", ")) {
								String numberPart = msgPart2.trim().split(" ")[0];
								int number;
								try {
									number = Integer.parseInt(numberPart);
								} catch (NumberFormatException e) {
									number = 0;
								}
								if (msgPart.contains("fatal error")) {
									info.setFatalError(number);
								} else if (msgPart.contains("error")) {
									info.setError(number);
								} else if (msgPart.contains("warning")) {
									info.setWarning(number);
								} else if (msgPart.contains("information")) {
									info.setInformation(number);
								}
							}
						}
					}

				}
				// Close the input stream
				br.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@WebMethod
	public int setAsCurrentVersion(String appName, String versionName, String castMSConnectionProfile, Calendar cal) {
		int returnValue;
		Date releaseDate = cal.getTime();

		logger.info(String.format("Set As Current Version"));
		logger.info(String.format("  - appName: %s", appName));
		logger.info(String.format("  - versionName: %s", versionName));
		logger.info(String.format("  - castMSConnectionProfile: %s", castMSConnectionProfile));

		try {
			if (!isWindows())
				throw new OSException(String.format("Unsupported Operating System - %s", OS));

			String castMSClient = globalProperties.getPropertyValue("castms.client");
			String deliveryFolder = globalProperties.getPropertyValue("aicPortal.deliveryFolder");

			String latestDeliveryVersion = getlatestDmtVersion(deliveryFolder, appName, versionName);
			logger.info(String.format("  - updated versionName: %s", latestDeliveryVersion));

			// SetAsCurrentVersion
			ProcessBuilder pb = new ProcessBuilder(castMSClient, "SetAsCurrentVersion", "-connectionProfile",
					castMSConnectionProfile, "-appli", appName, "-version", latestDeliveryVersion);

			returnValue = taskList.getNextId();
			BatchTask task = new BatchTask(returnValue, taskList, appName, versionName, releaseDate, "DMT");
			task.AddProcess(pb, "setAsCurrentVersion");
			taskList.submitTask(task);

			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Task Id: %d", returnValue));
			}
		} catch (IOException | InterruptedException | OSException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			try {
				returnValue = -errorMessageList.storeMessage(e.getMessage());
			} catch (InterruptedException e1) {
				logger.error(e1.getClass().getName());
				logger.error(e1);
				returnValue = -1;
			}
			;
		}
		return returnValue;
	}

	@WebMethod
	public int runPublishSnapshot(String aadSchemaName, String centralSchemaName, String appName, String versionName,
			Calendar cal) {
		int returnValue = 0;
		Date releaseDate = cal.getTime();

		logger.info(String.format("Publish Snapshot"));
		logger.info(String.format("  - aadSchemaName: %s", aadSchemaName));
		logger.info(String.format("  - centralSchemaName: %s", centralSchemaName));
		logger.info(String.format("  - appName: %s", appName));

		List<String> paramsClean = new ArrayList<String>();
		List<String> paramsPublish = new ArrayList<String>();

		try {
			String cleanIt = globalProperties.getPropertyValue("aad.clean");
			if (cleanIt == null)
				cleanIt = "true";

			if (!isWindows())
				throw new OSException(String.format("Unsupported Operating System - %s", OS));

			String aadClient = globalProperties.getPropertyValue("aad.client");
			if (aadClient == null || aadClient.isEmpty()) {
				logger.error("aad.client is not set");
				return -1;
			}
			String castDb = globalProperties.getPropertyValue("cast.database");
			if (castDb == null || castDb.isEmpty()) {
				logger.error("cast.database is not set");
				return -1;
			}
			String castDbMeasure = globalProperties.getPropertyValue("cast.database.measure");
			if (castDbMeasure == null || castDbMeasure.isEmpty()) {
				castDbMeasure = castDb;
			}
			String dbUser = globalProperties.getPropertyValue("db.user");
			if (dbUser == null || dbUser.isEmpty()) {
				logger.error("db.user is not set");
				return -1;
			}
			String dbPassword = globalProperties.getPropertyValue("db.password");
			if (dbPassword == null || dbPassword.isEmpty()) {
				logger.error("db.password is not set");
				return -1;
			}

			CentralDB cdb = new CentralDB();
			long siteId = cdb.getSiteId(centralSchemaName);
			long appId = cdb.getApplicationId(centralSchemaName, appName);

			// cleanup all snapshots for the specific central DB from AAD
			if (Boolean.parseBoolean(cleanIt)) {
				paramsClean.add(aadClient);
				paramsClean.add("-url");
				paramsClean.add(castDbMeasure);
				paramsClean.add("-schema");
				paramsClean.add(aadSchemaName);
				paramsClean.add("-user");
				paramsClean.add(dbUser);
				paramsClean.add("-password");
				paramsClean.add(dbPassword);
				paramsClean.add("-remote_url");
				paramsClean.add(castDb);
				paramsClean.add("-remote_schema");
				paramsClean.add(centralSchemaName);
				paramsClean.add("-remote_user");
				paramsClean.add(dbUser);
				paramsClean.add("-remote_password");
				paramsClean.add(dbPassword);
				paramsClean.add("-remote_site_id");
				paramsClean.add(Long.valueOf(siteId).toString());
				paramsClean.add("-remote_application_id");
				paramsClean.add(Long.valueOf(appId).toString());
				paramsClean.add("-delete");
			}

			// publish all snapshots in the central database to AAD
			paramsPublish.add(aadClient);
			paramsPublish.add("-url");
			paramsPublish.add(castDbMeasure);
			paramsPublish.add("-schema");
			paramsPublish.add(aadSchemaName);
			paramsPublish.add("-user");
			paramsPublish.add(dbUser);
			paramsPublish.add("-password");
			paramsPublish.add(dbPassword);
			paramsPublish.add("-remote_url");
			paramsPublish.add(castDb);
			paramsPublish.add("-remote_schema");
			paramsPublish.add(centralSchemaName);
			paramsPublish.add("-remote_user");
			paramsPublish.add(dbUser);
			paramsPublish.add("-remote_password");
			paramsPublish.add(dbPassword);

			returnValue = taskList.getNextId();
			BatchTask task = new BatchTask(returnValue, taskList, appName, versionName, releaseDate,
					"Publish Snapshot");

			if (Boolean.parseBoolean(cleanIt)) {
				task.AddProcess(new ProcessBuilder(paramsClean), "Clean AAD ");
			}

			task.AddProcess(new ProcessBuilder(paramsPublish), "Publish Snapshots ");
			taskList.submitTask(task);
			if (logger.isDebugEnabled())
				logger.debug(String.format("Task Id: %d", returnValue));
		} catch (OSException | InterruptedException | IOException e) {
			returnValue = logException(e, returnValue);
		}

		return returnValue;
	}

	@WebMethod
	public int runAnalysis(String appName, String versionName, String castMSConnectionProfile, Calendar cal) {
		int returnValue = 0;
		Date releaseDate = cal.getTime();

		logger.info(String.format("Run Analysis"));
		logger.info(String.format("  - appName: %s", appName));
		logger.info(String.format("  - versionName: %s", versionName));
		logger.info(String.format("  - castMSConnectionProfile: %s", castMSConnectionProfile));
		logger.info(String.format("  - releaseDate: %s", castDateFormat.format(releaseDate)));

		try {
			if (!isWindows())
				throw new OSException(String.format("Unsupported Operating System - %s", OS));
			String oracleFix = globalProperties.getPropertyValue("oracleFix");

			String castMSClient = globalProperties.getPropertyValue("castms.client");

			ProcessBuilder pb = new ProcessBuilder(castMSClient, "RunAnalysis", "-connectionProfile",
					castMSConnectionProfile, "-appli", appName);

			returnValue = taskList.getNextId();
			BatchTask task = new BatchTask(returnValue, taskList, appName, versionName, releaseDate, "Analysis");
			if (oracleFix != null && oracleFix.length() > 0) {
				List<String> paramsOracleFix = new ArrayList<String>();
				paramsOracleFix.add(oracleFix);
				paramsOracleFix.add(castMSConnectionProfile);
				task.AddProcess(new ProcessBuilder(paramsOracleFix), "oracleFix");
			}
			task.AddProcess(pb, "Analysis ");
			taskList.submitTask(task);
			String pidd = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Task Id: %d", returnValue));
			}
		} catch (IOException | InterruptedException | OSException e) {
			returnValue = logException(e, returnValue);
		}
		return returnValue;
	}

	@WebMethod
	public int runSnapshot(String appName, String castMSConnectionProfile, String snapshotName, String versionName,
			Calendar cal, String consolidateMeasures) {
		int returnValue;
		DateFormat captureDateFormat = new SimpleDateFormat("yyyyMMddHHmm");
		DateFormat castDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Date captureDate = cal.getTime();

		logger.info(String.format("Run Snapshot"));
		logger.info(String.format("  - appName: %s", appName == null ? "null" : appName));
		logger.info(String.format("  - castMSConnectionProfile: %s",
				castMSConnectionProfile == null ? "null" : castMSConnectionProfile));
		logger.info(String.format("  - snapshotName: %s", snapshotName == null ? "null" : snapshotName));
		logger.info(String.format("  - versionName: %s", versionName == null ? "null" : versionName));
		logger.info(String.format("  - captureDate: %s",
				captureDate == null ? "null" : captureDateFormat.format(captureDate)));
		logger.info(String.format("  - consolidateMeasures: %s", consolidateMeasures));

		try {
			if (appName == null)
				return -errorMessageList.storeMessage("Parameter 'Application Name' is mandatory");
			if (castMSConnectionProfile == null)
				return -errorMessageList.storeMessage("Parameter 'Connection Profile' is mandatory");
			if (snapshotName == null)
				return -errorMessageList.storeMessage("Parameter 'Snapshot Name is mandatory");

			if (!isWindows())
				throw new OSException(String.format("Unsupported Operating System - %s", OS));

			String castMSClient = globalProperties.getPropertyValue("castms.client");

			List<String> params = new ArrayList<String>();
			params.add(castMSClient);
			params.add("GenerateSnapshot");
			params.add("-connectionProfile");
			params.add(castMSConnectionProfile);
			params.add("-appli");
			params.add(appName);
			params.add("-snapshot");
			params.add(snapshotName);

			if (versionName != null) {
				params.add("-version");
				params.add(versionName);
			}

			if (captureDate != null) {
				params.add("-captureDate");
				String capdate = captureDateFormat.format(captureDate);
				params.add(captureDateFormat.format(captureDate));
			}

			params.add("-ignoreEmptyModule");
			params.add("true");

			params.add("-skipAnalysisJob");
			params.add("true");

			params.add("-consolidateMeasures");
			params.add(consolidateMeasures);

			ProcessBuilder pb = new ProcessBuilder(params);

			returnValue = taskList.getNextId();
			BatchTask task = new BatchTask(returnValue, taskList, appName, versionName, captureDate, "Analysis");
			task.AddProcess(pb, "Snapshot ");
			taskList.submitTask(task);
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Task Id: %d", returnValue));
			}
		} catch (IOException | InterruptedException | OSException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			try {
				returnValue = -errorMessageList.storeMessage(e.getMessage());
			} catch (InterruptedException e1) {
				logger.error(e1.getClass().getName());
				logger.error(e1);
				returnValue = -1;
			}
			;
		}

		// Delete all QA'a if rescanType is PROD

		return returnValue;
	}

	 @WebMethod
	 public int UpdateRescanStatus(String appName, String version, String
	 castDate, String status, String stepIdentifier, String RescanType, Boolean IsException)
	 {
	 String validateionProbURL = getValidationProbURL();
	 ValidationProbesService vps;
	 try
	 {
	 vps = new ValidationProbesService(validateionProbURL);
	 vps.UpdateRescanStatus(appName, version, castDate, status,
	 stepIdentifier, RescanType,IsException);
	 } catch (UnsupportedOperationException e)
	 {
	 // TODO Auto-generated catch block
	 e.printStackTrace();
	 } catch (SOAPException e)
	 {
	 // TODO Auto-generated catch block
	 e.printStackTrace();
	 }
	 return 1;
	 }

	@WebMethod
	public int setCurrentScanType(String appName, String rescanType) {
		String validateionProbURL = getValidationProbURL();
		ValidationProbesService vps;
		try {
			vps = new ValidationProbesService(validateionProbURL);
			vps.updateCurrentRescanTypeAOP(appName, rescanType);
		} catch (UnsupportedOperationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SOAPException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 1;
	}

	@WebMethod
	public String runAllChecks(String appName, String snapshotName) {
		StringBuffer sBuffer = new StringBuffer();
		List<ValidationResults> rslts = new ArrayList();
		String validateionProbURL = getValidationProbURL();
		ValidationProbesService vps;
		try {
			vps = new ValidationProbesService(validateionProbURL);
			rslts = vps.runAllChecks(appName, snapshotName);

			for (ValidationResults result : rslts) {
				sBuffer.setLength(0);
				sBuffer.append(result.getCheckNumber()).append("-").append(result.getTestDescription()).append(":")
						.append(result.getAdvice());

			}

		} catch (UnsupportedOperationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SOAPException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		String str = sBuffer.toString();
		if (str.contains("NO GO")) {
			return "false";
		} else {
			return "true";
		}
	}

	/**
	 * Add/increase an application count in the static map to keep track of
	 * number of application running on a analysis server.
	 */
	@WebMethod
	public int addAppl() {
		taskCount += 1;
		logger.info(String.format("Adding application: %d", taskCount));
		return taskCount;
	}

	/**
	 * Decrease an application count in the static map.
	 */
	@WebMethod
	public int deleteAppl() {
		taskCount -= 1;
		if (taskCount < 0) {
			taskCount = 0;
		}
		logger.info(String.format("Deleting application: %d", taskCount));
		return taskCount;
	}

	@WebMethod
	public int getApplCount() {
		logger.info(String.format("Getting application count: %d", taskCount));
		return taskCount;
	}

	@WebMethod
	public int resetApplCount() {
		taskCount = 0;
		return taskCount;
	}

	/**
	 * @params cms_anal1, cms_anal2, cms_anal3, cms_anal4, cms_anal5 - analysis
	 *         server urls.
	 * @return - Analysis server which has the minimum load out of the 5.
	 * 
	 * @WebMethod public String getLowestAppRunCmsAnalServer(String cms_anal1,
	 *            String cms_anal2, String cms_anal3, String cms_anal4, String
	 *            cms_anal5) { Map<String, Integer> map = new HashMap<String,
	 *            Integer>(); map.put(cms_anal1, getCmsCliInstances(cms_anal1));
	 *            map.put(cms_anal2, getCmsCliInstances(cms_anal2));
	 *            map.put(cms_anal3, getCmsCliInstances(cms_anal3));
	 *            map.put(cms_anal4, getCmsCliInstances(cms_anal4));
	 *            map.put(cms_anal5, getCmsCliInstances(cms_anal5));
	 * 
	 *            Entry<String, Integer> min = null;
	 * 
	 *            for (Entry<String, Integer> entry : map.entrySet()) { if (min
	 *            == null || min.getValue() > entry.getValue()) { min = entry; }
	 *            } return min.getKey(); }
	 * 
	 *            /**
	 * @params cms_anal - analysis server url.
	 * @return - Return number of CAST-MS-CLI.exe instances running on passed
	 *         analysis server.
	 * 
	 *         public int getCmsCliInstances(String cmsAnal) { int cmsCliNbrInst
	 *         = 0; try { String line = null; InputStream stdout = null; String
	 *         findProcess = "CAST-MS-CLI.exe"; String filenameFilter = "/nh /fi
	 *         \"Imagename eq " + findProcess + "\""; String[] cmsHostVal =
	 *         cmsAnal.split(":"); String tasksCmd = "Tasklist /S " +
	 *         cmsHostVal[1].replace("/", "") + " " + filenameFilter; ArrayList
	 *         <String> cmsCliLst = new ArrayList<>();
	 * 
	 *         // launch EXE and grab stdout Process process =
	 *         Runtime.getRuntime().exec(tasksCmd); stdout =
	 *         process.getInputStream();
	 * 
	 *         // clean up if any output in stdout BufferedReader brCleanUp =
	 *         new BufferedReader(new InputStreamReader(stdout));
	 * 
	 *         while ((line = brCleanUp.readLine()) != null) { if
	 *         (!line.isEmpty()) { if (!(line.contains( "INFO: No tasks are
	 *         running which match the specified criteria." ))) {
	 *         cmsCliLst.add(line); } } } brCleanUp.close(); cmsCliNbrInst =
	 *         cmsCliLst.size();
	 * 
	 *         } catch (IOException e) { // TODO Auto-generated catch block
	 *         e.printStackTrace(); }
	 * 
	 *         return cmsCliNbrInst; }
	 */
	
	@WebMethod
	public int deleteException(String appName, String version) {
		String validateionProbURL = getValidationProbURL();
		ValidationProbesService vps;
		try {
			vps = new ValidationProbesService(validateionProbURL);
			vps.deleteException(appName, version);
		} catch (UnsupportedOperationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SOAPException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 1;
		
	}	

	@WebMethod
	public int setSchemaNamesInAOP(String appName, String schemaPrefix) {
		String validateionProbURL = getValidationProbURL();
		ValidationProbesService vps;
		try {
			vps = new ValidationProbesService(validateionProbURL);
			vps.setSchemaNamesInAOP(appName, schemaPrefix);
		} catch (UnsupportedOperationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SOAPException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 1;
	}

	@WebMethod
	public int sendJenkinsConsolURL(String appName, String castDate, String consoleURL) {
		logger.info(String.format("Recieved %s, %s, %s ", appName, castDate, consoleURL));
		String validateionProbURL = getValidationProbURL();
		ValidationProbesService vps;
		try {
			vps = new ValidationProbesService(validateionProbURL);
			vps.sendJenkinsConsolInfo(appName, castDate, consoleURL);
		} catch (UnsupportedOperationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SOAPException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 1;

	}

	@WebMethod
	public int archiveDelivery(String appName, String appId, String versionName) {
		int returnValue;

		logger.info(String.format("Archive Delivery"));
		logger.info(String.format("  - appId: %s", appId == null ? "null" : appId));
		logger.info(String.format("  - versionName: %s", versionName == null ? "null" : versionName));

		try {
			if (!isWindows())
				throw new OSException(String.format("Unsupported Operating System - %s", OS));

			String dmtClient = globalProperties.getPropertyValue("dmt.client");
			String deliveryFolder = globalProperties.getPropertyValue("aicPortal.deliveryFolder");
			String dmtLog = globalProperties.getPropertyValue("dmt.log");
			
			String latestDeliveryVersion = getlatestDmtVersion(deliveryFolder, appName, versionName);
			logger.info(String.format("  - updated versionName: %s", latestDeliveryVersion));				

			List<String> params = new ArrayList<String>();
			params.add(dmtClient);
			params.add("ArchiveDelivery");
			params.add("-audience");
			params.add("Integrator");
			params.add("-oneApplicationMode");
			params.add(appId);
			params.add("-version");
			params.add(latestDeliveryVersion);
			params.add("-storagePath");
			params.add(deliveryFolder);

			if ((dmtLog != null) && (!dmtLog.equals(""))) {
				params.add("-logFilePath");
				params.add(dmtLog);
			}

			ProcessBuilder pb = new ProcessBuilder(params);

			returnValue = taskList.getNextId();
			BatchTask task = new BatchTask(returnValue, taskList);
			task.AddProcess(pb);
			taskList.submitTask(task);
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Task Id: %d", returnValue));
			}
		} catch (InterruptedException | IOException | OSException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			try {
				returnValue = -errorMessageList.storeMessage(e.getMessage());
			} catch (InterruptedException e1) {
				logger.error(e1.getClass().getName());
				logger.error(e1);
				returnValue = -1;
			}
			;
		}
		return returnValue;
	}

	@WebMethod
	public int deleteSnapshot(String appName, String castMSConnectionProfile, Date captureDate, String centralName) // central
	{
		int returnValue;
		DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

		logger.info(String.format("Delete Snapshot"));
		logger.info(String.format("  - appName: %s", appName));
		logger.info(String.format("  - castMSConnectionProfile: %s", castMSConnectionProfile));
		logger.info(String.format("  - captureDate: %s", dateFormat.format(captureDate)));
		logger.info(String.format("  - centralName: %s", centralName));

		try {
			if (!isWindows())
				throw new OSException(String.format("Unsupported Operating System - %s", OS));

			String castMSClient = globalProperties.getPropertyValue("castms.client");
			String capDate = dateFormat.format(captureDate);

			ProcessBuilder pb = new ProcessBuilder(castMSClient, "DeleteSnapshot", "-connectionProfile",
					castMSConnectionProfile, "-appli", appName, "-captureDate", capDate, "-dashboardService",
					centralName);
			// ProcessBuilder pb = new ProcessBuilder(castMSClient,
			// "DeleteSnapshotsInList", "-connectionProfile",
			// castMSConnectionProfile, "-appli", appName, "-snapshots",
			// capDate,
			// "-dashboardService", centralName);

			returnValue = taskList.getNextId();
			BatchTask task = new BatchTask(returnValue, taskList, appName, "", captureDate, "Analysis");
			task.AddProcess(pb, "Delete Snapshot ");
			taskList.submitTask(task);
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Task Id: %d", returnValue));
			}

		} catch (IOException | InterruptedException | OSException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			try {
				returnValue = -errorMessageList.storeMessage(e.getMessage());
			} catch (InterruptedException e1) {
				logger.error(e1.getClass().getName());
				logger.error(e1);
				returnValue = -1;
			}
			;
		}
		return returnValue;
	}
	
	@WebMethod
	public int deleteSnapshot83(String appName, String castMSConnectionProfile, String captureDate, String centralName) // central
	{
		logger.info(String.format("Got this date as String in CBWS : " + captureDate));
		int returnValue;
		//DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

		logger.info(String.format("Delete Snapshot"));
		logger.info(String.format("  - appName: %s", appName));
		logger.info(String.format("  - castMSConnectionProfile: %s", castMSConnectionProfile));
		logger.info(String.format("  - captureDate: %s", captureDate));
		logger.info(String.format("  - centralName: %s", centralName));

		try {
			if (!isWindows())
				throw new OSException(String.format("Unsupported Operating System - %s", OS));

			String castMSClient = globalProperties.getPropertyValue("castms.client");
			//String capDate = dateFormat.format(captureDate);

			ProcessBuilder pb = new ProcessBuilder(castMSClient, "DeleteSnapshot", "-connectionProfile",
					castMSConnectionProfile, "-appli", appName, "-captureDate", captureDate, "-dashboardService",
					centralName);

			returnValue = taskList.getNextId();
			BatchTask83 task = new BatchTask83(returnValue, taskList, appName, "", captureDate, "Analysis");
			task.AddProcess(pb, "Delete Snapshot ");
			taskList.submitTask(task);
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Task Id: %d", returnValue));
			}
			// returnValue = taskList.getNextId();
			// BatchTask task = new BatchTask(returnValue, taskList);
			// task.AddProcess(pb);
			// taskList.submitTask(task);
			// logger.debug(String.format("Task Id: %d", returnValue));

		} catch (IOException | InterruptedException | OSException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			try {
				returnValue = -errorMessageList.storeMessage(e.getMessage());
			} catch (InterruptedException e1) {
				logger.error(e1.getClass().getName());
				logger.error(e1);
				returnValue = -1;
			}
			;
		}
		return returnValue;
	}
	
	
	@WebMethod
	// not command line exists for this as of now
	public int consolidateSnapshot(String appName, String castMSConnectionProfile, Date captureDate) {
		logger.info(String.format("consolidateSnapshot"));
		// TODO: Implement
		return -1;
	}

	@WebMethod
	public String getApplicationUUID(String applicationName) {
		logger.info("getApplicationUUID");
		logger.info(String.format("  - applicationName: %s", applicationName));

		try {
			if (applicationName == null) {
				logger.error("(getApplicationUUID)Application name is null!");
				return null;
			}

			logger.info(String.format("getApplicationUUID for %s", applicationName));
			String deliveryFolder;
			deliveryFolder = globalProperties.getPropertyValue("aicPortal.deliveryFolder");
			DeliveryManager dm = new DeliveryManager(deliveryFolder);
			dm.reload();
			return dm.getApplicationUUID(applicationName);
		} catch (InterruptedException | IOException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			return null;
		}
	}

	@WebMethod
	public String listApplicationNames() {
		try {
			logger.info(String.format("listApplicationNames"));
			String deliveryFolder;
			deliveryFolder = globalProperties.getPropertyValue("aicPortal.deliveryFolder");
			DeliveryManager dm = new DeliveryManager(deliveryFolder);
			dm.reload();
			Gson gson = new Gson();

			return gson.toJson(dm.getApplicationNames());
		} catch (InterruptedException | IOException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			return null;
		}
	}

	@WebMethod
	public String listVersions(String applicationName) {
		try {
			if (applicationName == null) {
				logger.error("(listVersions)Application name is null!");
				return null;
			}

			logger.info(String.format("listVersions for %s", applicationName));
			String deliveryFolder;
			deliveryFolder = globalProperties.getPropertyValue("aicPortal.deliveryFolder");
			DeliveryManager dm = new DeliveryManager(deliveryFolder);
			dm.reload();

			Gson gson = new Gson();

			return gson.toJson(dm.getVersions(applicationName));
		} catch (InterruptedException | IOException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			return null;
		}
	}

	@WebMethod
	public String listConnectionProfiles() {
		logger.info(String.format("List Connection Profiles ..."));
		if (!isWindows())
			return String.format("Unsupported Operating System - %s", OS);

		try {
			String castMSClient = globalProperties.getPropertyValue("castms.client");
			logger.info(String.format("Checking in cast-ms ..."));
			logger.info(String.format(castMSClient));
			return ConnectionProfilesHelper.listConnectionProfiles(castMSClient);
		} catch (Exception e) {
			logger.info(String.format("Exception in listConnectionProfiles ..."));
			logger.error(e.getClass().getName());
			logger.error(e);
		}
		return "";
	}

	@WebMethod
	public String listReports(ReportType reportType) {
		logger.info(String.format("List Reports"));
		try {
			ArrayList<Report> reports;
			switch (reportType) {
			case Empowerment:
				if (logger.isDebugEnabled()) {
					logger.debug(String.format(" - Empowerment Reports"));
				}
				reports = ReportHelper.getEmpowermentReportList("reports.json");
				break;
			case ReportGenerator:
				if (logger.isDebugEnabled()) {
					logger.debug(String.format(" - Report Generator"));
				}
				String reportGeneratorTemplates = globalProperties.getPropertyValue("reportGenerator.templates");
				reports = ReportHelper.getReportGeneratorReportList(reportGeneratorTemplates);
				break;
			default:
				throw new ReportException("Unknow Report Type");
			}

			Gson gson = new Gson();

			return gson.toJson(reports);
		} catch (JsonParseException | IOException | InterruptedException | ReportException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			return e.getMessage();
		}
	}

	@WebMethod
	public int runReport(String reportName, String params, String recipients) {
		logger.info(String.format("Run Reports"));

		int returnValue;
		String fullPath;
		Report report;
		String outputParam;
		try {
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Report: %s", reportName));
				logger.debug(String.format("Parameters"));
			}
			Gson gson = new Gson();
			ReportParams reportParams = gson.fromJson(params, ReportParams.class);
			if (logger.isDebugEnabled()) {
				logger.debug(reportParams.formattedToString(" - "));
				logger.debug(String.format("Recipients: %s", recipients));
			}

			switch (reportParams.getReportType()) {
			case Empowerment:
				report = ReportHelper.getEmpowermentReport("reports.json", reportName);
				fullPath = globalProperties.getPropertyValue("empowerment.client");
				outputParam = "-file";
				break;
			case ReportGenerator:
				String reportGeneratorTemplates = globalProperties.getPropertyValue("reportGenerator.templates");
				fullPath = globalProperties.getPropertyValue("reportGenerator.client");
				report = ReportHelper.getReportGeneratorReport(reportGeneratorTemplates, reportName);
				outputParam = "-file";
				break;
			default:
				throw new ReportException("Unknow Report Type");
			}
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Template: %s", report.getTemplate()));
			}

			List<String> localParamArray = Report.formatParameters(report);
			List<String> paramArray = ReportParams.formatParameters(reportParams, report.getParams());

			localParamArray.add(0, fullPath);
			localParamArray.addAll(paramArray);

			// OutputFile
			String outputFolder = globalProperties.getPropertyValue("reportGenerator.outputFolder");
			localParamArray.add(outputParam);
			localParamArray.add(String.format("%s/%s", outputFolder, reportName));

			// System.out.println(localParamArray);

			ProcessBuilder pb = new ProcessBuilder(localParamArray);

			returnValue = taskList.getNextId();
			ReportGeneratorTask task = new ReportGeneratorTask(returnValue, taskList, reportName, reportParams);
			task.AddProcess(pb);
			task.setRecipients(recipients);
			taskList.submitTask(task);
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Task Id: %d", returnValue));
			}
		} catch (JsonParseException | IOException | InterruptedException | ReportException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			try {
				returnValue = -errorMessageList.storeMessage(e.getMessage());
			} catch (InterruptedException e1) {
				logger.error(e1.getClass().getName());
				logger.error(e1);
				returnValue = -1;
			}
			;
		}
		return returnValue;
	}

	@WebMethod
	public String listBatches() {
		logger.info(String.format("List Batches"));
		try {
			String jsonString = BatchHelper.getBatchListAsJson("batches.json");
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("%s", jsonString));
			}
			return jsonString;
		} catch (IOException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			return e.getMessage();
		}
	}

	@WebMethod
	public int runBatch(String batchName, String params) {
		logger.info(String.format("Run Reports"));

		int returnValue;
		String fullPath;
		try {
			fullPath = BatchHelper.getBatchFullPath("batches.json", batchName);
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("%s - %s", batchName, fullPath));
				logger.debug("Parameters:");
			}
			List<String> paramArray = CastUtil.customSplit(params, " ".charAt(0));
			for (String s : paramArray)
				if (logger.isDebugEnabled()) {
					logger.debug(String.format(" - %s", s));
				}

			if (fullPath.equals(""))
				return -errorMessageList
						.storeMessage(String.format("%s is not defined or the fullPath is not specified", batchName));

			paramArray.add(0, fullPath);
			if (fullPath.toLowerCase().endsWith("ps1")) {
				logger.info("Powershell script detected");
				paramArray.add(0, "powershell");
			}

			ProcessBuilder pb = new ProcessBuilder(paramArray);

			returnValue = taskList.getNextId();
			BatchTask task = new BatchTask(returnValue, taskList);
			task.AddProcess(pb);
			taskList.submitTask(task);
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Task Id: %d", returnValue));
			}
		} catch (JsonParseException | IOException | InterruptedException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
			try {
				returnValue = -errorMessageList.storeMessage(e.getMessage());
			} catch (InterruptedException e1) {
				logger.error(e1.getClass().getName());
				logger.error(e1);
				returnValue = -1;
			}
			;
		}
		return returnValue;
	}

	// Initialize Class
	{
		try {
			taskList.setNoConcurrentTasksLimit(true);
		} catch (InterruptedException e) {
			logger.error(e.getClass().getName());
			logger.error(e);
		}
	}

	@WebMethod
	public String getValidationProbURL() {
		String validationProbService;
		try {
			validationProbService = globalProperties.getPropertyValue("validation.prob.service");
		} catch (InterruptedException | IOException e) {
			validationProbService = "";
		}
		return validationProbService;
	}

	@WebMethod
	public Boolean isCaipVersion83() {
		String defCaipVersion = "8.2";
		String setCaipVersion;
		try {
			setCaipVersion = globalProperties.getPropertyValue("caip.version");
		} catch (InterruptedException | IOException e) {
			setCaipVersion = "8.2";
		}
		if (setCaipVersion.compareTo(defCaipVersion) >= 0) {
			return true;
		}
		return false;
	}

	@WebMethod
	public Boolean isDeletePrvsSnapShot() {
		String delPrvsSnap = "true";
		try {
			delPrvsSnap = globalProperties.getPropertyValue("delete.prvs.snapshot");
		} catch (InterruptedException | IOException e) {
			delPrvsSnap = "true";
		}
		if (delPrvsSnap.equals("true")) {
			return true;
		}
		return false;
	}

	@WebMethod
	public String getQAScanFlag() {
		String QAScanFlag;
		try {
			QAScanFlag = globalProperties.getPropertyValue("qa.scans");
		} catch (InterruptedException | IOException e) {
			QAScanFlag = "";
		}
		return QAScanFlag;
	}

	@WebMethod
	public String getValidationStopFlag() {
		String ValidationStopFlag;
		try {
			ValidationStopFlag = globalProperties.getPropertyValue("validation.stop");
		} catch (InterruptedException | IOException e) {
			ValidationStopFlag = "";
		}
		return ValidationStopFlag;
	}

	@WebMethod
	public String getDMTDeliveryFolder() {
		String deliveryFolder;
		try {
			deliveryFolder = globalProperties.getPropertyValue("aicPortal.deliveryFolder");
		} catch (InterruptedException | IOException e) {
			deliveryFolder = "";
		}
		return deliveryFolder;
	}

	@WebMethod
	public int runOptimization(String schemaName, String appName, String versionName, Calendar cal, String tempFolder) {
		int retValue = 0;
		try {
			retValue = taskList.getNextId();
			BatchTask task = new BackupTask(retValue, taskList, appName, versionName, cal.getTime(), "Optimize");

			runBackup(task, schemaName, appName, versionName, cal, "optimize");
			renameSchemas(task, schemaName, appName, versionName, cal, "optimize");
			runRestore(task, schemaName, appName, versionName, cal, "optimize");
			dropSchemas(task, schemaName, appName, versionName, cal, "optimize");

			taskList.submitTask(task);
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Task Id: %d", retValue));
			}
		} catch (InterruptedException e) {
			logException(e);
		}
		return retValue;
	}

	@WebMethod
	public int runBackup(String schemaName, String appName, String versionName, Calendar cal, String stageName) {
		int retValue = 0;
		try {
			retValue = taskList.getNextId();
			BatchTask task = new BackupTask(retValue, taskList, appName, versionName, cal.getTime(), "Backup ");

			runBackup(task, schemaName, appName, versionName, cal, stageName);

			taskList.submitTask(task);
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Task Id: %d", retValue));
			}
		} catch (InterruptedException e) {
			logException(e);
		}
		return retValue;
	}

	private void renameSchemas(BatchTask task, String schemaName, String appName, String versionName, Calendar cal,
			String tempFolder) {
		String backupDate = backupDateFormat.format(cal.getTime());

		logger.info(String.format("Rename Schemas"));
		logger.info(String.format("  - appName: %s", appName));
		logger.info(String.format("  - versionName: %s", versionName));
		logger.info(String.format("  - backupDate: %s", backupDate));
		logger.info(String.format("  - schemaName: %s", schemaName));

		try {
			if (!isWindows())
				throw new OSException(String.format("Unsupported Operating System - %s", OS));

			String psql = new File(globalProperties.getPropertyValue("psql.client")).getCanonicalPath();
			String dbAlterBatch = new File(globalProperties.getPropertyValue("db.alter.batch")).getCanonicalPath();
			String hostName = globalProperties.getPropertyValue("backup.database.host");
			String port = globalProperties.getPropertyValue("backup.database.port");
			String database = globalProperties.getPropertyValue("backup.database");
			String dbUser = globalProperties.getPropertyValue("db.user");
			String dbPassword = globalProperties.getPropertyValue("db.password");

			if (logger.isDebugEnabled()) {
				logger.debug(String.format("  - psql: %s", psql));
				logger.debug(String.format("  - dbAlterBatch: %s", dbAlterBatch));
				logger.debug(String.format("  - hostName: %s", hostName));
				logger.debug(String.format("  - port: %s", port));
				logger.debug(String.format("  - database: %s", database));
				logger.debug(String.format("  - schemaName: %s", schemaName));
				logger.debug(String.format("  - dbUser: %s", dbUser));
				logger.debug(String.format("  - dbPassword: %s", dbPassword));
			}
			List<String> params = new ArrayList<String>();

			params.add(dbAlterBatch);
			params.add(psql);
			params.add(hostName);
			params.add(port);
			params.add(database);
			params.add(dbUser);
			params.add(dbPassword);
			params.add(schemaName);

			task.AddProcess(new ProcessBuilder(params), "Rename Schema's ");

		} catch (OSException | InterruptedException | IOException e) {
			logException(e);
		}

	}

	private void dropSchemas(BatchTask task, String schemaName, String appName, String versionName, Calendar cal,
			String tempFolder) {
		String backupDate = backupDateFormat.format(cal.getTime());

		logger.info(String.format("Rename Schemas"));
		logger.info(String.format("  - appName: %s", appName));
		logger.info(String.format("  - versionName: %s", versionName));
		logger.info(String.format("  - backupDate: %s", backupDate));
		logger.info(String.format("  - schemaName: %s", schemaName));

		try {
			if (!isWindows())
				throw new OSException(String.format("Unsupported Operating System - %s", OS));

			String psql = new File(globalProperties.getPropertyValue("psql.client")).getCanonicalPath();
			String dbDeleteBatch = new File(globalProperties.getPropertyValue("db.delete.batch")).getCanonicalPath();
			String hostName = globalProperties.getPropertyValue("backup.database.host");
			String port = globalProperties.getPropertyValue("backup.database.port");
			String database = globalProperties.getPropertyValue("backup.database");
			String dbUser = globalProperties.getPropertyValue("db.user");
			String dbPassword = globalProperties.getPropertyValue("db.password");

			if (logger.isDebugEnabled()) {
				logger.debug(String.format("  - psql: %s", psql));
				logger.debug(String.format("  - dbDeleteBatch: %s", dbDeleteBatch));
				logger.debug(String.format("  - hostName: %s", hostName));
				logger.debug(String.format("  - port: %s", port));
				logger.debug(String.format("  - database: %s", database));
				logger.debug(String.format("  - schemaName: %s", schemaName));
				logger.debug(String.format("  - dbUser: %s", dbUser));
				logger.debug(String.format("  - dbPassword: %s", dbPassword));
			}

			List<String> params = new ArrayList<String>();

			params.add(dbDeleteBatch);
			params.add(psql);
			params.add(hostName);
			params.add(port);
			params.add(database);
			params.add(dbUser);
			params.add(dbPassword);
			params.add(schemaName);

			task.AddProcess(new ProcessBuilder(params), "Drop Schema's ");

		} catch (OSException | InterruptedException | IOException e) {
			logException(e);
		}

	}

	private void runBackup(BatchTask task, String schemaName, String appName, String versionName, Calendar cal,
			String special) {
		String backupDate = backupDateFormat.format(cal.getTime());

		logger.info(String.format("Run CSS Backup"));
		logger.info(String.format("  - appName: %s", appName));
		logger.info(String.format("  - versionName: %s", versionName));
		logger.info(String.format("  - backupDate: %s", backupDate));
		logger.info(String.format("  - schemaName: %s", schemaName));

		try {
			if (!isWindows())
				throw new OSException(String.format("Unsupported Operating System - %s", OS));

			String client = globalProperties.getPropertyValue("backup.client");
			String backupFolder = globalProperties.getPropertyValue("backup.folder");
			String database = globalProperties.getPropertyValue("backup.database");
			String hostName = globalProperties.getPropertyValue("backup.database.host");
			String port = globalProperties.getPropertyValue("backup.database.port");
			String dbUser = globalProperties.getPropertyValue("db.user");
			String dbPassword = globalProperties.getPropertyValue("db.password");

			if (logger.isDebugEnabled()) {
				logger.debug(String.format("  - client: %s", client));
				logger.debug(String.format("  - backupFolder: %s", backupFolder));
				logger.debug(String.format("  - hostName: %s", hostName));
				logger.debug(String.format("  - port: %s", port));
				logger.debug(String.format("  - database: %s", database));
				logger.debug(String.format("  - schemaName: %s", schemaName));
				logger.debug(String.format("  - dbUser: %s", dbUser));
				logger.debug(String.format("  - dbPassword: %s", dbPassword));
			}

			if (special.equals("bfr_rescan") || special.equals("aft_rescan")) {
				String ddmmyyhhmm = String.format("%02d", cal.get(Calendar.DAY_OF_MONTH))
						+ String.format("%02d", cal.get(Calendar.MONTH) + 1)
						+ String.format("%02d", cal.get(Calendar.YEAR) % 100)
						+ String.format("%02d", cal.get(Calendar.HOUR_OF_DAY))
						+ String.format("%02d", cal.get(Calendar.MINUTE));
				backupDate = versionName + special + "_" + ddmmyyhhmm;
				special = "";
			}

			File mngtBackupFileName = new File(
					String.format("%s/%s%s_mngt_%s.cssdmp", backupFolder, special, schemaName, backupDate));
			if (mngtBackupFileName.exists())
				mngtBackupFileName.delete();

			File localBackupFileName = new File(
					String.format("%s/%s%s_local_%s.cssdmp", backupFolder, special, schemaName, backupDate));
			if (localBackupFileName.exists())
				localBackupFileName.delete();

			File centralBackupFileName = new File(
					String.format("%s/%s%s_central_%s.cssdmp", backupFolder, special, schemaName, backupDate));
			if (centralBackupFileName.exists())
				centralBackupFileName.delete();

			List<String> paramsManagmentDB = new ArrayList<String>();
			paramsManagmentDB.add(client);
			paramsManagmentDB.add("-database");
			paramsManagmentDB.add(database);
			paramsManagmentDB.add("-port");
			paramsManagmentDB.add(port);
			paramsManagmentDB.add("-host");
			paramsManagmentDB.add(hostName);
			paramsManagmentDB.add("-username");
			paramsManagmentDB.add(dbUser);
			paramsManagmentDB.add("-password");
			paramsManagmentDB.add(dbPassword);

			List<String> paramsLocalDB = new ArrayList<String>(paramsManagmentDB);
			List<String> paramsCentralDB = new ArrayList<String>(paramsManagmentDB);

			// schema name
			paramsManagmentDB.add("-schema");
			paramsLocalDB.add("-schema");
			paramsCentralDB.add("-schema");

			paramsManagmentDB.add(String.format("%s_mngt", schemaName));
			paramsLocalDB.add(String.format("%s_local", schemaName));
			paramsCentralDB.add(String.format("%s_central", schemaName));

			// Backup File Name
			paramsManagmentDB.add("-file");
			paramsLocalDB.add("-file");
			paramsCentralDB.add("-file");

			paramsManagmentDB.add(mngtBackupFileName.getCanonicalPath());
			paramsLocalDB.add(localBackupFileName.getCanonicalPath());
			paramsCentralDB.add(centralBackupFileName.getCanonicalPath());

			// Log File Name
			paramsManagmentDB.add("-log");
			paramsLocalDB.add("-log");
			paramsCentralDB.add("-log");

			paramsManagmentDB.add(new File(String.format("%s/%s_mngt_%s.log", backupFolder, schemaName, backupDate))
					.getCanonicalPath());
			paramsLocalDB.add(new File(String.format("%s/%s_local_%s.log", backupFolder, schemaName, backupDate))
					.getCanonicalPath());
			paramsCentralDB.add(new File(String.format("%s/%s_central_%s.log", backupFolder, schemaName, backupDate))
					.getCanonicalPath());

			task.AddProcess(new ProcessBuilder(paramsManagmentDB), "Backup: Managment Database ");
			task.AddProcess(new ProcessBuilder(paramsLocalDB), "Backup: Local Database ");
			task.AddProcess(new ProcessBuilder(paramsCentralDB), "Backup: Central Database ");

		} catch (OSException | InterruptedException | IOException e) {
			logException(e);
		}
	}

	private void runRestore(BatchTask task, String schemaName, String appName, String versionName, Calendar cal,
			String special) {
		String backupDate = backupDateFormat.format(cal.getTime());

		logger.info(String.format("Run CSS Restore"));
		logger.info(String.format("  - appName: %s", appName));
		logger.info(String.format("  - versionName: %s", versionName));
		logger.info(String.format("  - backupDate: %s", backupDate));
		logger.info(String.format("  - schemaName: %s", schemaName));

		try {
			if (!isWindows())
				throw new OSException(String.format("Unsupported Operating System - %s", OS));

			String client = globalProperties.getPropertyValue("restore.client");
			String backupFolder = globalProperties.getPropertyValue("backup.folder");
			String database = globalProperties.getPropertyValue("backup.database");
			String hostName = globalProperties.getPropertyValue("backup.database.host");
			String port = globalProperties.getPropertyValue("backup.database.port");
			String dbUser = globalProperties.getPropertyValue("db.user");
			String dbPassword = globalProperties.getPropertyValue("db.password");

			if (logger.isDebugEnabled()) {
				logger.debug(String.format("  - client: %s", client));
				logger.debug(String.format("  - backupFolder: %s", backupFolder));
				logger.debug(String.format("  - hostName: %s", hostName));
				logger.debug(String.format("  - port: %s", port));
				logger.debug(String.format("  - database: %s", database));
				logger.debug(String.format("  - schemaName: %s", schemaName));
				logger.debug(String.format("  - dbUser: %s", dbUser));
				logger.debug(String.format("  - dbPassword: %s", dbPassword));
			}
			List<String> paramsManagmentDB = new ArrayList<String>();
			paramsManagmentDB.add(client);
			paramsManagmentDB.add("-database");
			paramsManagmentDB.add(database);
			paramsManagmentDB.add("-port");
			paramsManagmentDB.add(port);
			paramsManagmentDB.add("-host");
			paramsManagmentDB.add(hostName);
			paramsManagmentDB.add("-username");
			paramsManagmentDB.add(dbUser);
			paramsManagmentDB.add("-password");
			paramsManagmentDB.add(dbPassword);

			List<String> paramsLocalDB = new ArrayList<String>(paramsManagmentDB);
			List<String> paramsCentralDB = new ArrayList<String>(paramsManagmentDB);

			// schema name
			paramsManagmentDB.add("-schema");
			paramsLocalDB.add("-schema");
			paramsCentralDB.add("-schema");

			paramsManagmentDB.add(String.format("%s_mngt", schemaName));
			paramsLocalDB.add(String.format("%s_local", schemaName));
			paramsCentralDB.add(String.format("%s_central", schemaName));

			// Backup File Name
			paramsManagmentDB.add("-file");
			paramsLocalDB.add("-file");
			paramsCentralDB.add("-file");

			paramsManagmentDB.add(
					new File(String.format("%s/%s%s_mngt_%s.cssdmp", backupFolder, special, schemaName, backupDate))
							.getCanonicalPath());
			paramsLocalDB.add(
					new File(String.format("%s/%s%s_local_%s.cssdmp", backupFolder, special, schemaName, backupDate))
							.getCanonicalPath());
			paramsCentralDB.add(
					new File(String.format("%s/%s%s_central_%s.cssdmp", backupFolder, special, schemaName, backupDate))
							.getCanonicalPath());

			// Log File Name
			paramsManagmentDB.add("-log");
			paramsLocalDB.add("-log");
			paramsCentralDB.add("-log");

			paramsManagmentDB.add(new File(String.format("%s/%s_mngt_%s.log", backupFolder, schemaName, backupDate))
					.getCanonicalPath());
			paramsLocalDB.add(new File(String.format("%s/%s_local_%s.log", backupFolder, schemaName, backupDate))
					.getCanonicalPath());
			paramsCentralDB.add(new File(String.format("%s/%s_central_%s.log", backupFolder, schemaName, backupDate))
					.getCanonicalPath());

			task.AddProcess(new ProcessBuilder(paramsManagmentDB), "Restore: Managment Database ");
			task.AddProcess(new ProcessBuilder(paramsLocalDB), "Restore: Local Database ");
			task.AddProcess(new ProcessBuilder(paramsCentralDB), "Restore: Central Database ");

		} catch (Exception e) {
			logger.info(String.format("%s", e.toString()));
			logException(e);
		}
	}

	public int logException(Exception e, int retValue) {
		logger.error(e.getClass().getName());
		logger.error(e);
		try {
			retValue = -errorMessageList.storeMessage(e.getMessage());
		} catch (InterruptedException e1) {
			logger.error(e1.getClass().getName());
			logger.error(e1);
			retValue = -1;
		}
		return retValue;
	}

	public int logException(Exception e) {
		return logException(e, 0);
	}

}
