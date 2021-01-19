package com.castsoftware.batch.data;

public class AnalysisInfo
{
	public static final String FLD_ANA_UNIT_NAME = "anaUnitName";
	public static final String FLD_STATUS= "status";
	public static final String FLD_LOG = "log";

	private String anaUnitName;
	private String status;
	private String log;
	
	private int fatalError;
	private int error;
	private int warning;
	private int information;

	public AnalysisInfo(String anaUnitName, String status, String log)
	{
		super();
		
		this.anaUnitName = anaUnitName;
		this.status = status;
		this.log = log;
	}

	public String getAnaUnitName()
	{
		return anaUnitName;
	}

	public void setAnaUnitName(String anaUnitName)
	{
		this.anaUnitName = anaUnitName;
	}

	public String getStatus()
	{
		return status;
	}

	public void setStatus(String status)
	{
		this.status = status;
	}

	public String getLog()
	{
		return log;
	}

	public void setLog(String log)
	{
		this.log = log;
	}

	public int getFatalError()
	{
		return fatalError;
	}

	public void setFatalError(int fatalError)
	{
		this.fatalError = fatalError;
	}

	public int getError()
	{
		return error;
	}

	public void setError(int error)
	{
		this.error = error;
	}

	public int getWarning()
	{
		return warning;
	}

	public void setWarning(int warning)
	{
		this.warning = warning;
	}

	public int getInformation()
	{
		return information;
	}

	public void setInformation(int information)
	{
		this.information = information;
	} 
}
