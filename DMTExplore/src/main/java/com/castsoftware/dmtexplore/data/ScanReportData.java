package com.castsoftware.dmtexplore.data;

public class ScanReportData
{
	private String fileExtension="";
	private String rootPath="";
	private int added=0;
	private int removed=0;
	private int total=0;
	
	public ScanReportData (String fileExtension, String rootPath, int added, int removed, int total)
	{
		this.fileExtension=fileExtension;
		this.rootPath=rootPath;
		this.added=added;
		this.removed=removed;
		this.total=total;
	}

	public String getFileExtension()
	{
		return fileExtension;
	}

	public String getRootPath()
	{
		return rootPath;
	}

	public int getAdded()
	{
		return added;
	}

	public int getRemoved()
	{
		return removed;
	}

	public int getTotal()
	{
		return total;
	}

	@Override
	public String toString()
	{
		return new StringBuffer()
		     	.append(fileExtension).append(rootPath.isEmpty()?"":"|")
				.append(rootPath)
				.toString();
	}
	
	
}
