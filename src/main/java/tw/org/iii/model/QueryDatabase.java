package tw.org.iii.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

public class QueryDatabase {
	
	public static final String[] daysOfWeekName = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
	
	private JdbcTemplate analyticsJdbc;
	private JdbcTemplate stJdbc;
	
	private boolean isDisplay;
	
	public QueryDatabase(JdbcTemplate analyticsJdbc, JdbcTemplate stJdbc, boolean isDisplay) {
		this.analyticsJdbc = analyticsJdbc;
		this.stJdbc = stJdbc;
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
		
		List<String> poiIdList = new ArrayList<String>();
		poiIdList.add(poiId);
		List<Poi> poiList = getPoiListByPoiIdList(poiIdList, isMustPoi, looseType);
		return poiList.get(0);
	}
	
	public List<Poi> getPoiListByPoiIdList(List<String> poiIdList, boolean isMustPoi, int looseType) {
		String sql = "SELECT * FROM Scheduling2 WHERE poiId = '" + poiIdList.get(0) + "'";
		for (int i = 1; i < poiIdList.size(); ++i)
			sql += " OR poiId = '" + poiIdList.get(i) + "'";
		if (isDisplay)
			System.out.println("[SQL] " + sql);
		
		List<Map<String, Object>> queryResult = analyticsJdbc.queryForList(sql);
		if (isDisplay)
			System.out.println("[count] " + queryResult.size());
		
		List<Poi> poiList = new ArrayList<Poi>();
		for (int i = 0; i < queryResult.size(); ++i) {
			Poi poi = new Poi();
			poi.setFromQueryResult(queryResult.get(i), looseType);
			poi.setMustPoi(isMustPoi);
			poiList.add(poi);
		}
		
		// if there is any POI id which is not in database "analytics.Scheduling2", then try to find it in "ST_V3_ZH_TW.PoiFinalView2"
		List<String> poiIdListInST;
		if (poiList.size() < poiIdList.size()) {
			poiIdListInST = new ArrayList<String>();
			
			// get missing poiId
			for (String poiId : poiIdList) {
				boolean isFound = false;
				for (Poi poi : poiList) {
					if (poiId.equals( poi.getPoiId() )) {
						isFound = true;
						break;
					}
				}
				if (! isFound) {
					poiIdListInST.add(poiId);
					if (poiIdListInST.size() == poiIdList.size() - poiList.size())
						break;
				}
			}
			
			// query
			for (String poiId : poiIdListInST) {
				String sql2 = "SELECT id AS poiId, ASTEXT(location) AS location, countyId, name, checkinTotal, stayTime, themeId FROM PoiFinalView2"
						+ " WHERE id = '" + poiId + "'";
				if (isDisplay)
					System.out.println("[SQL] " + sql2);
				
				List<Map<String, Object>> queryResult2 = stJdbc.queryForList(sql2);
				if (isDisplay)
					System.out.println("[count] " + queryResult2.size());
				
				if (queryResult2.size() > 0) {
					Poi poi = new Poi();
					poi.setFromQueryResult_ST(queryResult2, looseType);
					poi.setMustPoi(isMustPoi);
					poiList.add(poi);
				}
			}
		}
		
		return poiList;
	}
	
	//===========================================================
	//    get random POI or POI list by (1)county (2)open day
	//===========================================================
	private List<Poi> getRandomPoiListByCountyAndOpenDay(int numSelection, Set<Integer> counties,
								boolean[] isMustOpen, int looseType, boolean isBackbonePoi) {
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
		
		// isBackbonePoi
		if (isBackbonePoi)
			sql += " AND distanceBackbone = 1";
		
		sql += " ORDER BY RAND() LIMIT " + numSelection;
		
		if (isDisplay)
			System.out.println("[SQL] " + sql);
		
		List<Map<String, Object>> queryResult = analyticsJdbc.queryForList(sql);
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
	public List<Poi> getRandomPoiListByCountyAndOpenDay(int numSelection, Set<Integer> counties,
								int mustOpenDay, int looseType, boolean isBackbonePoi) {
		boolean[] isMustOpen = new boolean[7];
		isMustOpen[mustOpenDay] = true;
		return getRandomPoiListByCountyAndOpenDay(numSelection, counties, isMustOpen, looseType, isBackbonePoi);
	}
	public List<Poi> getRandomPoiListByCountyAndOpenDay(int numSelection, Set<Integer> counties,
								String mustOpenDay, int looseType, boolean isBackbonePoi) {
		if (mustOpenDay.equals("allOpen")) {
			boolean[] isMustOpen = new boolean[7];
			Arrays.fill(isMustOpen, true);
			return getRandomPoiListByCountyAndOpenDay(numSelection, counties, isMustOpen, looseType, isBackbonePoi);
		}
		else if (mustOpenDay.equals("free")) {
			boolean[] isMustOpen = new boolean[7];
			return getRandomPoiListByCountyAndOpenDay(numSelection, counties, isMustOpen, looseType, isBackbonePoi);
		} else
			return null;
	}
	
	public Poi getRandomPoiByCountyAndOpenDay(String countyId, int mustOpenDay, int looseType, boolean isBackbonePoi) {
		Set<Integer> counties = new HashSet<Integer>();
		counties.add( County.getCountyIndex(countyId) );
		
		boolean[] isMustOpen = new boolean[7];
		isMustOpen[mustOpenDay] = true;
		
		List<Poi> poiList = getRandomPoiListByCountyAndOpenDay(1, counties, isMustOpen, looseType, isBackbonePoi);
		if (poiList == null || poiList.isEmpty())
			return null;
		else
			return poiList.get(0);
	}
	
	//========================================================================================
	//    get (random) POI list by (1)center poiId (2)county (not necessarily) (3)open day
	//    --use Table Distance, select within a circle measured by time
	//========================================================================================
	private List<Poi> getRandomPoiListByCenterPoiIdAndCountyAndOpenDay(int numSelectionLB, int numSelectionUB,
			String centerPoiId, int timeLB, int timeUB, int timeUBIncrement, int timeUBTermination,
			Set<Integer> counties, boolean[] isMustOpen, String orderBy, int looseType) {
		//usage:	enter timeLB <= 0 if not used
		//		enter timeUB <= 0 if not used  (at least one of timeLB/timeUB has to be > 0)
		//		enter timeUBIncrement = 0 if not used
		//		counties is allowed to be null or empty
		if (timeLB <= 0 && timeUB <= 0) {
			System.err.println("[!!! Error !!!][PoiQuery getRandomPoiListByCenterPoiIdAndCountyAndOpenDay] timeLB and timeUB are both <= 0 !");
			return null;
		}
		
		List<Map<String, Object>> queryResult;
		while (true) {
			String sql = "SELECT c.*, time AS timeMeasureToFixedPoi FROM "
					+ "(SELECT poiId_to, time FROM Distance WHERE ("
					+ getPartialSqlWithCenterPoiId(centerPoiId, timeLB, timeUB)
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
			
			queryResult = analyticsJdbc.queryForList(sql);
			if (isDisplay)
				System.out.println("[count] " + queryResult.size());
			
			if (queryResult.size() >= numSelectionLB || timeUBIncrement == 0 || timeUB > timeUBTermination)
				break;
			else
				timeUB += timeUBIncrement;
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
	private String getPartialSqlWithCenterPoiId(String poiId, int timeLB, int timeUB) {
		String result = "(poiId_from = '" + poiId + "'";		
		if (timeLB > 0)
			result = result + " AND time >= " + timeLB;
		if (timeUB > 0)
			result = result + " AND time <= " + timeUB;
		result = result + ")";
		return result;
	}
	public List<Poi> getRandomPoiListByCenterPoiIdAndCountyAndOpenDay(int numSelectionLB, int numSelectionUB,
			String centerPoiId, int timeLB, int timeUB, int timeUBIncrement, int timeUBTermination,
			Set<Integer> counties, int mustOpenDay, String orderBy, int looseType) {
		boolean[] isMustOpen = new boolean[7];
		isMustOpen[mustOpenDay] = true;
		return getRandomPoiListByCenterPoiIdAndCountyAndOpenDay(numSelectionLB, numSelectionUB,
				centerPoiId, timeLB, timeUB, timeUBIncrement, timeUBTermination,
				counties, isMustOpen, orderBy, looseType);
	}
	
	//========================================================================================
	//    get (random) POI list by (1)center poiId (2)county (not necessarily) (3)open day
	//    --use Table Scheduling2, select within a square
	//========================================================================================
	private List<Poi> getRandomPoiListByCenterPoiIdAndCountyAndOpenDayWithinSquare(int numSelectionLB, int numSelectionUB,
			Poi centerPoi, double deltaKm, double deltaKmIncrement, double deltaKmTermination,
			Set<Integer> counties, boolean[] isMustOpen, String orderBy, int looseType, boolean isBackbonePoi) {
		
		double latitude = centerPoi.getLatitude();
		double longitude = centerPoi.getLongitude();
		
		double kmPerLatitudeDegree = Poi.getGreatCircleDistance(latitude - 0.5, longitude, latitude + 0.5, longitude);
		double kmPerLongitudeDegree = Poi.getGreatCircleDistance(latitude, longitude - 0.5, latitude, longitude + 0.5);
		
		List<Map<String, Object>> queryResult;
		while (true) {
			String sql = "SELECT * FROM Scheduling2 WHERE"
					+ " latitude >= " + (latitude - deltaKm / kmPerLatitudeDegree)
					+ " AND latitude <= " + (latitude + deltaKm / kmPerLatitudeDegree)
					+ " AND longitude >= " + (longitude - deltaKm / kmPerLongitudeDegree)
					+ " AND longitude <= " + (longitude + deltaKm / kmPerLongitudeDegree);
			
			// county
			if (counties != null && ! counties.isEmpty()) {
				sql += " AND countyId IN (";
				boolean isFirst = true;
				for (Integer i : counties) {
					if (isFirst) {
						sql += "'TW" + i + "'";
						isFirst = false;
					} else
						sql += ", 'TW" + i + "'";
				}
				sql += ")";
			}
			
			// isMustOpen
			for (int i = 0; i < 7; ++i) {
				if (isMustOpen[i])
					sql += " AND (OH_" + daysOfWeekName[i] + " IS NULL OR OH_" + daysOfWeekName[i] + " != 'close')";
			}
			
			// isBackbonePoi
			if (isBackbonePoi)
				sql += " AND distanceBackbone = 1";
			
			sql += " ORDER BY " + orderBy + " LIMIT " + numSelectionUB;
			
			if (isDisplay)
				System.out.println("[SQL] " + sql);
			
			queryResult = analyticsJdbc.queryForList(sql);
			if (isDisplay)
				System.out.println("[count] " + queryResult.size());
			
			if (queryResult.size() >= numSelectionLB || deltaKm > deltaKmTermination)
				break;
			else
				deltaKm += deltaKmIncrement;
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
	public List<Poi> getRandomPoiListByCenterPoiIdAndCountyAndOpenDayWithinSquare(int numSelectionLB, int numSelectionUB,
			Poi centerPoi, double deltaKm, double deltaKmIncrement, double deltaKmTermination,
			Set<Integer> counties, int mustOpenDay, String orderBy, int looseType, boolean isBackbonePoi) {
		boolean[] isMustOpen = new boolean[7];
		isMustOpen[mustOpenDay] = true;
		return getRandomPoiListByCenterPoiIdAndCountyAndOpenDayWithinSquare(numSelectionLB, numSelectionUB,
				centerPoi, deltaKm, deltaKmIncrement, deltaKmTermination,
				counties, isMustOpen, orderBy, looseType, isBackbonePoi);
	}
	
	//============================================
	//    get nearby poiId / POI by coordinate
	//============================================
	public String getNearbyPoiIdByCoordinate(double[] coordinate, boolean isBackbonePoi) {
		return getNearbyPoiIdByCoordinate(coordinate[0], coordinate[1], isBackbonePoi);
	}
	public String getNearbyPoiIdByCoordinate(double latitude, double longitude, boolean isBackbonePoi) {
		List<Map<String, Object>> queryResult = getNearbyQueryResultByCoordinate(latitude, longitude, isBackbonePoi);
		if (queryResult != null)
			return queryResult.get(0).get("poiId").toString();
		else
			return null;
	}
	public Poi getNearbyPoiByCoordinate(double latitude, double longitude, boolean isMustPoi, int looseType, boolean isBackbonePoi) {
		List<Map<String, Object>> queryResult = getNearbyQueryResultByCoordinate(latitude, longitude, isBackbonePoi);
		if (queryResult != null) {
			Poi poi = new Poi();
			poi.setFromQueryResult(queryResult.get(0), looseType);
			poi.setMustPoi(isMustPoi);
			return poi;
		} else
			return null;
	}
	public Poi getNearbyPoiByCoordinate(double[] coordinate, boolean isMustPoi, int looseType, boolean isBackbonePoi) {
		List<Map<String, Object>> queryResult = getNearbyQueryResultByCoordinate(coordinate[0], coordinate[1], isBackbonePoi);
		if (queryResult != null) {
			Poi poi = new Poi();
			poi.setFromQueryResult(queryResult.get(0), looseType);
			poi.setMustPoi(isMustPoi);
			return poi;
		} else
			return null;
	}
	private List<Map<String, Object>> getNearbyQueryResultByCoordinate(double latitude, double longitude, boolean isBackbonePoi) {
		
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
					+ " AND longitude >= " + (longitude - tolerance) + " AND longitude <= " + (longitude + tolerance);
			if (isBackbonePoi)
				sql += " AND distanceBackbone = 1";
			sql += " ORDER BY RAND() LIMIT 1;";
			if (isDisplay)
				System.out.println("[SQL] " + sql);
			
			List<Map<String, Object>> queryResult = analyticsJdbc.queryForList(sql);
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
			return getNearbyPoiIdByCoordinate( centeredCoordinate[0], centeredCoordinate[1], false);
		}
	}
	public Poi getCenteredPoiByPoiList(List<Poi> poiList, int looseType) {
		if (poiList == null || poiList.isEmpty())
			return null;
		else if (poiList.size() == 1)
			return poiList.get(0);
		else {
			return getNearbyPoiByCoordinate( Poi.calculatePoiCenter(poiList), false, looseType, true);
		}
	}
	
	//======================================
	//    get pairwise distance and time
	//======================================
	public void getDistanceAndTimeMatrix(List<Poi> rawPoiList, double[][] distance, int[][] time) {
		
		int numPoi = rawPoiList.size();
		List<Poi> backbonePoiList = new ArrayList<Poi>();
		List<Integer> poiIndexNotBackbone = new ArrayList<Integer>(); //i.e. POI with source 2 or 3
		
		for (int i = 0; i < numPoi; ++i) {
			if (rawPoiList.get(i).isBackbonePoi())
				backbonePoiList.add( rawPoiList.get(i) );
			else {
				Poi poi = getNearbyPoiByCoordinate(rawPoiList.get(i).getCoordinate(), false, 0, true);
				backbonePoiList.add(poi);
				
				poiIndexNotBackbone.add(i);
			}
		}
		
		// get poiSet string to be used in sql
		String poiSet = "";
		boolean isFirst = true;
		for (int i = 0; i < numPoi; ++i) {
			if (isFirst) {
				poiSet += "'" + backbonePoiList.get(i).getPoiId() + "'";
				isFirst = false;
			} else {
				poiSet += ", '" + backbonePoiList.get(i).getPoiId() + "'";
			}
		}
		
		String sql = "SELECT poiId_from, poiId_to, distance, time FROM Distance WHERE poiId_from IN (" + poiSet + ")"
				+ " AND poiId_to IN (" + poiSet + ")";
		if (isDisplay)
			System.out.println("[SQL] " + sql);
		
		List<Map<String, Object>> queryResult = analyticsJdbc.queryForList(sql);
		if (isDisplay)
			System.out.println("[count] " + queryResult.size());
		
		// there are cases that some POI in poiList are duplicated: (1)startPoi=endPoi, (2)cases involve interpolated POI
		// poiIdIndexMap is used to deal with these cases.
		Map<String, List<Integer>> poiIdIndexMap = new HashMap<String, List<Integer>>();
		for (int i = 0; i < numPoi; ++i) {
			if (! poiIdIndexMap.containsKey( backbonePoiList.get(i).getPoiId() ) ) {
				List<Integer> temp = new ArrayList<Integer>();
				temp.add(i);
				poiIdIndexMap.put( backbonePoiList.get(i).getPoiId(), temp );
			} else {
				poiIdIndexMap.get( backbonePoiList.get(i).getPoiId() ).add(i);
			}
		}
		for (int i = 0; i < queryResult.size(); ++i) {
			List<Integer> js = poiIdIndexMap.get( queryResult.get(i).get("poiId_from").toString() );
			List<Integer> ks = poiIdIndexMap.get( queryResult.get(i).get("poiId_to").toString() );
			for (int j : js) {
				for (int k : ks) {
					if (distance != null)
						distance[j][k] = Double.parseDouble( queryResult.get(i).get("distance").toString() );
					if (time != null)
						time[j][k] = Integer.parseInt( queryResult.get(i).get("time").toString() );
				}
			}
		}
		
		// for POI not in backbone
		for (int k = 0; k < poiIndexNotBackbone.size(); ++k) {
			
			int i = poiIndexNotBackbone.get(k);
			
			double[] circleDistance_raw = Poi.getCircleDistanceArray(rawPoiList.get(i), rawPoiList);
			double[] circleDistance_backbone = Poi.getCircleDistanceArray(backbonePoiList.get(i), backbonePoiList);
			for (int j = 0; j < numPoi; ++j) {
				
				if (circleDistance_backbone[j] > 0) {
					
					double ratio = circleDistance_raw[j] / circleDistance_backbone[j];
					
					if (distance != null) {
						distance[i][j] = distance[i][j] * ratio;
						distance[j][i] = distance[j][i] * ratio;
					}
					if (time != null) {
						time[i][j] = (int)(time[i][j] * ratio);
						time[j][i] = (int)(time[j][i] * ratio);
					}
					
				} else if (circleDistance_backbone[j] == 0 && j != i) {
					// if accidentally the nearby backbone POI is another backbone POI (either raw or nearby)
					// then use the nearby-POI fitting formula for now
					
					if (distance != null) {
						distance[i][j] = DistanceAndTime.getEstimatedDistanceOfNearbyPoi(circleDistance_raw[j]);
						distance[j][i] = distance[i][j];
					}
					if (time != null) {
						time[i][j] = DistanceAndTime.getEstimatedTimeOfNearbyPoi(circleDistance_raw[j]);
						time[j][i] = time[i][j];
					}
					
//					// county-pair fitting formula
//					DistanceAndTime.getFittingDistanceAndTime( rawPoiList.get(i), rawPoiList.get(j), distanceOfPoiPair, timeOfPoiPair);
//					if (distance != null) {
//						distance[i][j] = distanceOfPoiPair[0];
//						distance[j][i] = distanceOfPoiPair[0];
//					}
//					if (time != null) {
//						time[i][j] = timeOfPoiPair[0];
//						time[j][i] = timeOfPoiPair[0];
//					}
				}
			}
		}
	}
	public int getTimeBetweenPoi(Poi poi1, Poi poi2) {
		List<Poi> poiList = new ArrayList<Poi>();
		poiList.add(poi1);
		poiList.add(poi2);
		int[][] time = new int[2][2];
		getDistanceAndTimeMatrix(poiList, null, time);
		return time[0][1];
	}
}

