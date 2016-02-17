package tw.org.iii.model;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class County {
	//-----------------------
	//  intermediate county
	//-----------------------
	private static Map<String, List<Integer>> intermediateCounty = new HashMap<String, List<Integer>>();
	
	static {
		InputStream inputStream = County.class.getClassLoader().getResourceAsStream("intermediate_county.txt");
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
		String line;
		try {
			while ((line = bufferedReader.readLine()) != null)   {
				String[] result = line.split(" ");
				List<Integer> counties = new ArrayList<Integer>();
				for (int i = 2; i < result.length; ++i) {
					counties.add( Integer.parseInt(result[i]) );
				}
				intermediateCounty.put(result[0] + "-" + result[1], counties);			
			}
		} catch (NumberFormatException | IOException e) {
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
	
	//-----------------------
	//   nearby county
	//-----------------------
	private static Map<String, List<Integer>> nearbyCounty = new HashMap<String, List<Integer>>();
	
	static {
		InputStream inputStream = County.class.getClassLoader().getResourceAsStream("nearby_county.txt");
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
		String line;
		try {
			while ((line = bufferedReader.readLine()) != null)   {
				String[] result = line.split(" ");
				List<Integer> counties = new ArrayList<Integer>();
				for (int i = 1; i < result.length; ++i) {
					counties.add( Integer.parseInt(result[i]) );
				}
				nearbyCounty.put(result[0], counties);			
			}
		} catch (NumberFormatException | IOException e) {
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
	
	//-----------------------
	//   county name
	//-----------------------
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
}
