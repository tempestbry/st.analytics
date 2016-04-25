package tw.org.iii.model;

public class GeoPoint {

	double lat;
	
	double lng;

	public double getLat() {
		return lat;
	}

	public void setLat(double lat) {
		this.lat = lat;
	}

	public double getLng() {
		return lng;
	}

	public void setLng(double lng) {
		this.lng = lng;
	}
	
	//ADD constructor
	public GeoPoint() {
		super();
	}
	
	public GeoPoint(double lat, double lng) {
		setLat(lat);
		setLng(lng);
	}
}
