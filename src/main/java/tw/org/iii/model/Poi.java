package tw.org.iii.model;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

public class Poi {
	
	// from database -- always exists
	private String poiId;
	private String poiName;
	private double latitude;
	private double longitude;
	private String countyId;
	private int checkins;
	private boolean[] theme = new boolean[8];
	private int stayTime;
	private String[] openHoursOfSevenDays = new String[7];
	
	// from database -- sometimes exists
	private int timeMeasureToFixedPoi; //travel time used to calculate scores
	
	// database source
	private int source;	// = 1: analytics.Scheduling2, distanceBackbone == 1 (is distance backbone!!)
				// = 2: analytics.Scheduling2, distanceBackbone == 0 (not distance backbone)
				// = 3: ST_V3_ZH_TW.PoiFinalView2 (not distance backbone)
	
	// from user input
	private boolean isMustPoi;
	
	// used to access distance matrix
	private int index;
	
	
	public void setFromQueryResult(Map<String, Object> queryResultPoi, int looseType) {
		poiId = queryResultPoi.get("poiId").toString();
		poiName = queryResultPoi.get("name").toString();
		latitude = Double.parseDouble( queryResultPoi.get("latitude").toString() );
		longitude = Double.parseDouble( queryResultPoi.get("longitude").toString() );
		countyId = queryResultPoi.get("countyId").toString();
		
		Object tempObj1 = queryResultPoi.get("checkinTotal");
		checkins = (tempObj1 != null) ? Integer.parseInt(tempObj1.toString()) : 0;
		
		for (int i = 10; i <= 17; ++i) {
			Object tempObj2 = queryResultPoi.get("TH" + i);
			theme[i-10] = ( tempObj2 != null && tempObj2.toString().equals("1") ) ? true : false;
		}
		
		Object tempObj3 = queryResultPoi.get("stayTime");
		stayTime = (int)( 60 * ( (tempObj3 != null && ! tempObj3.toString().isEmpty()) ? Double.parseDouble(tempObj3.toString()) : 1) );//minutes //no data-->60min
		if (looseType == 1)
			stayTime = (int)(1.2 * stayTime);
		else if (looseType == -1)
			stayTime = (int)(0.8 * stayTime);
		
		for (int i = 0; i < 7; ++i) {
			Object tempObj4 = queryResultPoi.get("OH_" + QueryDatabase.daysOfWeekName[i]);
			openHoursOfSevenDays[i] = (tempObj4 != null) ? tempObj4.toString() : null;
		}
		
		if (queryResultPoi.get("timeMeasureToFixedPoi") != null) {
			timeMeasureToFixedPoi = Integer.parseInt( queryResultPoi.get("timeMeasureToFixedPoi").toString() );
		}
		
		if (queryResultPoi.get("distanceBackbone").toString().equals("1"))
			source = 1;
		else
			source = 2;
	}
	
	public void setFromQueryResult_ST(List<Map<String, Object>> queryResult, int looseType) {
		
		Map<String, Object> queryResultPoi = queryResult.get(0);
		
		poiId = queryResultPoi.get("poiId").toString();
		poiName = queryResultPoi.get("name").toString();
		
		double[] coordinate = parseCoordinate( queryResultPoi.get("location").toString() );
		latitude = coordinate[0];
		longitude = coordinate[1];
		
		countyId = queryResultPoi.get("countyId").toString();
		
		Object tempObj1 = queryResultPoi.get("checkinTotal");
		checkins = (tempObj1 != null) ? Integer.parseInt(tempObj1.toString()) : 0;
		
		for (int i = 0; i < queryResult.size(); ++i) {
			Object tempObj2 = queryResult.get(i).get("themeId");
			if (tempObj2 != null) {
				String tempStr = tempObj2.toString();
				if (tempStr.length() == 4 && tempStr.substring(0, 3).equals("TH1")) {
					theme[ Integer.parseInt( tempStr.substring(3) ) ] = true;
				}
			}
		}
		
		Object tempObj3 = queryResultPoi.get("stayTime");
		stayTime = (int)( 60 * ( (tempObj3 != null && ! tempObj3.toString().isEmpty()) ? Double.parseDouble(tempObj3.toString()) : 1) );//minutes //no data-->60min
		if (looseType == 1)
			stayTime = (int)(1.2 * stayTime);
		else if (looseType == -1)
			stayTime = (int)(0.8 * stayTime);
		
		for (int i = 0; i < 7; ++i) {
			openHoursOfSevenDays[i] = null; //database does not provide clean open hours information!!
		}
		
		source = 3;
	}
	
	@Override
	public String toString() {
		//String str = "[" + poiId + "][" + poiName + "][" + countyId + " " + County.getCountyName(countyId) + "]";
		String str = "[" + poiId + "][" + poiName + "][" + countyId + " " + County.getCountyName(countyId) + "]"
				+ (isMustPoi() ? " ~必景~" : "");
		return str;
	}
	
	public String getOpenHoursOfADay(int day) {
		return openHoursOfSevenDays[day];
	}
	
	public int getCountyIndex() {
		return County.getCountyIndex(countyId);
	}
	
	public double[] getCoordinate() {
		double[] coordinate = {latitude, longitude};
		return coordinate;
	}
	public void setCoordinate(double[] coordinate) {
		this.latitude = coordinate[0];
		this.longitude = coordinate[1];
	}
	public void setCoordinate(double latitude, double longitude) {
		this.latitude = latitude;
		this.longitude = longitude;
	}
	
	public boolean isBackbonePoi() {
		return (source == 1) ? true : false;
	}
	
	//============================
	//    auto getter / setter
	//============================
	public String getPoiId() {
		return poiId;
	}

	public void setPoiId(String poiId) {
		this.poiId = poiId;
	}

	public String getPoiName() {
		return poiName;
	}

	public void setPoiName(String poiName) {
		this.poiName = poiName;
	}
	
	public double getLatitude() {
		return latitude;
	}

	public void setLatitude(double latitude) {
		this.latitude = latitude;
	}

	public double getLongitude() {
		return longitude;
	}

	public void setLongitude(double longitude) {
		this.longitude = longitude;
	}

	public String getCountyId() {
		return countyId;
	}

	public void setCountyId(String countyId) {
		this.countyId = countyId;
	}

	public int getCheckins() {
		return checkins;
	}

	public void setCheckins(int checkins) {
		this.checkins = checkins;
	}

	public boolean[] getTheme() {
		return theme;
	}

	public void setTheme(boolean[] theme) {
		this.theme = theme;
	}

	public int getStayTime() {
		return stayTime;
	}

	public void setStayTime(int stayTime) {
		this.stayTime = stayTime;
	}

	public String[] getOpenHoursOfSevenDays() {
		return openHoursOfSevenDays;
	}

	public void setOpenHoursOfSevenDays(String[] openHoursOfSevenDays) {
		this.openHoursOfSevenDays = openHoursOfSevenDays;
	}
	
	public int getTimeMeasureToFixedPoi() {
		return timeMeasureToFixedPoi;
	}

	public void setTimeMeasureToFixedPoi(int timeMeasureToFixedPoi) {
		this.timeMeasureToFixedPoi = timeMeasureToFixedPoi;
	}
	
	public int getSource() {
		return source;
	}

	public void setSource(int source) {
		this.source = source;
	}

	public boolean isMustPoi() {
		return isMustPoi;
	}

	public void setMustPoi(boolean isMustPoi) {
		this.isMustPoi = isMustPoi;
	}
	
	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}
	
	//===========================================
	//    constructor / deep copy constructor
	//===========================================
	public Poi() {
		super();
	}
	public Poi(Poi poi) {
		super();
		this.poiId = poi.poiId;
		this.poiName = poi.poiName;
		this.latitude = poi.latitude;
		this.longitude = poi.longitude;
		this.countyId = poi.countyId;
		this.checkins = poi.checkins;
		
		this.theme = new boolean[poi.theme.length];
		for (int i = 0; i < poi.theme.length; ++i)
			this.theme[i] = poi.theme[i];
		
		this.stayTime = poi.stayTime;
		
		this.openHoursOfSevenDays = new String[poi.openHoursOfSevenDays.length];
		for (int i = 0; i < poi.openHoursOfSevenDays.length; ++i)
			this.openHoursOfSevenDays[i] = poi.openHoursOfSevenDays[i];
		
		this.timeMeasureToFixedPoi = poi.timeMeasureToFixedPoi;
		this.source = poi.source;
		this.isMustPoi = poi.isMustPoi;
		this.index = poi.index;
	}

	//========================
	//    static functions
	//========================
	//-----------------------------------------
	//    static functions -- miscellaneous
	//-----------------------------------------
	public static double[] parseCoordinate(String str) {
		String[] spl = str.split(" ");
		double latitude = Double.parseDouble(spl[0].split("POINT\\(")[1]);
		double longitude = Double.parseDouble(spl[1].split("\\)")[0]);
		double[] coordinate = {latitude, longitude}; //{緯度, 經度}
		return coordinate;
	}
	
	public static double[] calculatePoiCenter(List<Poi> poiList) {
		if (poiList == null || poiList.isEmpty()) {
			System.err.println("[!!! Error !!!][Poi calculatePoiCenter] poiList null or empty!");
			return null;
		}
		
		double[] poiCenter = new double[2];
		if (poiList.size() == 1) {
			poiCenter[0] = poiList.get(0).getLatitude();
			poiCenter[1] = poiList.get(0).getLongitude();
		}
		else {
			double sumLatitude = 0;
			double sumLongitude = 0;
			for (Poi poi : poiList) {
				sumLatitude += poi.getLatitude();
				sumLongitude += poi.getLongitude();
			}
			poiCenter[0] = sumLatitude / poiList.size();
			poiCenter[1] = sumLongitude / poiList.size();
		}
		return poiCenter;
	}
	
	public static double[] calculateScores(List<Poi> poiList, boolean[] userInputTheme) {
		double[] scores = new double[poiList.size()];
		
		// calculate max/min time measure
		int minTimeMeasure = Integer.MAX_VALUE;
		int maxTimeMeasure = Integer.MIN_VALUE;
		for (Poi poi : poiList) {
			int timeMeasure = poi.getTimeMeasureToFixedPoi();
			if (timeMeasure < minTimeMeasure)
				minTimeMeasure = timeMeasure;
			if (timeMeasure > maxTimeMeasure)
				maxTimeMeasure = timeMeasure;
		}
		int rangeTimeMeasure = maxTimeMeasure - minTimeMeasure;
		
		// calculate scores
		for (int i = 0; i < poiList.size(); ++i) {
			//1. time measure
			double scoresTimeMeasure = 1;
			if (rangeTimeMeasure > 0)
				scoresTimeMeasure = 1.0 - (poiList.get(i).getTimeMeasureToFixedPoi() - minTimeMeasure) / rangeTimeMeasure;
			
			//2. checkins
			double scoresCheckins = 0;
			if (poiList.get(i).getCheckins() > 0)
				scoresCheckins = (1.0 - 1 / Math.pow(poiList.get(i).getCheckins(), 0.02)) * 4;
			
			//3. theme
			double scoresTheme = 0;
			double numerator = 0;
			double denominator = 0;
			for (int j = 0; j < 8; ++j) {
				if (poiList.get(i).getTheme()[j]) {
					++denominator;
					if (userInputTheme[j])
						++numerator;
				}
			}
			if (denominator > 0)
				scoresTheme = numerator / denominator;
			
			scores[i] = scoresTimeMeasure / 8 + scoresCheckins * 3 / 4 + scoresTheme / 8;
		}
		return scores;
	}
	
	public static int findShortestTimeFromPoiToPoiList(Poi poi, List<Poi> poiList, int[][] travelTime) {
		if (poi == null || poiList == null || poiList.isEmpty()) {
			System.err.println("[!!! Error !!!][Poi findShortestTimeIndex] invalid input!");
			return Integer.MAX_VALUE;
		}
		
		int shortestTime = Integer.MAX_VALUE;
		int bestIndex = -1;
		for (int i = 0; i < poiList.size(); ++i) {
			int time = travelTime[ poi.getIndex() ][ poiList.get(i).getIndex() ];
			if ( time < shortestTime ) {
				shortestTime = time;
				bestIndex = i;
			}
		}
		return shortestTime;
	}
	
	//-----------------------------------------------------------
	//    static functions -- deal with itinerary feasibility
	//-----------------------------------------------------------
	public static boolean checkOpenHoursFeasibility(List<Poi> poiList, int[][] travelTime, DailyInfo dailyInfo,
			int earlistLunchTime, int minutesForLunch) {
		
		List<TourEvent> itinerary = new ArrayList<TourEvent>();
		if ( getItineraryFromFeasibleSequence(poiList, travelTime, dailyInfo, earlistLunchTime, minutesForLunch, itinerary) >= 0 )
			return true;
		else
			return false;
	}
	
	public static int[] getEarliestFeasibleVisitDuration(int currentTime, Poi poi, int dayOfWeek) {
		
		String openHoursString = poi.getOpenHoursOfADay(dayOfWeek);
		int[][] openHours = parseOpenHoursString(openHoursString);
		
		int feasibleStartTime = currentTime; //initial guess
		
		if (openHours != null && ! openHoursString.equals("00:00-24:00;")) { //open all day (or assume open all day)
			int j;
			for (j = 0; j < openHours.length; ++j) {
				feasibleStartTime = Math.max(currentTime, openHours[j][0]);
				if ( feasibleStartTime + poi.getStayTime() <= openHours[j][1] )
					break;
			}
			if (j == openHours.length)
				return null; //infeasible
		}
		
		int endTime = feasibleStartTime + poi.getStayTime();
		int[] visitDuration = new int[]{feasibleStartTime, endTime};
		return visitDuration;
	}
	public static TourEvent createTourEvent(String poiId, Calendar calendar0, int startTime, int endTime) {
		TourEvent tourEvent = new TourEvent();
		tourEvent.setPoiId(poiId);
		
		Calendar calendarStartTime = (Calendar)calendar0.clone();
		calendarStartTime.add(Calendar.MINUTE, startTime);
		tourEvent.setStartTime(calendarStartTime.getTime());
		
		Calendar calendarEndTime = (Calendar)calendar0.clone();
		calendarEndTime.add(Calendar.MINUTE, endTime);
		tourEvent.setEndTime(calendarEndTime.getTime());
		
		return tourEvent;
	}
	public static int getItineraryFromFeasibleSequence(List<Poi> poiList, int[][] travelTime, DailyInfo dailyInfo,
			int earlistLunchTime, int minutesForLunch, List<TourEvent> itinerary) {
		
		int timePointer = dailyInfo.getStartTimeInMinutes();
		
		boolean isFinishLunch = false;
		if (dailyInfo.getStartTimeInMinutes() >= earlistLunchTime)
			isFinishLunch = true;
		
		for (int i = 0; i < poiList.size(); ++i) {
			
			int startTime;
			int endTime;
			
			// handle stay time
			if ( (i == 0 && ! dailyInfo.isStartPoiUseStayTime()) || (i == poiList.size() - 1 && ! dailyInfo.isEndPoiUseStayTime()) ) {
				// case: no need to check stay time
				startTime = timePointer;
				endTime = timePointer;
				
			} else {
				//case: need to check stay time
				int[] visitDuration = getEarliestFeasibleVisitDuration(timePointer, poiList.get(i), dailyInfo.getDayOfWeek());
				
				if (visitDuration == null)
					return -1; //infeasible
				else {
					startTime = visitDuration[0];
					endTime = visitDuration[1];
					timePointer = endTime;
				}
			}
			
			// put into TourEvent
			TourEvent tourEvent = createTourEvent(poiList.get(i).getPoiId(), dailyInfo.getCalendarThisDayAtMidnight(), startTime, endTime);
			itinerary.add(tourEvent);
			
			if (i < poiList.size() - 1) {
				
				// check lunch time
				if (! isFinishLunch && timePointer >= earlistLunchTime) {
					timePointer += minutesForLunch;
					isFinishLunch = true;
				}
				
				// handle travel time
				if ( i < poiList.size() - 2 || (i == poiList.size() - 2 && dailyInfo.isTravelToEndPoi()) )
					timePointer += travelTime[ poiList.get(i).getIndex() ][ poiList.get(i+1).getIndex() ];

				// check lunch time
				if (! isFinishLunch && timePointer >= earlistLunchTime) {
					timePointer += minutesForLunch;
					isFinishLunch = true;
				}
			}
		}
		if (timePointer <= dailyInfo.getEndTimeInMinutes())
			return dailyInfo.getEndTimeInMinutes() - timePointer;
		else
			return -1; //infeasible
	}
	
	public static int[][] parseOpenHoursString(String str) {
		if ( str == null || str.isEmpty() || str.length() % 12 != 0 )
			return null;
		else {
			int numPeriod = str.length() / 12;
			int[][] openHours = new int[numPeriod][2];
			for (int i = 0; i < numPeriod; ++i) {
				openHours[i][0] = Integer.parseInt(str.substring(0 + i * 12, 2 + i * 12)) * 60 + Integer.parseInt(str.substring(3 + i * 12, 5 + i * 12));
				openHours[i][1] = Integer.parseInt(str.substring(6 + i * 12, 8 + i * 12)) * 60 + Integer.parseInt(str.substring(9 + i * 12, 11 + i * 12));
				
				if (openHours[i][1] <= openHours[i][0])
					openHours[i][1] += 1440;
			}
			return openHours;
		}
	}
	
	//-----------------------------------------------------------
	//    static functions -- relate to great circle distance
	//-----------------------------------------------------------
	public static double getGreatCircleDistance(double[] start, double[] end) { //Equirectangular approximation //{latitude緯度, longitude經度}
		double PI = 3.14159265;
		double R = 6371.229; //km

		double x = (end[1] - start[1]) * PI / 180 * Math.cos((start[0] + end[0]) / 2 * PI / 180);
		double y = (end[0] - start[0]) * PI / 180;
		return Math.hypot(x, y) * R; //km
	}
	public static double getGreatCircleDistance(double startLatitude, double startLongitude, double endLatitude, double endLongitude) {
		double[] start = new double[]{startLatitude, startLongitude};
		double[] end = new double[]{endLatitude, endLongitude};
		return getGreatCircleDistance(start, end);
	}
	public static double[] getCircleDistanceArray(Poi poi0, List<Poi> poiList) {
		double[] distanceMeasure = new double[poiList.size()];
		for (int i = 0; i < poiList.size(); ++i)
			distanceMeasure[i] = getGreatCircleDistance( poi0.getCoordinate(), poiList.get(i).getCoordinate() );
		return distanceMeasure;
	}
	
	public static double[][] getPairwiseCircleDistanceMatrix(List<Poi> poiList) {
		double[][] distanceMeasure = new double[poiList.size()][poiList.size()];
		for (int i = 0; i < poiList.size(); ++i) {
			for (int j = 0; j < poiList.size(); ++j) {
				if (i == j)
					continue;
				distanceMeasure[i][j] = getGreatCircleDistance( poiList.get(i).getCoordinate(), poiList.get(j).getCoordinate() );
			}
		}
		return distanceMeasure;
	}
	public static double[][] getPairwiseCircleDistanceUpperMatrix(List<Poi> poiList) {
		double[][] distanceMeasure = new double[poiList.size()][poiList.size()];
		for (int i = 0; i < poiList.size() - 1; ++i) {
			for (int j = i + 1; j < poiList.size(); ++j) {
				distanceMeasure[i][j] = getGreatCircleDistance( poiList.get(i).getCoordinate(), poiList.get(j).getCoordinate() );
			}
		}
		return distanceMeasure;
	}
	
}

