package tw.org.iii.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

public class QueryDatabase {
	
	public static final String[] daysOfWeekName = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
	
	private JdbcTemplate analyticsjdbc;
	private boolean isDisplay;
	
	public QueryDatabase(JdbcTemplate analyticsjdbc, boolean isDisplay) {
		this.analyticsjdbc = analyticsjdbc;
		this.isDisplay = isDisplay;
	}
	
	public Poi getPoiByPoiId(String poiId, boolean isMustPoi, int looseType) {
		String sql = "SELECT * FROM Scheduling2_bkup WHERE poiId = '" + poiId + "'";
		if (isDisplay)
			System.out.println("[SQL] " + sql);
		
		List<Map<String, Object>> queryResult = analyticsjdbc.queryForList(sql);
		if (isDisplay)
			System.out.println("[result count] " + queryResult.size());
		
		if (queryResult.isEmpty()) {
			System.err.println("[!!! Error !!!][PoiQuery getPoiByPoiId] poiId not found!");
			return null;
		} else {
			Poi poi = new Poi();
			poi.setFromQueryResult(queryResult.get(0), looseType);
			poi.setMustPoi(isMustPoi);
			return poi;
		}
	}
	
	public List<Poi> getPoiListByPoiIdList(List<String> poiIdList, boolean isMustPoi, int looseType) {
		String sql = "SELECT * FROM Scheduling2_bkup WHERE poiId = '" + poiIdList.get(0) + "'";
		for (int i = 1; i < poiIdList.size(); ++i)
			sql += " OR poiId = '" + poiIdList.get(i) + "'";
		if (isDisplay)
			System.out.println("[SQL] " + sql);
		
		List<Map<String, Object>> queryResult = analyticsjdbc.queryForList(sql);
		if (isDisplay)
			System.out.println("[result count] " + queryResult.size());
		
		if (queryResult.size() != poiIdList.size()) {
			System.err.println("[!!! Error !!!][PoiQuery getPoiListByPoiIdList] Some poiId not found!");
			return null;
		} else {
			List<Poi> poiList = new ArrayList<Poi>();
			for (int i = 0; i < queryResult.size(); ++i) {
				Poi poi = new Poi();
				poi.setFromQueryResult(queryResult.get(i), looseType);
				poi.setMustPoi(isMustPoi);
				poiList.add(poi);
			}
			return poiList;
		}
	}
	
	public List<Poi> getRandomPoiListByCountyAndOpenDay(int numSelection, List<Integer> countyIndex, boolean[] isMustOpen, int looseType) {
		if (countyIndex.isEmpty()) {
			System.err.println("[!!! Error !!!][PoiQuery getRandomPoiListByCountyAndOpenDay] countyIndex is empty!");
			return null;
		}
		
		// countyIndex
		String sql = "SELECT * FROM Scheduling2_bkup WHERE countyId IN ('TW" + countyIndex.get(0) + "'";
		for (int i = 1; i < countyIndex.size(); ++i)
			sql += ", 'TW" + countyIndex.get(i) + "'";
		sql += ")";
		
		// isMustOpen
		for (int i = 0; i < 7; ++i) {
			if (isMustOpen[i])
				sql += " AND (OH_" + daysOfWeekName[i] + " IS NULL OR OH_" + daysOfWeekName[i] + " != 'close')";
		}
		sql += " ORDER BY RAND() LIMIT " + numSelection;
		
		if (isDisplay)
			System.out.println("[SQL] " + sql);
		
		List<Map<String, Object>> queryResult = analyticsjdbc.queryForList(sql);
		if (isDisplay)
			System.out.println("[result count] " + queryResult.size());
		
		if (queryResult.size() != numSelection) {
			System.err.println("[!!! Error !!!][PoiQuery getRandomPoiListByCountyAndOpenDay] Not select correct number of POIs!");
			return null;
		} else {
			List<Poi> poiList = new ArrayList<Poi>();
			for (int i = 0; i < queryResult.size(); ++i) {
				Poi poi = new Poi();
				poi.setFromQueryResult(queryResult.get(i), looseType);
				poi.setMustPoi(false);
				poiList.add(poi);
			}
			return poiList;
		}
	}
	
	public String getNearbyPoiIdByCoordinate(double latitude, double longitude) {
		
		final double latitudeLB = 20.9; //21.8976
		final double latitudeUB = 27.4; //26.3813
		final double longitudeLB = 117.2; //118.21799
		final double longitudeUB = 123.1; //122.1056
		
		if (latitude < latitudeLB || latitude > latitudeUB || longitude < longitudeLB || longitude > longitudeUB) {
			System.err.println("[!!! Error !!!][PoiQuery getNearbyPoiIdByCoordinate] latitude/longitude out of bound!");
			return null;
		}
		
		double tolerance = 0.01;
		while (true) {
			String sql = "SELECT * FROM Scheduling2 WHERE checkinTotal > 0"
						+ " AND latitude >= " + (latitude - tolerance) + " AND latitude <= " + (latitude + tolerance)
						+ " AND longitude >= " + (longitude - tolerance) + " AND longitude <= " + (longitude + tolerance)
						+ " ORDER BY RAND() LIMIT 1;";
			if (isDisplay)
				System.out.println("[SQL] " + sql);
			
			List<Map<String, Object>> queryResult = analyticsjdbc.queryForList(sql);
			if (isDisplay)
				System.out.println("[result count] " + queryResult.size());
			
			if (! queryResult.isEmpty())
				return queryResult.get(0).get("poiId").toString();
			else if (tolerance >= 8) {
				System.err.println("[!!! Error !!!][PoiQuery getNearbyPoiIdByCoordinate] tolerance exploded!");
				return null;
			}
			else
				tolerance += 0.01;
		}
	}
	public String getNearbyPoiIdByCoordinate(double[] coordinate) {
		return getNearbyPoiIdByCoordinate(coordinate[0], coordinate[1]);
	}
}

