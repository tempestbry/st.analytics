package tw.org.iii.model;

import java.util.*;
public class DailyItinerary {

	private String county;
	private List<String> preferenceList = new ArrayList<String>();
	private List<String> mustPoiList = new ArrayList<String>();
	
	private Calendar startTime;	
	private Calendar endTime;
	private GeoPoint startGeoPoint;
	private GeoPoint endGeoPoint;
	private String startPoiId;	
	private String endPoiId;
	
	private int looseType;
	private List<TourEvent> tourResult = new ArrayList<TourEvent>();
	
	
	public String getCounty() {
		return county;
	}
	public void setCounty(String county) {
		this.county = county;
	}
	
	public List<String> getPreferenceList() {
		return preferenceList;
	}
	public void setPreferenceList(List<String> preferenceList) {
		this.preferenceList = preferenceList;
	}

	public List<String> getMustPoiList() {
		return mustPoiList;
	}
	public void setMustPoiList(List<String> mustPoiList) {
		this.mustPoiList = mustPoiList;
	}

	public Calendar getStartTime() {
		return startTime;
	}
	public void setStartTime(Calendar startTime) {
		this.startTime = startTime;
	}

	public Calendar getEndTime() {
		return endTime;
	}
	public void setEndTime(Calendar endTime) {
		this.endTime = endTime;
	}

	public GeoPoint getStartGeoPoint() {
		return startGeoPoint;
	}
	public void setStartGeoPoint(GeoPoint startGeoPoint) {
		this.startGeoPoint = startGeoPoint;
	}
	
	public GeoPoint getEndGeoPoint() {
		return endGeoPoint;
	}
	public void setEndGeoPoint(GeoPoint endGeoPoint) {
		this.endGeoPoint = endGeoPoint;
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
		
	public int getLooseType() {
		return looseType;
	}
	public void setLooseType(int looseType) {
		this.looseType = looseType;
	}

	public List<TourEvent> getTourResult() {
		return tourResult;
	}
	public void setTourResult(List<TourEvent> tourResult) {
		this.tourResult = tourResult;
	}
}
