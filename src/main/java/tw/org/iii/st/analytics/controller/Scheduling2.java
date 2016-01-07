package tw.org.iii.st.analytics.controller;

import java.io.IOException;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import tw.org.iii.model.Poi;
import tw.org.iii.model.TourEvent;
import tw.org.iii.model.SchedulingInput;
import tw.org.iii.model.DailyInfo;
import tw.org.iii.model.GeoPoint;
import tw.org.iii.model.County;

@RestController
@RequestMapping("/Scheduling2")
public class Scheduling2 {
	
	@Autowired
	@Qualifier("analyticsJdbcTemplate")
	private JdbcTemplate analyticsjdbc;
	
	//---- Parameters ----//
	private static int startTimeEachDayInMinutes = 8 * 60;
	private static int endTimeEachDayInMinutes = 13 * 60;
	private static int fixedNumCandidatePoi = 5;
	
	private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
	
	private static final String[] daysOfWeekName = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
	
	//---------------------------------------------
	//   API main function: creating itinerary
	//---------------------------------------------
	@RequestMapping("/QuickPlan")
	private @ResponseBody
	List<TourEvent> createItinerary(@RequestBody SchedulingInput input) throws Exception {
		System.out.println("Program begin!");
		
		//--------------------
		//   preparing data
		//--------------------
		List<TourEvent> fullItinerary = new ArrayList<TourEvent>();
		List<Poi> fullItineraryPoi = new ArrayList<Poi>();
		
		long secondsDifference = (input.getEndTime().getTime() - input.getStartTime().getTime()) / 1000;
		int numDay = (int)(secondsDifference / 86400) + 1; 
		
		Calendar itineraryStartCalendar = Calendar.getInstance();
		itineraryStartCalendar.setTime(input.getStartTime());
		
		Calendar itineraryEndCalendar = Calendar.getInstance();
		itineraryEndCalendar.setTime(input.getEndTime());
		
		List<String> themeList = input.getPreferenceList();
		
		//starting location
		GeoPoint gps = input.getGps();
		String itineraryStartPoiId = null;
		if (gps != null)
			itineraryStartPoiId = searchNearbyPoiIdByCoordinate(gps.getLat(), gps.getLng()); //could be null
		if (itineraryStartPoiId == null)
			itineraryStartPoiId = "4_02_0000_0005";//中山北路條通商圈 4_02_0000_0005 (25.05035, 121.5249)
													//near 台北車站(25.0478, 121.5172)
		List<Map<String, Object>> queryResultInit = analyticsjdbc.queryForList("SELECT * FROM Scheduling2 WHERE poiId = '" + itineraryStartPoiId + "';");
		Poi itineraryStartPoi = new Poi();
		itineraryStartPoi.setFromQueryResult(queryResultInit.get(0), daysOfWeekName[itineraryStartCalendar.get(Calendar.DAY_OF_WEEK) - 1]); //0:Sun, 1:Mon, ..., 6:Sat
		itineraryStartPoi.setMustPoi(false);
		itineraryStartPoi.setInMustCounty(false);
		
		List<String> mustPoiIdList = input.getMustPoiList();
		
		int looseType = input.getLooseType();
		
		
		//--------------------------------------------
		//  initializing daily itinerary information
		//--------------------------------------------
		boolean isAnyMustCounty = false;
		List<DailyInfo> fullInfo = new ArrayList<DailyInfo>();
		if (numDay == 1) { //包含 "今日行程" 及 "多日行程-一天"
			
			int startTimeInMinutes = itineraryStartCalendar.get(Calendar.HOUR_OF_DAY) * 60 + itineraryStartCalendar.get(Calendar.MINUTE);
			int endTimeInMinutes = itineraryEndCalendar.get(Calendar.HOUR_OF_DAY) * 60 + itineraryEndCalendar.get(Calendar.MINUTE);
			
			DailyInfo dailyInfo = new DailyInfo();
			
			dailyInfo.setCalendarForDay((Calendar)itineraryStartCalendar.clone());
			dailyInfo.setDayOfWeek(dailyInfo.getCalendarForDay().get(Calendar.DAY_OF_WEEK) - 1); //0:Sun, 1:Mon, ..., 6:Sat
			dailyInfo.setStartTimeInMinutes(startTimeInMinutes);
			dailyInfo.setEndTimeInMinutes(endTimeInMinutes);
			dailyInfo.setMustCounty(input.getCityList().get(0));
			if (! input.getCityList().get(0).equals("all"))
				isAnyMustCounty = true;
			dailyInfo.setStartPoi(itineraryStartPoi);
			
			fullInfo.add(dailyInfo);
		}
		else { //包含 "多日行程-兩天(含)以上"
			for (int i = 0; i < numDay; ++i) {
				DailyInfo dailyInfo = new DailyInfo();
				
				dailyInfo.setCalendarForDay((Calendar)itineraryStartCalendar.clone());
				dailyInfo.getCalendarForDay().add(Calendar.DAY_OF_MONTH, i);
				dailyInfo.setDayOfWeek(dailyInfo.getCalendarForDay().get(Calendar.DAY_OF_WEEK) - 1); //0:Sun, 1:Mon, ..., 6:Sat
				dailyInfo.setStartTimeInMinutes(startTimeEachDayInMinutes);
				dailyInfo.setEndTimeInMinutes(endTimeEachDayInMinutes);
				dailyInfo.setMustCounty(input.getCityList().get(i));
				if (! input.getCityList().get(i).equals("all"))
					isAnyMustCounty = true;
				if (i == 0)
					dailyInfo.setStartPoi(itineraryStartPoi);
				else
					dailyInfo.setStartPoi(null);
				
				fullInfo.add(dailyInfo);
			}
		}
		
		//HashSet<String> selectedPoiInStage1 = new HashSet<String>();
		
		//-------------------------------------------------------------
		// stage 1: assigning poi to each day (must poi or seed poi)
		//          four cases
		//-------------------------------------------------------------
		if (! isAnyMustCounty && mustPoiIdList.isEmpty()) {
			//------------------------------------------
			// stage 1, case A： 無必去縣市、無必去景點
			//------------------------------------------
			List<String> selectedPoiId = new ArrayList<String>();
			
			for (int i = 0; i < numDay; ++i) {
				DailyInfo dailyInfo = fullInfo.get(i);
				String weekOfDay = daysOfWeekName[dailyInfo.getDayOfWeek()];
				
				List<Map<String, Object>> queryResult;
				
				//get this day's seed POI from database
				if (selectedPoiId.size() == 0) {
					while (true) {
						String sql = "SELECT c.* FROM ";
						sql += "(SELECT poiId_to FROM GreatCircleDistanceForScheduling2 WHERE ("
								+ getPartialSqlWithDistance(itineraryStartPoi.getPoiId(), null, "10")
								+ ")) b";
						sql += " LEFT JOIN Scheduling2 c ON b.poiId_to = c.poiId";
						sql += " WHERE OH_" + weekOfDay + " IS NULL OR OH_" + weekOfDay + " != 'close' ORDER BY RAND() LIMIT 1;";
						
						
						//String sql = "SELECT c.* FROM Scheduling2 c";	
						//sql += " WHERE OH_" + weekOfDay + " IS NULL OR OH_" + weekOfDay + " != 'close' ORDER BY RAND() LIMIT 1;";
						
						System.out.println("[1A-nearStartPoint] " + sql);
						queryResult = analyticsjdbc.queryForList(sql);
						System.out.println(queryResult.size());
						
						if (queryResult.size() > 0)
							break;
						else
							;
					}
					
				} else {
					double distanceLB = 15;
					double distanceUB = 40;
					
					while (true) {
						String sql = "SELECT c.* FROM ";
						if (selectedPoiId.size() == 1) {
							sql += "(SELECT poiId_to FROM GreatCircleDistanceForScheduling2 WHERE ("
									+ getPartialSqlWithDistance(selectedPoiId.get(0), String.valueOf(distanceLB), String.valueOf(distanceUB))
									+ ")) b";										

						} else {
							sql += "(SELECT poiId_to FROM (SELECT poiId_to FROM GreatCircleDistanceForScheduling2 WHERE (";
							int jj = selectedPoiId.size() - 1;
							sql += getPartialSqlWithDistance(selectedPoiId.get(jj), String.valueOf(distanceLB), String.valueOf(distanceUB));
							
							for (int j = selectedPoiId.size() - 2; j >= 0; --j)
								sql += " OR " + getPartialSqlWithDistance(selectedPoiId.get(j), String.valueOf(distanceLB), null);
							
							sql += ")) a GROUP BY poiId_to HAVING COUNT(*) >= " + selectedPoiId.size() + ") b";
						}
						sql += " LEFT JOIN Scheduling2 c ON b.poiId_to = c.poiId";
						sql += " WHERE OH_" + weekOfDay + " IS NULL OR OH_" + weekOfDay + " != 'close' ORDER BY RAND() LIMIT 1;";
						
						System.out.println("[1A-laterPoints] " + sql);
						queryResult = analyticsjdbc.queryForList(sql);
						System.out.println(queryResult.size());
						
						if (queryResult.size() > 0)
							break;
						else
							distanceUB += 10;
					}
				}
				
				//insert seed POI into POI list
				Poi seedPoi = new Poi();
				seedPoi.setFromQueryResult(queryResult.get(0), weekOfDay);
				seedPoi.setMustPoi(false);
				seedPoi.setInMustCounty(false);
				
				List<Poi> poiList = new ArrayList<Poi>();
				poiList.add(seedPoi);
				dailyInfo.setPoiList(poiList);
				
				selectedPoiId.add(seedPoi.getPoiId());
			}
		} else if (isAnyMustCounty && mustPoiIdList.isEmpty()) {
			//------------------------------------------
			// stage 1, case B： 有必去縣市、無必去景點
			//------------------------------------------
			List<String> selectedPoiId = new ArrayList<String>();
			
			Poi endPointPoi1 = null;
			Poi endPointPoi2 = null;
			List<Integer> dayIndicesWithoutMustCounty = new ArrayList<Integer>();
			
			for (int i = 0; i < numDay; ++i) {
				DailyInfo dailyInfo = fullInfo.get(i);
				
				if (dailyInfo.getMustCounty().equals("all")) {
					if (dayIndicesWithoutMustCounty.isEmpty()) {
						if (i == 0)
							endPointPoi1 = dailyInfo.getStartPoi();
						else
							endPointPoi1 = fullInfo.get(i - 1).getPoiList().get(0);
					}
					dayIndicesWithoutMustCounty.add(i);
					
				} else {
					//select seed POI from database for this day
					List<Map<String, Object>> queryResult;
					String weekOfDay = daysOfWeekName[dailyInfo.getDayOfWeek()];
					
					while (true) {
						String sql = "SELECT * FROM Scheduling2 WHERE countyId = '" + dailyInfo.getMustCounty()
									+ "' AND (OH_" + weekOfDay + " IS NULL OR OH_" + weekOfDay + " != 'close') ORDER BY RAND() LIMIT 1;";
						System.out.println("[1B-inCounty] " + sql);
						queryResult = analyticsjdbc.queryForList(sql);
						System.out.println(queryResult.size());
						
						if (queryResult.size() > 0)
							break;
						else
							System.err.println("Error in searching POI in a county!");
					}
					
					//insert seed POI into POI list
					Poi seedPoi = new Poi();
					seedPoi.setFromQueryResult(queryResult.get(0), weekOfDay);
					seedPoi.setMustPoi(false);
					seedPoi.setInMustCounty(true);
					
					List<Poi> poiList = new ArrayList<Poi>();
					poiList.add(seedPoi);
					dailyInfo.setPoiList(poiList);
					
					selectedPoiId.add(seedPoi.getPoiId());
					
					if (! dayIndicesWithoutMustCounty.isEmpty()) {
						endPointPoi2 = seedPoi;
						//interpolation: (use endPointPoi1, endPointPoi2, dayIndicesWithoutMustCounty)
						double[] coordinate1 = parseCoordinate(endPointPoi1.getLocation());
						double[] coordinate2 = parseCoordinate(endPointPoi2.getLocation());
						
						for (int j = 0; j < dayIndicesWithoutMustCounty.size(); ++j) {
							
							DailyInfo dailyInfo2 = fullInfo.get( dayIndicesWithoutMustCounty.get(j) );
							String weekOfDay2 = daysOfWeekName[dailyInfo2.getDayOfWeek()];
							
							double latitude = coordinate1[0] + (coordinate2[0] - coordinate1[0]) * (j + 1) / (dayIndicesWithoutMustCounty.size() + 1);
							double longitude = coordinate1[1] + (coordinate2[1] - coordinate1[1]) * (j + 1) / (dayIndicesWithoutMustCounty.size() + 1);
							String locationInterpolatedPoiId = searchNearbyPoiIdByCoordinate(latitude, longitude);
							
							//selecting candidate POIs from database
							double distanceUB = 10;
							while (true) {
								String sql = "SELECT c.* FROM "
										+ "(SELECT poiId_to FROM GreatCircleDistanceForScheduling2 WHERE ("
										+ getPartialSqlWithDistance(locationInterpolatedPoiId, null, String.valueOf(distanceUB))
										+ ")) b"
										+ " LEFT JOIN Scheduling2 c ON b.poiId_to = c.poiId"
										+ " WHERE OH_" + weekOfDay2 + " IS NULL OR OH_" + weekOfDay2 + " != 'close' ORDER BY RAND() LIMIT 1;";
								
								System.out.println("[1B-interpolation] " + sql);
								queryResult = analyticsjdbc.queryForList(sql);
								System.out.println(queryResult.size());
								
								if (queryResult.size() > 0)
									break;
								else
									distanceUB += 5;
							}
							//insert POI into POI list
							Poi poi = new Poi();
							poi.setFromQueryResult(queryResult.get(0), weekOfDay2);
							poi.setMustPoi(false);
							poi.setInMustCounty(false);
							
							List<Poi> poiList2 = new ArrayList<Poi>();
							poiList2.add(poi);
							dailyInfo2.setPoiList(poiList2);
							
							selectedPoiId.add(poi.getPoiId());							
						}
						
						//clear
						endPointPoi1 = null;
						endPointPoi2 = null;
						dayIndicesWithoutMustCounty.clear();
					}
				}
			}
			
			if (! dayIndicesWithoutMustCounty.isEmpty()) { //last segment

				for (int i = 0; i < dayIndicesWithoutMustCounty.size(); ++i) {
					
					DailyInfo dailyInfo2 = fullInfo.get( dayIndicesWithoutMustCounty.get(i) );
					String weekOfDay2 = daysOfWeekName[dailyInfo2.getDayOfWeek()];
					
					List<Map<String, Object>> queryResult;
					
					double distanceLB = 15;
					double distanceUB = 40;
					
					while (true) {
						String sql = "SELECT c.* FROM ";
						if (selectedPoiId.size() == 1) {
							sql += "(SELECT poiId_to FROM GreatCircleDistanceForScheduling2 WHERE ("
									+ getPartialSqlWithDistance(selectedPoiId.get(0), String.valueOf(distanceLB), String.valueOf(distanceUB))
									+ ")) b";										

						} else if (i == 0) { //use endPointPoi1
							sql += "(SELECT poiId_to FROM (SELECT poiId_to FROM GreatCircleDistanceForScheduling2 WHERE (";
							sql += getPartialSqlWithDistance(endPointPoi1.getPoiId(), String.valueOf(distanceLB), String.valueOf(distanceUB));
							
							for (int j = selectedPoiId.size() - 1; j >= 0; --j) {
								if (selectedPoiId.get(j).equals( endPointPoi1.getPoiId() ))
									continue;
								sql += " OR " + getPartialSqlWithDistance(selectedPoiId.get(j), String.valueOf(distanceLB), null);
							}
							
							sql += ")) a GROUP BY poiId_to HAVING COUNT(*) >= " + selectedPoiId.size() + ") b";
							
						} else {
							sql += "(SELECT poiId_to FROM (SELECT poiId_to FROM GreatCircleDistanceForScheduling2 WHERE (";
							int jj = selectedPoiId.size() - 1;
							sql += getPartialSqlWithDistance(selectedPoiId.get(jj), String.valueOf(distanceLB), String.valueOf(distanceUB));
							
							for (int j = selectedPoiId.size() - 2; j >= 0; --j)
								sql += " OR " + getPartialSqlWithDistance(selectedPoiId.get(j), String.valueOf(distanceLB), null);
							
							sql += ")) a GROUP BY poiId_to HAVING COUNT(*) >= " + selectedPoiId.size() + ") b";
						}
						sql += " LEFT JOIN Scheduling2 c ON b.poiId_to = c.poiId";
						sql += " WHERE OH_" + weekOfDay2 + " IS NULL OR OH_" + weekOfDay2 + " != 'close' ORDER BY RAND() LIMIT 1;";
						
						System.out.println("[1B-last-segment] " + sql);
						queryResult = analyticsjdbc.queryForList(sql);
						System.out.println(queryResult.size());
						
						if (queryResult.size() > 0)
							break;
						else
							distanceUB += 10;
					}
					//insert POI into POI list
					Poi poi = new Poi();
					poi.setFromQueryResult(queryResult.get(0), weekOfDay2);
					poi.setMustPoi(false);
					poi.setInMustCounty(false);
					
					List<Poi> poiList2 = new ArrayList<Poi>();
					poiList2.add(poi);
					dailyInfo2.setPoiList(poiList2);
					
					selectedPoiId.add(poi.getPoiId());
				}
			}
			
			
		} else if (! isAnyMustCounty && ! mustPoiIdList.isEmpty()) {
			//------------------------------------------
			// stage 1, case C： 無必去縣市、有必去景點
			//------------------------------------------
			System.out.println("ok..");
			return null;
			//input.getMustPoiList();
			// get clusters
			

		} else {
			//------------------------------------------
			// stage 1, case D： 有必去縣市、有必去景點
			//------------------------------------------
			System.out.println("ok..");
			return null;
		}
		
		//------------------------------------------
		// stage 2: generating complete itinerary
		//------------------------------------------
		// deciding between-day traffic times
		
		// completing itinerary
		for (int i = 0; i < numDay; ++i) {
			DailyInfo dailyInfo = fullInfo.get(i);
			String weekOfDay = daysOfWeekName[dailyInfo.getDayOfWeek()];
			
			List<Poi> poiList = dailyInfo.getPoiList();
			for (int j = 0; j < poiList.size(); ++j)
				System.out.println(poiList.get(j).getPoiId() + " " + poiList.get(j).getPoiName());
			
			//determining centered POI from POI list
			double distanceLB = 0.1;
			double distanceUB = 15;
			int numCandidatePoi = fixedNumCandidatePoi;
			List<Map<String, Object>> queryResult;
			String centeredPoiId;
			if (poiList.size() == 1)
				centeredPoiId = poiList.get(0).getPoiId();
			else {
				double sumLatitude = 0;
				double sumLongitude = 0;
				for (int j = 0; j < poiList.size(); ++j) {
					double[] coordinate = parseCoordinate(poiList.get(j).getLocation());
					sumLatitude += coordinate[0];
					sumLongitude += coordinate[1];
				}
				centeredPoiId = searchNearbyPoiIdByCoordinate(sumLatitude / poiList.size(), sumLongitude / poiList.size()); 
			}
			
			//selecting candidate POIs from database
			while (true) {
				String sql = "SELECT c.* FROM "
						+ "(SELECT poiId_to FROM GreatCircleDistanceForScheduling2 WHERE ("
						+ getPartialSqlWithDistance(centeredPoiId, String.valueOf(distanceLB), String.valueOf(distanceUB))
						+ ")) b"
						+ " LEFT JOIN Scheduling2 c ON b.poiId_to = c.poiId"
						+ " WHERE OH_" + weekOfDay + " IS NULL OR OH_" + weekOfDay + " != 'close' ORDER BY RAND() LIMIT " + numCandidatePoi + ";";
				
				System.out.println("[2-candidates] " + sql);
				queryResult = analyticsjdbc.queryForList(sql);
				System.out.println(queryResult.size());
				
				if (queryResult.size() >= numCandidatePoi)
					break;
				else
					distanceUB += 5;
			}
			
			//adding selected POIs into POI list
			for (int j = 0; j < numCandidatePoi; ++j) {
				Poi candidatePoi = new Poi();
				candidatePoi.setFromQueryResult(queryResult.get(j), weekOfDay);
				candidatePoi.setMustPoi(false);
				if (dailyInfo.getMustCounty().equals( candidatePoi.getCountyId() ))
					candidatePoi.setInMustCounty(true);
				else
					candidatePoi.setInMustCounty(false);
				poiList.add(candidatePoi);
			}
			
			System.out.println("----- begin day " + (i+1) + " -----");
			for (int j = 0; j < poiList.size(); ++j)
				System.out.println(poiList.get(j).getPoiId() + " " + poiList.get(j).getPoiName());
			System.out.println("----- end day " + (i+1) + " -----");
			
			//obtaining travel time
			double[][] distance = new double[poiList.size() + 2][poiList.size() + 2];
			for (int j = 0; j < poiList.size() + 2; ++j) {
				Poi poiJ;
				if (j == 0)
					poiJ = dailyInfo.getStartPoi();
				else if (j <= poiList.size())
					poiJ = poiList.get(j-1);
				else
					poiJ = null;
				
				for (int k = 0; k < poiList.size() + 2; ++k) {
					Poi poiK;
					if (k == 0)
						poiK = dailyInfo.getStartPoi();
					else if (k <= poiList.size())
						poiK = poiList.get(k-1);
					else
						poiK = null;
					
					if (j == poiList.size() + 1 || k == poiList.size() + 1 || j == k)
						distance[j][k] = 0;
					else {
						//String sql = "SELECT distance from GreatCircleDistanceForScheduling2 WHERE poiId_from = '" + poiList.get(j-1).getPoiId() + "' AND poiId_to = '"
						//		+ poiList.get(k-1).getPoiId() + "';";
						//List<Map<String, Object>> queryForDistance = analyticsjdbc.queryForList(sql);
						//distance[j][k] = Double.parseDouble(queryForDistance.get(0).get("distance").toString());
						
						distance[j][k] = getGreatCircleDistance(poiJ.getLocation(), poiK.getLocation());
					}
				}
			}
			int[][] travelTime = new int[poiList.size() + 2][poiList.size() + 2];
			for (int j = 0; j < poiList.size() + 2; ++j) {
				for (int k = 0; k < poiList.size() + 2; ++k) {
					travelTime[j][k] = getTimeFromDistance(distance[j][k]);
					System.out.print(travelTime[j][k] + " ");
				}
				System.out.println();
			}
			
			//solving by LINDO
			List<TourEvent> mipResults;
			List<Poi> mipResultsPoi;
			
			MipModel2 mipModel2 = new MipModel2();
			mipModel2.setDataUsedInMip(dailyInfo, travelTime);
			int status = mipModel2.run1();
			if (status == 1) {
				mipModel2.examineSolution();
				mipResults = mipModel2.extractSolution();
				fullItinerary.addAll(mipResults);
				
				mipResultsPoi = mipModel2.extractSolutionPoi();
				fullItineraryPoi.addAll(mipResultsPoi);
				
				if (i < numDay - 1) {
					fullInfo.get(i + 1).setStartPoi( fullItineraryPoi.get(fullItineraryPoi.size() - 1) );
				}
				
			} else {
				System.out.println("Optimality not reached when solving by LINDO!");
				
				if (i < numDay - 1) {
					if (fullItineraryPoi.size() > 0)
						fullInfo.get(i + 1).setStartPoi( fullItineraryPoi.get(fullItineraryPoi.size() - 1) );
					else
						fullInfo.get(i + 1).setStartPoi(itineraryStartPoi);
				}
			}
			//a loop - adding scheduledPoi
			
			mipModel2.endLindoEnvironment();
		}
		
		//displaying full itinerary
		for (int i = 0; i < fullItinerary.size(); ++i) {
			System.out.println("[" + fullItinerary.get(i).getStartTime() + "] [" + fullItinerary.get(i).getEndTime() + "] ["
					+ fullItinerary.get(i).getPoiId() + "] [" + fullItineraryPoi.get(i).getPoiName() + "] ["
					+ County.getCountyName(fullItineraryPoi.get(i).getCountyId()) + "]");
		}
		
		System.out.println("Program end!");
		return fullItinerary;
	}
	private int getTimeFromDistance(double d) {
		if (d == 0)
			return 0;
		else
			return (int)(100.0 * (1.0 - 0.8 / Math.pow(d, 0.2)));		
	}
	private String getPartialSqlWithDistance(String poiId, String distanceLB, String distanceUB) {
		String result = "(poiId_from = '" + poiId + "'";		
		if (distanceLB != null)
			result = result + " AND distance >= " + distanceLB;
		if (distanceUB != null)
			result = result + " AND distance <= " + distanceUB;
		result = result + ")";
		return result;
	}
	private String searchNearbyPoiIdByCoordinate(double latitude, double longitude) {
		
		final double latitudeLB = 20.9; //21.8976
		final double latitudeUB = 27.4; //26.3813
		final double longitudeLB = 117.2; //118.21799
		final double longitudeUB = 123.1; //122.1056
		
		if (latitude < latitudeLB || latitude > latitudeUB || longitude < longitudeLB || longitude > longitudeUB)
			return null;
		
		double tolerance = 0.01;
		while (true) {
			String sql = "SELECT * FROM PoiLocationForScheduling2 WHERE"
						+ " latitude >= " + (latitude - tolerance) + " AND latitude <= " + (latitude + tolerance)
						+ " AND longitude >= " + (longitude - tolerance) + " AND longitude <= " + (longitude + tolerance)
						+ " ORDER BY RAND() LIMIT 1;";
			List<Map<String, Object>> queryResult = analyticsjdbc.queryForList(sql);
			if (! queryResult.isEmpty())
				return queryResult.get(0).get("poiId").toString();
			else if (tolerance >= 8)
				return null;
			else
				tolerance += 0.01;
		}
	}
	
    @RequestMapping(method=RequestMethod.GET, value="/dist")
    public @ResponseBody double testDistance(
    		@RequestParam(value = "x1", required = false, defaultValue = "") String x1String,
    		@RequestParam(value = "y1", required = false, defaultValue = "") String y1String,
    		@RequestParam(value = "x2", required = false, defaultValue = "") String x2String,
    		@RequestParam(value = "y2", required = false, defaultValue = "") String y2String
    		) {
		double x1 = Double.parseDouble(x1String);
		double y1 = Double.parseDouble(y1String);
		double x2 = Double.parseDouble(x2String);
		double y2 = Double.parseDouble(y2String);
		double[] p1 = {x1, y1};
		double[] p2 = {x2, y2};
		return getGreatCircleDistance(p1, p2);
	}
	
	private double[] parseCoordinate(String str) {
		String[] spl = str.split(" ");
		double latitude = Double.parseDouble(spl[0].split("POINT\\(")[1]);
		double longitude = Double.parseDouble(spl[1].split("\\)")[0]);
		double[] coordinate = {latitude, longitude}; //{緯度, 經度}
		return coordinate;
	}
	private double getGreatCircleDistance(double[] start, double[] end) { //Equirectangular approximation //{latitude緯度, longitude經度}
		double PI = 3.14159265;
		double R = 6371.229; //km

		double x = (end[1] - start[1]) * PI / 180 * Math.cos((start[0] + end[0]) / 2 * PI / 180);
		double y = (end[0] - start[0]) * PI / 180;
		return Math.hypot(x, y) * R; //km
	}

	private double getGreatCircleDistance(String start, String end) { //Equirectangular approximation
		return getGreatCircleDistance(parseCoordinate(start), parseCoordinate(end));
	}
	
	private int[] parseMinutes_old(String s) {
		int[] minutes = new int[]{0, 1440}; //default: ALL DAY
		
		if (s == null || s.length() == 0)
			return minutes;
		
		if (s.equals("close")) { // use {-1, -1} to denote "close"
			minutes[0] = -1;
			minutes[1] = -1;
			return minutes;
		}
		
		String sep;
		//if (Pattern.matches("[\\d]+:[\\d]+~[\\d]+:[\\d]+;.*", s))
		if (Pattern.matches("(\\d)+:(\\d)+~(\\d)+:(\\d)+;*.*", s))
			sep = "~";
		else if (Pattern.matches("(\\d)+:(\\d)+-(\\d)+:(\\d)+;*.*", s))
			sep = "-";
		else
			return minutes;
		
		String[] splitTimeInterval, splitTime;
		splitTimeInterval = s.split(";"); //e.g. s = "09:00~17:00;09:00~16:30;"
		splitTime = splitTimeInterval[0].split(sep); //e.g. spl1[0] = "09:00~17:00";
		
		for (int i=0; i<2; ++i) {
			String[] spl = splitTime[i].split(":");
			minutes[i] = Integer.parseInt(spl[0]) * 60 + Integer.parseInt(spl[1]);
		}
		if (minutes[0] > minutes[1])
			minutes[1] += 1440;
		return minutes;
	}
}

