package tw.org.iii.st.analytics.controller;

import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.RecoverableDataAccessException;
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
import tw.org.iii.model.DistanceAndTimeEstimation;
import tw.org.iii.model.GeoPoint;
import tw.org.iii.model.HungarianAlgorithm;
import tw.org.iii.model.County;

//TODO: for POI which is not in Table Scheduling2, get data from PoiFinalView2.
//TODO: several consecutive days without mustCounty --> smarter POI choices

@RestController
@RequestMapping("/Scheduling2")
public class Scheduling2 {
	
	@Autowired
	@Qualifier("analyticsJdbcTemplate")
	private JdbcTemplate analyticsjdbc;
	
	//---- Parameters ----//
	private static final int startTimeEachDayInMinutes = 8 * 60;
	private static final int endTimeEachDayInMinutes = 19 * 60;
	private static final int earlistLunchTimeInMinutes = 12 * 60;
	private static final int minutesForLunch = 60;
	
	private static final int numPoiForSelectionUpperBound = 10;
	
	private static final int mustMergingDistanceMeasureBound = 5;
	
	//---- Constants ----//
	private static final int numCounty = 22;
	
	//===========================================
	//    API main function: create itinerary
	//===========================================
	@RequestMapping("/QuickPlan")
	public @ResponseBody
	List<TourEvent> createItinerary(@RequestBody SchedulingInput input) throws Exception {
		System.out.println("Program begin!");
		
		// database connection test
		while (true) {
			int count = 0;
			try {
//				analyticsjdbc.queryForList("SELECT poiId FROM Scheduling2 LIMIT 1");
				analyticsjdbc.queryForList("SELECT poiId FROM Scheduling2 WHERE poiId = 'e6449cc1-6047-f3a2-1618-5157c3765714'");
				break;
				
			} catch (RecoverableDataAccessException e) {
				e.printStackTrace();
				++count;
				System.err.println("Connection failure count " + count);
				if (count == 5) {
					throw e;
				}
			}
		}
		
		Date dateSchedulingStart = new Date(System.currentTimeMillis());
		
		QueryDatabase queryDatabase = new QueryDatabase(analyticsjdbc, true);
		boolean isDisplaySql = true;
		queryDatabase.setDisplay(isDisplaySql);
		
		//--------------------
		//   prepare data
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
		boolean[] userInputTheme = new boolean[8];
		for (String themeStr : themeList) {
			userInputTheme[Integer.parseInt(themeStr.substring(3, 4))] = true;
		}
		
		int looseType = input.getLooseType(); //寬鬆 = 1, 適中 = 0, 緊湊 = -1
		List<String> mustPoiIdList = input.getMustPoiList();
		
		// itinerary start and end location
		GeoPoint gps = input.getGps();
		Poi itineraryStartPoi = null;
		if (gps != null) {
			queryDatabase.display("-get itinerary start Poi");
			itineraryStartPoi = queryDatabase.getNearbyPoiByCoordinate(gps.getLat(), gps.getLng(), false, looseType); //could be null
		}
		if (itineraryStartPoi == null) {
			String itineraryStartPoiId = "e6449cc1-6047-f3a2-1618-5157c3765714"; //台北火車站 POINT(25.047767 121.517114)
			queryDatabase.display("-get itinerary start Poi");
			itineraryStartPoi = queryDatabase.getPoiByPoiId(itineraryStartPoiId, false, looseType);
		}
		
		Poi itineraryEndPoi = new Poi(itineraryStartPoi);
		
		//--------------------------------------------
		//  initialize daily itinerary information
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
		
		//----------------------------------
		// stage 1: assign POI to each day
		//----------------------------------
		
		// get must POI from database
		List<Poi> mustPoiList = new ArrayList<Poi>();
		if (! mustPoiIdList.isEmpty()) {
			queryDatabase.display("-get must Poi");
			mustPoiList = queryDatabase.getPoiListByPoiIdList(mustPoiIdList, true, looseType);
		}
		
		// initialize must county info map
		Map<Integer, MustCountyInfo> mustCountyInfoMap = new HashMap<Integer, MustCountyInfo>(); //key: must county index //value: information
		for (int i = 0; i < fullInfo.size(); ++i) {
			if (! fullInfo.get(i).getMustCounty().equals("all")) {
				int county = fullInfo.get(i).getMustCountyIndex();
				if ( mustCountyInfoMap.containsKey(county) )
					mustCountyInfoMap.get(county).days.add(i);
				else {
					MustCountyInfo mustCountyInfo = new MustCountyInfo();
					mustCountyInfo.days.add(i);
					mustCountyInfoMap.put(county, mustCountyInfo);
				}
			}
		}
		
		// for "must county", configure "merging quota" and "additional POI list"
		Map<Integer, Integer> mergingQuota = new HashMap<Integer, Integer>(); //key: mustCountyIndex, value: quota
		mergingQuota.put(0, 100); //for all non-must county, mustCountyIndex <-- 0, quota <-- 100
		
		List<Poi> additionalPoiList = new ArrayList<Poi>(); //if no must-POI belongs to a must county, add POI of that county
		
		// (1)adjust merging quota (2)add additional POI, if there is any must county
		if (isAnyMustCounty) {
			
			int[] numMustPoiInCounty = new int[numCounty + 1];
			for (Poi poi : mustPoiList)
				++numMustPoiInCounty[ poi.getCountyIndex() ];
			
			for (Integer i : mustCountyInfoMap.keySet()) {
				int numDayOfCounty = mustCountyInfoMap.get(i).getNumDays();
				if (numMustPoiInCounty[i] > numDayOfCounty) {
					mergingQuota.put(i, numMustPoiInCounty[i] - numDayOfCounty);
					
				} else {
					mergingQuota.put(i, 0);
					
					// add additional POI
					if (numMustPoiInCounty[i] < numDayOfCounty) {
						Set<Integer> counties = new HashSet<Integer>();
						counties.add(i);
						
						List<Poi> poiList = queryDatabase.getRandomPoiListByCountyAndOpenDay(
								numDayOfCounty - numMustPoiInCounty[i],
								counties, "free", looseType); // note: not force "allOpen" here, so open days have to be checked in stage 2
						
						additionalPoiList.addAll(poiList);
					}
				}
			}
		}
		// future: for each county, may generate other POIs if current ones (must & additional) are too closed to each other
		
		// collect base POI list
		List<Poi> basePoiList = new ArrayList<Poi>();
		basePoiList.addAll(mustPoiList);
		basePoiList.addAll(additionalPoiList);
		System.out.println("mustPoiList");
		for (Poi poi : mustPoiList)
			System.out.println(poi);
		System.out.println("additionalPoiList");
		for (Poi poi : additionalPoiList)
			System.out.println(poi);
		
		// prepare initial cluster information
		int[] clusterOfBasePoi = new int[basePoiList.size()]; //POI --> cluster
		int[] assignedMustCounty = new int[basePoiList.size()]; //cluster --> county (note: we assign a representing must county to a cluster)
		for (int i = 0; i < clusterOfBasePoi.length; ++i) {
			clusterOfBasePoi[i] = i;
			int countyIdx = basePoiList.get(i).getCountyIndex();
			assignedMustCounty[i] = mustCountyInfoMap.containsKey(countyIdx) ? countyIdx : 0; //if not a must county --> 0
		}
		
		boolean[] isClusterIdxUsed = new boolean[basePoiList.size()];
		Arrays.fill(isClusterIdxUsed, true);
		
		int numClusterUsed = isClusterIdxUsed.length;
		
		// clustering when #basePOI > 1
		if (basePoiList.size() > 1) {
			// obtain pairwise distances
			List<String> basePoiIdList = new ArrayList<String>();
			for (Poi poi : basePoiList)
				basePoiIdList.add( poi.getPoiId() );
			
			double[][] distance = new double[basePoiIdList.size()][basePoiIdList.size()];
			queryDatabase.getDistanceAndTimeMatrix( basePoiIdList, distance, null );
			
			// put (half) distances into priority queue
			Comparator<PoiPair> comparator = new PoiPairComparator();
			PriorityQueue<PoiPair> priorityQueue = new PriorityQueue<PoiPair>( basePoiList.size() * basePoiList.size() / 2, comparator );
			
			for (int i = 0; i < basePoiList.size() - 1; ++i) {
				for (int j = i + 1; j < basePoiList.size(); ++j) {
					priorityQueue.add( new PoiPair(i, j, distance[i][j]) );
				}
			}
			
			// display priority queue
//			System.out.println("Display priorityQueue of PoiPair:");
//			for (PoiPair poiPair : priorityQueue)
//				System.out.println(poiPair);
			
			// perform clustering
			while (! priorityQueue.isEmpty()) {
				PoiPair poiPair = priorityQueue.poll();
				
				if (poiPair.distanceMeasure > mustMergingDistanceMeasureBound && numClusterUsed <= numDay)
					break;
				else {
					if ( clusterOfBasePoi[poiPair.poiIdx1] != clusterOfBasePoi[poiPair.poiIdx2] ) { //in different clusters
						int removed = 0;
						int assignedMustCountyIdx1 = assignedMustCounty[ clusterOfBasePoi[poiPair.poiIdx1] ];
						int assignedMustCountyIdx2 = assignedMustCounty[ clusterOfBasePoi[poiPair.poiIdx2] ];
						int quota1 = mergingQuota.get(assignedMustCountyIdx1);
						int quota2 = mergingQuota.get(assignedMustCountyIdx2);
						
						// check whether to merge these two clusters
						if ( assignedMustCountyIdx1 == assignedMustCountyIdx2 ) { //same mustCounty
							if ( quota2 > 0 ) {
								removed = 2;
								mergingQuota.put(assignedMustCountyIdx2, quota2 - 1);
							}
						} else { //different mustCounty
							if ( quota1 > quota2 ) {
								removed = 1;
								mergingQuota.put(assignedMustCountyIdx1, quota1 - 1);
							}
							else if ( quota2 > quota1 ) {
								removed = 2;
								mergingQuota.put(assignedMustCountyIdx2, quota2 - 1);
							}
							else if ( quota2 > 0 ) {
								removed = 2; // can be randomly picked
								mergingQuota.put(assignedMustCountyIdx2, quota2 - 1);
							}
						}
						
						// merge two clusters
						if (removed > 0) {
							int removedCluster = (removed == 1) ? clusterOfBasePoi[poiPair.poiIdx1] : clusterOfBasePoi[poiPair.poiIdx2];
							int substituteCluster = (removed == 1) ? clusterOfBasePoi[poiPair.poiIdx2] : clusterOfBasePoi[poiPair.poiIdx1];
							for (int i = 0; i < clusterOfBasePoi.length; ++i) {
								if ( clusterOfBasePoi[i] == removedCluster )
									clusterOfBasePoi[i] = substituteCluster;
							}
							
							isClusterIdxUsed[ removedCluster ] = false;
							--numClusterUsed;
						}
						
						
					}
				}
			}
//			Display.print_1D_Array(isClusterIdxUsed, "Display isClusterIdxUsed");
//			Display.print_1D_Array(clusterOfBasePoi, "Display clusterOfPoi");
		}
		
		// wrap up clusters of POIs after merging
		int[] finalClusterIdx = new int[basePoiList.size()];
		Arrays.fill(finalClusterIdx, -1);
		int[] finalAssignedMustCounty = new int[numClusterUsed];
		int newIdx = 0;
		for (int i = 0; i < basePoiList.size(); ++i) {
			if (isClusterIdxUsed[i]) {
				finalClusterIdx[i] = newIdx;
				finalAssignedMustCounty[newIdx] = assignedMustCounty[i];
				++newIdx;
			}
		}
		
		if (newIdx != numClusterUsed) {
			System.err.println("Number of used clusters is wrong after merging!!");
			return null;
		}
		int[] finalClusterOfBasePoi = new int[basePoiList.size()];
		for (int i = 0; i < basePoiList.size(); ++i)
			finalClusterOfBasePoi[i] = finalClusterIdx[ clusterOfBasePoi[i] ];
		
		// display clusters - 0
//		System.out.println();
//		System.out.println("Clustering result:");
//		for (int i = 0; i < numClusterUsed; ++i) {
//			for (int j = 0; j < basePoiList.size(); ++j) {
//				if (finalClusterOfBasePoi[j] == i)
//					System.out.println(basePoiList.get(j) + " : " + i);
//			}
//		}
//		for (int i = 0; i < finalAssignedMustCounty.length; ++i) {
//			int countyId = finalAssignedMustCounty[i];
//			if (countyId == 0)
//				System.out.println("Cluster " + i + " : 不限");
//			else
//				System.out.println("Cluster " + i + " : TW" + countyId + " " + County.getCountyName(countyId));
//		}
		
		// construct cluster information
		List<Cluster> clusterList = new ArrayList<Cluster>();
		for (int i = 0; i < numClusterUsed; ++i) {
			Cluster cluster = new Cluster();
			cluster.representingMustCounty = finalAssignedMustCounty[i];
			clusterList.add(cluster);
		}
		for (int i = 0; i < basePoiList.size(); ++i) {
			clusterList.get( finalClusterOfBasePoi[i] ).poiList.add( basePoiList.get(i) );
		}
		
		// display clusters - 1
//		System.out.println();
//		System.out.println("clusterList");
//		for (int i = 0; i < clusterList.size(); ++i) {
//			System.out.println(i + "\n" + clusterList.get(i));
//		}
		
		// display mustCountyInfo
//		System.out.println("mustCountyInfoMap");
//		for (Map.Entry<Integer, MustCountyInfo> entry : mustCountyInfoMap.entrySet()) {
//			System.out.println(entry.getKey());
//			System.out.println(entry.getValue());
//		}
		
		// move appropriate clusters from "clusterList" to "mustCountyInfoMap"
		for (Integer countyIdx : mustCountyInfoMap.keySet()) {
			for (int i = 0; i < mustCountyInfoMap.get(countyIdx).getNumDays(); ++i) {
				boolean isFound = false;
				int j;
				for (j = 0; j < clusterList.size(); ++j) {
					Cluster cluster = clusterList.get(j);
					if (cluster.representingMustCounty == countyIdx) {
						mustCountyInfoMap.get(countyIdx).assignedClusters.add(cluster);
						clusterList.remove(j);
						isFound = true;
						break;
					}
				}
				if (! isFound) {
					System.err.println("!!! Not enough clusters with assignedMustCounty = " + countyIdx + " !!!");
					return null;
				}
			}
		}
		
		// display clusters - after moving
		System.out.println();
		System.out.println("clusterList");
		for (int i = 0; i < clusterList.size(); ++i) {
			System.out.println(i + "\n" + clusterList.get(i));
		}
		
		// display mustCountyInfo
		System.out.println("mustCountyInfoMap");
		for (Map.Entry<Integer, MustCountyInfo> entry : mustCountyInfoMap.entrySet()) {
			System.out.println(entry.getKey());
			System.out.println(entry.getValue());
		}
		
		// put poiList into fullInfo
		for (Map.Entry<Integer, MustCountyInfo> entry : mustCountyInfoMap.entrySet()) {
			
			MustCountyInfo mustCountyInfo = entry.getValue();
			
			for (int i = 0; i < mustCountyInfo.getNumDays(); ++i) {
				fullInfo.get( mustCountyInfo.days.get(i) ).getPoiList().addAll( mustCountyInfo.assignedClusters.get(i).poiList );
			}
		}
		
		// construct slot information
		List<Slot> slotList = new ArrayList<Slot>();
		
		// put in slot data: first county, last county, days
		boolean isSlotStart = false;
		int tempCounty = itineraryStartPoi.getCountyIndex();
		Slot tempSlot = null;
		int totalSlotCapacity = 0;
		
		for (int i = 0; i < fullInfo.size(); ++i) {
			
			if (fullInfo.get(i).getMustCounty().equals("all")) {
				if (! isSlotStart) {
					tempSlot = new Slot();
					tempSlot.firstCounty = tempCounty;
					tempSlot.days.add(i);
					
					isSlotStart  = true;
				} else
					tempSlot.days.add(i);
				
				++totalSlotCapacity;
			} else {
				if (isSlotStart) {
					tempSlot.lastCounty = fullInfo.get(i).getMustCountyIndex();
					slotList.add(tempSlot);
					
					isSlotStart = false;
				}
				tempCounty = fullInfo.get(i).getMustCountyIndex();
			}
		}
		if (isSlotStart) {
			tempSlot.lastCounty = itineraryEndPoi.getCountyIndex();
			slotList.add(tempSlot);
		}
		
		// put in slot data: feasible county
		for (int i = 0; i < slotList.size(); ++i) {
			// feasible county
			slotList.get(i).feasibleCounty.add( slotList.get(i).firstCounty );
			slotList.get(i).feasibleCounty.add( slotList.get(i).lastCounty );
			List<Integer> intermediateCounty = County.getIntermediateCounty(slotList.get(i).firstCounty, slotList.get(i).lastCounty);
			for (Integer j : intermediateCounty)
				slotList.get(i).feasibleCounty.add(j);
		}
		
		// display slot list
		System.out.println("slotList");
		for (int i = 0; i < slotList.size(); ++i) {
			System.out.print(i + "\n");
			System.out.println(slotList.get(i));
		}
		
		// assign clusters to slots
		if (clusterList.size() > 0) {
			
			if (slotList.size() == 1) {
				slotList.get(0).assignedClusters.addAll( clusterList );
				
				for (Cluster cluster : clusterList) {
					cluster.score = DistanceAndTimeEstimation.getShortestTime_BetweenCounties(cluster.poiList, slotList.get(0).feasibleCounty);
				}
				
			} else { // slotList.size() should > 1
				
				// create assignment cost matrix
				double[][] assignmentCostMatrix = new double[ clusterList.size() ][ totalSlotCapacity ];
				int[] slotIndex = new int[ totalSlotCapacity ];
				for (int i = 0; i < clusterList.size(); ++i) {
					int k = 0;
					for (int s = 0; s < slotList.size(); ++s) {
						double cost = DistanceAndTimeEstimation.getShortestTime_BetweenCounties(clusterList.get(i).poiList, slotList.get(s).feasibleCounty);
						
						for (int j = 0; j < slotList.get(s).getNumDays(); ++j) {
							assignmentCostMatrix[i][k] = cost;
							slotIndex[k++] = s;
						}
					}
				}
				HungarianAlgorithm hungarianAlgorithm = new HungarianAlgorithm(assignmentCostMatrix);
				int[] result = hungarianAlgorithm.execute();
//				for (int i = 0; i < result.length; ++i)
//					System.out.println("cluster " + i + " in slot " + slotIndex[result[i]]);
//				System.out.println();
				
				// move clusters into slots
				for (int i = 0; i < result.length; ++i) {
					clusterList.get(i).score = assignmentCostMatrix[i][result[i]];					
					slotList.get( slotIndex[result[i]] ).assignedClusters.add( clusterList.get(i) );
				}
			}
			clusterList.clear();
			
			// display slot list
			System.out.println("slotList");
			for (int i = 0; i < slotList.size(); ++i) {
				System.out.print(i + "\n");
				System.out.println(slotList.get(i));
			}
		}
		
		// in each slot, fill in additional clusters if there is a shortage 
		for (Slot slot : slotList) {
			int numAdditionalClusters = slot.getNumDays() - slot.getNumAssignedClusters();
			
			if (numAdditionalClusters > 0) {
				List<Poi> poiList = queryDatabase.getRandomPoiListByCountyAndOpenDay(
						numAdditionalClusters, slot.feasibleCounty , "free", looseType);
				for(Poi poi : poiList) {
					Cluster cluster = new Cluster();
					cluster.poiList.add(poi);
					cluster.score = 0; //selected inside the counties
					slot.assignedClusters.add(cluster);
				}
			}
		}
		
		// in each slot, arrange the clusters and put poiList into fullInfo
		for (Slot slot : slotList) {
			
			// arrange the clusters
			if (slot.getNumDays() > 1) {
				for (Cluster cluster : slot.assignedClusters) {
					if (slot.firstCounty != slot.lastCounty) {
						
						double scoreToFirst = DistanceAndTimeEstimation.getShortestTime_BetweenCounties(cluster.poiList, slot.firstCounty);
						if (cluster.score == 0) {
							cluster.score = scoreToFirst;
							
						} else {
							double scoreToLast = DistanceAndTimeEstimation.getShortestTime_BetweenCounties(cluster.poiList, slot.lastCounty);
							if (scoreToFirst <= scoreToLast)
								cluster.score = - scoreToFirst;
							else
								cluster.score = scoreToFirst;
						}
					}
				}
//				System.out.println("before:");
//				System.out.println("slot.assignedClusters");
//				for (int i = 0; i < slot.assignedClusters.size(); ++i) {
//					System.out.println(i + "\n" + slot.assignedClusters.get(i));
//				}

				Comparator<Cluster> comparator = new ClusterComparator();
				Collections.sort(slot.assignedClusters, comparator);
				
//				System.out.println("after");
//				System.out.println("slot.assignedClusters");
//				for (int i = 0; i < slot.assignedClusters.size(); ++i) {
//					System.out.println(i + "\n" + slot.assignedClusters.get(i));
//				}
			}
			
			// put poiList into fullInfo
			for (int i = 0; i < slot.getNumDays(); ++i) {
				fullInfo.get( slot.days.get(i) ).getPoiList().addAll( slot.assignedClusters.get(i).poiList );
			}
		}
		
		for (int i = 0; i < numDay; ++i) {
			System.out.println("day " + i + " " + fullInfo.get(i).getPoiList().size());
		}
		
		//------------------------------------------
		// stage 2: generate complete itinerary
		//------------------------------------------
		// complete itinerary
		for (int i = 0; i < numDay; ++i) {
			DailyInfo dailyInfo = fullInfo.get(i);
			
			// check if POI in this day are ALL CLOSE
			double[] originalCenteredPoi = null;
			Iterator<Poi> iterator = dailyInfo.getPoiList().iterator();
			while (iterator.hasNext()) {
				Poi poi = iterator.next();
				
				String openHoursOfThisDay = poi.getOpenHoursOfADay(dailyInfo.getDayOfWeek());
				if  (openHoursOfThisDay != null && openHoursOfThisDay.equals("close")) {
					
					if (originalCenteredPoi == null)
						originalCenteredPoi = Poi.calculatePoiCenter( dailyInfo.getPoiList() );
					
					iterator.remove();
				}	
			}
			// if ALL CLOSE, get a new center POI which is open
			if (dailyInfo.getPoiList().isEmpty()) {
				queryDatabase.display("-when all Poi in this day are close, get centered poiId");
				
				String centerPoiId = queryDatabase.getNearbyPoiIdByCoordinate( originalCenteredPoi );
				
				double distanceLB = 0;
				double distanceUB = 0.3;
				double distanceUBIncrement = 0.3;
				
				Set<Integer> counties = new HashSet<Integer>();
				
				if (! dailyInfo.getMustCounty().equals("all")) {
					counties.add( Integer.parseInt( dailyInfo.getMustCounty().replaceAll("[^0-9]", "")) );
					distanceUB = 5;
					distanceUBIncrement = 5;
				}
				queryDatabase.display("-get random Poi in radius of centered poiId");
				List<Poi> foundPoiList = queryDatabase.getRandomPoiListByCenterPoiIdAndCountyAndOpenDay(
						1, 1, centerPoiId, distanceLB, distanceUB, distanceUBIncrement,
						counties, dailyInfo.getDayOfWeek(), "RAND()", looseType);
				if (foundPoiList.size() == 1)
					dailyInfo.getPoiList().add( foundPoiList.get(0) );
				else
					;
			}
			
			if (dailyInfo.getStartPoi() == null) {
				System.err.println("Oooooh, shouldn't happen!!");
				return null;
			}
			if (dailyInfo.getEndPoi() == null) { //won't be the last day
				queryDatabase.display("-get centered Poi by Poi List. use next day's Poi to guide this day's direction");
				Poi tempPoi = queryDatabase.getCenteredPoiByPoiList( fullInfo.get(i+1).getPoiList(), looseType );
				dailyInfo.setEndPoi( tempPoi );
			}
			
			List<Poi> poiList = dailyInfo.getPoiList();
			System.out.println("--- Day " + (i+1) + " --- before adding candidate POIs:");
			for (Poi poi : poiList)
				System.out.println(poi);
			
			int numCandidatePoi = Math.max(numPoiForSelectionUpperBound - dailyInfo.getPoiList().size(), 0);
			
			if (numCandidatePoi > 0) {
				
				// get counties used for searching POI
				Set<Integer> countiesForSearch = new HashSet<Integer>();
				if (dailyInfo.getMustCounty().equals("all")) {
					for (Poi poi : poiList) {
						int j = poi.getCountyIndex();
						countiesForSearch.add(j);
						
						List<Integer> nearbyCounty = County.getNearbyCounty(j);
						countiesForSearch.addAll(nearbyCounty);
					}
				} else {
					countiesForSearch.add( dailyInfo.getMustCountyIndex() );
				}
				
				queryDatabase.display("-get centered poiId. use to select candidate Poi");
				String centerPoiId = queryDatabase.getCenteredPoiIdByPoiList(poiList);
				
				//select candidate POIs from database
				int numSelectionLB = 30;
				int numSelectionUB = 50;
				double distanceLB = 0;
				double distanceUB = 15;
				double distanceUBIncrement = 5;
				queryDatabase.display("-get candidate Poi");
				List<Poi> foundPoiList = queryDatabase.getRandomPoiListByCenterPoiIdAndCountyAndOpenDay(
						numSelectionLB, numSelectionUB, centerPoiId, distanceLB, distanceUB, distanceUBIncrement,
						countiesForSearch, dailyInfo.getDayOfWeek(), "checkinTotal DESC", looseType);
				
				// select #=numCandidatePoi from foundPoiList by probability/scores
				double[] scores = Poi.calculateScores(foundPoiList, userInputTheme);
				//Display.print_1D_Array(scores, "scores");
				
				List<Integer> selectedIndex = selectByProbabilityWithoutReplacement(scores, numCandidatePoi);
				//Display.print_1D_List(selectedIndex);
				for (Integer j : selectedIndex) {
					poiList.add( foundPoiList.get(j) );
				}
				//NOTE: for non-must POI already in poiList ..
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
			
			// obtain distance and travel time
			List<String> augmentedPoiIdList = new ArrayList<String>();
			
			augmentedPoiIdList.add( dailyInfo.getStartPoi().getPoiId() );
			for (Poi poi : poiList)
				augmentedPoiIdList.add( poi.getPoiId() );
			augmentedPoiIdList.add( dailyInfo.getEndPoi().getPoiId() );
			
			double[][] distance = new double[ augmentedPoiIdList.size() ][ augmentedPoiIdList.size() ]; // = poiList.size() + 2
			int[][] time = new int[ augmentedPoiIdList.size() ][ augmentedPoiIdList.size() ];
			queryDatabase.getDistanceAndTimeMatrix( augmentedPoiIdList, distance, null );
			Display.print_2D_Array(distance, "distance");
			Display.print_2D_Array(time, "time");
			
			// temp...
			int[][] travelTime = new int[poiList.size() + 2][poiList.size() + 2];
			for (int j = 0; j < poiList.size() + 2; ++j)
				for (int k = 0; k < poiList.size() + 2; ++k)
						travelTime[j][k] = getTimeFromDistance( distance[j][k] );
			Display.print_2D_Array(travelTime, "Display travelTime");
			
			
			double[] poiScore = new double[poiList.size()];
			
			//-----------------------------------------
			//  construct tour by insertion heuristic
			//-----------------------------------------
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
						int thisTime = Poi.findShortestTimeFromPoiToPoiList( rawPoiList.get(k), resultPoiList, travelTime);
						if (thisTime < shortestTime) {
							shortestTime = thisTime;
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
						int thisTime = Poi.findShortestTimeFromPoiToPoiList( rawPoiList.get(k), resultPoiList, travelTime);
						if (thisTime < shortestTime) {
							shortestTime = thisTime;
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
				itinerary.remove( itinerary.size() - 1 );
			}
			if (i > 0 && ! dailyInfo.isStartPoiUseStayTime()) {
				resultPoiList.remove(0);
				itinerary.remove(0);
			}
			
			// error check here: is it possible that distances are too long and no POI are selected in the result?
			
			// link to full itinerary
			fullItineraryPoi.addAll(resultPoiList);
			fullItinerary.addAll(itinerary);
			
			if (i < numDay - 1)
				fullInfo.get(i + 1).setStartPoi( resultPoiList.get( resultPoiList.size() - 1 ) );
		}
		
		
		//display full itinerary
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
					+ fullItineraryPoi.get(i)
					+ ( fullItineraryPoi.get(i).getCountyId().equals(fullInfo.get(d-1).getMustCounty()) ? " /必縣/" : "" ));
		}
		
		
		Date dateSchedulingEnd = new Date(System.currentTimeMillis());
		System.out.print("Total elapsed:");
		System.out.println(dateSchedulingEnd.getTime() - dateSchedulingStart.getTime());
		System.out.println("Program end!");
		return fullItinerary;
	}
	
	//============
	//    temp
	//============
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
		
		velocity = velocity / 1.4; //!!
		
		return (int)(d / velocity * 60);
	}
	
	//==================================================================
	//    For API test -- get great circle distance from coordinates
	//==================================================================
	@RequestMapping(method=RequestMethod.GET, value="/dist")
	public @ResponseBody double getGreatCircleDistance(
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
		return Poi.getGreatCircleDistance(p1, p2);
	}
	
	//============================
	//    class MustCountyInfo
	//============================
	private class MustCountyInfo {
		private List<Integer> days = new ArrayList<Integer>();
		private List<Cluster> assignedClusters = new ArrayList<Cluster>();
		
		private int getNumDays() {
			return days.size();
		}
		@Override
		public String toString() {
			String str = "--MustCountyInfo\n[1.days]";
			for (Integer i : days)
				str += " " + i;
			str += "\n[2.assignedClusters]\n";
			for (Cluster c : assignedClusters)
				str += c;
			str += "\n";
			return str;
		}
	}
	
	//===========================================
	//    class Cluster and ClusterComparator
	//===========================================
	private class Cluster {
		private List<Poi> poiList = new ArrayList<Poi>();
		private int representingMustCounty;
		private double score;
		
		@Override
		public String toString() {
			String str = "--Cluster:\n[1.poiList]\n";
			for (Poi poi : poiList)
				str += poi + "\n";
			str += "[2.representingMustCounty] " + representingMustCounty + "\n[3.score]" + score + "\n";
			return str;
		}
	}
	private class ClusterComparator implements Comparator<Cluster> {
		@Override
		public int compare(Cluster o1, Cluster o2) {
			if (o1 == null || o2 == null)
				return 0;
			if (o1.score < o2.score)
				return -1;
			if (o1.score > o2.score)
				return 1;
			return 0;
		}
	}
	
	//==================
	//    class Slot
	//==================
	private class Slot {
		private List<Integer> days = new ArrayList<Integer>(); //start from 0
		private int firstCounty;
		private int lastCounty;
		private Set<Integer> feasibleCounty = new HashSet<Integer>();
		private List<Cluster> assignedClusters = new ArrayList<Cluster>();
		
		private int getNumDays() {
			return days.size();
		}
		private int getNumAssignedClusters() {
			return assignedClusters.size();
		}

		@Override
		public String toString() {
			String str = "--Slot\n[1.days]";
			for (Integer i : days)
				str += " " + i;
			str += "\n[2.firstCounty] " + firstCounty + " [3.lastCounty] " + lastCounty;
			str += "\n[4.feasibleCounty]";
			for (Integer i : feasibleCounty)
				str += " " + i;
			str += "\n[5.assignedClusters]\n";
			for (Cluster c : assignedClusters)
				str += c;
			str += "\n";
			return str;
		}
	}
	
	//===========================================
	//    class PoiPair and PoiPairComparator
	//===========================================
	private class PoiPair {
		private int poiIdx1;
		private int poiIdx2;
		private double distanceMeasure;
		
		private PoiPair(int poiIdx1, int poiIdx2, double distanceMeasure) {
			this.poiIdx1 = poiIdx1;
			this.poiIdx2 = poiIdx2;
			this.distanceMeasure = distanceMeasure;
		}
		@Override
		public String toString() {
			String str = poiIdx1 + " " + poiIdx2 + " " + distanceMeasure;
			return str;
		}
	}
	private class PoiPairComparator implements Comparator<PoiPair> {
		@Override
		public int compare(PoiPair o1, PoiPair o2) {
			if (o1 == null || o2 == null)
				return 0;
			if (o1.distanceMeasure < o2.distanceMeasure)
				return -1;
			if (o1.distanceMeasure > o2.distanceMeasure)
				return 1;
			return 0;
		}
	}
	
	//===========================================================
	//    function: select by probability without replacement
	//===========================================================
	private List<Integer> selectByProbabilityWithoutReplacement(double[] scores, int numSelection) {
		List<Integer> selectedIndex = new ArrayList<Integer>();
		
		if (numSelection >= scores.length) {
			for (int i = 0; i < scores.length; ++i)
				selectedIndex.add(i);
			return selectedIndex;
		}
		
		// calculate cumulative scores
		double[] cumulativeScores = new double[scores.length];
		cumulativeScores[0] = scores[0];
		for (int i = 1; i < scores.length; ++i)
			cumulativeScores[i] = cumulativeScores[i - 1] + scores[i];
		//Display.print_1D_Array(cumulativeScores, "cumulativeScores");
		
		// calculate cumulative probability
		double[] cumulativeProbability = new double[scores.length];
		for (int i = 0; i < scores.length; ++i)
			cumulativeProbability[i] = cumulativeScores[i] / cumulativeScores[scores.length - 1];
		//Display.print_1D_Array(cumulativeProbability, "cumulativeProbability");
		
		// select by probability
		boolean[] isSelected = new boolean[scores.length];
		Random random = new Random();
		int count = 0;
		
		while (count < numSelection) {
			double randomNumber = random.nextDouble();
			int i = 0;
			while (randomNumber >= cumulativeProbability[i])
				++i;
			if (! isSelected[i]) {
				isSelected[i] = true;
				++count;
			}
			//System.out.println(randomNumber);
			//Display.print_1D_Array(isSelected, "isSelected");
			//System.out.println(numSelection + " / " + count);
		}
		
		for (int i = 0; i < isSelected.length; ++i)
			if (isSelected[i])
				selectedIndex.add(i);
		return selectedIndex;
	}
}

