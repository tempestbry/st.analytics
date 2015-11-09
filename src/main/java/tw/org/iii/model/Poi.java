package tw.org.iii.model;

public class Poi {
	private String poiId;
	
	public Poi(String aId) {
		super();
		this.poiId = aId;
	}
	public Poi() {
		super();
	}

	public String getPoiId() {
		return poiId;
	}

	public void setPlaceID(String aID) {
		this.poiId = aID;
	}
}
