package tw.org.iii.model;

public class RecommendInfo {
	String recommend_id;
	double total;
	
	public RecommendInfo(String id,double total) {
		super();
		this.recommend_id = id;
		this.total = total;
	}
	public RecommendInfo(){
		super();
	}
	
	public String getRecommendID() {
		return recommend_id;
	}

	public void setRecommendID(String id) {
		this.recommend_id = id;
	}
	public double getTotal() {
		return total;
	}

	public void setTotal(double total) {
		this.total = total;
	}
}
