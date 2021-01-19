package com.castsoftware.dmtexplore.data;

import java.util.ArrayList;
import java.util.List;

public class PackageData
{
	private String packageGuid = "";
	private String packageName = "";
	private String discoveryName = "";
	private String packageType = "";
	private String packageLocation = "";

	private String host = "";
	private String port = "";
	private String instanceName = "";
	private String loginName = "";
	private boolean rememberPassword = false;
	private boolean storedPassword = false;
	private String schema = "";
	private String repository = "";
	private String revision = "";

	private int alerts=0;
	private int added=0;
	private int removed=0;
	private int total = 0;
	
	private List scanReportList = new ArrayList();
	
	public PackageData(String packageName, String packageGuid)
	{
		this.packageGuid = packageGuid;
		this.packageName = packageName;
	}

	public String getPackageGuid()
	{
		return packageGuid;
	}

	public void setPackageGuid(String packageGuid)
	{
		this.packageGuid = packageGuid;
	}

	public String getPackageName()
	{
		return packageName;
	}

	public void setPackageName(String packageName)
	{
		this.packageName = packageName;
	}

	public String getPackageType()
	{
		return packageType;
	}

	public void setPackageType(String packageType)
	{
		this.packageType = packageType;
	}

	public String getPackageLocation()
	{
		return packageLocation;
	}

	public void setPackageLocation(String packageLocation)
	{
		this.packageLocation = packageLocation;
	}

	public String getHost()
	{
		return host;
	}

	public void setHost(String host)
	{
		this.host = host;
	}

	public String getPort()
	{
		return port;
	}

	public void setPort(String port)
	{
		this.port = port;
	}

	public String getInstanceName()
	{
		return instanceName;
	}

	public void setInstanceName(String instanceName)
	{
		this.instanceName = instanceName;
	}

	public String getLoginName()
	{
		return loginName;
	}

	public void setLoginName(String loginName)
	{
		this.loginName = loginName;
	}

	public boolean isRememberPassword()
	{
		return rememberPassword;
	}

	public void setRememberPassword(boolean rememberPassword)
	{
		this.rememberPassword = rememberPassword;
	}

	public boolean isStoredPassword()
	{
		return storedPassword;
	}

	public void setStoredPassword(boolean storedPassword)
	{
		this.storedPassword = storedPassword;
	}

	public String getSchema()
	{
		return schema;
	}

	public void setSchema(String schema)
	{
		this.schema = schema;
	}

	public String getRepository()
	{
		return repository;
	}

	public void setRepository(String repository)
	{
		this.repository = repository;
	}

	public String getDiscoveryName()
	{
		return discoveryName;
	}

	public void setDiscoveryName(String discoveryName)
	{
		this.discoveryName = discoveryName;
	}

	public int getAdded()
	{
		return added;
	}

	public void setAdded(int added)
	{
		this.added = added;
	}

	public int getRemoved()
	{
		return removed;
	}

	public void setRemoved(int removed)
	{
		this.removed = removed;
	}

	public int getAlerts()
	{
		return alerts;
	}

	public void setAlerts(int alerts)
	{
		this.alerts = alerts;
	}

	public int getTotal()
	{
		return total;
	}

	public void setTotal(int total)
	{
		this.total = total;
	}

	public String getRevision()
	{
		return revision;
	}

	public void setRevision(String revision)
	{
		this.revision = revision;
	}

	public List getScanReportList()
	{
		return scanReportList;
	}

	public void setScanReportList(List scanReportList)
	{
		this.scanReportList = scanReportList;
	}

	static public String getHeader()
	{
		return "Package Name\tPackage Guid\tDiscovery Name\tPackage Type\tRoot Folder\tHost\tPort\tInstance\tLogin\tCred Checkbox\tPassword\tSchema\tScan Report\tRepository\tRevision\tAdded\tRemoved\tTotal";
	}
	
	public String toString()
	{
		StringBuffer rslt = new StringBuffer()
				.append(packageName).append("\t")
				.append(packageGuid).append("\t")
				.append(discoveryName)	.append("\t")
				.append(packageType).append("\t")
				.append(packageLocation).append("\t")
				.append(host).append("\t")
				.append(port).append("\t")
				.append(instanceName).append("\t")
				.append(loginName).append("\t")
				.append(rememberPassword).append("\t")
				.append(storedPassword).append("\t")
				.append(schema).append("\t")
				.append(getScanReportString()).append("\t")
				.append(repository).append("\t")
				.append(revision).append("\t")
				.append(added).append("\t")
				.append(removed).append("\t")
				.append(total).append("\t");
		
		return rslt.toString();
	}
	public String getScanReportString()
	{
		StringBuffer scanRpt = new StringBuffer();
		List <ScanReportData>scanList = getScanReportList();
		for (ScanReportData scanData: scanList )
		{
			if (scanRpt.toString().length() > 0)
			{
				scanRpt.append(";");
			}
			scanRpt.append(scanData);
		}
		return scanRpt.toString();

	}
	static public String getCompareHeader()
	{
		return "Package Name\tPackage Guid\t\tDiscovery Name\t\tPackageType\t\tRoot Folder\t\tHost\t\tPort\t\tInstance\t\tLogin\t\tCred Checkbox\t\tPassword\t\tSchema\t\tScan Report\t\tRepository\t\tRevision\tAdded\tRemoved\tTotal";
	}
	public String compare(PackageData compareRec)
	{
		
		return new StringBuffer()
				.append(packageName).append("\t")
				.append(packageGuid).append("\t")
				.append(discoveryName.equals(compareRec.getDiscoveryName())?"":"X").append("\t").append(discoveryName).append("\t")
				.append(packageType.equals(compareRec.getPackageType())?"":"X").append("\t").append(packageType).append("\t")
				.append(packageLocation.equals(compareRec.getPackageLocation())?"":"X").append("\t").append(packageLocation).append("\t")
				.append(host.equals(compareRec.getHost())?"":"X").append("\t").append(host).append("\t")
				.append(port.equals(compareRec.getPort())?"":"X").append("\t").append(port).append("\t")
				.append(instanceName.equals(compareRec.getInstanceName())?"":"X").append("\t").append(instanceName).append("\t")
				.append(loginName.equals(compareRec.getLoginName())?"":"X").append("\t").append(loginName).append("\t")
				.append(rememberPassword==compareRec.isRememberPassword()?"":"X").append("\t").append(rememberPassword).append("\t")
				.append(storedPassword==compareRec.isStoredPassword()?"":"X").append("\t").append(storedPassword).append("\t")
				.append(schema.equals(compareRec.getSchema())?"":"X").append("\t").append(schema).append("\t")
				.append(getScanReportString().equals(compareRec.getScanReportString())?"":"X").append("\t").append(getScanReportString()).append("\t")
				.append(repository.equals(compareRec.getRepository())?"":"X").append("\t").append(repository).append("\t")
				.append(revision.equals(compareRec.getRevision())?"":"X").append("\t").append(revision).append("\t")
				.append(added).append("\t")
				.append(removed).append("\t")
				.append(total).append("\t")
				.toString();

	}	
	public String getFromRecordString()
	{
		return new StringBuffer().append(packageName).append("\t")
													 .append(packageGuid).append("\t\t")
													 .append(discoveryName).append("\t\t")
													 .append(packageType).append("\t\t")
													 .append(packageLocation).append("\t\t")
													 .append(host).append("\t\t")
												 	 .append(port).append("\t\t")
												 	 .append(instanceName).append("\t\t")
												 	 .append(loginName).append("\t\t")
											 	 	 .append(rememberPassword).append("\t\t")
											 	 	 .append(storedPassword).append("\t\t")
											 	 	 .append(schema).append("\t\t")
											 	 	 .append(getScanReportString()).append("\t\t")
										 	 	 	 .append(repository).append("\t\t")
										 	 	 	 .append(revision).append("\t")
										 	 	 	 .append(added).append("\t")
										 	 	 	 .append(removed).append("\t")
										 	 	 	 .append(total).append("\t")
										 	 	 	 .toString();
	}
	static public String blankRecord()
	{
		return new StringBuffer().append("\t\tX\t\tX\t\tX\t\tX\t\tX\t\tX\t\tX\t\tX\t\tX\t\tX\t\tX\t\tX").toString();
	}

}
