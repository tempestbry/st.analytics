package tw.org.iii.st.analytics.controller;

import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.PriorityQueue;

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
import tw.org.iii.model.QueryDatabase;
import tw.org.iii.model.TourEvent;
import tw.org.iii.model.SchedulingInput;
import tw.org.iii.model.DailyInfo;
import tw.org.iii.model.Display;
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
	private static int endTimeEachDayInMinutes = 19 * 60;
	private static int earlistLunchTimeInMinutes = 12 * 60;
	private static int minutesForLunch = 60;
	
	private static int numPoiForSelectionUpperBound = 10;
	
	//---------------------------------------------
	//   API main function: creating itinerary
	//---------------------------------------------
	@RequestMapping("/QuickPlan")
	private @ResponseBody
	List<TourEvent> createItinerary(@RequestBody SchedulingInput input) throws Exception {
		System.out.println("Program begin!");
		Date dateSchedulingStart = new Date(System.currentTimeMillis());
		
		QueryDatabase queryDatabase = new QueryDatabase(analyticsjdbc, true);
		
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
		
		List<String> themeList = input.getPreferenceList(); //should have at least one element
		int[] userInputTheme = new int[8];
		for (String themeStr : themeList) {
			userInputTheme[Integer.parseInt(themeStr.substring(3, 4))] = 1;
		}
		
		int looseType = input.getLooseType();
		List<String> mustPoiIdList = input.getMustPoiList();
		
		// itinerary start and end location
		GeoPoint gps = input.getGps();
		String itineraryStartPoiId = null;
		if (gps != null)
			itineraryStartPoiId = queryDatabase.getNearbyPoiIdByCoordinate(gps.getLat(), gps.getLng()); //could be null
		if (itineraryStartPoiId == null)
			itineraryStartPoiId = "e6449cc1-6047-f3a2-1618-5157c3765714"; //台北火車站 POINT(25.047767 121.517114)
		
		Poi itineraryStartPoi = queryDatabase.getPoiByPoiId(itineraryStartPoiId, false, looseType);
		
		String itineraryEndPoiId = itineraryStartPoiId;
		Poi itineraryEndPoi = queryDatabase.getPoiByPoiId(itineraryEndPoiId, false, looseType);
		
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
			dailyInfo.setEndPoi(itineraryEndPoi);
			
			dailyInfo.setStartPoiUseStayTime(false);
			dailyInfo.setTravelToEndPoi(true);
			dailyInfo.setEndPoiUseStayTime(false);
			
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
				
				dailyInfo.setStartPoi( i == 0 ? itineraryStartPoi : null );
				dailyInfo.setEndPoi( i == numDay - 1 ? itineraryEndPoi : null );
				
				dailyInfo.setStartPoiUseStayTime(false);
				dailyInfo.setTravelToEndPoi( i == numDay-1 ? true : false);
				dailyInfo.setEndPoiUseStayTime(false);
				
				fullInfo.add(dailyInfo);
			}
		}
		
		//Set<String> selectedPoiInStage1 = new HashSet<String>();
		
		//-------------------------------------------------------------
		// stage 1: assigning poi to each day (must poi or seed poi)
		//  four cases
		//-------------------------------------------------------------
		if (! isAnyMustCounty && mustPoiIdList.isEmpty()) {
			//----------------------------------------------------------------------------------------
			// stage 1, case A： 無必去縣市、無必去景點 (! isAnyMustCounty && mustPoiIdList.isEmpty())
			//----------------------------------------------------------------------------------------
			List<String> selectedPoiId = new ArrayList<String>();
			
			for (int i = 0; i < numDay; ++i) {
				DailyInfo dailyInfo = fullInfo.get(i);
				String dayOfWeekStr = QueryDatabase.daysOfWeekName[dailyInfo.getDayOfWeek()];
				
				List<Map<String, Object>> queryResult;
				
				//get this day's seed POI from database
				if (selectedPoiId.size() == 0) {
					while (true) {
						String sql = "SELECT c.* FROM "
							+ "(SELECT poiId_to FROM GreatCircleDistanceForScheduling2 WHERE ("
							+ getPartialSqlWithDistance(itineraryStartPoi.getPoiId(), null, "10")
							+ ")) b"
							+ " LEFT JOIN Scheduling2_bkup c ON b.poiId_to = c.poiId"
							+ " WHERE OH_" + dayOfWeekStr + " IS NULL OR OH_" + dayOfWeekStr + " != 'close' ORDER BY RAND() LIMIT 1;";
						
						
						//String sql = "SELECT c.* FROM Scheduling2_bkup c";	
						//sql += " WHERE OH_" + dayOfWeekStr + " IS NULL OR OH_" + dayOfWeekStr + " != 'close' ORDER BY RAND() LIMIT 1;";
						
						System.out.println("[1A-nearStartPoint] " + sql);
						queryResult = analyticsjdbc.queryForList(sql);
						System.out.println("[result count] " + queryResult.size());
						
						if (queryResult.size() > 0)
							break;
						else
							System.err.println("Error in searching POI in [1A-nearStartPoint]!");;
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
						sql += " LEFT JOIN Scheduling2_bkup c ON b.poiId_to = c.poiId"
								+ " WHERE OH_" + dayOfWeekStr + " IS NULL OR OH_" + dayOfWeekStr + " != 'close' ORDER BY RAND() LIMIT 1;";
						
						System.out.println("[1A-laterPoints] " + sql);
						queryResult = analyticsjdbc.queryForList(sql);
						System.out.println("[result count] " + queryResult.size());
						
						if (queryResult.size() > 0)
							break;
						else
							distanceUB += 10;
					}
				}
				
				//insert seed POI into POI list
				Poi seedPoi = new Poi();
				seedPoi.setFromQueryResult(queryResult.get(0), looseType);
				seedPoi.setMustPoi(false);
				
				dailyInfo.getPoiList().add(seedPoi);
								
				selectedPoiId.add(seedPoi.getPoiId());
			}
		} else if (isAnyMustCounty && mustPoiIdList.isEmpty()) {
			//----------------------------------------------------------------------------------------
			// stage 1, case B： 有必去縣市、無必去景點 (isAnyMustCounty && mustPoiIdList.isEmpty())
			//----------------------------------------------------------------------------------------
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
					String dayOfWeekStr = QueryDatabase.daysOfWeekName[dailyInfo.getDayOfWeek()];
					
					while (true) {
						String sql = "SELECT * FROM Scheduling2_bkup WHERE countyId = '" + dailyInfo.getMustCounty()
									+ "' AND (OH_" + dayOfWeekStr + " IS NULL OR OH_" + dayOfWeekStr + " != 'close') ORDER BY RAND() LIMIT 1;";
						System.out.println("[1B-inCounty] " + sql);
						queryResult = analyticsjdbc.queryForList(sql);
						System.out.println("[result count] " + queryResult.size());
						
						if (queryResult.size() > 0)
							break;
						else
							System.err.println("Error in searching POI in a county!");
					}
					
					//insert seed POI into POI list
					Poi seedPoi = new Poi();
					seedPoi.setFromQueryResult(queryResult.get(0), looseType);
					seedPoi.setMustPoi(false);
					
					dailyInfo.getPoiList().add(seedPoi);
					
					selectedPoiId.add(seedPoi.getPoiId());
					
					if (! dayIndicesWithoutMustCounty.isEmpty()) {
						endPointPoi2 = seedPoi;
						//interpolation: (use endPointPoi1, endPointPoi2, dayIndicesWithoutMustCounty)
						double[] coordinate1 = Poi.parseCoordinate(endPointPoi1.getLocation());
						double[] coordinate2 = Poi.parseCoordinate(endPointPoi2.getLocation());
						
						for (int j = 0; j < dayIndicesWithoutMustCounty.size(); ++j) {
							
							DailyInfo dailyInfo2 = fullInfo.get( dayIndicesWithoutMustCounty.get(j) );
							String dayOfWeekStr2 = QueryDatabase.daysOfWeekName[dailyInfo2.getDayOfWeek()];
							
							double latitude = coordinate1[0] + (coordinate2[0] - coordinate1[0]) * (j + 1) / (dayIndicesWithoutMustCounty.size() + 1);
							double longitude = coordinate1[1] + (coordinate2[1] - coordinate1[1]) * (j + 1) / (dayIndicesWithoutMustCounty.size() + 1);
							String locationInterpolatedPoiId = queryDatabase.getNearbyPoiIdByCoordinate(latitude, longitude);
							
							//selecting candidate POIs from database
							double distanceUB = 10;
							while (true) {
								String sql = "SELECT c.* FROM "
									+ "(SELECT poiId_to FROM GreatCircleDistanceForScheduling2 WHERE ("
									+ getPartialSqlWithDistance(locationInterpolatedPoiId, null, String.valueOf(distanceUB))
									+ ")) b"
									+ " LEFT JOIN Scheduling2_bkup c ON b.poiId_to = c.poiId"
									+ " WHERE OH_" + dayOfWeekStr2 + " IS NULL OR OH_" + dayOfWeekStr2 + " != 'close' ORDER BY RAND() LIMIT 1;";
								
								System.out.println("[1B-interpolation] " + sql);
								queryResult = analyticsjdbc.queryForList(sql);
								System.out.println("[result count] " + queryResult.size());
								
								if (queryResult.size() > 0)
									break;
								else
									distanceUB += 5;
							}
							//insert POI into POI list
							Poi poi = new Poi();
							poi.setFromQueryResult(queryResult.get(0), looseType);
							poi.setMustPoi(false);
							
							dailyInfo2.getPoiList().add(poi);
							
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
					String dayOfWeekStr2 = QueryDatabase.daysOfWeekName[dailyInfo2.getDayOfWeek()];
					
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
						sql += " LEFT JOIN Scheduling2_bkup c ON b.poiId_to = c.poiId"
							+ " WHERE OH_" + dayOfWeekStr2 + " IS NULL OR OH_" + dayOfWeekStr2 + " != 'close' ORDER BY RAND() LIMIT 1;";
						
						System.out.println("[1B-last-segment] " + sql);
						queryResult = analyticsjdbc.queryForList(sql);
						System.out.println("[result count] " + queryResult.size());
						
						if (queryResult.size() > 0)
							break;
						else
							distanceUB += 10;
					}
					//insert POI into POI list
					Poi poi = new Poi();
					poi.setFromQueryResult(queryResult.get(0), looseType);
					poi.setMustPoi(false);
					
					dailyInfo2.getPoiList().add(poi);

					selectedPoiId.add(poi.getPoiId());
				}
			}
			
		} else if (! isAnyMustCounty && ! mustPoiIdList.isEmpty()) {
			//----------------------------------------------------------------------------------------
			// stage 1, case C： 無必去縣市、有必去景點 (! isAnyMustCounty && ! mustPoiIdList.isEmpty())
			//----------------------------------------------------------------------------------------
			
			//---- obtain must-POI information from database ----
			
			List<Poi> collectedPoiList = queryDatabase.getPoiListByPoiIdList(mustPoiIdList, true, looseType);
			
			ClusterInfo[] clusterInfo = new ClusterInfo[numDay];
			for (int i = 0; i < numDay; ++i)
				clusterInfo[i] = new ClusterInfo();
			
			if (numDay == 1) {
			//---- ONE DAY itinerary ----
				
				DailyInfo dailyInfo = fullInfo.get(0);
				
				for (Poi poi : collectedPoiList) {
					clusterInfo[0].getPoiList().add(poi);
					
					String openHoursOfThisDay = poi.getOpenHoursOfADay(dailyInfo.getDayOfWeek());					
					if (openHoursOfThisDay == null || ! openHoursOfThisDay.equals("close")) {
						dailyInfo.getPoiList().add(poi);
					}
				}
				clusterInfo[0].calculatePoiCenter(); // this is used to locate random POI generation in case all POIs are close.
				
			} else {
			//---- MORE THAN ONE DAY itinerary ----
				
				//---- generate clusters ----
				
				// collectedPoiList.size() may <, =, > numCluster
				
				int[] clusterOfPoi = new int[collectedPoiList.size()];
				for (int i = 0; i < clusterOfPoi.length; ++i) {
					clusterOfPoi[i] = i;
				}
				
				int numClusterUsed = collectedPoiList.size();
				
				boolean[] isClusterIdxUsed = new boolean[collectedPoiList.size()];
				Arrays.fill(isClusterIdxUsed, true);
				
				if (collectedPoiList.size() > 1) {
					// obtain pairwise distances and put in priority queue
					Comparator<PoiPair> comparator = new PoiPairComparator(); 
					PriorityQueue<PoiPair> priorityQueue = new PriorityQueue<PoiPair>( collectedPoiList.size() * collectedPoiList.size() / 2, comparator );
					for (int i = 0; i < collectedPoiList.size() - 1; ++i)
						for (int j = i + 1; j < collectedPoiList.size(); ++j) {
							double distance = getGreatCircleDistance( collectedPoiList.get(i).getLocation(), collectedPoiList.get(j).getLocation() );
							priorityQueue.add( new PoiPair(i, j, distance) );
						}
					
					// display priority queue
					System.out.println("Display priorityQueue of PoiPair:");
					for (PoiPair poiPair : priorityQueue)
						System.out.println(poiPair);
					
					final double mustMergingBound = 5;
					
					// perform clustering
					while (true) {
						PoiPair poiPair = priorityQueue.poll();
						int idx1 = poiPair.getPoiIdx1();
						int idx2 = poiPair.getPoiIdx2();
						double distance = poiPair.getDistance();
						
						if (distance <= mustMergingBound || numClusterUsed > numDay) {
						
							if ( clusterOfPoi[idx1] != clusterOfPoi[idx2] ) {
								int removedCluster = clusterOfPoi[idx2];
								for (int i = 0; i < clusterOfPoi.length; ++i) {
									if ( clusterOfPoi[i] == removedCluster )
										clusterOfPoi[i] = clusterOfPoi[idx1];
								}
								isClusterIdxUsed[ removedCluster ] = false;
								--numClusterUsed;
							}
						} else
							break;
						
						if (priorityQueue.isEmpty())
							break;
					}
					Display.print_1D_Array(isClusterIdxUsed, "Display isClusterIdxUsed");
					Display.print_1D_Array(clusterOfPoi, "Display clusterOfPoi");
				}
				
				
				// now: numClusterUsed < numDay or numClusterUsed == numDay
				
				// wrap up cluster of pois after merging
				int[] finalClusterIdx = new int[collectedPoiList.size()];
				Arrays.fill(finalClusterIdx, -1);
				int k = 0;
				for (int i = 0; i < collectedPoiList.size(); ++i)
					if (isClusterIdxUsed[i])
						finalClusterIdx[i] = k++;
				
				if (k != numClusterUsed) {
					System.err.println("Number of used clusters is wrong after merging!!");
					return null;
				}
				
				for (int i = 0; i < collectedPoiList.size(); ++i) {
					int cluster = finalClusterIdx[ clusterOfPoi[i] ];
					clusterInfo[cluster].getPoiList().add( collectedPoiList.get(i) );
				}
				
				
				// display cluster information
				for (int i = 0; i < clusterInfo.length; ++i) {
					System.out.println("cluster : " + i);
					for (Poi poi : clusterInfo[i].getPoiList())
						System.out.println(poi);
				}
				
				//---- fill in enough POIs ----				
				if (numClusterUsed < numDay) {
					
					// initialize isCandidateCounty by using counties of must-POI
					boolean[] isCandidateCounty = new boolean[23];
					for (Poi poi : collectedPoiList) {
						int countyIdx = Integer.parseInt( poi.getCountyId().replaceAll("[^0-9]", "") );
						isCandidateCounty[countyIdx] = true;
					}
					
					// initialize counties of must-poi
					List<Integer> mustPoiCountyIndex = new ArrayList<Integer>();
					for (int i = 1; i <= 22; ++i)
						if (isCandidateCounty[i])
							mustPoiCountyIndex.add(i);
					
					// fill in intermediate counties into candidate counties
					for (int i = 0; i < mustPoiCountyIndex.size() - 1; ++i)
						for (int j = i + 1; j < mustPoiCountyIndex.size(); ++j) {
							List<Integer> intermediateCounty = County.getIntermediateCounty(mustPoiCountyIndex.get(i), mustPoiCountyIndex.get(j));
							for (Integer ic : intermediateCounty)
								isCandidateCounty[ic] = true;
						}
					
					// initialize candidate counties
					List<Integer> candidateCountyIndex = new ArrayList<Integer>();
					for (int i = 1; i <= 22; ++i)
						if (isCandidateCounty[i])
							candidateCountyIndex.add(i);
					
					// if there's only one candidate county, then add nearby county if there is any
					if (candidateCountyIndex.size() == 1) {
						List<Integer> nearbyCounty = County.getNearbyCounty( candidateCountyIndex.get(0) );
						candidateCountyIndex.addAll( nearbyCounty );
					}
					
					
					// select additional POIs from database
					boolean[] isMustOpen = new boolean[7];
					Arrays.fill(isMustOpen, true);
					
					List<Poi> poiList = queryDatabase.getRandomPoiListByCountyAndOpenDay(
							numDay - numClusterUsed, candidateCountyIndex, isMustOpen, looseType);
					
					for (int i = 0; i < numDay - numClusterUsed; ++i) {
						clusterInfo[i + numClusterUsed].getPoiList().add( poiList.get(i) );
					}
					
					// display cluster information
					for (int i = 0; i < clusterInfo.length; ++i) {
						System.out.println("cluster : " + i);
						for (Poi poi : clusterInfo[i].getPoiList())
							System.out.println(poi);
					}
				}
				
				// assign clusters to days -- prepare data
				int numCluster = numDay;
				boolean[][] feasibleAssignment = new boolean[numCluster][numDay]; //row: cluster, column: day
				int[][] travelTime = new int[numCluster][numCluster]; //row: cluster, column: cluster

				double openPoiProportionLB = 0.5;

				for (int i = 0; i < numCluster; ++i) {
					clusterInfo[i].calculatePoiCenter();
					
					for (int j = 0; j < numDay; ++j) {
						int count = clusterInfo[i].getCountOfOpenPoi( fullInfo.get(j).getDayOfWeek() );
						feasibleAssignment[i][j] = ((double)count / clusterInfo[i].getPoiList().size() >= openPoiProportionLB) ? true : false;
					}
				}
				
				for (int i = 0; i < numCluster - 1; ++i) {
					for (int j = i; j < numCluster; ++j) {
						if (i == j)
							travelTime[i][j] = 0;
						else {
							int time = getTimeFromDistance( getGreatCircleDistance( clusterInfo[i].getPoiCenter(), clusterInfo[j].getPoiCenter() ) );
							travelTime[i][j] = time;
							travelTime[j][i] = time;
						}
					}
				}
				
				Display.print_2D_Array(feasibleAssignment, "Display feasibleAssignment");
				Display.print_2D_Array(travelTime, "Display travelTime");
				
				// assign clusters to days
				//TODO
				
				// assign clusters to days -- solve by LINDO
				/*MipModel1 mipModel1 = new MipModel1();
				mipModel1.setDataUsedInMip(feasibleAssignment, travelTime);
				
				int status = mipModel1.run();
				if (status == 1) {
					mipModel1.examineSolution();
					mipModel1.extractSolution();
					
				} else {
					System.err.println("Optimality not reached when solving by LINDO!");
				}
				
				mipModel1.endLindoEnvironment();*/
				
				
				// insert cluster's POI into DailyInfo
				for (int i = 0; i < numDay; ++i) {
					DailyInfo dailyInfo = fullInfo.get(i);
					
					// insert into DailyInfo / check open hours
					for (Poi poi : clusterInfo[i].getPoiList()) {
						String openHoursOfThisDay = poi.getOpenHoursOfADay(dailyInfo.getDayOfWeek());
						if (openHoursOfThisDay == null || ! openHoursOfThisDay.equals("close")) {
							dailyInfo.getPoiList().add(poi);
						}
					}
				}
			}
			
			// check if all POI in this cluster are ALL CLOSE
			for (int i = 0; i < numDay; ++i) {
				DailyInfo dailyInfo = fullInfo.get(i);
				
				if (dailyInfo.getPoiList().isEmpty()) {
					String dayOfWeekStr = QueryDatabase.daysOfWeekName[dailyInfo.getDayOfWeek()];
					String centeredPoiId = queryDatabase.getNearbyPoiIdByCoordinate( clusterInfo[i].getPoiCenter() );

					List<Map<String, Object>> queryResult;
					double distanceUB = 0.3;
					while (true) {
						String sql1 = "SELECT c.* FROM "
							+ "(SELECT poiId_to FROM GreatCircleDistanceForScheduling2 WHERE ("
							+ getPartialSqlWithDistance(centeredPoiId, null, String.valueOf(distanceUB))
							+ ")) b"
							+ " LEFT JOIN Scheduling2_bkup c ON b.poiId_to = c.poiId"
							+ " WHERE OH_" + dayOfWeekStr + " IS NULL OR OH_" + dayOfWeekStr + " != 'close' ORDER BY RAND() LIMIT 1;";
						
						System.out.println("[1C-allPoiCloseInCluster] " + sql1);
						queryResult = analyticsjdbc.queryForList(sql1);
						System.out.println(queryResult.size());
						
						if (queryResult.size() > 0)
							break;
						else
							distanceUB += 0.3;
					}
					Poi poi = new Poi();
					poi.setFromQueryResult(queryResult.get(0), looseType);
					poi.setMustPoi(false);
					dailyInfo.getPoiList().add(poi);
				}
			}
			
		} else {
			//----------------------------------------------------------------------------------------
			// stage 1, case D： 有必去縣市、有必去景點 (isAnyMustCounty && ! mustPoiIdList.isEmpty())
			//----------------------------------------------------------------------------------------
			

			
			
/*			
			// initialize allMustCountyInfo[0~22]
			MustCountyInfo[] allMustCountyInfo = new MustCountyInfo[23];//"all","TW1","TW2",...,"TW22"
			for (int i = 0; i < allMustCountyInfo.length; ++i)
				allMustCountyInfo[i] = new MustCountyInfo();
			
			// put day information into allMustCountyInfo
			for (int i = 0; i < numDay; ++i) {
				String countyIdxString = fullInfo.get(i).getMustCounty().replaceAll("[^0-9]", "");
				int countyIdx = (countyIdxString.length() == 0) ? 0 : Integer.parseInt(countyIdxString);
				
				allMustCountyInfo[countyIdx].getDayIndexInItinerary().add(i);
			}
			
			// put poi information into allMustCountyInfo
			for (String mustPoiId : mustPoiIdList) {
				List<Map<String, Object>> queryResult = analyticsjdbc.queryForList("SELECT * FROM Scheduling2_bkup WHERE poiId = '" + mustPoiId + "';");
				Poi poi = new Poi();
				poi.setFromQueryResult(queryResult.get(0));
				poi.setMustPoi(true);;
				
				String countyIdxString = poi.getCountyId().replaceAll("[^0-9]", "");
				int countyIdx = Integer.parseInt(countyIdxString);
				List<Poi> mustPoiList = allMustCountyInfo[countyIdx].getMustPoiList();
				
				// add this poi, ordered by checkins
				int j = 0;;
				for (; j < mustPoiList.size(); ++j)
					if (poi.getCheckins() > mustPoiList.get(j).getCheckins()) {
						mustPoiList.add(j,  poi);
						break;
					}
				if (j == mustPoiList.size())
					mustPoiList.add(j,  poi);
			}
			
			// display/check
			for (int i = 0; i < allMustCountyInfo.length; ++i) {
				MustCountyInfo mustCountyInfo = allMustCountyInfo[i];
				
				if (i == 0)
					System.out.println("0 all");
				else
					System.out.println(i + " " + County.getCountyName("TW" + i));
				
				if (! mustCountyInfo.getDayIndexInItinerary().isEmpty()) {
					System.out.print("-->Day index: ");
					for (int j = 0; j < mustCountyInfo.getDayIndexInItinerary().size(); ++j)
						System.out.print(mustCountyInfo.getDayIndexInItinerary().get(j) + " ");
					System.out.println();
				}
				if (! mustCountyInfo.getMustPoiList().isEmpty()) {
					System.out.print("==>POI list: ");
					for (int j = 0; j < mustCountyInfo.getMustPoiList().size(); ++j)
						System.out.print(mustCountyInfo.getMustPoiList().get(j).getPoiId() + " ");
					System.out.println();
				}				
			}
			
			// find out and sort counties with nonzero counts <-- the sorting can be removed
			List<Integer> countyOccurrenceCount = new ArrayList<Integer>();
			List<Integer> countyIndex = new ArrayList<Integer>();
			for (int i = 1; i < allMustCountyInfo.length; ++i) { //countyId="all" hasn't been handled yet
				int count = allMustCountyInfo[i].getDayIndexInItinerary().size();
				if (count > 0) {
					int j = 0;
					for (; j < countyOccurrenceCount.size(); ++j) {
						if (count < countyOccurrenceCount.get(j)) {
							countyOccurrenceCount.add(j, count);
							countyIndex.add(j, i);
							break;
						}
					}
					if (j == countyOccurrenceCount.size()) {
						countyOccurrenceCount.add(j, count);
						countyIndex.add(j, i);					
					}
				}
			}
			// display/check
			for (int i = 0; i < countyOccurrenceCount.size(); ++i) {
				System.out.println(countyOccurrenceCount.get(i) + " TW" + countyIndex.get(i));
			}
*/
			
			/*
			// get pairwise distance of must POIs from database
			String allPoiIdSet = "(";
			for (int i = 0; i < mustPoiIdList.size(); ++i) {
				allPoiIdSet += "'" + mustPoiIdList.get(i) + "'";
				if (i < mustPoiIdList.size() - 1)
					allPoiIdSet += ", ";
			}
			allPoiIdSet += ")";
			
			String sql = "SELECT * FROM (";
			for (int i = 0; i < mustPoiIdList.size(); ++i) {
				sql += " SELECT * FROM GreatCircleDistanceForScheduling2 WHERE (poiId_from = '"
						+ mustPoiIdList.get(i) + "' AND poiId_to in " + allPoiIdSet + ")";
				if (i < mustPoiIdList.size() - 1)
					sql += " UNION";
			}
			sql += ") a ORDER BY distance";
			*/
			
			
			System.out.println("ok 1D..");
			return null;
		}
		
		//------------------------------------------
		// stage 2: generating complete itinerary
		//------------------------------------------
		// deciding between-day traffic times
		
		// completing itinerary
		for (int i = 0; i < numDay; ++i) {
			DailyInfo dailyInfo = fullInfo.get(i);
			
			if (dailyInfo.getStartPoi() == null) {
				System.err.println("Oooooh!!");
				return null;
			}
			if (dailyInfo.getEndPoi() == null) { //won't be the last day
				String tempPoiId = queryDatabase.getNearbyPoiIdByCoordinate( Poi.calculatePoiCenter( fullInfo.get(i+1).getPoiList() ));
				dailyInfo.setEndPoi( queryDatabase.getPoiByPoiId(tempPoiId, false, looseType) );
			}
			
			
			List<Poi> poiList = dailyInfo.getPoiList();
			System.out.println("--- Day " + (i+1) + " --- before adding candidate POIs:");
			for (Poi poi : poiList)
				System.out.println(poi);
			
			String dayOfWeekStr = QueryDatabase.daysOfWeekName[dailyInfo.getDayOfWeek()];
			
			int numCandidatePoi = Math.max(numPoiForSelectionUpperBound - dailyInfo.getPoiList().size(), 0);
			
			if (numCandidatePoi > 0) {
				
				// get counties used for searching POI
				boolean[] isCountyForSearch = new boolean[23];
				for (Poi poi : poiList) {
					int j = Integer.parseInt( poi.getCountyId().replaceAll("[^0-9]", "") );
					isCountyForSearch[j] = true;
					
					List<Integer> nearbyCounty = County.getNearbyCounty(j);
					for (Integer k : nearbyCounty)
						isCountyForSearch[k] = true;
				}
				List<Integer> countyIndexForSearch = new ArrayList<Integer>();
				for (int j = 1; j <= 22; ++j)
					if (isCountyForSearch[j])
						countyIndexForSearch.add(j);
				
				
				String centeredPoiId = queryDatabase.getNearbyPoiIdByCoordinate( Poi.calculatePoiCenter(poiList) );
				
				List<Map<String, Object>> queryResult;
				
				//selecting candidate POIs from database (OLD)
				double distanceLB = 0.1;
				double distanceUB = 15;
				while (true) {
					String sql = "SELECT c.* FROM "
							+ "(SELECT poiId_to FROM GreatCircleDistanceForScheduling2 WHERE ("
							+ getPartialSqlWithDistance(centeredPoiId, String.valueOf(distanceLB), String.valueOf(distanceUB))
							+ ")) b"
							+ " LEFT JOIN Scheduling2_bkup c ON b.poiId_to = c.poiId"
							+ " WHERE countyId IN ('TW" + countyIndexForSearch.get(0) + "'";
					for (int j = 1; j < countyIndexForSearch.size(); ++j)
						sql += ", 'TW" + countyIndexForSearch.get(j) + "'";
					sql += ") AND OH_" + dayOfWeekStr + " IS NULL OR OH_" + dayOfWeekStr + " != 'close' ORDER BY RAND() LIMIT " + numCandidatePoi + ";";
					
					System.out.println("[2-candidates] " + sql);
					queryResult = analyticsjdbc.queryForList(sql);
					System.out.println(queryResult.size());
					
					if (queryResult.size() >= numCandidatePoi)
						break;
					else
						distanceUB += 5;
				}
				
				//selecting candidate POIs from database
				/*double initialdistanceLB = 2;
				
				while (true) { //While make sure the number of candidates > 50
					
					String sql = "SELECT * FROM "
							+ "(SELECT * FROM GreatCircleDistanceForScheduling2 WHERE poiId_from ='"
							+ centeredPoiId
							+ "' AND distance <= "
							+ String.valueOf(initialdistanceLB)
							+ ") a "
							+ "left join Scheduling2_bkup b "
							+ "on a.poiId_to = b.poiId "
							+ "WHERE checkinTotal > 0 "
							+ "AND (OH_" + dayOfWeekStr + " IS NULL OR OH_" + dayOfWeekStr + " != 'close')"
							+ "ORDER BY checkinTotal desc "
							+ "LIMIT 100 ";
					
					System.out.println("[2-candidates] " + sql);
					queryResult = analyticsjdbc.queryForList(sql);
					System.out.println(queryResult.size());
					
					int numcount = queryResult.size();
					if (numcount < 50) {//Make sure we got at least 50 candidates
						initialdistanceLB += 1; //expand the search bound if numcount < 50
					} else {
						
						int i1 = 0;
						double maxdistance = 0;
						double mindistance = 5000;
						double sumscore = 0;
						String[][] poiarrary = new String[numcount][8];//store poi info & score//[][0]poiId, [][1]name, [][2]distance, [][3]checkinTotal, [][4]score probability, [][5]accumulated score
						double[][] poithemearrary = new double[numcount][8];//store poi theme info
						String[] selectedpoiarray = new String[numCandidatePoi];//store selected poi ==>output
						double[] selectedpoiscorearray = new double[numCandidatePoi];//store selected poi score ==>output
						double[] poiprobabilityranklist = new double[numcount];//store poi rank, just for show
						
						//get poi informatiom from DB
						for (Map<String, Object> result : queryResult) {

							poiarrary[i1][0] = result.get("poiId").toString();
							poiarrary[i1][1] = result.get("name").toString();
							poiarrary[i1][2] = result.get("distance").toString();
							poiarrary[i1][3] = result.get("checkinTotal").toString();

							if (result.get("TH10") != null && result.get("TH10").toString().equals("1"))
								poithemearrary[i1][0] = 1;
							if (result.get("TH11") != null && result.get("TH11").toString().equals("1"))
								poithemearrary[i1][1] = 1;
							if (result.get("TH12") != null && result.get("TH12").toString().equals("1"))
								poithemearrary[i1][2] = 1;
							if (result.get("TH13") != null && result.get("TH13").toString().equals("1"))
								poithemearrary[i1][3] = 1;
							if (result.get("TH14") != null && result.get("TH14").toString().equals("1"))
								poithemearrary[i1][4] = 1;
							if (result.get("TH15") != null && result.get("TH15").toString().equals("1"))
								poithemearrary[i1][5] = 1;
							if (result.get("TH16") != null && result.get("TH16").toString().equals("1"))
								poithemearrary[i1][6] = 1;
							if (result.get("TH17") != null && result.get("TH17").toString().equals("1"))
								poithemearrary[i1][7] = 1;
							i1++;
						}
						
						// find maxdistance & mindistance from poi candidates, for calculate distancescore later
						for (String[] poiarrary1 : poiarrary) {
							if (maxdistance < Double.parseDouble(poiarrary1[2])) {
								maxdistance = Double.parseDouble(poiarrary1[2]);
							}
							if (mindistance > Double.parseDouble(poiarrary1[2])) {
								mindistance = Double.parseDouble(poiarrary1[2]);
							}
						}
						double distancescore;
						double themescore;
						double themesum; //Calculate how many themes did user select
						
						for (int x = 0; x < poiarrary.length; x++) {
							//distance score caculating
							distancescore = 1 - (Double.parseDouble(poiarrary[x][2]) - mindistance) / (maxdistance - mindistance);
							
							//checkins score caculating
							double checkinsscore = 1 * (1 - 1 / Math.pow(Double.parseDouble(poiarrary[x][3]), 0.02)) / 0.243;
							
							//theme score caculating
							themesum = 0;
							themescore = 0;
							for (int j = 0; j < 8; j++) {
								themescore += themearray[j] * poithemearrary[x][j];
								themesum += themearray[j];
							}
							themescore = themescore / themesum;
							
							double poiscore = distancescore / 8 + checkinsscore / 4 * 3 + themescore / 8;
							sumscore += poiscore;
							poiarrary[x][4] = String.valueOf(poiscore);
						}
						
						//Turn the score to accumulated probability from 0 to 1
						poiarrary[0][4] = String.valueOf(Double.parseDouble(poiarrary[0][4]) / sumscore);
						poiarrary[0][5] = poiarrary[0][4];
						poiprobabilityranklist[0] = Double.parseDouble(poiarrary[0][4]);
						for (int x = 1; x < poiarrary.length; x++) {
							poiarrary[x][4] = String.valueOf(Double.parseDouble(poiarrary[x][4]) / sumscore);                        
							poiarrary[x][5] = String.valueOf(Double.parseDouble(poiarrary[x][4]) + Double.parseDouble(poiarrary[x - 1][5]));
							System.out.println(poiarrary[x][4]+"  probability:"+poiarrary[x][4]+"  accumulated probability"+poiarrary[x][5]);
							poiprobabilityranklist[x] = Double.parseDouble(poiarrary[x][4]);
						}
						Arrays.sort(poiprobabilityranklist);//sort the poiprobability to get the rank, only for show

						

						//Get the chosen PoI by random number
						int alreadychoose = 0;//How many candidates already been chosed
						while (alreadychoose < numCandidatePoi) {

							double randomseed = Math.random();
							for (int x = 1; x < poiarrary.length; x++) {

							    if (randomseed >= 0 && randomseed <= Double.parseDouble(poiarrary[0][5])) {
							        if (alreadychoose > 0) {
							            if (Arrays.asList(selectedpoiarray).contains(poiarrary[0][1])) {//check already been chosed
							                break;
							            }
							        }
							        selectedpoiarray[alreadychoose] = poiarrary[0][1];
							        selectedpoiscorearray[alreadychoose] = Double.parseDouble(poiarrary[0][4]);
							        alreadychoose++;
							        break;
							    }
							    if (randomseed > Double.parseDouble(poiarrary[x - 1][5]) && randomseed <= Double.parseDouble(poiarrary[x][5])) {
							        if (alreadychoose > 0) {
							            if (Arrays.asList(selectedpoiarray).contains(poiarrary[x][1])) {
							                break;
							            }
							        }
							        selectedpoiarray[alreadychoose] = poiarrary[x][1];
							        selectedpoiscorearray[alreadychoose] = Double.parseDouble(poiarrary[x][4]);
							        alreadychoose++;
							        break;
							    }
							    if (x == (poiarrary.length - 1)) {
							        if (alreadychoose > 0) {
							            if (Arrays.asList(selectedpoiarray).contains(poiarrary[poiarrary.length - 1][1])) {
							                break;
							            }
							        }
							        selectedpoiarray[alreadychoose] = poiarrary[poiarrary.length - 1][1];
							        selectedpoiscorearray[alreadychoose] = Double.parseDouble(poiarrary[poiarrary.length - 1][4]);
							        alreadychoose++;
							        break;
							    }
							}
						}

						//find the rank index, just for show
						int[] ranklist = new int[numCandidatePoi];
						for (int x = 0; x < selectedpoiscorearray.length; x++) {
							ranklist[x] = poiprobabilityranklist.length - Arrays.binarySearch(poiprobabilityranklist, selectedpoiscorearray[x]);
						}

						System.out.println(Arrays.toString(selectedpoiarray));
						System.out.println(Arrays.toString(selectedpoiscorearray));
						
						System.out.println(Arrays.toString(ranklist) + " Total candidates: " + poiprobabilityranklist.length);
						break;
					}
				}*/
				
				
				
				//adding selected POIs into POI list
				for (int j = 0; j < numCandidatePoi; ++j) {
					Poi candidatePoi = new Poi();
					candidatePoi.setFromQueryResult(queryResult.get(j), looseType);
					candidatePoi.setMustPoi(false);
					poiList.add(candidatePoi);
				}
			}
			
			System.out.println("--- Day " + (i+1) + " --- after adding candidate POIs:");
			for (Poi poi : poiList)
				System.out.println(poi);
			System.out.println("-------------");
			
			
			// set POI index for accessing the distance matrix
			dailyInfo.getStartPoi().setIndex(0);
			for (int j = 0; j < poiList.size(); ++j)
				poiList.get(j).setIndex(j + 1);
			dailyInfo.getEndPoi().setIndex(poiList.size() + 1);
			
			//obtaining travel time
			int[][] travelTime = new int[poiList.size() + 2][poiList.size() + 2];
			for (int j = 0; j < poiList.size() + 2; ++j) {
				Poi poiJ;
				if (j == 0)
					poiJ = dailyInfo.getStartPoi();
				else if (j <= poiList.size())
					poiJ = poiList.get(j-1);
				else
					poiJ = dailyInfo.getEndPoi();
				
				for (int k = 0; k < poiList.size() + 2; ++k) {
					if (j == k) {
						travelTime[j][k] = 0;
					}
					else {
						Poi poiK;
						if (k == 0)
							poiK = dailyInfo.getStartPoi();
						else if (k <= poiList.size())
							poiK = poiList.get(k-1);
						else
							poiK = dailyInfo.getEndPoi();
						
						//String sql = "SELECT distance from GreatCircleDistanceForScheduling2 WHERE poiId_from = '" + poiList.get(j-1).getPoiId() + "' AND poiId_to = '"
						//		+ poiList.get(k-1).getPoiId() + "';";
						//List<Map<String, Object>> queryForDistance = analyticsjdbc.queryForList(sql);
						//distance[j][k] = Double.parseDouble(queryForDistance.get(0).get("distance").toString());
						
						travelTime[j][k] = getTimeFromDistance( getGreatCircleDistance(poiJ.getLocation(), poiK.getLocation()) );
					}
				}
			}
			Display.print_2D_Array(travelTime, "Display travelTime");
			
			
			double[] poiScore = new double[poiList.size()];
			
			// construct tour
			String tourConstructingMethod = "insertion heuristic";
			
			if (tourConstructingMethod.equals("insertion heuristic")) {
				//------------------------------
				//  insertion heuristic
				//------------------------------
				List<Poi> rawMustPoiList = new ArrayList<Poi>();
				List<Poi> rawOtherPoiList = new ArrayList<Poi>();
				for (Poi poi : poiList) {
					if (poi.isMustPoi())
						rawMustPoiList.add(poi);
					else
						rawOtherPoiList.add(poi);
				}
				
				List<Poi> resultPoiList = new ArrayList<Poi>();
				resultPoiList.add( dailyInfo.getStartPoi() );
				resultPoiList.add( dailyInfo.getEndPoi() );
				
				
				// if must county - get the first POI in must county
				
				String mustCounty = dailyInfo.getMustCounty();
				if (! mustCounty.equals("all")) {
					
					List<Poi> rawPoiList = null;
					int bestIndex = -1;
					
					for (int j = 0; j < 2; ++j) {
						rawPoiList = (j == 0) ? rawMustPoiList : rawOtherPoiList;
						
						int shortestTime = Integer.MAX_VALUE;
						bestIndex = -1;
						
						for (int k = 0; k < rawPoiList.size(); ++k) {
							if (! rawPoiList.get(k).getCountyId().equals( mustCounty ))
								continue;
							int time = Poi.findShortestTimeFromPoiToPoiList( rawPoiList.get(k), resultPoiList, travelTime);
							if (time < shortestTime) {
								shortestTime = time;
								bestIndex = k;
							}
						}
						
						if (bestIndex != -1)
							break;
					}
					if (bestIndex == -1) {
						System.err.println("In day " + i + ", no POI in must county!");
						return null;
					} else {
						resultPoiList.add( 1, rawPoiList.get(bestIndex) );
						rawPoiList.remove( bestIndex );
					}
				}
				
				// complete the insertion heuristic
				
				for (int j = 0; j < 2; ++j) {
					List<Poi> rawPoiList = (j == 0) ? rawMustPoiList : rawOtherPoiList;
					
					while (! rawPoiList.isEmpty()) {
						
						// find a remaining POI which has the shortest distance to any of the selected POI
						
						int shortestTime = Integer.MAX_VALUE;
						int bestIndex = -1;
						for (int k = 0; k < rawPoiList.size(); ++k) {
							int time = Poi.findShortestTimeFromPoiToPoiList( rawPoiList.get(k), resultPoiList, travelTime);
							if (time < shortestTime) {
								shortestTime = time;
								bestIndex = k;
							}
						}
						
						// find a slot where (1) open hours is satisfied and (2) the distance increments is smallest
						
						int smallestTimeIncrement = Integer.MAX_VALUE;
						int bestSlotIndex = -1;
						int smallestTimeIncrementWhenFeasible = Integer.MAX_VALUE;
						int bestSlotIndexWhenFeasible = -1;
						
						for (int k = 0; k < resultPoiList.size() - 1; ++k) {
							int timeIncrement = travelTime[ resultPoiList.get(k).getIndex() ][ rawPoiList.get(bestIndex).getIndex() ]
									+ travelTime[ rawPoiList.get(bestIndex).getIndex() ][ resultPoiList.get(k+1).getIndex() ]
									- travelTime[ resultPoiList.get(k).getIndex() ][ resultPoiList.get(k+1).getIndex() ];
							
							List<Poi> tempPoiList = new ArrayList<Poi>();
							tempPoiList.addAll( resultPoiList.subList(0, k+1) );
							tempPoiList.add( rawPoiList.get(bestIndex) );
							tempPoiList.addAll( resultPoiList.subList(k+1, resultPoiList.size()) );
							boolean isFeasible = Poi.checkOpenHoursFeasibility(tempPoiList, travelTime, dailyInfo, earlistLunchTimeInMinutes, minutesForLunch);
							
							if (timeIncrement < smallestTimeIncrement) {
								smallestTimeIncrement = timeIncrement;
								bestSlotIndex = k;
							}
							if (isFeasible && timeIncrement < smallestTimeIncrementWhenFeasible) {
								smallestTimeIncrementWhenFeasible = timeIncrement;
								bestSlotIndexWhenFeasible = k;
							}
						}
						
						// update
						
						if (bestSlotIndexWhenFeasible == bestSlotIndex
								|| smallestTimeIncrementWhenFeasible <= 30
								|| smallestTimeIncrementWhenFeasible <= smallestTimeIncrement * 1.5) { //to be tuned
							resultPoiList.add( bestSlotIndexWhenFeasible + 1, rawPoiList.get(bestIndex) );
						}
						rawPoiList.remove(bestIndex);
					}
					
				}
				
				List<TourEvent> itinerary = Poi.getTimeInfoFromFeasibleSequence(resultPoiList, travelTime, dailyInfo, earlistLunchTimeInMinutes, minutesForLunch);
				
				// handle if last/first POI will be used
				
				if (i < numDay - 1 && ! dailyInfo.isEndPoiUseStayTime()) {
					resultPoiList.remove( resultPoiList.size() - 1 );
					itinerary.remove( resultPoiList.size() - 1 );
				}
				if (i > 0 && ! dailyInfo.isStartPoiUseStayTime()) {
					resultPoiList.remove(0);
					itinerary.remove(0);
				}
				
				// link to full itinerary
				fullItineraryPoi.addAll(resultPoiList);
				fullItinerary.addAll(itinerary);
				
				if (i < numDay - 1)
					fullInfo.get(i + 1).setStartPoi( resultPoiList.get( resultPoiList.size() - 1 ) );
				
			} else if (tourConstructingMethod.equals("mip model 2")) {
				//solving by LINDO
				
				/*List<TourEvent> mipResults;
				List<Poi> mipResultsPoi;
				
				MipModel2 mipModel2 = new MipModel2();
				mipModel2.setDataUsedInMip(dailyInfo, travelTime);
				int status = mipModel2.run1();
				if (status == 1) {
					mipModel2.examineSolution();
					mipResults = mipModel2.extractSolution();
					
					mipModel2.run2();
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
				
				mipModel2.endLindoEnvironment();*/
			}
		}
		
		//displaying full itinerary
		Calendar calendar = Calendar.getInstance();
		int dayOfWeek0 = -10;
		int d = 0;
		for (int i = 0; i < fullItinerary.size(); ++i) {
			calendar.setTime(fullItinerary.get(i).getStartTime());
			int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1;
			if (dayOfWeek != dayOfWeek0) {
				++d;
				System.out.println("Day " + d);
				dayOfWeek0 = dayOfWeek;
			}

			System.out.println("[" + fullItinerary.get(i).getStartTime() + "][" + fullItinerary.get(i).getEndTime() + "] "
					+ fullItineraryPoi.get(i));
		}
		
		
		Date dateSchedulingEnd = new Date(System.currentTimeMillis());
		System.out.print("Total elapsed:");
		System.out.println(dateSchedulingEnd.getTime() - dateSchedulingStart.getTime());
		System.out.println("Program end!");
		return fullItinerary;
	}
	
	private int getTimeFromDistance(double d) {
		if (d < 0) {
			System.err.println("Negative distance found in getTimeFromDistance()!");
			return 0;
		}
		
		d = d * 1.3;
		
		double velocity;
		if (d < 0.5)
			velocity = 8;
		else
			velocity = 100.0 * (1.0 - 0.8 / Math.pow(d, 0.2));
		return (int)(d / velocity * 60);
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

	
	/*@RequestMapping(method=RequestMethod.GET, value="/mip2")
	public @ResponseBody void testMipModel2() throws FileNotFoundException, IOException {
	
		DailyInfo dailyInfo = new DailyInfo();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.YEAR, 2015);
		calendar.set(Calendar.MONTH, Calendar.SEPTEMBER);
		calendar.set(Calendar.DAY_OF_MONTH, 19);
		dailyInfo.setCalendarForDay(calendar);
		dailyInfo.setDayOfWeek(calendar.get(Calendar.DAY_OF_WEEK) - 1); //0:Sun, 1:Mon, ..., 6:Sat
		dailyInfo.setStartTimeInMinutes(8 * 60);
		dailyInfo.setEndTimeInMinutes(13 * 60);
		dailyInfo.setMustCounty("all");
		String dayOfWeekStr = daysOfWeekName[calendar.get(Calendar.DAY_OF_WEEK) - 1];
		String itineraryStartPoiId = null;
		itineraryStartPoiId = searchNearbyPoiIdByCoordinate(25.0584759, 121.5548724);
		List<Map<String, Object>> queryResultInit = analyticsjdbc.queryForList("SELECT * FROM Scheduling2_bkup WHERE poiId = '" + itineraryStartPoiId + "';");
		Poi itineraryStartPoi = new Poi();
		itineraryStartPoi.setFromQueryResult(queryResultInit.get(0), dayOfWeekStr);
		itineraryStartPoi.setMustPoi(false);
		dailyInfo.setStartPoi(itineraryStartPoi);
		List<Poi> poiList = new ArrayList<Poi>();
		try (FileInputStream fileInputStream = new FileInputStream("C:/Users/wu/Downloads/poi.txt");
				InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
				BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
			String line = bufferedReader.readLine();
			while (line != null) {
				List<Map<String, Object>> queryResult = analyticsjdbc.queryForList("SELECT * FROM Scheduling2_bkup WHERE poiId = '" + line + "';");
				if (queryResult.size() > 0) {
					Poi poi = new Poi();
					poi.setFromQueryResult(queryResult.get(0), dayOfWeekStr);
					poi.setMustPoi(false);
					
					poiList.add(poi);
				}
				line = bufferedReader.readLine();
			}
		}
		dailyInfo.setPoiList(poiList);
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
		
				
		// solve by LINDO
		
		MipModel2 mipModel2 = new MipModel2();
		mipModel2.setDataUsedInMip(dailyInfo, travelTime);
		List<TourEvent> mipResults;
		List<Poi> mipResultsPoi;
		int status = mipModel2.run1();
		if (status == 1) {
			mipModel2.examineSolution();
			mipResults = mipModel2.extractSolution();
			mipResultsPoi = mipModel2.extractSolutionPoi();
			
		} else {
			System.out.println("Optimality not reached when solving by LINDO!");
		}
		mipModel2.endLindoEnvironment();

	}*/
	
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
	

	private double getGreatCircleDistance(double[] start, double[] end) { //Equirectangular approximation //{latitude緯度, longitude經度}
		double PI = 3.14159265;
		double R = 6371.229; //km

		double x = (end[1] - start[1]) * PI / 180 * Math.cos((start[0] + end[0]) / 2 * PI / 180);
		double y = (end[0] - start[0]) * PI / 180;
		return Math.hypot(x, y) * R; //km
	}

	private double getGreatCircleDistance(String start, String end) { //Equirectangular approximation
		return getGreatCircleDistance(Poi.parseCoordinate(start), Poi.parseCoordinate(end));
	}
	
/*	private int[] parseMinutes_old(String s) {
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
	private class MustCountyInfo {
		private List<Integer> dayIndexInItinerary = new ArrayList<Integer>();
		private List<Poi> mustPoiList = new ArrayList<Poi>();
		
		public List<Integer> getDayIndexInItinerary() {
			return dayIndexInItinerary;
		}
		public void setDayIndexInItinerary(List<Integer> dayIndexInItinerary) {
			this.dayIndexInItinerary = dayIndexInItinerary;
		}
		public List<Poi> getMustPoiList() {
			return mustPoiList;
		}
		public void setMustPoiList(List<Poi> mustPoiList) {
			this.mustPoiList = mustPoiList;
		}
	}*/
	
	private class ClusterInfo {
		private List<Poi> poiList = new ArrayList<Poi>();
		private double[] poiCenter = new double[2];
		
		public void calculatePoiCenter() {
			poiCenter = Poi.calculatePoiCenter(poiList);
		}
		public int getCountOfOpenPoi(int dayOfWeek) {
			int count = 0;
			for (Poi poi : poiList) {
				String openHoursStr = poi.getOpenHoursOfSevenDays()[dayOfWeek];
				count += ( openHoursStr == null || ! openHoursStr.equals("close") ) ? 1 : 0;
			}
			return count;
		}
		
		public List<Poi> getPoiList() {
			return poiList;
		}
		public void setPoiList(List<Poi> poiList) {
			this.poiList = poiList;
		}
		public double[] getPoiCenter() {
			return poiCenter;
		}
		public void setPoiCenter(double[] poiCenter) {
			this.poiCenter = poiCenter;
		}
		
	}
	
	private class PoiPair {
		private int poiIdx1;
		private int poiIdx2;
		private double distance;
		
		public PoiPair(int poiIdx1, int poiIdx2, double distance) {
			this.poiIdx1 = poiIdx1;
			this.poiIdx2 = poiIdx2;
			this.distance = distance;
		}
		@Override
		public String toString() {
			String str = poiIdx1 + " " + poiIdx2 + " " + distance;
			return str;
		}
		public int getPoiIdx1() {
			return poiIdx1;
		}
		public void setPoiIdx1(int poiIdx1) {
			this.poiIdx1 = poiIdx1;
		}
		public int getPoiIdx2() {
			return poiIdx2;
		}
		public void setPoiIdx2(int poiIdx2) {
			this.poiIdx2 = poiIdx2;
		}
		public double getDistance() {
			return distance;
		}
		public void setDistance(double distance) {
			this.distance = distance;
		}
	}
	private class PoiPairComparator implements Comparator<PoiPair> {
		@Override
		public int compare(PoiPair o1, PoiPair o2) {
			if (o1 == null || o2 == null)
				return 0;
			if (o1.getDistance() < o2.getDistance())
				return -1;
			if (o1.getDistance() > o2.getDistance())
				return 1;
			return 0;
		}
	}
	
}

