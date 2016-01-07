package tw.org.iii.model;

import java.util.Map;

public class Poi {
	private String poiId;
	private String poiName;
	private String location;
	private String countyId;
	private int checkins;
	private int stayTime; //In MIP2	
	private String openHoursOfThisDay; //In MIP2 //link to a day
	
	private boolean isMustPoi; //In MIP2
	private boolean isInMustCounty; //In MIP2 //link to a day
	
	private double longitude;
	private double latitude;
	private OpenHours openHours;
	
	public void setFromQueryResult(Map<String, Object> queryResultPoi, String weekOfDay) {
		poiId = queryResultPoi.get("poiId").toString();
		poiName = queryResultPoi.get("name").toString();
		location = queryResultPoi.get("location").toString();
		countyId = queryResultPoi.get("countyId").toString();
		
		Object tempObj1 = queryResultPoi.get("checkinSeason");
		if (tempObj1 != null)
			checkins = Integer.parseInt(tempObj1.toString());
		else
			checkins = 0;
		
		Object tempObj2 = queryResultPoi.get("stayTime");
		if (tempObj2 != null)
			stayTime = Integer.parseInt(tempObj2.toString());
		else
			stayTime = 60;
		
		Object tempObj3 = queryResultPoi.get("OH_" + weekOfDay);
		if (tempObj3 != null)
			openHoursOfThisDay = tempObj3.toString();
		else
			openHoursOfThisDay = null;
	}
	
	public double[] getCoordinate() {
		double[] coordinate = {latitude, longitude};
		return coordinate;
	}
	public void setCoordinate(double[] coordinate) {
		this.latitude = coordinate[0];
		this.longitude = coordinate[1];
	}
	public void setCoordinate(double latitude, double longitude) {
		this.latitude = latitude;
		this.longitude = longitude;
	}
	
	
	public String getPoiId() {
		return poiId;
	}
	public void setPoiId(String poiId) {
		this.poiId = poiId;
	}
	public String getPoiName() {
		return poiName;
	}
	public void setPoiName(String poiName) {
		this.poiName = poiName;
	}
	public String getLocation() {
		return location;
	}
	public void setLocation(String location) {
		this.location = location;
	}
	public String getCountyId() {
		return countyId;
	}
	public void setCountyId(String countyId) {
		this.countyId = countyId;
	}
	public int getCheckins() {
		return checkins;
	}
	public void setCheckins(int checkins) {
		this.checkins = checkins;
	}
	public int getStayTime() {
		return stayTime;
	}
	public void setStayTime(int stayTime) {
		this.stayTime = stayTime;
	}
	public boolean isMustPoi() {
		return isMustPoi;
	}
	public void setMustPoi(boolean isMustPoi) {
		this.isMustPoi = isMustPoi;
	}
	public boolean isInMustCounty() {
		return isInMustCounty;
	}
	public void setInMustCounty(boolean isInMustCounty) {
		this.isInMustCounty = isInMustCounty;
	}
	public String getOpenHoursOfThisDay() {
		return openHoursOfThisDay;
	}
	public void setOpenHoursOfThisDay(String openHoursOfThisDay) {
		this.openHoursOfThisDay = openHoursOfThisDay;
	}
	public double getLongitude() {
		return longitude;
	}
	public void setLongitude(double longitude) {
		this.longitude = longitude;
	}
	public double getLatitude() {
		return latitude;
	}
	public void setLatitude(double latitude) {
		this.latitude = latitude;
	}
	public OpenHours getOpenHours() {
		return openHours;
	}
	public void setOpenHours(OpenHours openHours) {
		this.openHours = openHours;
	}
	

}

