package tw.org.iii.model;

public class PoiCheckins {

	double px;
	
	double py;
	
	int checkins;
	
	String place_id;

	public PoiCheckins(String id, int checkin, double x,double y) {
		super();
		this.place_id = id;
		this.checkins = checkin;
		this.px = x;
		this.py = y;
	}
	
	public PoiCheckins(){
		super();
	}

	public String getPlaceID() {
		return place_id;
	}

	public void setPlaceID(String id) {
		this.place_id = id;
	}

	public int getCheckins() {
		return checkins;
	}

	public void setCheckins(int checkin) {
		this.checkins = checkin;
	}

	public double getPx() {
		return px;
	}

	public void setPx(double px) {
		this.px = px;
	}
	
	public double getPy() {
		return py;
	}

	public void setPy(double py) {
		this.py = py;
	}
	
	
}
