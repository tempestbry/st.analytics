package tw.org.iii.model;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class County {
	public static final int numCounty = 22;
	
	//===========================
	//    intermediate county
	//===========================
	private static Map<String, List<Integer>> intermediateCounty = new HashMap<String, List<Integer>>();
	
	static {
		InputStream inputStream = County.class.getClassLoader().getResourceAsStream("intermediate_county.txt");
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
		String line;
		boolean isFirst = true;
		try {
			while ((line = bufferedReader.readLine()) != null)   {
				if (isFirst) { //skip header line
					isFirst = false;
					continue;
				}
				String[] result = line.split(" ");
				List<Integer> counties = new ArrayList<Integer>();
				for (int i = 2; i < result.length; ++i) {
					counties.add( Integer.parseInt(result[i]) );
				}
				intermediateCounty.put(result[0] + "-" + result[1], counties);			
			}
		} catch (NumberFormatException | IOException e) {
			System.err.println("[!!! Error !!!][County] intermediateCounty read data error!");
			e.printStackTrace();
		}
	}
	
	public static List<Integer> getIntermediateCounty(int countyIdx1, int countyIdx2) {
		String key = countyIdx1 + "-" + countyIdx2;
		if (! intermediateCounty.containsKey(key))
			return null;
		else
			return intermediateCounty.get(key);
	}
	
	//=====================
	//    nearby county
	//=====================
	private static Map<String, List<Integer>> nearbyCounty = new HashMap<String, List<Integer>>();
	
	static {
		InputStream inputStream = County.class.getClassLoader().getResourceAsStream("nearby_county.txt");
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
		String line;
		boolean isFirst = true;
		try {
			while ((line = bufferedReader.readLine()) != null)   {
				if (isFirst) { //skip header line
					isFirst = false;
					continue;
				}
				String[] result = line.split(" ");
				List<Integer> counties = new ArrayList<Integer>();
				for (int i = 1; i < result.length; ++i) {
					counties.add( Integer.parseInt(result[i]) );
				}
				nearbyCounty.put(result[0], counties);			
			}
		} catch (NumberFormatException | IOException e) {
			System.err.println("[!!! Error !!!][County] nearbyCounty read data error!");
			e.printStackTrace();
		}
	}
	
	public static List<Integer> getNearbyCounty(int countyIdx) {
		String key = Integer.toString(countyIdx);
		if (! nearbyCounty.containsKey(key))
			return null;
		else
			return nearbyCounty.get(key);
	}
	
	//====================
	//    county index
	//====================
	public static int getCountyIndex(String countyId) {
		if (countyId.equals("all"))
			return 0;
		else
			return Integer.parseInt( countyId.substring(2) );
	}
	
	//===================
	//    county name
	//===================
	private static Map<String, String> countyName = new HashMap<String, String>();
	
	static {
		countyName.put("TW1", "基隆市");
		countyName.put("TW2", "臺北市");
		countyName.put("TW3", "新北市");
		countyName.put("TW4", "桃園市");
		countyName.put("TW5", "新竹縣");
		countyName.put("TW6", "新竹市");
		countyName.put("TW7", "苗栗縣");
		countyName.put("TW8", "臺中市");
		countyName.put("TW9", "彰化縣");
		countyName.put("TW10", "南投縣");
		countyName.put("TW11", "雲林縣");
		countyName.put("TW12", "嘉義縣");
		countyName.put("TW13", "嘉義市");
		countyName.put("TW14", "臺南市");
		countyName.put("TW15", "高雄市");
		countyName.put("TW16", "屏東縣");
		countyName.put("TW17", "宜蘭縣");
		countyName.put("TW18", "花蓮縣");
		countyName.put("TW19", "臺東縣");
		countyName.put("TW20", "澎湖縣");
		countyName.put("TW21", "金門縣");
		countyName.put("TW22", "連江縣");
	}
	
	public static String getCountyName(String countyId) {
		return countyName.get(countyId);
	}
	
	public static String getCountyName(int countyId) {
		return countyName.get("TW" + countyId);
	}
	
	//=============================================
	//    default start location in each county
	//=============================================
	public static double[][] defaultStartCoordinate = new double[numCounty][2];
	
	static {
		InputStream inputStream = County.class.getClassLoader().getResourceAsStream("defaultStartLocation.txt");
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
		String line;
		boolean isFirst = true;
		try {
			int k = 0;
			while ((line = bufferedReader.readLine()) != null)   {
				if (isFirst) { //skip header line
					isFirst = false;
					continue;
				}
				String[] result = line.split(" ");
				defaultStartCoordinate[k][0] = Double.parseDouble(result[2]);
				defaultStartCoordinate[k++][1] = Double.parseDouble(result[3]);
			}
		} catch (NumberFormatException | IOException e) {
			System.err.println("[!!! Error !!!][County] defaultStartCoordinate read data error!");
			e.printStackTrace();
		}
	}
	
//	public static String[] startingPoiIdInCounty = new String[]
//			{"C1_376570000A_000078", "e6449cc1-6047-f3a2-1618-5157c3765714", "331d1dd1-03de-6598-3860-95331d6c5574",
//			"C1_376430000A_000046", "C1_376440000A_000920", "C1_376580000A_000003",
//			"K_m07_20040722093203", "C1_387000000A_000002", "N_achi_20061009140205",
//			"C1_376480000A_000283", "C1_376490000A_000032", "C1_376500000A_000090",
//			"C1_376600000A_000021", "C1_395000000A_000225", "C1_397000000A_000015",
//			"C1_376530000A_000328", "A_l50740_20081105222247", "9c935214-d13c-029e-0a35-91d0f2a8b24f",
//			"dcb22967-7e90-f01b-9a84-6af6ec4b733d", "C1_315080600H_000261", "C1_371020000A_000540",
//			"Z_jasmine_20051114154859"};
	
	//========================
	//    county selection
	//========================
//	private static int[] westCounty = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 10, 9, 11, 12, 13, 14, 15, 16};
//	private static int[] eastCounty = new int[]{17, 18, 19};
//	private static int[] outerIslandCounty = new int[]{20, 21, 22};
	
	private static void getNeighborsInWest(int baseCounty, int numDays, Set<Integer> neighbors) {
		int range = Math.min(numDays, 7);
		int end1 = baseCounty - range;
		int end2 = baseCounty + range;
		if (end1 < 1) {
			end1 = 1;
			end2 = end2 - end1 + 1;
		}
		if (end2 > 16) {
			end2 = 16;
			end1 = end1 - (end2 - 16);
		}
		for (int i = end1; i <= end2; ++i)
			neighbors.add(i);
	}
	private static void getNeighborsInEast(Set<Integer> neighbors) {
		neighbors.add(17);
		neighbors.add(18);
		neighbors.add(19);
	}
	public static Set<Integer> getCandidateCounty(int baseCounty, int numDays) {
		Set<Integer> candidateCounties = new HashSet<Integer>();
		
		if (baseCounty <= 16) { //1~16
			getNeighborsInWest(baseCounty, numDays, candidateCounties);
			
			if (numDays >= 6)
				getNeighborsInEast(candidateCounties);
			
		} else if (baseCounty <= 19) { //17~19
			getNeighborsInEast(candidateCounties);
			
			if (numDays >= 4) {
				Random random = new Random();
				getNeighborsInWest(random.nextInt(16) + 1, numDays - 2, candidateCounties);
			}
			
		} else { //20~22
			if (numDays >= 5) {
				Random random = new Random();
				int county = random.nextInt(19) + 1;
				if (county <= 16)
					getNeighborsInWest(county, numDays - 4, candidateCounties);
				else
					getNeighborsInEast(candidateCounties);
				
				candidateCounties.add(baseCounty);
			}
		}
		return candidateCounties;
	}
}
