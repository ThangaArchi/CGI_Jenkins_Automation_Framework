package com.castsoftware.batch;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import javax.xml.soap.SOAPException;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jfree.util.Log;
import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListener;
//import org.apache.commons.io;
import org.apache.commons.io.input.TailerListenerAdapter;

import com.castsoftware.dmtexplore.util.NoOpEntityResolver;
import com.castsoftware.util.GlobalProperties;
import com.castsoftware.util.GlobalPropertiesManager;
import com.castsoftware.vps.ValidationProbesService;

public class AlertDMTChange extends Thread {
	private static final Logger logger = Logger.getLogger(AlertDMTChange.class);
	private static final GlobalProperties globalProperties = GlobalPropertiesManager.getGlobalProperties();

	private boolean run = true;
	private int timeInterval = 30 * 1000;
	private String deliveryFolder;
	private String validationProbService;
	private ValidationProbesService vps = null;
	private List<String> applBlackList = new ArrayList<String>();

	public void addIgnoreAppl(String applName) {
		applBlackList.add(applName);
	}

	public boolean isApplBlacklisted(String applName) {
		boolean rslt = false;

		int idx = 0;
		for (String str : applBlackList) {
			if (str.trim().equals(applName)) {
				applBlackList.remove(idx);
				rslt = true;
				break;
			}
			idx++;
		}

		return rslt;
	}

	public List<String> getNewDeliveryInfo(List<DMTData> dmtData, Date lastCheck) {
		List<String> recentVerUuidLst = new ArrayList<String>();
		try {
			String dmtLog2RdrJar = globalProperties.getPropertyValue("dmt.log2.reader.jar");
			String aicDlryFolder = globalProperties.getPropertyValue("aicPortal.deliveryFolder");
			String dmtLog2FileName = globalProperties.getPropertyValue("dmt.log2.file.name");
			Map<String, String> stAwtValdt = new HashMap<String, String>(); // StatusAwaitingValidation Map
														
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd H:m:s.S");

			for (DMTData d : dmtData) {
				stAwtValdt.put(d.getVersionInfo().getUuid(),
						"cast.deliveryserver.upload.file [file=\"" + aicDlryFolder.toLowerCase().replace("/", "\\") + "\\data\\" + "{"
								+ d.getUuid() + "}" + "\\" + d.getVersionInfo().getUuid() + ".entity.xml" + "\"]"); 
			}
			
			logger.info("StatusAwaitingValidation versions Map: ");
			logger.info(stAwtValdt);
			logger.info("");

			String cmd = "java -jar " + dmtLog2RdrJar + " -input \"" + aicDlryFolder + "\\" + dmtLog2FileName + "\" ";
			Process proc = Runtime.getRuntime().exec(cmd);

			BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String s = null;
			String[] arrOfStr;
			while ((s = stdInput.readLine()) != null) {
				arrOfStr = s.split("INFO:");
				if (arrOfStr.length > 1) {
					//logger.info(arrOfStr[1].toString());
					if (stAwtValdt.containsValue(arrOfStr[1].toLowerCase().trim())) {
						Date dlvryDate = formatter.parse(arrOfStr[0].replaceAll("-", "").trim());
						logger.info("Delivery date of new version : " + dlvryDate.toString());
						logger.info("Last check date of polling : " + lastCheck.toString());
						if (dlvryDate.after(lastCheck)) {
							File f = new File(arrOfStr[1].trim());
							String recentUuidVal = f.getName().replaceAll(".entity.xml\"]", "");
							if (!recentVerUuidLst.contains(recentUuidVal))
							    recentVerUuidLst.add(recentUuidVal);
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		logger.info("Latest List of version UUID after Polling last check timestamp : " + recentVerUuidLst.toString());
		return recentVerUuidLst;
	}

	@Override
	public void run() {
		String jenkinsURL;
		try {
			jenkinsURL = globalProperties.getPropertyValue("dmt.alert.jenkins.url");
			String jenkinsAccount = globalProperties.getPropertyValue("dmt.alert.jenkins.account");
			String jenkinsPassword = globalProperties.getPropertyValue("dmt.alert.jenkins.password");
			validationProbService = globalProperties.getPropertyValue("validation.prob.service");
			Boolean runDMTReport = Boolean.valueOf(globalProperties.getPropertyValue("dmt.alert.jenkins.url", "false"));

			logger.info("Alert on DMT Change is Active");
			logger.info(String.format("Jenkins URL: %s", jenkinsURL));
			logger.debug(String.format("AOL URL: %s", validationProbService));
			logger.debug(String.format("Delivery Folder: %s", deliveryFolder));
			logger.info(String.format("time interval: %d milliseconds", timeInterval));

			SimpleDateFormat dmtFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			SimpleDateFormat castDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			Jenkins jenkins = new Jenkins(jenkinsURL, jenkinsAccount, jenkinsPassword);

			Date lastCheck = new Date();
			List<String> recentVerUuidLst = new ArrayList<String>();

			while (run) {

				try {
					logger.info("Checking DMT for Changes");
					List<DMTData> dmtData = readDMTIndex();
					recentVerUuidLst = getNewDeliveryInfo(dmtData, lastCheck);
					logger.info("Poll last check timestamp : " + lastCheck.toString());
					if (dmtData != null) {
						for (DMTData d : dmtData) {
							
							logger.info("Checking for App :" + d.getApplName());
							if (null != d.getVersionInfo().getStatus()
									&& d.getVersionInfo().getStatus().equals("delivery.StatusAwaitingValidation")
									&& recentVerUuidLst.contains(d.getVersionInfo().getUuid())){
								logger.info("Found new delivery for App Name : " + d.getApplName());
								logger.info("Found delivery.StatusAwaitingValidation version for version ... "
										+ d.getVersionInfo().getVersionName());
								logger.info("DMT Delivery time using CMS or Jenkins, (NOT from AICP): " + d.getVersionInfo().getLastModified().toString());
								logger.info("\n------------------------");

								// if
								// (d.getVersionInfo().getLastModified().after(lastCheck))
								// { //MKU
								logger.info(String.format("Application Delivered: %s", d.getApplName()));

								if (validationProbService != null && validationProbService.isEmpty()) {
									logger.info("Connection to AOP is not configured");
								} else {
									// ValidationProbesService vps;
									try {
										logger.info("Delivery Received, getting ready to run Jenkins ...");

										Date date1 = dmtFormat.parse(d.getVersionInfo().getReleaseDate());
										String castDate = castDateFormat.format(date1);

										String applName = d.getApplName();

										if (isApplBlacklisted(applName)) {
											Log.info(String.format(
													"Skipping delivery trigger, %s was delivered via the automation framework",
													applName));
										} else {
											CastBatchWebService si = CastBatchWebServiceServer.getServiceProcess();

											String versionName = d.getVersionInfo().getVersionName();
											Calendar cal = convertCastDate(castDate);

											logger.info("Starting Jenkins Job");
											if (!jenkins.runJob(applName, castDate, versionName, 2)) {
												logger.info("Jenkins Job Not Found");

												if (vps == null) {
													vps = new ValidationProbesService(validationProbService);
												}
												if (vps != null) {
													logger.info("Sending AOP Update status message");
													vps.UpdateRescanStatus(applName, versionName, castDate,
															"Not Automated in Jenkins", "Process Exception", "PROD",
															false);
												}
											}
											if (runDMTReport) {
												logger.info("Running delivery report");
												String appId = si.getApplicationUUID(applName);
												String referenceVersion = si.getPrevDmtVersion(applName);
												si.deliveryReport(appId, applName, referenceVersion, versionName, cal);
												si.DMTLogs(appId, applName, referenceVersion, versionName, cal);
											}

										}

										// jenkins.runJob(d.getApplName(),
										// rescanType)

										// vps = new
										// ValidationProbesService(validationProbService);
										// vps.UpdateRescanStatus(d.getApplName(),
										// "versionName",
										// castDateFormat.format(d.getLastModified()),
										// "DMT Delivery Complete", "DMT
										// Close ");
									} catch (UnsupportedOperationException | ParseException | SOAPException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
								}
								// } MKU
							}
							// lastCheck.setTime(System.currentTimeMillis());
							// NKA
						}
					}
					lastCheck.setTime(System.currentTimeMillis());
					
					logger.info("Again setting the system time ..." + lastCheck.toString());
					logger.info("Sleeping now for 1 min ...");
					logger.info("\n\n =================================");
					Thread.sleep(timeInterval);
				} catch (InterruptedException e) {
					// Interrupted exception will occur if
					// the Worker object's interrupt() method
					// is called. interrupt() is inherited
					// from the Thread class.
					break;
				}
			}
		} catch (InterruptedException | IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	private Calendar convertCastDate(String castDate) throws ParseException {
		Calendar cal = Calendar.getInstance();
		DateFormat castDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");
		Date dateForToday = castDateFormat.parse(castDate);
		cal.setTime(dateForToday);
		return cal;
	}

	public void stopThread() {
		logger.info(String.format("Deactivating Alert on DMT Change"));
		run = false;
	}

	public void setTimeInterval(int timeInterval) {
		this.timeInterval = timeInterval;
	}

	public void setDeliveryFolder(String deliveryFolder) {
		this.deliveryFolder = deliveryFolder;
	}

	public void setValidationProbService(String validationProbService) {
		this.validationProbService = validationProbService;
	}

	private List<DMTData> readDMTIndex() {
		List<DMTData> dmtData = new<DMTData> ArrayList();

		SAXBuilder builder = new SAXBuilder();
		builder.setValidation(false);
		builder.setEntityResolver(new NoOpEntityResolver());
		File xmlFile = new File(deliveryFolder + "\\data\\index.xml");

		Document document;
		try {
			document = builder.build(xmlFile);
			Element rootNode = document.getRootElement();
			List<Element> list = rootNode.getChildren("entry");

			String key = "";
			String appName = "";
			String lastModified = "";

			int items = 0;
			for (Element node : list) {
				items++;
				String attributeValue = node.getAttributeValue("key");
				String[] attribParts = attributeValue.split("_");

				if (!attribParts[0].equals(key) || items == list.size()) {
					if (!key.isEmpty()) {
						DMTData data = new DMTData();
						data.setApplName(appName);
						data.setUuid(key);

						if (lastModified.equals("")) {
							lastModified = String.valueOf(System.currentTimeMillis() / 1000);
						}
						Date dt = new Date(Long.valueOf(lastModified));

						data.setLastModified(dt);

						VersionData version = null;
						int retryCount = 5;
						while (null == version && retryCount > 0) {
							version = getDeliveryStatus(key);
							if (null == version) {
								logger.info(String.format(
										"Error reading version information for application: %s will retry %d more times",
										appName, retryCount));
								retryCount--;
								sleep(10000); // sleep for 10 seconds
								continue;
							}
						}
						if (null == version && retryCount == 0) {
							logger.info(String.format(
									"Error reading version information for application: %s aborting...", appName));
							return null;
						}
						data.setVersionInfo(version);
						dmtData.add(data);
					}

					key = attribParts[0];
					appName = "";
					lastModified = "";
				}

				String nodeValue = node.getValue();
				if (attribParts.length > 2) {
					if (attribParts[2].equalsIgnoreCase("lastModified")) {
						lastModified = nodeValue;
					}
				}

				if (attribParts.length > 1) {
					if (attribParts[1].equalsIgnoreCase("name")) {
						appName = nodeValue;
					}
				}
			}
		} catch (JDOMException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return dmtData;

	}

	private VersionData getDeliveryStatus(String guid) {
		VersionData rslt = null;
		List<VersionData> dmtData = new<VersionData> ArrayList();
		SAXBuilder builder = new SAXBuilder();
		builder.setValidation(false);
		builder.setEntityResolver(new NoOpEntityResolver());
		File xmlFile = new File(deliveryFolder + "\\data\\{" + guid + "}" + "\\index.xml");

		if (!xmlFile.exists()) {
			logger.warn(new StringBuffer().append("Delivery File: ").append(xmlFile).append(" does not exist"));
			return null;
		}

		Document document;
		try {
			document = builder.build(xmlFile);
			Element rootNode = document.getRootElement();
			List<Element> list = rootNode.getChildren("entry");

			String key = "";
			String lastModified = "";
			String versionName = "";
			String status = "";
			String releaseDate = "";
			boolean type_version_flag = false; // Check for every version entry
			// data.

			for (Element node : list) {
				String attributeValue = node.getAttributeValue("key");
				String[] attribParts = attributeValue.split("_");

				if (!attribParts[0].equals(key) || type_version_flag) {
					if (!key.isEmpty() && !lastModified.isEmpty()) {
						if (!"delivery.StatusReadyForAnalysisAndDeployed".equals(status)
								&& !"delivery.StatusPurged".equals(status) && !"delivery.StatusOpened".equals(status)) {

							VersionData data = new VersionData();
							data.setVersionName(versionName);
							data.setUuid(key);
							data.setStatus(status);
							data.setReleaseDate(releaseDate);

							Date dt = new Date(Long.valueOf(lastModified));
							data.setLastModified(dt);

							dmtData.add(data);
						}
					}

					key = attribParts[0];
					versionName = "";
					lastModified = "";
					type_version_flag = false;
				}

				String nodeValue = node.getValue();
				if (attribParts.length > 2) {
					if (attribParts[2].equalsIgnoreCase("lastModified")) {
						lastModified = nodeValue;
					}
				}

				if (attribParts.length > 1) {
					if (attribParts[1].equalsIgnoreCase("name")) {
						versionName = nodeValue;
					}
					if (attribParts[1].equalsIgnoreCase("serverStatus")) {
						status = node.getValue();
					}
					if (attribParts[1].equalsIgnoreCase("date")) {
						releaseDate = node.getValue();
					}
					if (attribParts[1].equalsIgnoreCase("type")
							&& node.getValue().equalsIgnoreCase("delivery.Version")) {
						type_version_flag = true;
					}
				}
			}

			if (dmtData.size() > 0) {
				Collections.sort(dmtData, new Comparator<VersionData>() {
					@Override
					public int compare(VersionData o1, VersionData o2) {
						return o1.getLastModified().compareTo(o2.getLastModified());
					}
				});
				rslt = dmtData.get(dmtData.size() - 1);
			} else {
				rslt = new VersionData();
			}

		} catch (IOException | JDOMException e) {
			logger.error(e.getMessage());
		}
		return rslt;
	}
}
