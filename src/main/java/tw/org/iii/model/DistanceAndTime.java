package tw.org.iii.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DistanceAndTime {
	//-------------------------------
	//    distance between county
	//-------------------------------
	private static double[][] distanceBetweenCounty = new double[County.numCounty][County.numCounty];
	static {
		InputStream inputStream = County.class.getClassLoader().getResourceAsStream("distanceBetweenCounty.txt");
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
		try {
			for (int i = 0; i < County.numCounty; ++i) {
				String line = bufferedReader.readLine();
				String[] result = line.split(" ");
				for (int j = 0; j < County.numCounty; ++j) {
					distanceBetweenCounty[i][j] = Double.parseDouble( result[j] );
				}
			}
		} catch (IOException e) {
			System.err.println("[!!! Error !!!][QueryDatabase] distanceBetweenCounty read data error!");
			e.printStackTrace();
		}

	}
	//---------------------------
	//    time between county
	//---------------------------
	private static int[][] timeBetweenCounty = new int[County.numCounty][County.numCounty];
	static {
		InputStream inputStream = County.class.getClassLoader().getResourceAsStream("timeBetweenCounty.txt");
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
		try {
			for (int i = 0; i < County.numCounty; ++i) {
				String line = bufferedReader.readLine();
				String[] result = line.split(" ");
				for (int j = 0; j < County.numCounty; ++j) {
					timeBetweenCounty[i][j] = Integer.parseInt( result[j] );
				}
			}
		} catch (IOException e) {
			System.err.println("[!!! Error !!!][QueryDatabase] timeBetweenCounty read data error!");
			e.printStackTrace();
		}

	}
	//------------------------------------------
	//    get shortest time between counties
	//------------------------------------------
	public static int getShortestTime_BetweenCounties(List<Poi> poiList, Set<Integer> countySet) {
		Map<Integer, Integer> minTimeStorage = new HashMap<Integer, Integer>(); //only store the following:
											//key: countyIndex of a POI (1st arg) which is not in countySet (2nd arg)
											//value: shortest time to countySet (2nd arg)
		double grandTotal = 0;
		for (Poi poi : poiList) {
			int i = poi.getCountyIndex();
			
			if (! countySet.contains(i)) {
				
				if (minTimeStorage.containsKey(i)) {
					grandTotal += minTimeStorage.get(i);
				} else {
					int minTime = Integer.MAX_VALUE;
					for (Integer j : countySet) {
						int time = timeBetweenCounty[i-1][j-1];
						if (time < minTime)
							minTime = time;
					}
					minTimeStorage.put(i, minTime);
					
					grandTotal += minTime;
				}
			}
		}
		return (int)(grandTotal / poiList.size());
	}
	public static int getShortestTime_BetweenCounties(List<Poi> poiList, int county) {
		
		double grandTotal = 0;
		for (Poi poi : poiList) {
			int i = poi.getCountyIndex();
			
			if (i != county) {
				grandTotal += timeBetweenCounty[i-1][county-1];
			}
		}
		return (int)(grandTotal / poiList.size());
	}
	
	//-------------------------------------------------
	//    get estimated distance/time of nearby POI
	//-------------------------------------------------
	public static double getEstimatedDistanceOfNearbyPoi(double circleDistance) {
		return (double)Math.round( (0.534 + 1.517 * circleDistance) * 10) / 10; //round to one decimal place
	}
	public static double getEstimatedDistanceOfNearbyPoi(Poi poi1, Poi poi2) {
		return getEstimatedDistanceOfNearbyPoi( Poi.getGreatCircleDistance(poi1.getCoordinate(), poi2.getCoordinate()) );
	}
	public static int getEstimatedTimeOfNearbyPoi(double circleDistance) {
		return Math.max(1, (int)(-0.766 + 0.531 * circleDistance));
	}
	public static int getEstimatedTimeOfNearbyPoi(Poi poi1, Poi poi2) {
		return getEstimatedTimeOfNearbyPoi( Poi.getGreatCircleDistance(poi1.getCoordinate(), poi2.getCoordinate()) );
	}
	
//	//-------------------------------------------------
//	//    fitting coefficients of distance and time
//	//-------------------------------------------------
//	private static FittingCoefficient[][] coefficient = new FittingCoefficient[County.numCounty][County.numCounty];
//	static {
//		InputStream inputStream = County.class.getClassLoader().getResourceAsStream("distanceAndTimeFittingCoefficient.txt");
//		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
//		String line;
//		boolean isFirst = true;
//		try {
//			while ((line = bufferedReader.readLine()) != null)   {
//				if (isFirst) { //skip header line
//					isFirst = false;
//					continue;
//				}
//				String[] result = line.split(" ");
//				FittingCoefficient coef = new FittingCoefficient(
//						Double.parseDouble( result[2] ), Double.parseDouble( result[3] ),
//						Double.parseDouble( result[4] ), Double.parseDouble( result[5] ));
//				coefficient[ Integer.parseInt( result[0] ) - 1 ][ Integer.parseInt( result[1] ) - 1 ] = coef;
//			}
//		} catch (IOException e) {
//			System.err.println("[!!! Error !!!][QueryDatabase] coefficient read data error!");
//			e.printStackTrace();
//		}
//
//	}
//	private static class FittingCoefficient {
//		private double distance_b0;
//		private double distance_b1;
//		private double time_b0;
//		private double time_b1;
//		FittingCoefficient(double distance_b0, double distance_b1, double time_b0, double time_b1) {
//			this.distance_b0 = distance_b0;
//			this.distance_b1 = distance_b1;
//			this.time_b0 = time_b0;
//			this.time_b1 = time_b1;
//		}
//	}
//	
//	//-------------------------------------
//	//    get fitting distance and time
//	//-------------------------------------
//	public static void getFittingDistanceAndTime(Poi poi1, Poi poi2, double[] distanceOfPoiPair, int[] timeOfPoiPair) {
//		int cid1 = poi1.getCountyIndex();
//		int cid2 = poi2.getCountyIndex();
//		if ( (cid1 <= 20 && cid2 <= 20) || (cid1 == cid2) ) {
//			double circleDistance = Poi.getGreatCircleDistance(poi1.getCoordinate(), poi2.getCoordinate());
//			distanceOfPoiPair[0] = coefficient[cid1 - 1][cid2 - 1].distance_b0 + coefficient[cid1 - 1][cid2 - 1].distance_b1 * circleDistance;
//			distanceOfPoiPair[0] = (double)Math.round(distanceOfPoiPair[0] * 10) / 10; //round to one decimal place
//			
//			timeOfPoiPair[0] = (int)(coefficient[cid1 - 1][cid2 - 1].time_b0 + coefficient[cid1 - 1][cid2 - 1].time_b1 * circleDistance);
//		} else {
//			distanceOfPoiPair[0] = distanceBetweenCounty[cid1 - 1][cid2 - 1];
//			timeOfPoiPair[0] = timeBetweenCounty[cid1 - 1][cid2 - 1];
//		}
//	}
}

