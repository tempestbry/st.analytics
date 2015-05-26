package tw.org.iii.model;

import java.util.*;
public class SchedulingInput 
{
	private List<String> cityList = new ArrayList<String>();
	private List<String> preferenceList = new ArrayList<String>();
	private String startTime;
	
	private String endTime;
	
	private String gps;
	
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

	public String getStartTime() {
		return startTime;
	}

	public void setStartTime(String startTime) {
		this.startTime = startTime;
	}

	public String getEndTime() {
		return endTime;
	}

	public void setEndTime(String endTime) {
		this.endTime = endTime;
	}

	public String getGps() {
		return gps;
	}

	public void setGps(String gps) {
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
