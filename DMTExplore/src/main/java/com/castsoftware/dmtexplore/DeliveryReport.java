package com.castsoftware.dmtexplore;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.filter.ElementFilter;
import org.jdom.input.SAXBuilder;

import com.castsoftware.dmtexplore.data.DeliveryData;
import com.castsoftware.dmtexplore.data.PackageData;
import com.castsoftware.dmtexplore.util.NoOpEntityResolver;

public class DeliveryReport
{
	static Logger log = Logger.getLogger(DeliveryReport.class.getName());
	public String deliveryFolder = "";
	private final String cmdLine = "java -jar CASTDeliveryReporter.jar -delivery \"#DMT_LOC#\" -application \"#APP_NAME#\" -version \"#VERSION#\" -previousversion \"#PREV_VERSION#\" -detailed \"#DETAILED_REPORT#\"";

	public DeliveryReport(String deliveryFolder)
	{
		this.deliveryFolder = deliveryFolder;
	}

	public boolean runReport(String application, String version, String prevVersion,
			boolean detailed)
	{
		boolean rslt = true;
		String cmd = this.cmdLine;

		String df = deliveryFolder.replaceAll("\\\\","\\\\\\\\");
		
		cmd = cmd.replaceAll("#DMT_LOC#", df);
		cmd = cmd.replaceAll("#APP_NAME#", application);
		cmd = cmd.replaceAll("#VERSION#", version);
		cmd = cmd.replaceAll("#PREV_VERSION#", prevVersion);
		cmd = cmd.replaceAll("#DETAILED_REPORT#", Boolean.toString(detailed));

		try {
			Process proc = Runtime.getRuntime().exec(cmd);
			proc.waitFor();
			rslt = (proc.exitValue() == 0) ? true : false;
		} catch (IOException | InterruptedException e) {
			rslt = false;
		}

		return rslt;
	}

	public XmlData findApplication(String application, List<XmlData> data)
	{
		XmlData xmlData = null;
		for (XmlData item : data) {
			if (item.getAppName().equals(application)) {
				xmlData = item;
				break;
			}
		}
		return xmlData;
	}

	public void parseReport(List<XmlData> data)
	{
		for (XmlData item : data) {
			if (item.getDeliveries().size() > 1) {
				parseReport(item);
			}
		}
	}

	public void parseReport(XmlData app)
	{
		SAXBuilder builder = new SAXBuilder();
		builder.setValidation(false);
		builder.setEntityResolver(new NoOpEntityResolver());
		int added = 0;
		int removed = 0;
		int changed = 0;
		int unchanged = 0;

		List<DeliveryData> deliveries = app.getDeliveries();
		DeliveryData lastDelivery = deliveries.get(deliveries.size() - 1);
		DeliveryData prevDelivery = deliveries.get(deliveries.size() - 2);

		runReport(app.getAppName(), lastDelivery.getDeliveryName(), prevDelivery.getDeliveryName(),
				false);

		for (PackageData pkg : lastDelivery.getPackages()) {
			File xmlFile = new File(new StringBuffer().append(deliveryFolder).append("\\data\\{")
					.append(app.getAppGuid()).append("}\\{").append(lastDelivery.getDeliveryName())
					.append("}\\{").append(pkg.getPackageGuid())
					.append("}\\extended_scan_report.pmx").toString());
			if (xmlFile.exists()) {
				Document document;
				try {
					document = builder.build(xmlFile);
					Element rootNode = document.getRootElement();

					ElementFilter filter = new ElementFilter("items");
					Iterator<?> itr = rootNode.getDescendants(filter);
					while (itr.hasNext()) {
						Element element = (Element) itr.next();
						List list = element.getChildren();
						for (int i = 0; i < list.size(); i++) {
							Element node = (Element) list.get(i);

							added += Integer.valueOf(node.getAttributeValue("added", "0"));
							removed += Integer.valueOf(node.getAttributeValue("removed", "0"));
							changed += Integer.valueOf(node.getAttributeValue("changed", "0"));
							unchanged += Integer.valueOf(node.getAttributeValue("unchanged", "0"));

						}
					}
					pkg.setAdded(added);
//					pkg.setChanged(unchanged);
//					pkg.setUnchanged(unchanged);
					pkg.setRemoved(removed);
				} catch (JDOMException | IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				// item.setAdded(added);
				// item.setChanged(changed);
				// item.setRemoved(removed);
				// item.setUnchanged(unchanged);
				// item.setTotal(item.getAdded()+item.getRemoved()+item.getChanged()+item.getUnchanged());

			}
		}
	}
}
