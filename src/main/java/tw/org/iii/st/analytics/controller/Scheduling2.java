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
import tw.org.iii.model.DistanceAndTime;
import tw.org.iii.model.GeoPoint;
import tw.org.iii.model.HungarianAlgorithm;
import tw.org.iii.model.County;

@RestController
@RequestMapping("/Scheduling2")
public class Scheduling2 {
	
	@Autowired
	@Qualifier("analyticsJdbcTemplate")
	private JdbcTemplate analyticsJdbc;
	
	@Autowired
	@Qualifier("stJdbcTemplate")
	private JdbcTemplate stJdbc;
	
	//---- Parameters ----//
	private static final int startTimeEachDayInMinutes = 8 * 60;
	private static final int endTimeEachDayInMinutes = 19 * 60;
	private static final int earlistLunchTimeInMinutes = 12 * 60;
	private static final int minutesForLunch = 60;
	
	private static final int numPoiForSelectionUpperBound = 10;
	
	private static final int mustMergingTimeMeasureBound = 20;
	
	//===========================================
	//    API main function: create itinerary
	//===========================================
	@RequestMapping("/QuickPlan")
	public @ResponseBody
	List<TourEvent> createItinerary(@RequestBody SchedulingInput input) throws Exception {
		System.out.println("Program begin!");
		
//		// database connection test
//		while (true) {
//			int count = 0;
//			try {
////				analyticsjdbc.queryForList("SELECT poiId FROM Scheduling2 LIMIT 1");
//				analyticsJdbc.queryForList("SELECT poiId FROM Scheduling2 WHERE poiId = 'e6449cc1-6047-f3a2-1618-5157c3765714'");
//				break;
//				
//			} catch (RecoverableDataAccessException e) {
//				e.printStackTrace();
//				++count;
//				System.err.println("Connection failure count " + count);
//				if (count == 5) {
//					throw e;
//				}
//			}
//		}
		
		Date dateSchedulingStart = new Date(System.currentTimeMillis());
		
		boolean isDisplaySql = true;
		QueryDatabase queryDatabase = new QueryDatabase(analyticsJdbc, stJdbc, isDisplaySql);
		
		// POI id repository (prevent selecting the same POIs)
		Set<String> poiIdRepository = new HashSet<String>();
//		poiIdRepository.add(itineraryStartPoi.getPoiId());
//		poiIdRepository.add(itineraryEndPoi.getPoiId());
		
		//-------------------------
		//    handle input data
		//-------------------------
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
		Set<String> mustPoiIdSet = new HashSet<String>(input.getMustPoiList());
		List<String> mustPoiIdList = new ArrayList<String>(mustPoiIdSet);
		
		// itinerary start and end location
		GeoPoint gps = input.getGps();
		Poi itineraryStartPoi = null;
		if (gps != null) {
			queryDatabase.display("-get itinerary start POI");
			itineraryStartPoi = queryDatabase.getNearbyPoiByCoordinate(gps.getLat(), gps.getLng(), false, looseType, true); //could be null
		}
		if (itineraryStartPoi == null) {
			int startCountyIdx = County.getCountyIndex( input.getCityList().get(0) );
			if (startCountyIdx == 0)
				startCountyIdx = 2; //default for now: Taipei city
			queryDatabase.display("-get itinerary start POI");
			itineraryStartPoi = queryDatabase.getNearbyPoiByCoordinate( County.defaultStartCoordinate[startCountyIdx - 1], false, looseType, true );
		}
		
		Poi itineraryEndPoi = new Poi(itineraryStartPoi);
		
		// if start POI happens to be a must POI 
		if (mustPoiIdList.contains(itineraryStartPoi.getPoiId())) {
			itineraryStartPoi.setMustPoi(true);
			mustPoiIdList.remove( itineraryStartPoi.getPoiId() );
			
			poiIdRepository.add(itineraryStartPoi.getPoiId());
		}
		
		//----------------------------------------------
		//    initialize daily itinerary information
		//----------------------------------------------
		boolean isAnyMustCounty = false;
		List<DailyInfo> fullInfo = new ArrayList<DailyInfo>();
		
		for (int i = 0; i < numDay; ++i) {
			DailyInfo dailyInfo = new DailyInfo();
			
			Calendar calendar = (Calendar)itineraryStartCalendar.clone();
			calendar.add(Calendar.DAY_OF_MONTH, i);
			calendar.set(Calendar.HOUR_OF_DAY, 0);
			calendar.set(Calendar.MINUTE, 0);
			calendar.set(Calendar.SECOND, 0);
			calendar.set(Calendar.MILLISECOND, 0);
			dailyInfo.setCalendarThisDayAtMidnight(calendar);
			
			dailyInfo.setDayOfWeek(dailyInfo.getCalendarThisDayAtMidnight().get(Calendar.DAY_OF_WEEK) - 1); //0:Sun, 1:Mon, ..., 6:Sat
			
			int startTimeInMinutes;
			int endTimeInMinutes;
			if (numDay == 1) {
				startTimeInMinutes = itineraryStartCalendar.get(Calendar.HOUR_OF_DAY) * 60 + itineraryStartCalendar.get(Calendar.MINUTE);
				endTimeInMinutes = itineraryEndCalendar.get(Calendar.HOUR_OF_DAY) * 60 + itineraryEndCalendar.get(Calendar.MINUTE);
			} else {
				startTimeInMinutes = startTimeEachDayInMinutes;
				endTimeInMinutes = endTimeEachDayInMinutes;
			}
			dailyInfo.setStartTimeInMinutes(startTimeInMinutes);
			dailyInfo.setEndTimeInMinutes(endTimeInMinutes);
			
			dailyInfo.setMustCounty(input.getCityList().get(i));
			if (! input.getCityList().get(i).equals("all"))
				isAnyMustCounty = true;
			
			if (i == 0) {
				dailyInfo.setStartPoi(itineraryStartPoi);
				dailyInfo.setStartPoiUseStayTime( itineraryStartPoi.isMustPoi() );
			}
			if (i == numDay - 1) {
				dailyInfo.setEndPoi(itineraryEndPoi);
				dailyInfo.setTravelToEndPoi(true);
				dailyInfo.setEndPoiUseStayTime(false);
			}
			
			fullInfo.add(dailyInfo);
		}
		
		//======================================================================
		//    Stage 1: Overall Planning - schedule necessary POI to each day
		//======================================================================
		// get must POI from database
		List<Poi> mustPoiList = new ArrayList<Poi>();
		if (! mustPoiIdList.isEmpty()) {
			queryDatabase.display("-get must POI");
			mustPoiList = queryDatabase.getPoiListByPoiIdList(mustPoiIdList, true, looseType);
		}
		// update repository
		poiIdRepository.addAll(mustPoiIdList);
		
		// initialize mapping of must county v.s. days
		MustCountyInfo mustCountyInfo = new MustCountyInfo(fullInfo);
		
		// for "must county", configure "merging quota" and "additional POI list"
		Map<Integer, Integer> mergingQuota = new HashMap<Integer, Integer>(); //key: mustCountyIndex, value: quota
		mergingQuota.put(0, 100); //for all non-must county, mustCountyIndex <-- 0, quota <-- 100
		
		List<Poi> additionalPoiList = new ArrayList<Poi>(); //if no must-POI belongs to a must county, add POI of that county
		
		// (1)adjust merging quota (2)add additional POI, if there is any must county
		if (isAnyMustCounty) {
			
			int[] numMustPoiInCounty = new int[County.numCounty + 1];
			for (Poi poi : mustPoiList)
				++numMustPoiInCounty[ poi.getCountyIndex() ];
			
			for (Integer i : mustCountyInfo.daysMapping.keySet()) {
				int numDayOfCounty = mustCountyInfo.getNumDays(i);
				if (numMustPoiInCounty[i] > numDayOfCounty) {
					mergingQuota.put(i, numMustPoiInCounty[i] - numDayOfCounty);
					
				} else {
					mergingQuota.put(i, 0);
					
					// add additional POI
					if (numMustPoiInCounty[i] < numDayOfCounty) {
						Set<Integer> counties = new HashSet<Integer>();
						counties.add(i);
						
						List<Poi> poiList;
						boolean isDuplicatedPoi;
						do {
							//note: can separate the selections!!!
							queryDatabase.display("-get additional POI to fullfill must county");
							poiList = queryDatabase.getRandomPoiListByCountyAndOpenDay(
									numDayOfCounty - numMustPoiInCounty[i],
									counties, "free", looseType, true); // note: not force "allOpen" here, so open days have to be checked in stage 2
							isDuplicatedPoi = false;
							for (Poi poi : poiList) {
								if (poiIdRepository.contains(poi.getPoiId())) {
									isDuplicatedPoi = true;
									break;
								}
							}
						} while (isDuplicatedPoi);
						
						additionalPoiList.addAll(poiList);
						
						// update repository
						for (Poi poi : poiList)
							poiIdRepository.add(poi.getPoiId());
					}
				}
			}
		}
		// future: for each county, may generate other POIs if current ones (must & additional) are too closed to each other
		
		// collect base POI list
		List<Poi> basePoiList = new ArrayList<Poi>();
		basePoiList.addAll(mustPoiList);
		basePoiList.addAll(additionalPoiList);
		System.out.println("\n" + "mustPoiList");
		for (Poi poi : mustPoiList)
			System.out.println(poi);
		System.out.println("\n" + "additionalPoiList");
		for (Poi poi : additionalPoiList)
			System.out.println(poi);
		System.out.println();
		
		// prepare initial cluster information
		int[] clusterOfBasePoi = new int[basePoiList.size()]; //POI --> cluster
		int[] assignedMustCounty = new int[basePoiList.size()]; //cluster --> county (note: we assign a representing must county to a cluster)
		for (int i = 0; i < clusterOfBasePoi.length; ++i) {
			clusterOfBasePoi[i] = i;
			int countyIdx = basePoiList.get(i).getCountyIndex();
			assignedMustCounty[i] = mustCountyInfo.daysMapping.containsKey(countyIdx) ? countyIdx : 0; //if not a must county --> 0
		}
		
		boolean[] isClusterIdxUsed = new boolean[basePoiList.size()];
		Arrays.fill(isClusterIdxUsed, true);
		
		int numClusterUsed = isClusterIdxUsed.length;
		
		// clustering when #basePOI > 1
		if (basePoiList.size() > 1) {
			// obtain pairwise distances
			int[][] time = new int[basePoiList.size()][basePoiList.size()];
			queryDatabase.display("-get time matrix to perform clustering");
			queryDatabase.getDistanceAndTimeMatrix( basePoiList, null, time );
			
			// put (half) distances into priority queue
			Comparator<PoiPair> comparator = new PoiPairComparator();
			PriorityQueue<PoiPair> priorityQueue = new PriorityQueue<PoiPair>( basePoiList.size() * basePoiList.size() / 2, comparator );
			
			for (int i = 0; i < basePoiList.size() - 1; ++i) {
				for (int j = i + 1; j < basePoiList.size(); ++j) {
					priorityQueue.add( new PoiPair(i, j, time[i][j]) );
				}
			}
			
			// display priority queue
//			System.out.println("Display priorityQueue of PoiPair:");
//			for (PoiPair poiPair : priorityQueue)
//				System.out.println(poiPair);
			
			// perform clustering
			while (! priorityQueue.isEmpty()) {
				PoiPair poiPair = priorityQueue.poll();
				
				if (poiPair.timeMeasure > mustMergingTimeMeasureBound && numClusterUsed <= numDay)
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
		
		//-----------------------------
		//    construct clusterList
		//-----------------------------
		List<Cluster> clusterList = new ArrayList<Cluster>();
		for (int i = 0; i < numClusterUsed; ++i) {
			Cluster cluster = new Cluster();
			cluster.representingMustCounty = finalAssignedMustCounty[i];
			clusterList.add(cluster);
		}
		for (int i = 0; i < basePoiList.size(); ++i) {
			clusterList.get( finalClusterOfBasePoi[i] ).poiList.add( basePoiList.get(i) );
		}
		for (Cluster cluster : clusterList) {
			queryDatabase.display("-get centered POI of a cluster");
			cluster.centerPoi = queryDatabase.getCenteredPoiByPoiList( cluster.poiList, looseType );
		}
		
		// display clusters - 1
//		System.out.println();
//		System.out.println("clusterList");
//		for (int i = 0; i < clusterList.size(); ++i) {
//			System.out.println(i + "\n" + clusterList.get(i));
//		}
		
		//-----------------------------
		//    construct fullCluster
		//-----------------------------
		// move appropriate clusters from "clusterList" to "fullCluster"
		Cluster[] fullCluster = new Cluster[numDay];
		
		for (Integer countyIdx : mustCountyInfo.daysMapping.keySet()) {
			for (int i = 0; i < mustCountyInfo.getNumDays(countyIdx); ++i) {
				boolean isFound = false;
				int j;
				for (j = 0; j < clusterList.size(); ++j) {
					Cluster cluster = clusterList.get(j);
					if (cluster.representingMustCounty == countyIdx) {
						
						fullCluster[ mustCountyInfo.getDays(countyIdx).get(i) ] = new Cluster( cluster );
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
		
		// display fullCluster
//		System.out.println("\n" + "fullCluster --- 1/5 --- finish filling in clusters into must-county-days");
//		for (int i = 0; i < fullCluster.length; ++i) {
//			System.out.println(i);
//			System.out.println(fullCluster[i]);
//			System.out.println();
//		}
		
		// display mustCounty
//		System.out.println("\n" + mustCountyInfo);
		
		//---------------------------------
		//    construct blockOfDaysList
		//---------------------------------
		List<BlockOfDays> blockOfDaysList = new ArrayList<BlockOfDays>();
		
		boolean isSlotStart = false;
		BlockOfDays tempBlockOfDays = null;
		
		for (int i = 0; i < fullInfo.size(); ++i) {
			
			if (fullInfo.get(i).getMustCounty().equals("all")) {
				if (! isSlotStart) {
					tempBlockOfDays = new BlockOfDays();
					tempBlockOfDays.type = "slot";
					tempBlockOfDays.days.add(i);
					
					isSlotStart = true;
				} else
					tempBlockOfDays.days.add(i);
				
			} else {
				if (isSlotStart) {
					blockOfDaysList.add(tempBlockOfDays);
					
					isSlotStart = false;
				}
				
				tempBlockOfDays = new BlockOfDays();
				tempBlockOfDays.type = "mustCounty";
				tempBlockOfDays.days.add(i);
				tempBlockOfDays.mustCounty = fullInfo.get(i).getMustCountyIndex();
				
				blockOfDaysList.add(tempBlockOfDays);
			}
		}
		if (isSlotStart) {
			blockOfDaysList.add(tempBlockOfDays);
		}
		
//		// display blockOfDaysList
//		for (int i = 0; i < blockOfDaysList.size(); ++i) {
//			System.out.println("blockOfDaysList " + i);
//			System.out.println(blockOfDaysList.get(i));
//		}
		
		// put in feasible county for type="slot" blockOfDays
		List<Integer> slotIndexInBlocks = new ArrayList<Integer>();
		int totalSlotCapacity = 0;
		
		for (int b = 0; b < blockOfDaysList.size(); ++b) {
			if (blockOfDaysList.get(b).type.equals("slot")) {
				slotIndexInBlocks.add(b);
				totalSlotCapacity += blockOfDaysList.get(b).days.size();
				
				BlockOfDays slot = blockOfDaysList.get(b);
				int firstCounty = (b == 0) ? itineraryStartPoi.getCountyIndex() : blockOfDaysList.get(b - 1).mustCounty;
				int lastCounty = (b == blockOfDaysList.size() - 1) ? itineraryEndPoi.getCountyIndex() : blockOfDaysList.get(b + 1).mustCounty;
				
				slot.feasibleCounty.add(firstCounty);
				slot.feasibleCounty.add(lastCounty);
				List<Integer> intermediateCounty = County.getIntermediateCounty(firstCounty, lastCounty);
				for (Integer j : intermediateCounty)
					slot.feasibleCounty.add(j);
			}
		}
		
		// display blockOfDaysList
//		for (int i = 0; i < blockOfDaysList.size(); ++i) {
//			System.out.println("\n" + "blockOfDaysList " + i);
//			System.out.println(blockOfDaysList.get(i));
//		}
		
		
		// assign clusters to slots
		int[] numAssignedCluster = new int[slotIndexInBlocks.size()];
		
		if (clusterList.size() > 0) { //implies slotIndexInBlocks.size() > 0
			
			if (slotIndexInBlocks.size() == 1) {
				BlockOfDays slot = blockOfDaysList.get( slotIndexInBlocks.get(0) );
				
				for (int i = 0; i < clusterList.size(); ++i) { // clusterList.size() <= slot.days.size()
					Cluster cluster = clusterList.get(i);
					cluster.score = DistanceAndTime.getShortestTime_BetweenCounties(cluster.poiList, slot.feasibleCounty);
					fullCluster[ slot.days.get(i) ] = cluster;
					++numAssignedCluster[0];
				}
				
			} else { // slotIndexInBlocks.size() should > 1
				
				// create assignment cost matrix
				double[][] assignmentCostMatrix = new double[ clusterList.size() ][ totalSlotCapacity ];
				int[] slotIndex = new int[ totalSlotCapacity ];
				
				for (int i = 0; i < clusterList.size(); ++i) {
					int col = 0;
					for (int s = 0; s < slotIndexInBlocks.size(); ++s) {
						BlockOfDays slot = blockOfDaysList.get( slotIndexInBlocks.get(s) );
						double cost = DistanceAndTime.getShortestTime_BetweenCounties(clusterList.get(i).poiList, slot.feasibleCounty);
						
						for (int k = 0; k < slot.getNumDays(); ++k) {
							assignmentCostMatrix[i][col] = cost;
							slotIndex[col++] = s;
						}
					}
				}
				HungarianAlgorithm hungarianAlgorithm = new HungarianAlgorithm(assignmentCostMatrix);
				int[] result = hungarianAlgorithm.execute(); //size = clusterList.size()
				
				// move clusters into slots
				for (int i = 0; i < result.length; ++i) {
					clusterList.get(i).score = assignmentCostMatrix[i][result[i]];
					int s = slotIndex[result[i]];
					
					fullCluster[ blockOfDaysList.get( slotIndexInBlocks.get( s ) ).days.get( numAssignedCluster[ s ] ) ] = clusterList.get(i);
					++numAssignedCluster[ s ];
				}
			}
			
		}
		
		// display fullCluster
//		System.out.println("\n" + "fullCluster --- 2/5 --- finish assigning remaining clusters into slots");
//		for (int i = 0; i < fullCluster.length; ++i) {
//			System.out.println(i);
//			System.out.println(fullCluster[i]);
//			System.out.println();
//		}
		
		// in each slot, fill in additional clusters if there is a shortage
		for (int s = 0; s < slotIndexInBlocks.size(); ++s) {
			int b = slotIndexInBlocks.get(s);
			BlockOfDays slot = blockOfDaysList.get(b);
			
			int numAdditionalClusters = slot.getNumDays() - numAssignedCluster[s];
			
			if (numAdditionalClusters > 0) {
				
				// decide candidate counties
				Set<Integer> candidateCounty = null;
				if ( (slot.feasibleCounty.size() == 1 && numAdditionalClusters >= 3)
					|| (slot.feasibleCounty.size() == 2 && numAdditionalClusters >= 4) ) {
					
					int firstCounty = (b == 0) ? itineraryStartPoi.getCountyIndex() : blockOfDaysList.get(b - 1).mustCounty;
					
					candidateCounty = County.getCandidateCounty(firstCounty, numAdditionalClusters);

					for (Integer j : mustCountyInfo.daysMapping.keySet()) {
						if (candidateCounty.contains(j))
							candidateCounty.remove(j);
					}
					candidateCounty.addAll(slot.feasibleCounty);
				} else
					candidateCounty = slot.feasibleCounty;
				
				// select POI
				List<Poi> poiList;
				boolean isDuplicatedPoi;
				do {
					queryDatabase.display("-get enough POI in a slot");
					poiList = queryDatabase.getRandomPoiListByCountyAndOpenDay(
							numAdditionalClusters, candidateCounty, "free", looseType, true); // note: not force "allOpen" here, so open days have to be checked in stage 2
					isDuplicatedPoi = false;
					for (Poi poi : poiList) {
						if (poiIdRepository.contains(poi.getPoiId())) {
							isDuplicatedPoi = true;
							break;
						}
					}
				} while (isDuplicatedPoi);
				
				for (int i = 0; i < numAdditionalClusters; ++i) {
					Cluster cluster = new Cluster();
					cluster.poiList.add(poiList.get(i));
					cluster.centerPoi = poiList.get(i);
					cluster.score = 0; //selected inside the counties
					fullCluster[ slot.days.get(numAssignedCluster[s] + i) ] = cluster;
					
					// update repository
					poiIdRepository.add( poiList.get(i).getPoiId() );
				}
			}
		}
		
		// display fullCluster
//		System.out.println("\n" + "fullCluster --- 3/5 --- finish filling in clusters into all days");
//		for (int i = 0; i < fullCluster.length; ++i) {
//			System.out.println(i);
//			System.out.println(fullCluster[i]);
//			System.out.println();
//		}
		
		
		// in each slot, arrange the clusters
		for (int s = 0; s < slotIndexInBlocks.size(); ++s) {
			int b = slotIndexInBlocks.get(s);
			BlockOfDays slot = blockOfDaysList.get(b);
			
			if (slot.getNumDays() > 1) {
				// rawPoiList, resultPoiList, poiIdList, daysOfSlot, clusterBackup
				List<Poi> rawPoiList = new ArrayList<Poi>();
				List<Poi> resultPoiList = new ArrayList<Poi>();
				List<Poi> completePoiList = new ArrayList<Poi>();
				
				int[] daysOfSlot = new int[slot.getNumDays()];
				Cluster[] clusterBackup = new Cluster[slot.getNumDays()];
				
				// start POI
				Poi firstPoi = (b == 0) ? itineraryStartPoi : fullCluster[ blockOfDaysList.get(b - 1).days.get(0) ].centerPoi;
				firstPoi.setIndex(0);
				resultPoiList.add( firstPoi );
				completePoiList.add( firstPoi );
				
				// main POI
				for (int i = 0; i < slot.getNumDays(); ++i) {
					daysOfSlot[i] = slot.days.get(i);
					clusterBackup[i] = fullCluster[daysOfSlot[i]];
					
					Poi poi = fullCluster[daysOfSlot[i]].centerPoi;
					poi.setIndex(i + 1);
					rawPoiList.add(poi);
					completePoiList.add(poi);
				}
				
				// end POI
				Poi lastPoi = (b == blockOfDaysList.size() - 1) ? itineraryEndPoi : fullCluster[ blockOfDaysList.get(b + 1).days.get(0) ].centerPoi;
				lastPoi.setIndex( slot.getNumDays() + 1 ); //When lastPoi==firstPoi, index is updated. But should not matter.  
				resultPoiList.add( lastPoi );
				completePoiList.add( lastPoi );
				
				// get distance and time matrix
//				double[][] distance = new double[ poiIdList.size() ][ poiIdList.size() ];
				int[][] time = new int[ completePoiList.size() ][ completePoiList.size() ];
				queryDatabase.display("-get distance/time matrix in a slot");
				queryDatabase.getDistanceAndTimeMatrix( completePoiList, null, time );
//				Display.print_2D_Array(distance, "distance");
//				Display.print_2D_Array(time, "time");
				
				// insertion heuristic
				runInsertionHeuristic_WithoutFeasibilityCheck(rawPoiList, resultPoiList, time);
				
				// re-assign clusters
				for (int i = 1; i < resultPoiList.size() - 1; ++i) {
					fullCluster[ daysOfSlot[i - 1] ] = clusterBackup[ resultPoiList.get(i).getIndex() - 1 ];
				}
			}
		}
		
		// display fullCluster
//		System.out.println("\n" + "fullCluster --- 4/5 --- finish rearranging clusters within slots");
//		for (int i = 0; i < fullCluster.length; ++i) {
//			System.out.println(i);
//			System.out.println(fullCluster[i]);
//			System.out.println();
//		}
		
		// re-arrange clusters in consecutive days which have the same must county
		List<List<Integer>> allConsecutiveDays = mustCountyInfo.getConsecutiveDays();
		// display
//		System.out.println("\n" + "consecutive days:\n");
//		for (List<Integer> days : allConsecutiveDays) {
//			for (Integer i : days) {
//				System.out.print(i + " ");
//			}
//			System.out.println();
//		}
//		System.out.println();
		
		for (List<Integer> days : allConsecutiveDays) {
			// rawPoiList, resultPoiList, poiIdList, clusterBackup
			List<Poi> rawPoiList = new ArrayList<Poi>();
			List<Poi> resultPoiList = new ArrayList<Poi>();
			List<Poi> completePoiList = new ArrayList<Poi>();
			
			Cluster[] clusterBackup = new Cluster[days.size()];
			
			// start POI
			Poi firstPoi = (days.get(0) == 0) ? itineraryStartPoi : fullCluster[ days.get(0) - 1 ].centerPoi;
			firstPoi.setIndex(0);
			resultPoiList.add( firstPoi );
			completePoiList.add( firstPoi );
			
			// main POI
			for (int i = 0; i < days.size(); ++i) {
				clusterBackup[i] = fullCluster[days.get(i)];
				
				Poi poi = fullCluster[days.get(i)].centerPoi;
				poi.setIndex(i + 1);
				rawPoiList.add(poi);
				completePoiList.add(poi);
			}
			
			// end POI
			Poi lastPoi = (days.get(days.size()-1) == numDay - 1) ? itineraryEndPoi : fullCluster[ days.get(days.size()-1) + 1 ].centerPoi;
			lastPoi.setIndex( days.size() + 1 ); //When lastPoi==firstPoi, index is updated. But should not matter.  
			resultPoiList.add( lastPoi );
			completePoiList.add( lastPoi );
			
			// get distance and time matrix
//			double[][] distance = new double[ poiIdList.size() ][ poiIdList.size() ];
			int[][] time = new int[ completePoiList.size() ][ completePoiList.size() ];
			queryDatabase.display("-get distance/time matrix for re-arranging clusters in consecutive days which have the same must county");
			queryDatabase.getDistanceAndTimeMatrix( completePoiList, null, time );
//			Display.print_2D_Array(distance, "distance");
//			Display.print_2D_Array(time, "time");
			
			// insertion heuristic
			runInsertionHeuristic_WithoutFeasibilityCheck(rawPoiList, resultPoiList, time);
			
			// re-assign clusters
			for (int i = 1; i < resultPoiList.size() - 1; ++i) {
				fullCluster[ days.get(i-1) ] = clusterBackup[ resultPoiList.get(i).getIndex() - 1 ];
			}

		}
		
		
		// display fullCluster
		System.out.println("\n" + "fullCluster --- 5/5 --- finish rearranging clusters in consecutive days which have the same must county");
		for (int i = 0; i < fullCluster.length; ++i) {
			System.out.println(i);
			System.out.println(fullCluster[i]);
			System.out.println();
		}
		
		// display number of poiList in each day
		System.out.println("\n" + "count of POI in stage 1");
		for (int i = 0; i < numDay; ++i) {
			System.out.println("day " + i + " " + fullCluster[i].poiList.size());
		}
		System.out.println();
		
		//======================================================
		//    Stage 2: Daily Planning - complete daily plans
		//======================================================
		
		// complete itinerary
		for (int i = 0; i < numDay; ++i) {
			DailyInfo dailyInfo = fullInfo.get(i);
			List<Poi> poiList = fullCluster[i].poiList;
			
			// check if POI in this day are close. if close, remove it.
			Iterator<Poi> iterator = poiList.iterator();
			while (iterator.hasNext()) {
				Poi poi = iterator.next();
				
				String openHoursOfThisDay = poi.getOpenHoursOfADay(dailyInfo.getDayOfWeek());
				if  (openHoursOfThisDay != null && openHoursOfThisDay.equals("close")) {
					iterator.remove();
				}
			}
			
			// dailyInfo settings
			if (i < numDay - 1) { //not the last day
				dailyInfo.setEndPoi( fullCluster[i + 1].centerPoi );
				dailyInfo.setTravelToEndPoi(false);
				dailyInfo.setEndPoiUseStayTime(false);
			}
			
			// when numDay >= 2 and last day, check if there is enough time to visit the predetermined area
			// if there is not enough time, then visit the itinerary end POI
			if (numDay >= 2 && i == numDay - 1) {
				
				queryDatabase.display("-in the last day, get time between POI pairs");
				int estimatedTime = queryDatabase.getTimeBetweenPoi( fullItineraryPoi.get( fullItineraryPoi.size() - 1 ), fullCluster[i].centerPoi )
						+ queryDatabase.getTimeBetweenPoi( fullCluster[i].centerPoi, itineraryEndPoi );
				
				if ( (endTimeEachDayInMinutes - startTimeEachDayInMinutes) - estimatedTime <= 60 ) {
					
					int numSelectionLB = 1;
					int numSelectionUB = 1;
					double deltaKm = 15;
					double deltaKmIncrement = 15;
					double deltaKmTermination = 90;
					Set<Integer> countiesForSearch = new HashSet<Integer>();
					countiesForSearch.add(itineraryEndPoi.getCountyIndex());
					
					queryDatabase.display("-in the last day, replace the cluster center POI");
					List<Poi> pois = queryDatabase.getRandomPoiListByCenterPoiIdAndCountyAndOpenDayWithinSquare(
							numSelectionLB, numSelectionUB, itineraryEndPoi,
							deltaKm, deltaKmIncrement, deltaKmTermination,
							countiesForSearch, dailyInfo.getDayOfWeek(), "RAND()", looseType, true);
					if (! pois.isEmpty()) {
						fullCluster[i].centerPoi = pois.get(0);
						fullCluster[i].poiList = pois;
					}
					else {
						fullCluster[i].centerPoi = itineraryEndPoi;
						pois.add(itineraryEndPoi);
						fullCluster[i].poiList = pois;
					}
				}
			}
			
			System.out.println("--- Day " + (i+1) + " --- before adding candidate POIs:");
			for (Poi poi : poiList)
				System.out.println(poi);
			
			//---------------------------------------------------
			//    get candidate POI information from database
			//---------------------------------------------------
			// get counties used for searching POI
			Set<Integer> countiesForSearch = new HashSet<Integer>();
			if (dailyInfo.getMustCounty().equals("all") || mustCountyInfo.getNumDays(dailyInfo.getMustCountyIndex()) >= 4) {
				for (Poi poi : poiList) {
					int j = poi.getCountyIndex();
					countiesForSearch.add(j);
					
					List<Integer> nearbyCounty = County.getNearbyCounty(j);
					countiesForSearch.addAll(nearbyCounty);
				}
			} else {
				countiesForSearch.add( dailyInfo.getMustCountyIndex() );
			}
			
			int numCandidatePoi = Math.max(numPoiForSelectionUpperBound - poiList.size(), 0);
			
			if (numCandidatePoi > 0) {
				// if the centerPoi is not a backbone POI, then need another centerPoi for searching candidates
				if (! fullCluster[i].centerPoi.isBackbonePoi()) {
					queryDatabase.display("-get alternative center POI");
					fullCluster[i].centerPoi = queryDatabase.getNearbyPoiByCoordinate(
								fullCluster[i].centerPoi.getCoordinate(), false, looseType, true );
				}
				
				// Parameters: select candidate POIs from database
				int numSelectionLB = 10;
				int numSelectionUB = 30;
				int timeLB = 0;
				int timeUB = 30;
				int timeUBIncrement = 10;
				int timeUBTermination = 60;
				
				List<Poi> foundPoiList;
				
				queryDatabase.display("-get candidate POI");
				foundPoiList = queryDatabase.getRandomPoiListByCenterPoiIdAndCountyAndOpenDay(
						numSelectionLB, numSelectionUB, fullCluster[i].centerPoi.getPoiId(), timeLB, timeUB, timeUBIncrement, timeUBTermination,
						countiesForSearch, dailyInfo.getDayOfWeek(), "checkinTotal DESC", looseType);
				Iterator<Poi> foundPoiIterator = foundPoiList.iterator();
				while (foundPoiIterator.hasNext()) {
					Poi poi = foundPoiIterator.next();
					if  (poiIdRepository.contains(poi.getPoiId())) {
						foundPoiIterator.remove();
					}
				}
				
				// if candidate POI is not enough, select more POI within square
				List<Poi> extraPoiList = null;
				if (foundPoiList.size() < numSelectionLB) {
					
					// Parameters: select extra candidate POIs from database
					double deltaKm = 10;
					double deltaKmIncrement = 10;
					double deltaKmTermination = 40;
					
					queryDatabase.display("-get candidate POI within square");
					extraPoiList = queryDatabase.getRandomPoiListByCenterPoiIdAndCountyAndOpenDayWithinSquare(
							numSelectionLB, numSelectionUB, fullCluster[i].centerPoi,
							deltaKm, deltaKmIncrement, deltaKmTermination,
							countiesForSearch, dailyInfo.getDayOfWeek(), "RAND()", looseType, false);
					Iterator<Poi> extraPoiIterator = extraPoiList.iterator();
					while (extraPoiIterator.hasNext()) {
						Poi poi = extraPoiIterator.next();
						
						if  (poiIdRepository.contains(poi.getPoiId())) {
							extraPoiIterator.remove();
							continue;
						}
						for (Poi foundPoi : foundPoiList) {
							if (poi.getPoiId().equals( foundPoi.getPoiId() )) {
								extraPoiIterator.remove();
								break;
							}
						}
					}
					
					// set timeMeasureToFixedPoi
					for (Poi poi : extraPoiList) {
						// approximated time
						poi.setTimeMeasureToFixedPoi( DistanceAndTime.getEstimatedTimeOfNearbyPoi(poi, fullCluster[i].centerPoi) );
					}
					
					// combine
					foundPoiList.addAll( extraPoiList );
				}
				
				// select #=numCandidatePoi from foundPoiList by probability/scores
				double[] scores = Poi.calculateScores(foundPoiList, userInputTheme);
				//Display.print_1D_Array(scores, "scores");
				
				List<Integer> selectedIndex = selectByProbabilityWithoutReplacement(scores, numCandidatePoi);
				//Display.print_1D_List(selectedIndex);
				for (Integer j : selectedIndex) {
					poiList.add( foundPoiList.get(j) );
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
			
			// obtain distance and travel time
			List<Poi> completePoiList = new ArrayList<Poi>();
			completePoiList.add( dailyInfo.getStartPoi() );
			completePoiList.addAll(poiList);
			completePoiList.add( dailyInfo.getEndPoi() );
			
			double[][] distance = new double[ completePoiList.size() ][ completePoiList.size() ]; // = poiList.size() + 2
			int[][] travelTime = new int[ completePoiList.size() ][ completePoiList.size() ];
			queryDatabase.display("-get distance/time matrix of selected POI in a day");
			queryDatabase.getDistanceAndTimeMatrix( completePoiList, distance, travelTime );
//			Display.print_2D_Array(distance, "distance");
			Display.print_2D_Array(travelTime, "travelTime");
			
			//---------------------------------------------
			//    construct tour by insertion heuristic
			//---------------------------------------------
			List<Poi> rawMustPoiList = new ArrayList<Poi>();
			List<Poi> rawOtherPoiList = new ArrayList<Poi>();
			for (Poi poi : poiList) {
				if (poi.isMustPoi())
					rawMustPoiList.add(poi);
				else
					rawOtherPoiList.add(poi);
			}
			
			List<Poi> itineraryPoi = new ArrayList<Poi>();
			itineraryPoi.add( dailyInfo.getStartPoi() );
			itineraryPoi.add( dailyInfo.getEndPoi() );
			
			
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
						int thisTime = Poi.findShortestTimeFromPoiToPoiList( rawPoiList.get(k), itineraryPoi, travelTime);
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
					itineraryPoi.add( 1, rawPoiList.get(bestIndex) );
					rawPoiList.remove( bestIndex );
				}
			}
			
			// complete the insertion heuristic "with feasibility check"
			for (int j = 0; j < 2; ++j) {
				List<Poi> rawPoiList = (j == 0) ? rawMustPoiList : rawOtherPoiList;
				
				while (! rawPoiList.isEmpty()) {
					
					// find a remaining POI which has the shortest distance to any of the selected POI
					
					int shortestTime = Integer.MAX_VALUE;
					int bestIndex = -1;
					for (int k = 0; k < rawPoiList.size(); ++k) {
						int thisTime = Poi.findShortestTimeFromPoiToPoiList( rawPoiList.get(k), itineraryPoi, travelTime);
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
					
					for (int k = 0; k < itineraryPoi.size() - 1; ++k) {
						int timeIncrement = travelTime[ itineraryPoi.get(k).getIndex() ][ rawPoiList.get(bestIndex).getIndex() ]
								+ travelTime[ rawPoiList.get(bestIndex).getIndex() ][ itineraryPoi.get(k+1).getIndex() ]
								- travelTime[ itineraryPoi.get(k).getIndex() ][ itineraryPoi.get(k+1).getIndex() ];
						
						List<Poi> tempPoiList = new ArrayList<Poi>();
						tempPoiList.addAll( itineraryPoi.subList(0, k+1) );
						tempPoiList.add( rawPoiList.get(bestIndex) );
						tempPoiList.addAll( itineraryPoi.subList(k+1, itineraryPoi.size()) );
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
						itineraryPoi.add( bestSlotIndexWhenFeasible + 1, rawPoiList.get(bestIndex) );
					}
					rawPoiList.remove(bestIndex);
				}
				
			}
			
			List<TourEvent> itinerary = new ArrayList<TourEvent>();
			int minutesLeft = Poi.getItineraryFromFeasibleSequence(itineraryPoi, travelTime, dailyInfo, earlistLunchTimeInMinutes, minutesForLunch, itinerary);
			
			// update repository
			for (int j = 1; j < itinerary.size() - 1; ++j)
				poiIdRepository.add( itinerary.get(j).getPoiId() );
			
			//----------------------------------------------------------------
			//    select and fill in more POI if there is enough time left
			//----------------------------------------------------------------
			int minutesLeftThreshold = 45;
			if (looseType == 1)
				minutesLeftThreshold = (int)(1.2 * minutesLeftThreshold);
			else if (looseType == -1)
				minutesLeftThreshold = (int)(0.8 * minutesLeftThreshold);
			
			
			// when there are some minutes left in the end of the day, try to add more POI
			if (minutesLeft >= minutesLeftThreshold) {
				
//				String centerPoiId = itinerary.get(itinerary.size() - 2).getPoiId();
				
//				// Parameters
//				int numSelectionLB = 1;
//				int numSelectionUB = 5;
//				int timeLB = 0;
//				int timeUB = 15;
//				int timeUBIncrement = 0;
//				int timeUBTermination = 60;
//				
//				// get extra POI
//				queryDatabase.display("-get extra POI if there are minutes left in the end of the day");
//				List<Poi> foundPoiList = queryDatabase.getRandomPoiListByCenterPoiIdAndCountyAndOpenDay(
//						numSelectionLB, numSelectionUB, centerPoiId, timeLB, timeUB, timeUBIncrement, timeUBTermination,
//						countiesForSearch, dailyInfo.getDayOfWeek(), "RAND()", looseType);
//				
//				Iterator<Poi> foundPoiIterator = foundPoiList.iterator();
//				while (foundPoiIterator.hasNext()) {
//					Poi poi = foundPoiIterator.next();
//					if  (poiIdRepository.contains(poi.getPoiId())) {
//						foundPoiIterator.remove();
//					}
//				}
				
				
				// Parameters
				int numSelectionLB = 1;
				int numSelectionUB = 5;
				double deltaKm = 5;
				double deltaKmIncrement = 5;
				double deltaKmTermination = 15;
				
				queryDatabase.display("-get extra POI if there are minutes left in the end of the day (within square)");
				List<Poi> foundPoiList = queryDatabase.getRandomPoiListByCenterPoiIdAndCountyAndOpenDayWithinSquare(
						numSelectionLB, numSelectionUB, itineraryPoi.get( itineraryPoi.size() - 2 ),
						deltaKm, deltaKmIncrement, deltaKmTermination,
						countiesForSearch, dailyInfo.getDayOfWeek(), "RAND()", looseType, false);
				
				Iterator<Poi> foundPoiIterator = foundPoiList.iterator();
				while (foundPoiIterator.hasNext()) {
					Poi poi = foundPoiIterator.next();
					if  (poiIdRepository.contains(poi.getPoiId())) {
						foundPoiIterator.remove();
					}
				}
				
				
				// get time matrix
				List<Poi> completeDayEndPoiList = new ArrayList<Poi>();
				completeDayEndPoiList.add( itineraryPoi.get(itineraryPoi.size() - 2) );
				completeDayEndPoiList.addAll( foundPoiList );
				completeDayEndPoiList.add( itineraryPoi.get(itineraryPoi.size() - 1) );
				
				double[][] dayEndDistance = new double[ completeDayEndPoiList.size() ][ completeDayEndPoiList.size() ];
				int[][] dayEndTravelTime = new int [ completeDayEndPoiList.size() ][ completeDayEndPoiList.size() ];
				queryDatabase.display("-get distance/time matrix for day-end extra POI");
				queryDatabase.getDistanceAndTimeMatrix( completeDayEndPoiList, dayEndDistance, dayEndTravelTime );
//				Display.print_2D_Array(dayEndDistance, "dayEndDistance");
//				Display.print_2D_Array(dayEndTravelTime, "dayEndTravelTime");
				
				boolean[] isAvailable = new boolean[completeDayEndPoiList.size()];
				Arrays.fill(isAvailable, true);
				isAvailable[0] = false;
				isAvailable[isAvailable.length - 1] = false;
				
				//
				int currentIndex = 0;
				do {
					System.out.println("In the end-day POI adding loop. minutesLeft = " + minutesLeft);
					
					// calculate time pointer to start with
					Calendar calendar = Calendar.getInstance();
					calendar.setTime(itinerary.get(itinerary.size() - 2).getEndTime());
					int timePointer = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
					
					// select the POI which might be finished in the earliest time
					int minTimeMeasure = Integer.MAX_VALUE;
					int bestIndex = -1;
					for (int k = 1; k <= foundPoiList.size(); ++k) {
						if (isAvailable[k]) {
							int measure = dayEndTravelTime[ currentIndex ][ k ] + foundPoiList.get( k - 1 ).getStayTime();
							if ( measure < minTimeMeasure) {
								minTimeMeasure = measure;
								bestIndex = k;
							}
						}
					}
					
					//(1) all found POI are unavailable (2) all found POI cannot be feasible, then done
					if (bestIndex == -1 || timePointer + minTimeMeasure > dailyInfo.getEndTimeInMinutes()) {
						break;
					}
					
					isAvailable[bestIndex] = false; // finish checking this POI. mark unavailable whether this is feasible or not.
					
					// now: check the precise open hours
					timePointer += dayEndTravelTime[ currentIndex ][ bestIndex ];
					
					int[] visitDuration = Poi.getEarliestFeasibleVisitDuration(
							timePointer, foundPoiList.get(bestIndex - 1), dailyInfo.getDayOfWeek());
					if (visitDuration == null) { //infeasible
						continue;
					}
					
					timePointer = visitDuration[1];
					
					int[] visitDurationLast = new int[]{visitDuration[1], visitDuration[1]}; //temp
					
					if (dailyInfo.isTravelToEndPoi()) {
						timePointer += dayEndTravelTime[ bestIndex ][ completeDayEndPoiList.size() - 1 ];
						
						if (! dailyInfo.isEndPoiUseStayTime()) {
							visitDurationLast[0] = timePointer;
							visitDurationLast[1] = timePointer;
							
						} else {
							visitDurationLast = Poi.getEarliestFeasibleVisitDuration(
									timePointer, itineraryPoi.get(itineraryPoi.size() - 1), dailyInfo.getDayOfWeek());
							
							timePointer = visitDurationLast[1];
						}
					}
					
					if (timePointer <= dailyInfo.getEndTimeInMinutes()) { // INSERT THIS POI!
						
						// insert this POI in itineraryPoi
						itineraryPoi.add(itineraryPoi.size() - 1, foundPoiList.get(bestIndex - 1));
						
						// insert this POI in itinerary
						itinerary.remove(itinerary.size() - 1);
						
						TourEvent tourEvent = Poi.createTourEvent( foundPoiList.get(bestIndex - 1).getPoiId(),
								dailyInfo.getCalendarThisDayAtMidnight(), visitDuration[0], visitDuration[1] );
						itinerary.add(tourEvent);
						
						TourEvent tourEventLast = Poi.createTourEvent( itineraryPoi.get(itineraryPoi.size() - 1).getPoiId(),
								dailyInfo.getCalendarThisDayAtMidnight(), visitDurationLast[0], visitDurationLast[1] );
						itinerary.add(tourEventLast);
						
						// others
						currentIndex = bestIndex;
						minutesLeft = dailyInfo.getEndTimeInMinutes() - visitDurationLast[1];
						
						// update repository
						poiIdRepository.add(tourEvent.getPoiId());
					}
				} while ( minutesLeft >= minutesLeftThreshold );

			}
			
			//------------------------------
			//    final steps in stage 2
			//------------------------------
			// handle if first/last POI will be used
			if (i > 0 && ! dailyInfo.isStartPoiUseStayTime()) {
				itineraryPoi.remove(0);
				itinerary.remove(0);
			}
			if (i < numDay - 1 && ! dailyInfo.isEndPoiUseStayTime()) {
				itineraryPoi.remove( itineraryPoi.size() - 1 );
				itinerary.remove( itinerary.size() - 1 );
			}
			// error check here: is it possible that distances are too long and no POI are selected in the result?
			
			// link to full itinerary
			fullItineraryPoi.addAll(itineraryPoi);
			fullItinerary.addAll(itinerary);
			
			// set start POI for next day
			if (i < numDay - 1) {
				fullInfo.get(i + 1).setStartPoi( itineraryPoi.get( itineraryPoi.size() - 1 ) );
				fullInfo.get(i + 1).setStartPoiUseStayTime(false);
			}
		}
		
		
		//display full itinerary
		Calendar calendar = Calendar.getInstance();
		int dayOfWeek0 = -10;
		int d = 0;
		System.out.println();
		for (int i = 0; i < fullItinerary.size(); ++i) {
			calendar.setTime(fullItinerary.get(i).getStartTime());
			int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1;
			if (dayOfWeek != dayOfWeek0) {
				++d;
				System.out.println("Day " + d + " -- " + Display.getStringOf_date(calendar));
				dayOfWeek0 = dayOfWeek;
			}

			System.out.println("[" + Display.getStringOf_time(fullItinerary.get(i).getStartTime()) + " - "
					+ Display.getStringOf_time(fullItinerary.get(i).getEndTime()) + "] "
					+ fullItineraryPoi.get(i)
					+ ( fullItineraryPoi.get(i).getCountyId().equals(fullInfo.get(d-1).getMustCounty()) ? " /必縣/" : "" ));
		}
		
		Date dateSchedulingEnd = new Date(System.currentTimeMillis());
		System.out.print("\nTotal elapsed:");
		System.out.println(dateSchedulingEnd.getTime() - dateSchedulingStart.getTime());
		System.out.println("Program end!");
		return fullItinerary;
	}
	
	//============
	//    temp
	//============
//	private int getTimeFromDistance(double d) {
//		if (d < 0) {
//			System.err.println("Negative distance found in getTimeFromDistance()!");
//			return 0;
//		}
//		
//		d = d * 1.3;
//		
//		double velocity;
//		if (d < 0.5)
//			velocity = 8;
//		else
//			velocity = 100.0 * (1.0 - 0.8 / Math.pow(d, 0.2));
//		
//		velocity = velocity / 1.4; //!!
//		
//		return (int)(d / velocity * 60);
//	}
	
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
		private Map<Integer, List<Integer>> daysMapping = new HashMap<Integer, List<Integer>>();
		
		private List<Integer> getDays(int county) {
			return daysMapping.get(county); // return null if key=county does not exist
		}
		private int getNumDays(int county) {
			if (daysMapping.containsKey(county))
				return daysMapping.get(county).size();
			else
				return 0;
		}
		private MustCountyInfo(List<DailyInfo> fullInfo) {
			for (int i = 0; i < fullInfo.size(); ++i) {
				
				if (! fullInfo.get(i).getMustCounty().equals("all")) {
					int county = fullInfo.get(i).getMustCountyIndex();
					
					if ( daysMapping.containsKey(county) )
						daysMapping.get(county).add(i);
					else {
						List<Integer> days = new ArrayList<Integer>();
						days.add(i);
						daysMapping.put(county, days);
					}
				}
			}
		}
		
		@Override
		public String toString() {
			String str = "--MustCountyInfo\n";
			for (Map.Entry<Integer, List<Integer>> entry : daysMapping.entrySet()) {
				str += "[mustCounty] " + entry.getKey() + " [days]";
				for (Integer i : entry.getValue())
					str += " " + i;
				str += "\n";
			}
			return str;
		}
		
		private List<List<Integer>> getConsecutiveDays() {
			List<List<Integer>> result = new ArrayList<List<Integer>>();
			
			for (Map.Entry<Integer, List<Integer>> entry : daysMapping.entrySet()) {
				List<Integer> days = entry.getValue();
				
				if (days.size() > 1) {
					List<Integer> consecutiveDays = new ArrayList<Integer>();
					
					for (int i = 0; i < days.size(); ++i) {
						if (consecutiveDays.isEmpty()) {
							consecutiveDays.add( days.get(i) );
						} else {
							if (consecutiveDays.get(consecutiveDays.size() - 1) + 1 == days.get(i)) {
								consecutiveDays.add( days.get(i) );
							} else {
								if (consecutiveDays.size() > 1) {
									result.add(consecutiveDays);
								}
								consecutiveDays = new ArrayList<Integer>();
								consecutiveDays.add( days.get(i) );
							}
						}
					}
					if (consecutiveDays.size() > 1) {
						result.add(consecutiveDays);
					}
				}
			}
			
			Collections.sort(result, new ListOfIntegerComparator());
			return result;
		}
	}
	private class ListOfIntegerComparator implements Comparator<List<Integer>> { //used in MustCountyInfo.getConsecutiveDays() 
		@Override
		public int compare(List<Integer> o1, List<Integer> o2) {
			if (o1 == null || o2 == null || o1.isEmpty() || o2.isEmpty())
				return 0;
			if (o1.get(0) < o2.get(0))
				return -1;
			if (o1.get(0) > o2.get(0))
				return 1;
			return 0;
		}
	}
	
	//=====================
	//    class Cluster
	//=====================
	private class Cluster {
		private List<Poi> poiList = new ArrayList<Poi>();
		private int representingMustCounty;
		private Poi centerPoi;
		private double score;
		
		private Cluster() {
		}
		private Cluster(Cluster cluster) {
			this.poiList = new ArrayList<Poi>();
			for (Poi poi : cluster.poiList)
				this.poiList.add(poi);
			
			this.representingMustCounty = cluster.representingMustCounty;
			
			this.centerPoi = new Poi(cluster.centerPoi);
			
			this.score = cluster.score;
		}
		@Override
		public String toString() {
			String str = "--Cluster:\n[1.poiList]\n";
			for (Poi poi : poiList)
				str += poi + "\n";
			str += "[2.representingMustCounty] " + representingMustCounty + "\n[3.centerPoi]" + centerPoi + "\n[4.score]" + score;
			return str;
		}
	}
	
	//=========================
	//    class BlockOfDays
	//=========================
	private class BlockOfDays {
		private List<Integer> days = new ArrayList<Integer>(); //start from 0
		private String type; //"mustCounty" or "slot"
		private Integer mustCounty; //for type "mustCounty"
		private Set<Integer> feasibleCounty = new HashSet<Integer>(); //for type "slot"
		
		private int getNumDays() {
			return days.size();
		}
		@Override
		public String toString() {
			String str = "--BlockOfDays\n[1.days]";
			for (Integer i : days)
				str += " " + i;
			str += "\n[2.type] " + type + "\n[3.mustCounty] " + mustCounty;
			str += "\n[4.feasibleCounty]";
			for (Integer i : feasibleCounty)
				str += " " + i;
			return str;
		}
	}
	
	//===========================================
	//    class PoiPair and PoiPairComparator
	//===========================================
	private class PoiPair {
		private int poiIdx1;
		private int poiIdx2;
		private int timeMeasure;
		
		private PoiPair(int poiIdx1, int poiIdx2, int timeMeasure) {
			this.poiIdx1 = poiIdx1;
			this.poiIdx2 = poiIdx2;
			this.timeMeasure = timeMeasure;
		}
		@Override
		public String toString() {
			String str = poiIdx1 + " " + poiIdx2 + " " + timeMeasure;
			return str;
		}
	}
	private class PoiPairComparator implements Comparator<PoiPair> {
		@Override
		public int compare(PoiPair o1, PoiPair o2) {
			if (o1 == null || o2 == null)
				return 0;
			if (o1.timeMeasure < o2.timeMeasure)
				return -1;
			if (o1.timeMeasure > o2.timeMeasure)
				return 1;
			return 0;
		}
	}
	
	//===================================================================
	//    function: run insertion heuristic without feasibility check
	//===================================================================
	private void runInsertionHeuristic_WithoutFeasibilityCheck(List<Poi> rawPoiList, List<Poi> resultPoiList, int[][] travelTime) {
		
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
			
			// find a slot where "the distance increments is smallest"
			int smallestTimeIncrement = Integer.MAX_VALUE;
			int bestSlotIndex = -1;
			
			for (int k = 0; k < resultPoiList.size() - 1; ++k) {
				int timeIncrement = travelTime[ resultPoiList.get(k).getIndex() ][ rawPoiList.get(bestIndex).getIndex() ]
						+ travelTime[ rawPoiList.get(bestIndex).getIndex() ][ resultPoiList.get(k+1).getIndex() ]
						- travelTime[ resultPoiList.get(k).getIndex() ][ resultPoiList.get(k+1).getIndex() ];
				
				if (timeIncrement < smallestTimeIncrement) {
					smallestTimeIncrement = timeIncrement;
					bestSlotIndex = k;
				}
			}
			
			// update
			resultPoiList.add( bestSlotIndex + 1, rawPoiList.get(bestIndex) );
			rawPoiList.remove(bestIndex);
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

