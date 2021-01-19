package com.castsoftware.batch.data;

public class DeliveryLogInfo
{
	private String packageName;
	private String logLocation;
	
	public DeliveryLogInfo(String packageName, String logLocation)
	{
		super();
		this.packageName = packageName;
		this.logLocation = logLocation;
	}

	public String getPackageName()
	{
		return packageName;
	}

	public String getLogLocation()
	{
		return logLocation;
	}
	
}
