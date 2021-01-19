package com.castsoftware.batch;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.xml.bind.DatatypeConverter;

import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

public class Jenkins {
  private static final Logger logger = Logger.getLogger(AlertDMTChange.class);
  private String              url;
  private String              account;
  private String              password;
  private String              jobPostfix;

  public Jenkins(String url, String account, String password, String jobPostfix)
  {
    super();
    this.url = url;
    this.account = account;
    this.password = password;
    this.jobPostfix = jobPostfix;
  }

  public Jenkins(String url, String account, String password)
  {
    super();
    this.url = url;
    this.account = account;
    this.password = password;
    this.jobPostfix = "";
  }

  public boolean runJob(String jobName, String castDate, String versionName, int startAt)
  {
    boolean rslt = true;

    URL url=null;
    try
    {
      url = new URL(String.format("%s/job/%s%s/buildWithParameters", this.url, jobName, this.jobPostfix));
      String authStr = account + ":" + password;
      String encoding = DatatypeConverter.printBase64Binary(authStr.getBytes("utf-8"));

      logger.info(String.format("Running Jenkins job: %s",url));
      
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("POST");
      connection.setDoOutput(true);
      connection.setRequestProperty("Authorization", "Basic " + encoding);

      String urlParams = String.format("START_AT=%d&CAST_DATE=%s&VERSION_NAME_OVERRIDE=%s&BLOCK_STEP=2", startAt, castDate,versionName);
      logger.info(String.format("Using parameters: %s",urlParams));
      byte[] postData = urlParams.getBytes("utf-8");
      try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream()))
      {
        wr.write(postData);
      }

      InputStream content = connection.getInputStream();
      BufferedReader in = new BufferedReader(new InputStreamReader(content));
      String line;
      while ((line = in.readLine()) != null)
      {
        System.out.println(line);
      }

    } catch (FileNotFoundException e)
    {
      logger.error(String.format("Invalid Url: %s", url.toString()) );
      rslt = false;
    } catch (Exception e)
    {
      // TODO Auto-generated catch block
      logger.error(String.format(e.toString()) );
      e.printStackTrace();
      rslt = false;
    }

    return rslt;
  }

//  public static void main(String[] args)
//  {
//    Jenkins jenkins = new Jenkins("http://demotest:8080", "_ANALYSIS");
//
//    String version = "2018-06-14 21:51:00";
//    SimpleDateFormat dmtFormat=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");  
//    SimpleDateFormat castDateFormat=new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");  
//    String castDate="";
//    
//    try
//    {
//      Date date1=dmtFormat.parse(version);
//      castDate = castDateFormat.format(date1);
////      jenkins.runJob("ECT", "PROD", castDate, 2);
//    } catch (ParseException e)
//    {
//      // TODO Auto-generated catch block
//      e.printStackTrace();
//    }  
//    
//
//  }
}
