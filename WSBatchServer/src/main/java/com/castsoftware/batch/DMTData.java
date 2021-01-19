package com.castsoftware.batch;

import java.util.Calendar;
import java.util.Date;

public class DMTData {
  private String applName;
  private String uuid;
  private Date lastModified;
  private VersionData versionInfo;
  
  
  public String getApplName()
  {
    return applName;
  }
  public void setApplName(String applName)
  {
    this.applName = applName;
  }
  public String getUuid()
  {
    return uuid;
  }
  public void setUuid(String uuid)
  {
    this.uuid = uuid;
  }
  public Date getLastModified()
  {
    return lastModified;
  }
  public void setLastModified(Date lastModified)
  {
    this.lastModified = lastModified;
  }
  public VersionData getVersionInfo()
  {
    return versionInfo;
  }
  public void setVersionInfo(VersionData versionInfo)
  {
    this.versionInfo = versionInfo;
  }
 
}
