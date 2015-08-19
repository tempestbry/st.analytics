package tw.org.iii.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class OptimizeInput {
	//private List<String> mustPoiList = new ArrayList<String>();
	private List<Poi> mustPoiList = new ArrayList<Poi>();
	private OptimizeOptions optimizeCondition = new OptimizeOptions();
	private double looseType;
	private Date startTime;	
	private Date endTime;
	
	private GeoPoint gps; 
	/*
	private List<String> cityList = new ArrayList<String>();
	private List<String> preferenceList = new ArrayList<String>();
	private String startPoiId;
	private String endPoiId;
	private boolean shake;
	private String tourType;
	*/
	
	public List<Poi> getMustPoiList() {
		return mustPoiList;
	}
	public void setMustPoiList(List<Poi> aMustPoiList) {
		this.mustPoiList = aMustPoiList;
	}
	
	
	/*public List<String> getMustPoiList() {
		return mustPoiList;
	}
	public void setPoiList(List<String> aMustPoiList) {
		this.mustPoiList = aMustPoiList;
	}*/
	
	public String getOptimizeCondition() {
		return optimizeCondition.getByConditions();
	}
	public void setOptimizeCondition(String aOptimizeOptions) {
		this.optimizeCondition.setByConditions(aOptimizeOptions);
	}
	
	public double getLooseType(){
		return looseType;
	}
	public void setLooseType(double aLooseType) {
		this.looseType = aLooseType;
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
}
