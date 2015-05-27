package tw.org.iii.model;

import java.util.*;
public class SchedulingInput 
{
	private List<String> cityList = new ArrayList<String>();
	private List<String> preferenceList = new ArrayList<String>();
	private Date startTime;
	
	private Date endTime;
	
	private GeoPoint gps;
	
	private String startPoiId;
	
	private String endPoiId;
	
	private boolean shake;

	public List<String> getCityList() {
		return cityList;
	}

	public void setCityList(List<String> cityList) {
		this.cityList = cityList;
	}

	public List<String> getPreferenceList() {
		return preferenceList;
	}

	public void setPreferenceList(List<String> preferenceList) {
		this.preferenceList = preferenceList;
	}

	

	

	public Date getStartTime() {
		return startTime;
	}

	public void setStartTime(Date startTime) {
		this.startTime = startTime;
	}

	public Date getEndTime() {
		return endTime;
	}

	public void setEndTime(Date endTime) {
		this.endTime = endTime;
	}

	public GeoPoint getGps() {
		return gps;
	}

	public void setGps(GeoPoint gps) {
		this.gps = gps;
	}

	public String getStartPoiId() {
		return startPoiId;
	}

	public void setStartPoiId(String startPoiId) {
		this.startPoiId = startPoiId;
	}

	public String getEndPoiId() {
		return endPoiId;
	}

	public void setEndPoiId(String endPoiId) {
		this.endPoiId = endPoiId;
	}

	public boolean isShake() {
		return shake;
	}

	public void setShake(boolean shake) {
		this.shake = shake;
	}
	
	

}
