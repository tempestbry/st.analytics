package tw.org.iii.model;

import java.util.ArrayList;
import java.util.List;

public class RecommendInput {
	String poiId;
	List<String> countyId = new ArrayList();
	String returnType;
	int limit=-1;
	
	public int getLimit() {
		return limit;
	}
	public void setLimit(int limit) {
		this.limit = limit;
	}
	GeoPoint gps;
	
	public RecommendInput(String id,List<String> county,String Type, GeoPoint gps) {
		super();
		this.poiId = id;
		this.countyId = county;
		this.returnType = Type;
		this.gps = gps;
	}
	public RecommendInput(){
		super();
	}
	
	public String getPoiId() {
		return poiId;
	}
	public void setPoiId(String id) {
		this.poiId = id;
	}
	
	public List<String> getCountyId() {
		return countyId;
	}
	public void setCountyId(List<String> county) {
		this.countyId = county;
	}
	
	public String getReturnType() {
		return "poi".equals(returnType) ? "0,1,2,3,4" : returnType;
	}
	public void setReturnType(String type) {
		this.returnType = type;
	}
	public GeoPoint getGps() {
		return gps;
	}
	public void setGps(GeoPoint gps) {
		this.gps = gps;
	}
	
	
}
