package tw.org.iii.model;

import java.util.Date;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * 
 * @author ansonliu
 *fffddd
 */
public class TourEvent {

	@JsonSerialize(using=tw.org.iii.st.analytics.spring.IsoDateSerializer.class)
	Date startTime;
	
	@JsonSerialize(using=tw.org.iii.st.analytics.spring.IsoDateSerializer.class)
	Date endTime;
	
	String poiId;

	public TourEvent(Date startTime, Date endTime, String poiId) {
		super();
		this.startTime = startTime;
		this.endTime = endTime;
		this.poiId = poiId;
	}
	
	public TourEvent(){
		super();
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

	public String getPoiId() {
		return poiId;
	}

	public void setPoiId(String poiId) {
		this.poiId = poiId;
	}
	
	
}
