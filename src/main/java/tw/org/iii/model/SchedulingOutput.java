package tw.org.iii.model;

import java.util.List;

public class SchedulingOutput {
	List<TourEvent> poiList;
	String message;
	
	public List<TourEvent> getPoiList() {
		return poiList;
	}
	public void setPoiList(List<TourEvent> poiList) {
		this.poiList = poiList;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
}
