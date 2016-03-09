package tw.org.iii.model;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class DailyInfo {
	
	private Calendar calendarForDay;
	private int dayOfWeek; //0:Sun, 1:Mon, ..., 6:Sat
	private int startTimeInMinutes; //In MIP2
	private int endTimeInMinutes; //In MIP2
	private String mustCounty; //In MIP2
	private Poi startPoi;
	private Poi endPoi;
	private boolean isStartPoiUseStayTime;
	private boolean isTravelToEndPoi;
	private boolean isEndPoiUseStayTime;
	private List<Poi> poiList = new ArrayList<Poi>();
	
	public int getMustCountyIndex() {
		return mustCounty.equals("all") ? 0 : Integer.parseInt( mustCounty.replaceAll("[^0-9]", "") );
	}
	
	//------------------------
	//  auto getter / setter
	//------------------------
	public Calendar getCalendarForDay() {
		return calendarForDay;
	}
	public void setCalendarForDay(Calendar calendarForDay) {
		this.calendarForDay = calendarForDay;
	}
	public int getDayOfWeek() {
		return dayOfWeek;
	}
	public void setDayOfWeek(int dayOfWeek) {
		this.dayOfWeek = dayOfWeek;
	}
	public int getStartTimeInMinutes() {
		return startTimeInMinutes;
	}
	public void setStartTimeInMinutes(int startTimeInMinutes) {
		this.startTimeInMinutes = startTimeInMinutes;
	}
	public int getEndTimeInMinutes() {
		return endTimeInMinutes;
	}
	public void setEndTimeInMinutes(int endTimeInMinutes) {
		this.endTimeInMinutes = endTimeInMinutes;
	}
	public String getMustCounty() {
		return mustCounty;
	}
	public void setMustCounty(String mustCounty) {
		this.mustCounty = mustCounty;
	}
	public Poi getStartPoi() {
		return startPoi;
	}
	public void setStartPoi(Poi startPoi) {
		this.startPoi = startPoi;
	}
	public Poi getEndPoi() {
		return endPoi;
	}
	public void setEndPoi(Poi endPoi) {
		this.endPoi = endPoi;
	}
	public boolean isStartPoiUseStayTime() {
		return isStartPoiUseStayTime;
	}
	public void setStartPoiUseStayTime(boolean isStartPoiUseStayTime) {
		this.isStartPoiUseStayTime = isStartPoiUseStayTime;
	}
	public boolean isTravelToEndPoi() {
		return isTravelToEndPoi;
	}
	public void setTravelToEndPoi(boolean isTravelToEndPoi) {
		this.isTravelToEndPoi = isTravelToEndPoi;
	}
	public boolean isEndPoiUseStayTime() {
		return isEndPoiUseStayTime;
	}
	public void setEndPoiUseStayTime(boolean isEndPoiUseStayTime) {
		this.isEndPoiUseStayTime = isEndPoiUseStayTime;
	}
	public List<Poi> getPoiList() {
		return poiList;
	}
	public void setPoiList(List<Poi> poiList) {
		this.poiList = poiList;
	}
}
