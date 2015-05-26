package tw.org.iii.model;

import java.util.*;
public class SchedulingInput 
{
	private List<String> cityList = new ArrayList<String>();
	public void setCity(List<String> p)
	{
		this.cityList = p;
	}
	public List<String> getCity()
	{
		return cityList;
	}
	
	private List<String> preferenceList = new ArrayList<String>();
	public void setPreference(List<String> p)
	{
		this.preferenceList = p;
	}
	public List<String> getPreference()
	{
		return preferenceList;
	}
	
	private String startTime;
	public void setStartTime(String t)
	{
		this.startTime = t;
	}
	public String getStartTime()
	{
		return startTime;
	}
	
	private String endTime;
	public void setEndTime(String t)
	{
		this.endTime = t;
	}
	public String getEndTime()
	{
		return endTime;
	}
	
	private String gps;
	public void setGps(String g)
	{
		this.gps = g;
	}
	public String getGps()
	{
		return gps;
	}
	
	private String startPoiId;
	public void setStartPoiId(String t)
	{
		this.startPoiId = t;
	}
	public String getStartPoiId()
	{
		return startPoiId;
	}
	
	private String endPoiId;
	public void setDestination(String t)
	{
		this.endPoiId = t;
	}
	public String getDestination()
	{
		return endPoiId;
	}
	
	private boolean shake;
	public void setShake(boolean t)
	{
		this.shake = t;
	}
	public boolean getShake()
	{
		return shake;
	}
	
	

}
