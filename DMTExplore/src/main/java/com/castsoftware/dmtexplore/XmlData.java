package com.castsoftware.dmtexplore;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.castsoftware.dmtexplore.data.DeliveryData;
import com.castsoftware.dmtexplore.data.PackageData;

public class XmlData
{
	private List<DeliveryData> deliveries=null;
	private String appName = "";
	private String appGuid="";
	
	boolean compareResults = false;
	
	public XmlData(String appName, String appGuid)
	{
		this.appName=appName;
		this.appGuid=appGuid;
	}
	
	public List<DeliveryData> getDeliveries()
	{
		return deliveries;
	}
	public void addDelivery(DeliveryData delivery)
	{
		if (deliveries==null)
		{
			deliveries  = new ArrayList<DeliveryData>();
		}
		deliveries.add(delivery);
	}

	public String getAppName()
	{
		return appName;
	}
	public void setAppName(String appName)
	{
		this.appName = appName;
	}
	
	public String getAppGuid()
	{
		return appGuid;
	}
	public void setAppGuid(String appGuid)
	{
		this.appGuid = appGuid;
	}

	static public String getHeader()
	{
		StringBuffer  rslt = new StringBuffer()
			.append("App Name\tAppGuid\t").append(DeliveryData.getHeader()).append("\t").append(PackageData.getHeader());
		return rslt.toString();
	}

	static public String getCompareHeader()
	{
		StringBuffer  rslt = new StringBuffer()
			.append("App Name\tAppGuid\t").append(DeliveryData.getCompareHeader()).append("\t").append(PackageData.getCompareHeader());
		return rslt.toString();
	}

	public String toString()
	{
		StringBuffer rslt = new StringBuffer();
		StringBuffer appRslt = new StringBuffer().append(appName).append("\t").append(appGuid).append("\t");
		
		List delList = getDeliveries();
		int lastDel = delList.size();
		if (lastDel > 0)
		{
			DeliveryData delRecord = (DeliveryData) delList.get(lastDel-1);
			StringBuffer delRslt = new StringBuffer().append(appRslt).append(delRecord).append("\t");
			for (PackageData pkgRec: delRecord.getPackages())
			{
				rslt.append(delRslt).append(pkgRec).append("\n");
			}
		}
		return rslt.toString();
	}
	public String compare(String compareType)
	{
		StringBuffer rslt = new StringBuffer();
		StringBuffer appRslt = new StringBuffer().append(appName).append("\t").append(appGuid).append("\t");
		
		int compareFrom = 0;
		int compareTo = 0;
		
		List<DeliveryData> delList = getDeliveries();
		int lastDel = delList.size();
		if (compareType=="L")
		{
			if (lastDel >= 2)
			{
				compareFrom = lastDel - 2;
				compareTo = lastDel - 1;
			}  
		} else {
			compareFrom = 0;
			compareTo = lastDel - 1;			
		}
		
		if (compareFrom >= 0 && compareTo >= 0)
		{
			DeliveryData fromDelRecord = (DeliveryData) delList.get(compareFrom);
			DeliveryData toDelRecord = (DeliveryData) delList.get(compareTo);
			
			StringBuffer fromDelRslt = new StringBuffer().append(appRslt).append(fromDelRecord.getFromRecordString()).append("\t");
			StringBuffer toDelRslt = new StringBuffer().append(appRslt).append(toDelRecord.compare(fromDelRecord)).append("\t");
			
			List <PackageData> fromList = fromDelRecord.getPackages();
			List <PackageData> toList = toDelRecord.getPackages();
			List <PackageData> controlList = (fromList.size() > toList.size())?fromList:toList;			

			for (PackageData pkgRec: controlList)
			{
				String pkgGuid = pkgRec.getPackageGuid();
				PackageData fromPkg = null;
				PackageData toPkg = null;
				String pkgId = pkgRec.getPackageGuid();
				for (PackageData fromRec: fromList)
				{
					if (fromRec.getPackageGuid().equals(pkgGuid))
					{
						fromPkg = fromRec;
						break;
					}
				}
				for (PackageData toRec: toList)
				{
					if (toRec.getPackageGuid().equals(pkgId))
					{
						toPkg = toRec;
						break;
					}
				}
				
				PackageData cntrl = pkgRec;
				if (fromPkg==null)
				{
					rslt.append(fromDelRslt).append(PackageData.blankRecord()).append("\n");
					rslt.append(toDelRslt).append(cntrl.getFromRecordString()).append("\n");
				} else if (toPkg==null)
				{
					rslt.append(fromDelRslt).append(cntrl.getFromRecordString()).append("\n");
					rslt.append(toDelRslt).append(PackageData.blankRecord()).append("\n");
				} else {
					rslt.append(fromDelRslt).append(cntrl.getFromRecordString()).append("\n");
					rslt.append(toDelRslt).append(fromPkg.compare(toPkg)).append("\n");
				}
				
			}
			
//			rslt.append(fromDelRslt).append("\n");
//			rslt.append(toDelRslt).append("\n");
			
		}
		return rslt.toString();
	}
	
}
