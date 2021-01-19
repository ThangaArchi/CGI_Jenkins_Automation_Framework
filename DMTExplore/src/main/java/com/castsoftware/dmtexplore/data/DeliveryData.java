package com.castsoftware.dmtexplore.data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DeliveryData implements Comparable
{
	private String deliveryGuid="";
	private Date deliveryDate=null;
	private String deliveryName="";
	private String deliveryStatus="";
	private List<PackageData> packages=null;

	public DeliveryData (String deliveryName, String deliveryGuid, Date deliveryDate)
	{
		this.deliveryDate = deliveryDate;
		this.deliveryGuid= deliveryGuid;
		this.deliveryName = deliveryName;
	}
	
	public String getDeliveryGuid()
	{
		return deliveryGuid;
	}
	public void setDeliveryGuid(String deliveryGuid)
	{
		this.deliveryGuid = deliveryGuid;
	}
	public Date getDeliveryDate()
	{
		return deliveryDate;
	}
	public void setDeliveryDate(Date deliveryDate)
	{
		this.deliveryDate = deliveryDate;
	}
	public String getDeliveryName()
	{
		return deliveryName;
	}
	public void setDeliveryName(String deliveryName)
	{
		this.deliveryName = deliveryName;
	}
	public List<PackageData> getPackages()
	{
		return packages;
	}
	public void addPackage(PackageData pkg)
	{
		if (packages==null)
		{
			packages  = new ArrayList<PackageData>();
		}
		packages.add(pkg);
	}
	static public String getHeader() 
	{
		return "Delivery Name\tDelivery Guid\tDelivery Date\tDelivery Status";
	}
	static public String getCompareHeader() 
	{
		return "Delivery Name\tDelivery Guid\tDelivery Date\tDelivery Status";
	}

	public String getDeliveryStatus()
	{
		return deliveryStatus;
	}

	public void setDeliveryStatus(String deliveryStatus)
	{
		this.deliveryStatus = deliveryStatus;
	}

	public String toString()
	{
		
		return new StringBuffer()
				.append(deliveryName).append("\t")
		        .append(deliveryGuid).append("\t")
				.append(deliveryDate).append("\t")
				.append(deliveryStatus)
				.toString();

	}	
	public String getFromRecordString()
	{
		return toString();	
	}
	
	public String compare(DeliveryData compareRec)
	{
		return toString();	
	}

	@Override
	public int compareTo(Object o)
	{
		DeliveryData dd = (DeliveryData) o;
		int c = getDeliveryDate().compareTo(dd.getDeliveryDate());
		if (c == 0) c = getDeliveryName().compareTo(dd.deliveryName);
		return c;
	}	
}
