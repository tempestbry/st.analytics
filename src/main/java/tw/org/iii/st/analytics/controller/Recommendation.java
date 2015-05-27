package tw.org.iii.st.analytics.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;




import tw.org.iii.model.PoiCheckins;
import tw.org.iii.model.RecommendInfo;

@RestController
@RequestMapping("/Recommendation")
public class Recommendation {
	@Autowired
	JdbcTemplate jdbcTemplate;

	//test
	
	@RequestMapping("/ThisTime")
	public @ResponseBody String[] homeRecommendation(@RequestParam(value = "px", defaultValue = "121.5548724") double px,@RequestParam(value = "py", defaultValue = "25.0584759") double py) throws ParseException, ClassNotFoundException, SQLException, IOException
	{
		timeInfo ti = getWeekday();
		String[] result = FindBestPOI(ti,px,py);
		
		return result;
	}
	
	@RequestMapping("/Related")
	public String[] relatedRecommendation(@RequestParam(value = "pid", defaultValue = "") String pid) throws ClassNotFoundException, SQLException
	{
		List<RecommendInfo> sqlresult = Query1("SELECT recommend_id,(0.5*AR+0.2*Top+0.3*CB) AS total FROM Hybrid WHERE "
				+ "place_id = '"+pid+"' and CB <> 1 and place_county = recommend_county ORDER BY total DESC LIMIT 0,5");
		
		
		int i=0;
		String result[] = new String[5];
		for (RecommendInfo ri : sqlresult) 
		{
			result[i++] = ri.getRecommendID();
		}
				
		return result;
      
	}
	
	private boolean askGoogle(double px,double py) throws IOException
	{
		String county[] = {"台東","花蓮","宜蘭"};
		
		String html = request("http://maps.google.com/maps/api/geocode/json?latlng="+py+","+px+"&language=zh-TW&sensor=true","UTF-8");
		
		Pattern p = Pattern.compile("\"long_name\" : \".{2}[縣市]\","); 
		Matcher m = p.matcher(html);
		
		String city="";
		if (m.find())
		{
			city = Parser(m.group(),"\"long_name\" : \"","\",",1);
		}
		for (String c : county)
		{
			if (city.contains(c))
				return false;
		}
		return true;

	}
	
	
	private String Parser(String text,String front,String end,int type)
	{
		int from=0;
		int finish=0;
		
		if (text.indexOf(front)==-1 || text.indexOf(end) == -1)
			return "";
		switch (type)
		{
			//前後tag都是第一次出現
			case 1:
				from = text.indexOf(front)+front.length();
				finish = text.indexOf(end);
				break;
			//end tag為出現在 front之後的第一個
			case 2:
				from = text.indexOf(front)+front.length();
				finish = text.indexOf(end,from);
				break;
			//前後tag都是最後一次出現
			case 3:
				from = text.lastIndexOf(front)+front.length();
				finish = text.lastIndexOf(end);
				break;
		}
		try
		{
			return text.substring(from,finish).replace("'", "").replace("\r", "").replace("\n", "").trim();
		}
		catch (Exception e)
		{
			return "";
		}

	}
	
	private  String request(String url,String type) throws IOException
	{
		StringBuilder results = new StringBuilder();
		try
		{
			URL myURL = new URL(url);
	        HttpURLConnection connection = (HttpURLConnection) myURL.openConnection();
	        connection.setRequestMethod("GET");
	        connection.setDoOutput(true);
	        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
	        connection.connect();
	        
	        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(),type));
	        
	        String line;
	        while ((line = reader.readLine()) != null) {
	            results.append(line);
	        }

	        connection.disconnect();
	        
		}
		catch (Exception e)
		{
			results.append("");
		}
		return results.toString();
		
	}
	
	
	private String[] FindBestPOI(timeInfo ti,double px,double py) throws SQLException, ClassNotFoundException, IOException
	{
		
		int hour = ti.hour;

		HashMap<String,Integer> checkins = new HashMap<String,Integer>();
		HashMap<String,Double> distance = new HashMap<String,Double>();
		HashMap<String,Integer> finalResult = new HashMap<String,Integer>();
		
		String result[] = new String[5];
		
		List<PoiCheckins> sqlresult = null;
		if (24-hour>2)
		{
			sqlresult = Query("SELECT A.place_id,checkins,px,py FROM scheduling AS A, OpenTimeArray AS B "
					+ "WHERE B.weekday = '"+ti.weekday+"' and ("+hour+"_Oclock = 1 and "+(hour+1)+"_Oclock = 1 and "+(hour+2)+"_Oclock=1) "
					+ "and A.checkins IS NOT NULL and A.Place_Id = B.place_id GROUP BY A.fb_id");	
		}
		else
		{
			if (24-hour==1)
			{
				sqlresult = Query("SELECT A.place_id,checkins,px,py FROM scheduling AS A, OpenTimeArray AS B "
						+ "WHERE B.weekday = '"+ti.weekday+"' and ("+hour+"_Oclock = 1 and 0_Oclock = 1 and 1_Oclock=1) "
						+ "and A.checkins IS NOT NULL and A.Place_Id = B.place_id GROUP BY A.fb_id");
			}
			else if (24-hour==2)
			{
				sqlresult = Query("SELECT A.place_id,checkins,px,py FROM scheduling AS A, OpenTimeArray AS B "
						+ "WHERE B.weekday = '"+ti.weekday+"' and ("+hour+"_Oclock = 1 and "+(hour+1)+"_Oclock = 1 and 0_Oclock=1) "
						+ "and A.checkins IS NOT NULL and A.Place_Id = B.place_id GROUP BY A.fb_id");	
			}
		}
		
		for (PoiCheckins p : sqlresult)
		{
			double dis = Distance(py,px,p.getPy(),p.getPx());
			distance.put(p.getPlaceID(), dis);
			checkins.put(p.getPlaceID(), p.getCheckins());
		}

		
		//List<Map.Entry<String, Integer>> minResult = min_sort(distance);
		
		
		
		int i=0;
		//if (minResult.get(minResult.size()-1).getValue()>80) //與所有景點的min distance距離很遠(外縣市), 則推花東top景點
		if (askGoogle(px,py))
		{
			
			int limit=0;
			//找出前30名的打卡點
			sqlresult = Query("SELECT checkins FROM scheduling AS A, OpenTimeArray AS B "
					+ "WHERE B.weekday = '"+ti.weekday+"' "
					+ "and A.checkins IS NOT NULL and A.Place_Id = B.place_id GROUP BY fb_id ORDER BY checkins DESC limit 0,30");
			for (PoiCheckins p : sqlresult)
			{
				limit++;
				if (limit==30)
					limit = p.getCheckins();
			}
			//30個裡面隨機挑選
			sqlresult = Query("SELECT A.place_id FROM scheduling AS A, OpenTimeArray AS B "
					+ "WHERE B.weekday = '"+ti.weekday+"' "
					+ "and A.checkins IS NOT NULL and A.Place_Id = B.place_id and A.checkins > "+limit+" GROUP BY fb_id ORDER BY RAND()");
			for (PoiCheckins p : sqlresult)
			{
				if (i==5)
					break;
				result[i++] = p.getPlaceID(); 
			}
			return result;
		}
			
		for (String d : distance.keySet()) //尋找30分鐘內可到的點
		{
			if ((distance.get(d) / 0.7)<30)
			{
				finalResult.put(d, checkins.get(d));
			}	
		}
		
		if (finalResult.size()<5) //擴大範圍->一小時內
		{
			for (String d : distance.keySet())
			{
				if ((distance.get(d) / 0.7)<60 && !finalResult.containsKey(d))
				{
					finalResult.put(d, checkins.get(d));
				}	
			}
			
			if (finalResult.size()<5) //全部有打卡數的景點加入
			{
				for (String d : distance.keySet())
				{
					if (!finalResult.containsKey(d))
					{
						finalResult.put(d, checkins.get(d));
					}	
				}
			}
		}
		List<Map.Entry<String, Integer>> rank = sort(finalResult);
		for (Map.Entry<String, Integer> entry:rank) 
	    {
			result[i++] = entry.getKey();
			if (i==5)
				break;
	    }
		return result;
	}
	
	private List<PoiCheckins> Query(String q) {

		
		List<PoiCheckins> results = jdbcTemplate.query(
				q,
				new RowMapper<PoiCheckins>() {
					@Override
					public PoiCheckins mapRow(ResultSet rs, int rowNum)
							throws SQLException {
						return new PoiCheckins(rs.getString("A.place_id"),rs.getInt("checkins"),rs.getDouble("px"),rs.getDouble("py"));
					}
				});
		return results;
		
	}
	
	private static List<Map.Entry<String, Integer>> sort(HashMap<String,Integer> a)
	{
		List<Map.Entry<String, Integer>> list_Data = new ArrayList<Map.Entry<String, Integer>>(a.entrySet());

		Collections.sort(list_Data, new Comparator<Map.Entry<String, Integer>>()
		{
            public int compare(Map.Entry<String, Integer> entry1,
                               Map.Entry<String, Integer> entry2){
                return (entry2.getValue() - entry1.getValue());
            }
        });
		
		return list_Data;
	}
	private class timeInfo
	{
		int hour;
		String weekday;
	}
	private timeInfo getWeekday() throws ParseException
	{
		Date date = new Date();
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
		timeInfo ti = new timeInfo();
		switch(dayOfWeek)
		{
		    case Calendar.SUNDAY:
		    	ti.weekday = "Op_Sunday";
		    	break;
		    case Calendar.MONDAY:
		    	ti.weekday = "Op_Monday";
		    	break;
		    case Calendar.TUESDAY:
		    	ti.weekday = "Op_Tuesday";
		    	break;
		    case Calendar.WEDNESDAY:
		    	ti.weekday = "Op_Wednesday";
		    	break;
		    case Calendar.THURSDAY:
		    	ti.weekday = "Op_Thursday";
		    	break;
		    case Calendar.FRIDAY:
		    	ti.weekday = "Op_Friday";
		    	break;
		    case Calendar.SATURDAY:
		    	ti.weekday = "Op_Saturday";
		    	break;
	    }
		ti.hour = date.getHours();
		return ti;
	}
	private double Distance(double wd1,double jd1,double wd2,double jd2) //緯度, 經度這樣放
	{
		double x,y,out;
		double PI=3.14159265;
		double R=6.371229*1e6;
	
		x=(jd2-jd1)*PI*R*Math.cos(((wd1+wd2)/2) *PI/180)/180;
		y=(wd2-wd1)*PI*R/180;
		out=Math.hypot(x,y);
		return out/1000;
	}
	
	
	private List<RecommendInfo> Query1(String q) {

		
		List<RecommendInfo> results = jdbcTemplate.query(
				q,
				new RowMapper<RecommendInfo>() {
					@Override
					public RecommendInfo mapRow(ResultSet rs, int rowNum)
							throws SQLException {
						return new RecommendInfo(rs.getString("recommend_id"),rs.getDouble("total"));
					}
				});
		return results;
		
	}
}
