package tw.org.iii.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

public class QueryDatabase {
	
	public static final String[] daysOfWeekName = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
	
	private JdbcTemplate analyticsjdbc;
	private boolean isDisplay;
	
	public QueryDatabase(JdbcTemplate analyticsjdbc, boolean isDisplay) {
		this.analyticsjdbc = analyticsjdbc;
		this.isDisplay = isDisplay;
	}
	
	public boolean isDisplay() {
		return isDisplay;
	}
	public void setDisplay(boolean isDisplay) {
		this.isDisplay = isDisplay;
	}
	public void display(String string) {
		if (isDisplay)
			System.out.println(string);
	}
	
	//====================================
	//    get POI or POI list by poiId
	//====================================
	public Poi getPoiByPoiId(String poiId, boolean isMustPoi, int looseType) {
		
		String sql = "SELECT * FROM Scheduling2 WHERE poiId = '" + poiId + "'";
		if (isDisplay)
			System.out.println("[SQL] " + sql);
		
		List<Map<String, Object>> queryResult = analyticsjdbc.queryForList(sql);
		if (isDisplay)
			System.out.println("[count] " + queryResult.size());
		
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
		String sql = "SELECT * FROM Scheduling2 WHERE poiId = '" + poiIdList.get(0) + "'";
		for (int i = 1; i < poiIdList.size(); ++i)
			sql += " OR poiId = '" + poiIdList.get(i) + "'";
		if (isDisplay)
			System.out.println("[SQL] " + sql);
		
		List<Map<String, Object>> queryResult = analyticsjdbc.queryForList(sql);
		if (isDisplay)
			System.out.println("[count] " + queryResult.size());
		
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
	
	//===========================================================
	//    get random POI or POI list by (1)county (2)open day
	//===========================================================
	private List<Poi> getRandomPoiListByCountyAndOpenDay(int numSelection, Set<Integer> counties, boolean[] isMustOpen, int looseType) {
		if (counties.isEmpty()) {
			System.err.println("[!!! Error !!!][PoiQuery getRandomPoiListByCountyAndOpenDay] counties is empty!");
			return null;
		}
		
		// countyIndex
		String sql = "SELECT * FROM Scheduling2 WHERE countyId IN (";
		boolean isFirst = true;
		for (Integer i : counties) {
			if (isFirst) {
				sql += "'TW" + i + "'";
				isFirst = false;
			} else
				sql += ", 'TW" + i + "'";
		}
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
			System.out.println("[count] " + queryResult.size());
		
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
	public List<Poi> getRandomPoiListByCountyAndOpenDay(int numSelection, Set<Integer> counties, int mustOpenDay, int looseType) {
		boolean[] isMustOpen = new boolean[7];
		isMustOpen[mustOpenDay] = true;
		return getRandomPoiListByCountyAndOpenDay(numSelection, counties, isMustOpen, looseType);
	}
	public List<Poi> getRandomPoiListByCountyAndOpenDay(int numSelection, Set<Integer> counties, String mustOpenDay, int looseType) {
		if (mustOpenDay.equals("allOpen")) {
			boolean[] isMustOpen = new boolean[7];
			Arrays.fill(isMustOpen, true);
			return getRandomPoiListByCountyAndOpenDay(numSelection, counties, isMustOpen, looseType);
		}
		else if (mustOpenDay.equals("free")) {
			boolean[] isMustOpen = new boolean[7];
			return getRandomPoiListByCountyAndOpenDay(numSelection, counties, isMustOpen, looseType);
		} else
			return null;
	}
	
	public Poi getRandomPoiByCountyAndOpenDay(String countyId, int mustOpenDay, int looseType) {
		Set<Integer> counties = new HashSet<Integer>();
		counties.add( Integer.parseInt( countyId.replaceAll("[^0-9]", "")) );
		
		boolean[] isMustOpen = new boolean[7];
		isMustOpen[mustOpenDay] = true;
		
		List<Poi> poiList = getRandomPoiListByCountyAndOpenDay(1, counties, isMustOpen, looseType);
		if (poiList == null || poiList.isEmpty())
			return null;
		else
			return poiList.get(0);
	}
	
	//========================================================================================
	//    get (random) POI list by (1)center poiId (2)county (not necessarily) (3)open day
	//========================================================================================
	private List<Poi> getRandomPoiListByCenterPoiIdAndCountyAndOpenDay(int numSelectionLB, int numSelectionUB,
			String centerPoiId, double distanceLB, double distanceUB, double distanceUBIncrement,
			Set<Integer> counties, boolean[] isMustOpen, String orderBy, int looseType) {
		//usage:	enter distanceLB <= 0 if not used
		//		enter distanceUB <= 0 if not used  (at least one of distanceLB/distanceUB has to be > 0)
		//		enter distanceUBIncrement = 0 if not used
		//		counties is allowed to be null or empty
		if (distanceLB <= 0 && distanceUB <= 0) {
			System.err.println("[!!! Error !!!][PoiQuery getRandomPoiListByCenterPoiIdAndCountyAndOpenDay] distanceLB and distanceUB are both <= 0 !");
			return null;
		}
		double distanceUBForTermination = 100;
		
		List<Map<String, Object>> queryResult;
		while (true) {
			String sql = "SELECT c.*, distance AS distanceMeasureToFixedPoi FROM "
					+ "(SELECT poiId_to, distance FROM Distance WHERE ("
					+ getPartialSqlWithCenterPoiId(centerPoiId, distanceLB, distanceUB)
					+ ")) b LEFT JOIN Scheduling2 c ON b.poiId_to = c.poiId";
			// county
			if (counties != null && ! counties.isEmpty()) {
				sql += " WHERE countyId IN (";
				boolean isFirst = true;
				for (Integer i : counties) {
					if (isFirst) {
						sql += "'TW" + i + "'";
						isFirst = false;
					} else
						sql += ", 'TW" + i + "'";
				}
				sql += ")";
			} else
				sql += " WHERE 1";
			
			// isMustOpen
			for (int i = 0; i < 7; ++i) {
				if (isMustOpen[i])
					sql += " AND (OH_" + daysOfWeekName[i] + " IS NULL OR OH_" + daysOfWeekName[i] + " != 'close')";
			}
			
			sql += " ORDER BY " + orderBy + " LIMIT " + numSelectionUB;
			
			if (isDisplay)
				System.out.println("[SQL] " + sql);
			
			queryResult = analyticsjdbc.queryForList(sql);
			if (isDisplay)
				System.out.println("[count] " + queryResult.size());
			
			if (queryResult.size() >= numSelectionLB || distanceUBIncrement == 0 || distanceUB > distanceUBForTermination)
				break;
			else
				distanceUB += distanceUBIncrement;
		}
		
		List<Poi> poiList = new ArrayList<Poi>();
		for (int i = 0; i < queryResult.size(); ++i) {
			Poi poi = new Poi();
			poi.setFromQueryResult(queryResult.get(i), looseType);
			poi.setMustPoi(false);
			poiList.add(poi);
		}
		return poiList;
	}
	private String getPartialSqlWithCenterPoiId(String poiId, double distanceLB, double distanceUB) {
		String result = "(poiId_from = '" + poiId + "'";		
		if (distanceLB > 0)
			result = result + " AND distance >= " + distanceLB;
		if (distanceUB > 0)
			result = result + " AND distance <= " + distanceUB;
		result = result + ")";
		return result;
	}
	public List<Poi> getRandomPoiListByCenterPoiIdAndCountyAndOpenDay(int numSelectionLB, int numSelectionUB,
			String centerPoiId, double distanceLB, double distanceUB, double distanceUBIncrement,
			Set<Integer> counties, int mustOpenDay, String orderBy, int looseType) {
		boolean[] isMustOpen = new boolean[7];
		isMustOpen[mustOpenDay] = true;
		return getRandomPoiListByCenterPoiIdAndCountyAndOpenDay(numSelectionLB, numSelectionUB,
				centerPoiId, distanceLB, distanceUB, distanceUBIncrement,
				counties, isMustOpen, orderBy, looseType);
	}
	
	//============================================
	//    get nearby poiId / POI by coordinate
	//============================================
	public String getNearbyPoiIdByCoordinate(double[] coordinate) {
		return getNearbyPoiIdByCoordinate(coordinate[0], coordinate[1]);
	}
	public String getNearbyPoiIdByCoordinate(double latitude, double longitude) {
		List<Map<String, Object>> queryResult = getNearbyQueryResultByCoordinate(latitude, longitude);
		if (queryResult != null)
			return queryResult.get(0).get("poiId").toString();
		else
			return null;
	}
	public Poi getNearbyPoiByCoordinate(double latitude, double longitude, boolean isMustPoi, int looseType) {
		List<Map<String, Object>> queryResult = getNearbyQueryResultByCoordinate(latitude, longitude);
		if (queryResult != null) {
			Poi poi = new Poi();
			poi.setFromQueryResult(queryResult.get(0), looseType);
			poi.setMustPoi(isMustPoi);
			return poi;
		} else
			return null;
	}
	private List<Map<String, Object>> getNearbyQueryResultByCoordinate(double latitude, double longitude) {
		
		final double latitudeLB = 20.9; //21.8976
		final double latitudeUB = 27.4; //26.3813
		final double longitudeLB = 117.2; //118.21799
		final double longitudeUB = 123.1; //122.1056
		
		if (latitude < latitudeLB || latitude > latitudeUB || longitude < longitudeLB || longitude > longitudeUB) {
			System.err.println("[!!! Error !!!][PoiQuery getNearbyPoiIdByCoordinate] latitude/longitude out of bound!");
			return null;
		}
		
		double tolerance = 0.01;
		double toleranceIncrement = 0.01;
		while (true) {
			String sql = "SELECT * FROM Scheduling2 WHERE"
					+ " latitude >= " + (latitude - tolerance) + " AND latitude <= " + (latitude + tolerance)
					+ " AND longitude >= " + (longitude - tolerance) + " AND longitude <= " + (longitude + tolerance)
					+ " ORDER BY RAND() LIMIT 1;";
			if (isDisplay)
				System.out.println("[SQL] " + sql);
			
			List<Map<String, Object>> queryResult = analyticsjdbc.queryForList(sql);
			if (isDisplay)
				System.out.println("[count] " + queryResult.size());
			
			if (! queryResult.isEmpty())
				return queryResult;
			else if (tolerance >= 8) {
				System.err.println("[!!! Error !!!][PoiQuery getNearbyPoiIdByCoordinate] tolerance exploded!");
				return null;
			}
			else
				tolerance += toleranceIncrement;
		}
	}
	
	//============================================
	//    get centered poiId / POI by POI list
	//============================================
	public String getCenteredPoiIdByPoiList(List<Poi> poiList) {
		if (poiList == null || poiList.isEmpty())
			return null;
		else if (poiList.size() == 1)
			return poiList.get(0).getPoiId();
		else {
			double[] centeredCoordinate = Poi.calculatePoiCenter(poiList);
			return getNearbyPoiIdByCoordinate( centeredCoordinate[0], centeredCoordinate[1] );
		}
	}
	public Poi getCenteredPoiByPoiList(List<Poi> poiList, int looseType) {
		if (poiList == null || poiList.isEmpty())
			return null;
		else if (poiList.size() == 1)
			return poiList.get(0);
		else {
			double[] centeredCoordinate = Poi.calculatePoiCenter(poiList);
			return getNearbyPoiByCoordinate( centeredCoordinate[0], centeredCoordinate[1], false, looseType);
		}
	}
	
	//======================================
	//    get pairwise distance and time
	//======================================
	public void getDistanceAndTimeMatrix(List<String> poiIdList, double[][] distance, int[][] time) {
		int numPoi = poiIdList.size();
		
		// get poiSet string to be used in sql
		String poiSet = "";
		boolean isFirst = true;
		for (int i = 0; i < numPoi; ++i) {
			if (isFirst) {
				poiSet += "'" + poiIdList.get(i) + "'";
				isFirst = false;
			} else {
				poiSet += ", '" + poiIdList.get(i) + "'";
			}
		}
		
		String sql = "SELECT * FROM Distance WHERE poiId_from IN (" + poiSet + ")"
				+ " AND poiId_to IN (" + poiSet + ") ";
		if (isDisplay)
			System.out.println("[SQL] " + sql);
		
		List<Map<String, Object>> queryResult = analyticsjdbc.queryForList(sql);
		if (isDisplay)
			System.out.println("[count] " + queryResult.size());
		
		for (int i = 0; i < queryResult.size(); ++i) {
			int j = poiIdList.indexOf( queryResult.get(i).get("poiId_from").toString() );
			int k = poiIdList.indexOf( queryResult.get(i).get("poiId_to").toString() );
			if (distance != null)
				distance[j][k] = Double.parseDouble( queryResult.get(i).get("distance").toString() );
			if (time != null)
				time[j][k] = Integer.parseInt( queryResult.get(i).get("time").toString() );
		}
	}
	
}

