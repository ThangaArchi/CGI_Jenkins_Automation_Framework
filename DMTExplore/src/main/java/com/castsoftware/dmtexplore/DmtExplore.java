package com.castsoftware.dmtexplore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.filter.ElementFilter;
import org.jdom.input.SAXBuilder;

import com.castsoftware.dmtexplore.data.DeliveryData;
import com.castsoftware.dmtexplore.data.PackageData;
import com.castsoftware.dmtexplore.data.ScanReportData;
import com.castsoftware.dmtexplore.util.NoOpEntityResolver;
import com.castsoftware.dmtexplore.util.OptionsValidation;

public class DmtExplore
{
	static Logger log = Logger.getLogger(DmtExplore.class.getName());

	private OutputStreamWriter output;
	private OptionsValidation options = null;
	private List<XmlData> dmtData;

	public static void main(String[] args)
	{
		// new DmtExplore(args);

		DmtExplore dmtExplore = new DmtExplore(args);
		List<XmlData> dmtData = dmtExplore.getDmtData();
		String latestVersion = "";
		for (XmlData appData : dmtData) { // get the applications
			List<DeliveryData> deliveryData = appData.getDeliveries();
			Collections.sort(deliveryData);
			for (int idx = deliveryData.size() - 1; idx >= 0; idx--) {
				DeliveryData delivery = deliveryData.get(idx);
				System.out.printf("delivery %s, %s\n", delivery.getDeliveryName(),delivery.getDeliveryDate());
			}
		}

	}

	public DmtExplore(String dmtBase, String appName)
	{
		dmtData = parseDMT(dmtBase, appName);
	}

	// public DmtExplore(String base, String destFileName)
	public DmtExplore(String args[])
	{
		System.out.print("DMT Explore - Copyright (c) CAST, All Rights Reserved\n");
		try {
			options = new OptionsValidation(args);

			String dmtBase = options.getDMTRoot();
			String destFileName = options.getOutputFile();
			String appFilter = options.getApplicationName();
			dmtData = parseDMT(dmtBase, appFilter);

			output = new OutputStreamWriter(new FileOutputStream(destFileName));
			boolean doCompare = options.isCurrentToBase() || options.isCurrentToPrevious();
			if (doCompare) // compare results
			{
				String compareType = "L";
				if (options.isCurrentToBase()) {
					compareType = "B";
				}

				output.append(XmlData.getCompareHeader()).append("\n");
				for (XmlData rslt : dmtData) { // get the applications
					output.append(rslt.compare(compareType));
				}
			} else {
				output.append(XmlData.getHeader()).append("\n");
				for (XmlData rslt : dmtData) {
					output.append(rslt.toString());
				}
			}
			output.close();
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}
		log.info("Program Terminate");
	}

	public List<XmlData> parseDMT(String dmtBase, String appFilter)
	{
		SAXBuilder builder = new SAXBuilder();
		builder.setValidation(false);
		builder.setEntityResolver(new NoOpEntityResolver());
		File xmlFile = new File(dmtBase + "\\data\\index.xml");
		log.debug("Application Index File:  " + xmlFile);

		List<XmlData> data = new ArrayList<XmlData>();
		Document document;
		try {
			document = builder.build(xmlFile);
			Element rootNode = document.getRootElement();
			List<Element> list = rootNode.getChildren("entry");

			String appName = "";
			String appGuid = "";

			for (Element node : list) {
				String attributeValue = node.getAttributeValue("key");
				String[] attribParts = attributeValue.split("_");

				if (attribParts.length > 1) {
					String nodeValue = node.getValue();
					if (attribParts[1].equalsIgnoreCase("name")) {
						appName = nodeValue; 
						appGuid = "";
					} else if (attribParts[1].equalsIgnoreCase("uuid")) {
						appGuid = nodeValue;
					}
				}
				if (appName.length() > 0 && appGuid.length() > 0) {
					if ((!appFilter.isEmpty() && appFilter.equalsIgnoreCase(appName)) || appFilter.isEmpty()) {
						log.debug(new StringBuffer().append("Application: ").append(appName)
								.append(" found using GUID:").append(appGuid));
						XmlData xmlData = new XmlData(appName, appGuid);
						if (parseDelivery(xmlFile, xmlData)) {
							data.add(xmlData);
						}
					}
					appName = "";
					appGuid = "";
				}
			}
		} catch (JDOMException | IOException e) {
			log.error(e.getMessage(), e);
		}
		return data;
	}

	private boolean parseDelivery(File base, XmlData item)
	{
		SAXBuilder builder = new SAXBuilder();
		builder.setValidation(false);
		builder.setEntityResolver(new NoOpEntityResolver());

		String appGuid = item.getAppGuid();
		File xmlFile = new File(base.getParentFile() + "\\{" + appGuid + "}" + "\\index.xml");
		log.debug(new StringBuffer().append("Delivery index: ").append(xmlFile));
		if (!xmlFile.exists()) {
			log.warn(new StringBuffer().append("Delivery File: ").append(xmlFile).append(" does not exist"));
			return false;
		}

		Document document;
		try {
			document = builder.build(xmlFile);
			Element rootNode = document.getRootElement();
			List<Element> list = rootNode.getChildren("entry");

			String deliveryGuid = "";
			Date deliveryDate = null;
			String deliveryName = "";

			for (Element node : list) {
				String attributeValue = node.getAttributeValue("key");
				String[] attribParts = attributeValue.split("_");

				if (attribParts.length > 1) {
					String nodeValue = node.getValue();
					if (attribParts[1].equalsIgnoreCase("name")) {
						deliveryName = nodeValue;
						deliveryGuid = attribParts[0];
						File packageFolder = new File(base.getParentFile() + "\\{" + appGuid + "}\\{" + deliveryGuid
								+ "}");
						if (!packageFolder.exists()) {
							deliveryGuid = "badGuid";
						}
					} else if (attribParts[1].equalsIgnoreCase("date")) {
						deliveryDate = stringToDate(nodeValue, "yyyy-MM-dd hh:mm:ss");
					}

					if (deliveryGuid.length() > 0 && deliveryName.length() > 0 && deliveryDate != null) {
						DeliveryData delivery = new DeliveryData(deliveryName, deliveryGuid, deliveryDate);
						if (!deliveryGuid.equals("badGuid")) {
							parseDeliveryEntity(xmlFile, delivery);
							if (parsePackages(xmlFile, delivery)) {
								item.addDelivery(delivery);
							}
						}

						// reset for next delivery record
						deliveryGuid = "";
						deliveryDate = null;
						deliveryName = "";

					}

				}
			}
		} catch (JDOMException | IOException e) {
			log.warn(e.getMessage(), e);
		}
		return true;
	}

	private boolean parseDeliveryEntity(File base, DeliveryData item)
	{
		SAXBuilder builder = new SAXBuilder();
		builder.setValidation(false);
		builder.setEntityResolver(new NoOpEntityResolver());
		File xmlFile = new File(String.format("%s\\%s.entity.xml", base.getParentFile(), item.getDeliveryGuid()));
		if (!xmlFile.exists()) {
			log.warn(new StringBuffer().append("Package File: ").append(xmlFile).append(" does not exist"));
			return false;
		}

		Document document;
		try {
			document = builder.build(xmlFile);
			Element rootNode = document.getRootElement();
			Element el = null;
			if ((el = findElement(rootNode, "delivery.Version")) != null) {
				item.setDeliveryStatus(el.getAttributeValue("serverStatus"));
			} else {
				return false;
			}
		} catch (JDOMException | IOException e) {
			log.warn(e.getMessage(), e);
			return false;
		}
		return true;
	}

	private boolean parsePackages(File base, DeliveryData item)
	{
		SAXBuilder builder = new SAXBuilder();
		builder.setValidation(false);
		builder.setEntityResolver(new NoOpEntityResolver());
		File xmlFile = new File(base.getParentFile() + "\\{" + item.getDeliveryGuid() + "}" + "\\index.xml");
		if (!xmlFile.exists()) {
			log.warn(new StringBuffer().append("Package File: ").append(xmlFile).append(" does not exist"));
			return false;
		}

		try {
			Document document = builder.build(xmlFile);
			Element rootNode = document.getRootElement();
			List<Element> list = rootNode.getChildren("entry");

			for (Element node : list) {
				String attributeValue = node.getAttributeValue("key");
				String[] attribParts = attributeValue.split("[_.]");

				if (attribParts.length > 1) {
					if (attribParts[1].equalsIgnoreCase("name")) {
						PackageData pkg = new PackageData(node.getValue(), attribParts[0]);
						if (parseEntity(xmlFile, pkg)) {
							item.addPackage(pkg);
							parseValidationDifferenceReport(xmlFile, pkg);
							parseSelectionReport(xmlFile, pkg);
							pkg.setScanReportList(parseScanReport(xmlFile, pkg));
						}
					}
				}
			}
		} catch (IOException | JDOMException e) {
			log.warn(e.getMessage(), e);
		}
		return true;
	}

	private List<ScanReportData> parseScanReport(File base, PackageData item)
	{
		List<ScanReportData> rslt = new ArrayList();
		File xmlFile = new File(base.getParentFile() + "\\{" + item.getPackageGuid() + "}\\scan_report.pmx");
		if (xmlFile.exists()) {
			SAXBuilder builder = new SAXBuilder();
			builder.setEntityResolver(new NoOpEntityResolver());
			try {
				Document document = builder.build(xmlFile);
				Element rootNode = document.getRootElement();
				document = builder.build(xmlFile);
				ElementFilter filter = new ElementFilter("ExtractionReport");
				Iterator<?> itr = rootNode.getDescendants(filter);
				while (itr.hasNext()) {
					Element xml = (Element) itr.next();
					List<Element> items = xml.getChildren();
					for (Element child : items) {
						List<Element> reportItems = child.getChildren();
						for (Element reportItem : reportItems) {
							if (!reportItem.getAttributeValue("extension", "").isEmpty()) {
								rslt.add(new ScanReportData(reportItem.getAttributeValue("extension", ""), reportItem
										.getAttributeValue("rootPath", ""), Integer.parseInt(reportItem
										.getAttributeValue("added", "0")), Integer.parseInt(reportItem
										.getAttributeValue("removed", "0")), Integer.parseInt(reportItem
										.getAttributeValue("total", "0"))));

								item.setAdded(item.getAdded()
										+ Integer.parseInt(reportItem.getAttributeValue("added", "0")));
								item.setRemoved(item.getRemoved()
										+ Integer.parseInt(reportItem.getAttributeValue("removed", "0")));
								item.setTotal(item.getTotal()
										+ Integer.parseInt(reportItem.getAttributeValue("total", "0")));
							}
						}
					}
				}
			} catch (JDOMException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			log.warn(new StringBuffer().append("file not found: ").append(xmlFile));
		}

		return rslt;
	}

	private boolean parseEntity(File base, PackageData item)
	{
		SAXBuilder builder = new SAXBuilder();
		builder.setValidation(false);
		builder.setEntityResolver(new NoOpEntityResolver());
		File xmlFile = new File(base.getParentFile() + "\\" + item.getPackageGuid() + ".entity.xml");
		if (!xmlFile.exists()) {
			log.warn(new StringBuffer().append("Entity File: ").append(xmlFile).append(" does not exist"));
			return false;
		}

		try {
			Document document = builder.build(xmlFile);
			Element rootNode = document.getRootElement();
			processAssemblies(rootNode, item);
			processMSTSQL(rootNode, item);
			processTFS(rootNode, item);
			processMainframe(rootNode, item);
			processFileSystem(rootNode, item);
			processDB2(rootNode, item);
			processUDBExtractor(rootNode, item);
			processOracleExtractor(rootNode, item);
			processMaven(rootNode, item);
			processSVN(rootNode, item);
			processSybase(rootNode, item);

			if (item.getPackageType().isEmpty()) {
				item.setPackageType("Unknown");
			}
		} catch (IOException | JDOMException e) {
			log.warn(e.getMessage(), e);
		}

		return true;
	}

	private boolean parseValidationDifferenceReport(File base, PackageData item) throws JDOMException, IOException
	{
		SAXBuilder builder = new SAXBuilder();
		builder.setValidation(false);
		builder.setEntityResolver(new NoOpEntityResolver());

		File xmlFile = new File(base.getParentFile() + "\\{" + item.getPackageGuid() + "}" + "\\validation_diff_report.pmx");
		if (!xmlFile.exists()) {
			log.warn(new StringBuffer().append("validation_diff_report File: ").append(xmlFile).append(" does not exist"));
			return false;
		}
		
		Document document = builder.build(xmlFile);
		Element rootNode = document.getRootElement();

		int totalAlerts = 0;
		
		ElementFilter filter = new ElementFilter("items");
		Iterator<?> itr = rootNode.getDescendants(filter);
		while (itr.hasNext()) {
			Element node = (Element) itr.next();
			List <Element>itemList  = node.getChildren();
			for (Element childItem: itemList)
			{
				if (childItem.getName().startsWith("delivery."))
				{
					String alerts = childItem.getAttribute("number").getValue();
					if (alerts != null && !alerts.isEmpty())
					{
						totalAlerts +=  Integer.parseInt(alerts);
					}
				}
			}
		}
		item.setAlerts(totalAlerts);
		
		return true;
	}
	
	private boolean parseSelectionReport(File base, PackageData item) throws JDOMException, IOException
	{
		SAXBuilder builder = new SAXBuilder();
		builder.setValidation(false);
		builder.setEntityResolver(new NoOpEntityResolver());

		File xmlFile = new File(base.getParentFile() + "\\{" + item.getPackageGuid() + "}" + "\\selection_report.pmx");
		if (!xmlFile.exists()) {
			log.warn(new StringBuffer().append("Entity File: ").append(xmlFile).append(" does not exist"));
			return false;
		}

		if (xmlFile.exists()) {
			Document document = builder.build(xmlFile);
			Element rootNode = document.getRootElement();

			ElementFilter filter = new ElementFilter("ProjectsSelectionReportItem");
			Iterator<?> itr = rootNode.getDescendants(filter);
			while (itr.hasNext()) {
				Element node = (Element) itr.next();
				item.setDiscoveryName(node.getAttributeValue("discovererName"));
			}
		}
		return true;
	}

	private void processAssemblies(Element rootNode, PackageData item) throws JDOMException, IOException
	{
		ElementFilter filter = new ElementFilter("assemblies");
		Iterator<?> itr = rootNode.getDescendants(filter);
		if (itr.hasNext()) {
			item.setPackageType("Assemblies");

			filter = new ElementFilter("extractor");
			itr = rootNode.getDescendants(filter);
			if (itr.hasNext()) {
				List list = ((Element) itr.next()).getChildren();
				for (int i = 0; i < list.size(); i++) {
					Element node = (Element) list.get(i);
					if (node.getName().equalsIgnoreCase("dmtdevmicrosofttechno.NetResourceFilesPackage")
							|| node.getName()
									.equalsIgnoreCase("dmtdevnetresourcesextractor.NetResourceFolderExtractor")) {
						item.setPackageLocation(node.getAttributeValue("folderPath"));
					}
				}
			}
		}
	}

	private void processMainframe(Element rootNode, PackageData item)
	{
		ElementFilter filter = new ElementFilter("dmtdevmainframetechno.MainframePackage");
		Iterator<?> itr = rootNode.getDescendants(filter);
		if (itr.hasNext()) {
			item.setPackageType("Mainframe");
			filter = new ElementFilter("dmtdevmainframeextractor.MainframeLibraryDefinition");
			itr = rootNode.getDescendants(filter);
			String fileLoc = "";
			while (itr.hasNext()) {
				Element node = (Element) itr.next();
				if (fileLoc.length() > 0) fileLoc += ";";
				fileLoc += node.getAttributeValue("libraryFilePath", "");
			}
			item.setPackageLocation(fileLoc);
		}
	}

	private void processMSTSQL(Element rootNode, PackageData item)
	{
		ElementFilter filter = new ElementFilter("dmtdevsqlcastextractormstsql.SQLCASTExtractorMSTSQL");
		Iterator<?> itr = rootNode.getDescendants(filter);
		while (itr.hasNext()) {
			item.setPackageType("MS SQL Server");
			Element element = (Element) itr.next();
			item.setHost(element.getAttributeValue("host"));

			List list = element.getChildren();
			for (int i = 0; i < list.size(); i++) {
				Element node = (Element) list.get(i);
				if (node.getName().equalsIgnoreCase("identification")) {
					List subList = node.getChildren();
					for (int ii = 0; ii < subList.size(); ii++) {
						Element subNode = (Element) subList.get(ii);
						if (subNode.getName().equalsIgnoreCase("dmtdevdbtechno.SQLServerInstanceName")) {
							item.setInstanceName(subNode.getAttributeValue("instanceName", ""));
						}
						if (subNode.getName().equalsIgnoreCase("dmtdevdbtechno.SQLServerPort")) {
							item.setPort(subNode.getAttributeValue("port", ""));
						}
					}
				} else if (node.getName().equalsIgnoreCase("credentials")) {
					List subList = node.getChildren();
					for (int ii = 0; ii < subList.size(); ii++) {
						Element subNode = (Element) subList.get(ii);
						if (subNode.getName().equalsIgnoreCase("delivery.Credentials")) {
							item.setLoginName(subNode.getAttributeValue("loginName", ""));
							item.setRememberPassword(!subNode.getAttributeValue("rememberPassword", "").isEmpty());
							item.setStoredPassword(!subNode.getAttributeValue("rememberPassword", "").isEmpty());
						}
					}
				} else if (node.getName().equalsIgnoreCase("rootPaths")) {
					String fileLoc = "";
					List subList = node.getChildren();
					for (int ii = 0; ii < subList.size(); ii++) {
						Element subNode = (Element) subList.get(ii);
						if (subNode.getName().equalsIgnoreCase("delivery.DatabaseNameWrapper")) {
							if (fileLoc.length() > 0) fileLoc += "; ";
							fileLoc += subNode.getAttributeValue("value", "");
						}
					}
					item.setSchema(fileLoc);

				}
			}
		}
	}

	private void checkIdentification(PackageData item, Element outerNode)
	{
		if (outerNode.getName().equals("identification")) {
			List<Element> children = outerNode.getChildren();
			for (Element node : children) {
				item.setInstanceName(node.getAttributeValue("sid", ""));
				if (item.getInstanceName().isEmpty()) {
					item.setInstanceName(node.getAttributeValue("service", ""));
				}
			}
		}
	}

	private void checkCredentials(PackageData item, Element outerNode)
	{
		if (outerNode.getName().equals("credentials")) {
			List<Element> children = outerNode.getChildren();
			for (Element node : children) {
				item.setLoginName(node.getAttributeValue("loginName"));
				item.setStoredPassword(!node.getAttributeValue("storedPassword", "").isEmpty());
				item.setStoredPassword(!node.getAttributeValue("rememberPassword", "").isEmpty());
			}
		}
	}

	private void checkSchemas(PackageData item, Element outerNode)
	{
		StringBuffer schemas = new StringBuffer();
		if (outerNode.getName().equals("rootPaths")) {
			schemas.setLength(0);
			List<Element> children = outerNode.getChildren();
			for (Element node : children) {
				if (schemas.toString().length() > 0) {
					schemas.append(";");
				}
				schemas.append(node.getAttributeValue("value", ""));
			}
			item.setSchema(schemas.toString());
		}
	}

	private void processOracleExtractor(Element rootNode, PackageData item)
	{
		ElementFilter filter = new ElementFilter("dmtdevsqlcastextractorextraction.SQLCASTExtractor");
		Iterator<Element> itr = rootNode.getDescendants(filter);
		if (!itr.hasNext()) {
			filter = new ElementFilter("dmtdevsqlcastextractorextraction.SQLCASTExtraction");
			itr = rootNode.getDescendants(filter);
		}
		if (!itr.hasNext()) {
			filter = new ElementFilter("dmtdevsqlcastextractororacle.SQLCASTExtractor");
			itr = rootNode.getDescendants(filter);
		}

		StringBuffer schemas = new StringBuffer();

		while (itr.hasNext()) {
			Element element = (Element) itr.next();
			if (element.getName().startsWith("dmtdevsqlcastextractor")) {
				if (element.getName().endsWith("SQLCASTExtractor")) {
					item.setPackageType("Oracle - online");
					item.setHost(element.getAttributeValue("host"));
					item.setPort(element.getAttributeValue("port"));
				} else if (element.getName().endsWith("SQLCASTExtraction")) {
					// the delivery is using an offline extraction curently
					// required for oracle 12c
					item.setPackageType("Oracle - offline");
					item.setPackageLocation(element.getAttributeValue("serverVersionFile"));
				}
				if (!item.getPackageType().isEmpty()) {
					List<Element> outerList = element.getChildren();
					for (Element outerNode : outerList) {
						checkCredentials(item, outerNode);
						checkSchemas(item, outerNode);
						checkIdentification(item, outerNode);
					}
				}
			}
		}
	}

	private void processUDBExtractor(Element rootNode, PackageData item)
	{
		ElementFilter filter = new ElementFilter("dmtdevdb2udbextractor.DB2UDBExtractor");
		Iterator<Element> itr = rootNode.getDescendants(filter);
		while (itr.hasNext()) {
			Element element = (Element) itr.next();
			if (element.getName().endsWith("dmtdevdb2udbextractor.DB2UDBExtractor")) {
				item.setPackageType("UDB - online");
				item.setHost(element.getAttributeValue("host"));
				item.setPort(element.getAttributeValue("port"));

				List<Element> outerList = element.getChildren();
				for (Element outerNode : outerList) {
					checkCredentials(item, outerNode);
					checkSchemas(item, outerNode);
				}
			}
		}
	}

	private void processSybase(Element rootNode, PackageData item)
	{
		ElementFilter filter = new ElementFilter("dmtdevsqlcastextractorasetsql.SQLCASTExtractorASETSQL");
		Iterator<?> itr = rootNode.getDescendants(filter);
		while (itr.hasNext()) {
			item.setPackageType("Sybase");
			Element element = (Element) itr.next();
			item.setHost(element.getAttributeValue("host"));

			List list = element.getChildren();
			for (int lst = 0; lst < list.size(); lst++) {
				Element node = (Element) list.get(lst);
				checkCredentials(item, node);
				checkSchemas(item, node);
			}
		}
	}

	private void processTFS(Element rootNode, PackageData item)
	{
		ElementFilter filter = new ElementFilter("dmtdevtfsextractor.TFSExtractor");
		Iterator itr = rootNode.getDescendants(filter);
		while (itr.hasNext()) {
			item.setPackageType("TFS");
			Element element = (Element) itr.next();
			item.setHost(element.getAttributeValue("connectionURL"));
			item.setRepository(element.getAttributeValue("repository"));
			item.setRevision(element.getAttributeValue("revision", ""));
			List<Element> list = element.getChildren();
			for (int lst = 0; lst < list.size(); lst++) {
				Element node = (Element) list.get(lst);
				if (node.getName().equalsIgnoreCase("credentials")) {
					List subList = node.getChildren();
					for (int ii = 0; ii < subList.size(); ii++) {
						Element subNode = (Element) subList.get(ii);
						if (subNode.getName().equalsIgnoreCase("delivery.Credentials")) {
							item.setLoginName(subNode.getAttributeValue("loginName"));
							item.setRememberPassword(!subNode.getAttributeValue("rememberPassword", "").isEmpty());
							item.setStoredPassword(!subNode.getAttributeValue("rememberPassword", "").isEmpty());
						}
					}
				}
			}
		}
	}

	private void processSVN(Element rootNode, PackageData item)
	{
		ElementFilter filter = new ElementFilter("dmtdevsvnextractor.SVNExtractor");
		Iterator<?> itr = rootNode.getDescendants(filter);
		while (itr.hasNext()) {
			item.setPackageType("SVN");
			Element element = (Element) itr.next();
			item.setHost(element.getAttributeValue("connectionURL"));
			item.setRevision(element.getAttributeValue("revision", ""));
			List list = element.getChildren();
			for (int lst = 0; lst < list.size(); lst++) {
				Element node = (Element) list.get(lst);
				if (node.getName().equalsIgnoreCase("credentials")) {
					List subList = node.getChildren();
					for (int ii = 0; ii < subList.size(); ii++) {
						Element subNode = (Element) subList.get(ii);
						if (subNode.getName().equalsIgnoreCase("delivery.Credentials")) {
							item.setLoginName(subNode.getAttributeValue("loginName"));
							item.setRememberPassword(!subNode.getAttributeValue("rememberPassword", "").isEmpty());
							item.setStoredPassword(!subNode.getAttributeValue("rememberPassword", "").isEmpty());
						}
					}
				}
			}
		}
	}

	private void processFileSystem(Element rootNode, PackageData item)
	{
		ElementFilter filter = new ElementFilter("dmtdevfolderextractor.SourceFolderExtractor");
		Iterator<?> itr = rootNode.getDescendants(filter);
		if (itr.hasNext()) {
			item.setPackageType("File System");
			Element element = (Element) itr.next();
			item.setPackageLocation(element.getAttributeValue("folderPath"));
		}
	}

	private void processDB2(Element rootNode, PackageData item)
	{
		ElementFilter filter = new ElementFilter("dmtdevdb2zosextractor.DB2zOSExtractor");
		Iterator<?> itr = rootNode.getDescendants(filter);
		if (itr.hasNext()) {
			item.setPackageType("DB2");
			Element element = (Element) itr.next();
			item.setHost(element.getAttributeValue("manifest"));

			filter = new ElementFilter("delivery.SchemaNameWrapper");
			itr = rootNode.getDescendants(filter);
			String fileLoc = "";
			while (itr.hasNext()) {
				Element node = (Element) itr.next();
				if (fileLoc.length() > 0) fileLoc += "; ";
				fileLoc += node.getAttributeValue("value", "");
			}
			item.setSchema(fileLoc);
		}
	}

	private void processMaven(Element rootNode, PackageData item)
	{
		ElementFilter filter = new ElementFilter("dmtdevjeemavenresourcesextractor.MavenFileSystemExtractor");
		Iterator<?> itr = rootNode.getDescendants(filter);
		if (itr.hasNext()) {
			Element element = (Element) itr.next();
			item.setPackageType("File System");
			item.setHost(element.getAttributeValue("folderPath"));
		}
	}

	private Date stringToDate(String inDate, String inFormat)
	{
		DateFormat df = new SimpleDateFormat(inFormat);
		Date rslt = null;
		try {
			rslt = df.parse(inDate);
		} catch (java.text.ParseException e) {
			log.warn("Can't convert date", e);
		}
		return rslt;
	}

	public List<XmlData> getDmtData()
	{
		return dmtData;
	}

	public Element findElement(Element current, String elementName)
	{
		if (current.getName() == elementName) {
			return current;
		}
		List children = current.getChildren();
		Iterator iterator = children.iterator();
		while (iterator.hasNext()) {
			Element child = (Element) iterator.next();
			Element tempElement = findElement(child, elementName);
			if (tempElement != null) {
				return tempElement;
			}
		}
		// Didn't find the element anywhere.
		return null;
	}
}
