package tw.org.iii.model;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

public class Poi {
	
	// from database
	private String poiId;
	private String poiName;
	private String location;
	private String countyId;
	private int checkins;
	private int[] theme = new int[8];
	private int stayTime;
	private String[] openHoursOfSevenDays = new String[7];
	
	// from user input
	private boolean isMustPoi;
	
	// used to access distance matrix
	private int index;
	
	//private double longitude;
	//private double latitude;
	
	public void setFromQueryResult(Map<String, Object> queryResultPoi, int looseType) {
		poiId = queryResultPoi.get("poiId").toString();
		poiName = queryResultPoi.get("name").toString();
		location = queryResultPoi.get("location").toString();
		countyId = queryResultPoi.get("countyId").toString();
		
		Object tempObj1 = queryResultPoi.get("checkinTotal");
		checkins = (tempObj1 != null) ? Integer.parseInt(tempObj1.toString()) : 0;
		
		for (int i = 10; i <= 17; ++i) {
			Object tempObj2 = queryResultPoi.get("TH" + i);
			theme[i-10] = ( tempObj2 != null && tempObj2.toString().equals("1") ) ? 1 : 0;
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
	}
	
	@Override
	public String toString() {
		String str = "[" + poiId + "][" + poiName + "][" + countyId + " " + County.getCountyName(countyId) + "]";
		return str;
	}
	
	public String getOpenHoursOfADay(int day) {
		return openHoursOfSevenDays[day];
	}
	
	//------------------------
	//  auto getter / setter
	//------------------------
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

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
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

	public int[] getTheme() {
		return theme;
	}

	public void setTheme(int[] theme) {
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

	//---------------------
	//  static functions
	//---------------------
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
			poiCenter = parseCoordinate( poiList.get(0).getLocation() );
		}
		else {
			double sumLatitude = 0;
			double sumLongitude = 0;
			for (Poi poi : poiList) {
				double[] coordinate = parseCoordinate(poi.getLocation());
				sumLatitude += coordinate[0];
				sumLongitude += coordinate[1];
			}
			poiCenter[0] = sumLatitude / poiList.size();
			poiCenter[1] = sumLongitude / poiList.size();
		}
		return poiCenter;
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
	
	public static boolean checkOpenHoursFeasibility(List<Poi> poiList, int[][] travelTime, DailyInfo dailyInfo,
			int earlistLunchTime, int minutesForLunch) {
		
		if ( getTimeInfoFromFeasibleSequence(poiList, travelTime, dailyInfo, earlistLunchTime, minutesForLunch) != null )
			return true;
		else
			return false;
	}
	
	public static List<TourEvent> getTimeInfoFromFeasibleSequence(List<Poi> poiList, int[][] travelTime, DailyInfo dailyInfo,
			int earlistLunchTime, int minutesForLunch) {
		
		List<TourEvent> itinerary = new ArrayList<TourEvent>();
		
		Calendar calendar0 = dailyInfo.getCalendarForDay();
		calendar0.set(Calendar.HOUR_OF_DAY, 0);
		calendar0.set(Calendar.MINUTE, 0);
		calendar0.set(Calendar.SECOND, 0);
		calendar0.set(Calendar.MILLISECOND, 0);
		
		int timePointer = dailyInfo.getStartTimeInMinutes();
		boolean isFinishLunch = false;
		
		for (int i = 0; i < poiList.size(); ++i) {
			
			int startTime;
			int endTime;
			
			// handle stay time
			if ( (i == 0 && ! dailyInfo.isStartPoiUseStayTime()) || (i == poiList.size() - 1 && ! dailyInfo.isEndPoiUseStayTime()) ) {
				startTime = timePointer;
				endTime = timePointer;
				//timePointer unchanged
			} else {
				String openHoursString = poiList.get(i).getOpenHoursOfADay( dailyInfo.getDayOfWeek() );
				int[][] openHours = parseOpenHoursString(openHoursString);
				int feasibleStartTime = -1;
				
				if (openHours == null || openHoursString.equals("00:00-24:00;")) { //open all day (or assume open all day)
					feasibleStartTime = timePointer;
				} else {
					int j;
					for (j = 0; j < openHours.length; ++j) {
						feasibleStartTime = Math.max(timePointer, openHours[j][0]);
						if ( feasibleStartTime + poiList.get(i).getStayTime() <= openHours[j][1] )
							break;
					}
					if (j == openHours.length)
						return null;
				}
				startTime = feasibleStartTime;
				endTime = startTime + poiList.get(i).getStayTime();
				timePointer = endTime;
			}
			
			// put into TourEvent
			TourEvent tourEvent = new TourEvent();
			tourEvent.setPoiId(poiList.get(i).getPoiId());
			
			Calendar calendarStartTime = (Calendar)calendar0.clone();
			calendarStartTime.add(Calendar.MINUTE, startTime);
			tourEvent.setStartTime(calendarStartTime.getTime());
			
			Calendar calendarEndTime = (Calendar)calendar0.clone();
			calendarEndTime.add(Calendar.MINUTE, endTime);
			tourEvent.setEndTime(calendarEndTime.getTime());
			
			itinerary.add(tourEvent);
			
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
		if (timePointer <= dailyInfo.getEndTimeInMinutes())
			return itinerary;
		else
			return null;
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
	
/*	public double[] getCoordinate() {
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
	}*/
	
}

