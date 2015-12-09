package tw.org.iii.st.analytics.controller;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;












import tw.org.iii.model.PoiCheckins;
import tw.org.iii.model.RecommendInfo;
import tw.org.iii.model.RecommendInput;

@RestController
@RequestMapping("/Recommendation")
public class Recommendation {
	@Autowired
	@Qualifier("hualienJdbcTempplate")
	private JdbcTemplate jdbcTemplate;
	
	@Autowired
	@Qualifier("stcJdbcTemplate")
	private JdbcTemplate commonJdbcTemplate;

	@Autowired
	@Qualifier("analyticsJdbcTemplate")
	private JdbcTemplate analyticsjdbc;
	
	//test
	
//	@RequestMapping("/ThisTime")
//	public @ResponseBody String[] homeRecommendation(@RequestParam(value = "px", defaultValue = "121.5548724") double px,@RequestParam(value = "py", defaultValue = "25.0584759") double py) throws ParseException, ClassNotFoundException, SQLException, IOException
//	{
//		timeInfo ti = getWeekday();
//		String[] result = FindBestPOI(ti,px,py);
//		
//		return result;
//	}
//	
//	@RequestMapping("/Related")
//	public String[] relatedRecommendation(@RequestParam(value = "pid", defaultValue = "") String pid) throws ClassNotFoundException, SQLException
//	{
//		List<RecommendInfo> sqlresult = Query1("SELECT recommend_id,(0.5*AR+0.2*Top+0.3*CB) AS total FROM Hybrid WHERE "
//				+ "place_id = '"+pid+"' and CB <> 1 and place_county = recommend_county ORDER BY total DESC LIMIT 0,5");
//		
//		
//		int i=0;
//		String result[] = new String[5];
//		for (RecommendInfo ri : sqlresult) 
//		{
//			result[i++] = ri.getRecommendID();
//		}
//				
//		return result;
//      
//	}
	
	@RequestMapping("/Related")
	public String[] relatedRecommendation(@RequestBody RecommendInput json) throws ClassNotFoundException, SQLException, NumberFormatException, IOException, ParseException
	{
		String result[] = new String[5];
		
		String pid = json.getPoiId();
		if ("".equals(pid) || pid == null) //主頁的猜你喜歡
		{
			//花東場域
			if (json.getCountyId().contains("TW18") && json.getCountyId().contains("TW19"))
			{
				timeInfo ti = getWeekday();
				//String spl[] = json.getGps().split(",");
				result = FindBestPOI(ti, json.getGps().getLng(), json.getGps().getLat());
				return result;
			}
			else //智慧觀光平台的
			{
				result = FinStBestPOI(json);
				return result;
			}
	
		}
		else //相關推薦
		{
			//花東場域
			if (json.getCountyId().contains("TW18") && json.getCountyId().contains("TW19"))
			{
				List<String> c = json.getCountyId();
				String countyList = "";
				for (int i=0;i<c.size()-1;i++)
					countyList += "place_county = '"+c.get(i)+"' or ";
				countyList += "place_county = '"+c.get(c.size()-1)+"'";
				List<RecommendInfo> sqlresult = Query1("SELECT recommend_id,(0.5*AR+0.2*Top+0.3*CB) AS total FROM Hybrid WHERE "
						+ "place_id = '"+pid+"' and CB <> 1 and place_county = recommend_county and ("+countyList+") and type = '"+json.getReturnType()+"' ORDER BY total DESC LIMIT 0,5");
				int i=0;
				
				for (RecommendInfo ri : sqlresult) 
				{
					result[i++] = ri.getRecommendID();
				}
				
				//沒有結果時, 則挑top進行推薦
				if (result[4]==null)
				{
					HashMap<String,defaultResult> tmp = new HashMap<String,defaultResult>();
					List<place_info> sql = Query2("SELECT id,location2,checkins FROM recommendation WHERE "+countyList.replace("place_county", "countyId")+" and id = '"+pid+"'");
					List<place_info> sql1 = Query2("SELECT id,location2,checkins FROM recommendation WHERE "+countyList.replace("place_county", "countyId")+" and location2 <> 'POINT(0 0)' and checkins > 20000 and id <> '"+pid+"' and fb_name IS NOT NULL GROUP BY fb_name");
					for (place_info s : sql) 
					{
						double latitude = 0,latitude_tmp;
						double longitude = 0,longitude_tmp;
						String[] location = s.gps.split("\\(|\\)| "),location_tmp;

						longitude = Double.parseDouble(location[2]);
						latitude = Double.parseDouble(location[1]);

						for (place_info s1 : sql1) 
						{
							defaultResult d = new defaultResult();
							location_tmp = s1.gps.split("\\(|\\)| ");
							longitude_tmp = Double.parseDouble(location_tmp[2]);
							latitude_tmp = Double.parseDouble(location_tmp[1]);
							d.distance = Distance(longitude,latitude,longitude_tmp,latitude_tmp);
							d.checkins = s1.checkins;
							tmp.put(s1.id, d);
						}
					}
					List<Map.Entry<String, Integer>> sortresult = locationsort(tmp);
					
					for (Map.Entry<String, Integer> entry:sortresult) 
				    {
						result[i++] =entry.getKey();
						if (i==5)
							break;
				    }		
					
				}
				
				if (i<5) //最後確認
				{
					
					List<place_info> sql = Query2("SELECT id,location2,checkins FROM recommendation WHERE "+countyList.replace("place_county", "countyId")+" ORDER BY checkins");
					for (place_info s : sql) 
				    {
						result[i++] = s.id;
						if (i==5)
							break;
					}
				}
			}
			else //智慧觀光場域
			{
//				ArrayList<String> tmp = new ArrayList<String>();
//				int i=0;
//				List<Map<String, Object>> rs = analyticsjdbc.queryForList("SELECT related_id FROM IntegratedRecommendation WHERE poiId = '"+json.getPoiId()+"' and cb > 0 ORDER BY cb DESC limit 0,10");
//				for (Map<String, Object> r : rs) 
//					tmp.add(r.get("related_id").toString());
//				long seed = System.nanoTime();
//				Collections.shuffle(tmp, new Random(seed));
//				for (String t : tmp) 
//			    {
//					result[i++] = t;
//					if (i==5)
//						break;
//				}
				
				normalize n = new normalize();
				double cb,ar,checkins;
				HashMap<String,info> record = new HashMap<String,info>();
				List<Map<String, Object>> rs = analyticsjdbc.queryForList("SELECT related_id,cb,ar,top FROM IntegratedRecommendation WHERE poiId = '"+json.getPoiId()+"' and type = '"+json.getReturnType()+"'");
				if (rs.size()>0)
				{
					boolean flag = true;
					for (Map<String, Object> r : rs) 
					{
						cb = Double.parseDouble(r.get("cb")+"");
						ar = Double.parseDouble(r.get("ar")+"");
						checkins = Double.parseDouble(r.get("top")+"");
						info i = new info(cb,ar,checkins);
						record.put(r.get("related_id").toString(), i);
						if (flag)
						{
							n.cbMax = cb;
							n.cbMin = cb;
							n.arMax = ar;
							n.arMin = ar;
							n.checkinsMax = checkins;
							n.checkinsMin = checkins;
							flag = false;
						}
						if (cb > n.cbMax)
							n.cbMax = cb;
						if (cb < n.cbMin)
							n.cbMin = cb;
						if (ar > n.arMax)
							n.arMax = ar;
						if (ar < n.arMin)
							n.arMin = ar;
						if (checkins > n.checkinsMax)
							n.checkinsMax = checkins;
						if (checkins < n.checkinsMin)
							n.checkinsMin = checkins;
					}
					n.cbValue = n.cbMax - n.cbMin;
					n.arValue = n.arMax - n.arMin;
					n.checkinsValue = n.checkinsMax - n.checkinsMin;
					result = integrated(record,n);
				}
				else //沒有在integrated表裡面
				{
					rs = commonJdbcTemplate.queryForList("SELECT countyId FROM Poi WHERE id = '"+json.getPoiId()+"'");
					String county = rs.get(0).get("countyId").toString();
					ArrayList<String> tmp = new ArrayList<String>();
					int value = 100000;
					do
					{
						rs = analyticsjdbc.queryForList("SELECT id FROM recommendation WHERE countyId = '"+county+"' and checkins > "+value+" and type = '"+json.getReturnType()+"' GROUP BY fb_id ORDER by rand() limit 0,10");
						for (Map<String, Object> i : rs)
						{
							if (!tmp.contains(i.get("id").toString()))
								tmp.add(i.get("id").toString());
							if (tmp.size()==10)
								break;
						}
							
						value-=5000;
						if (value < 0)
						{
							break;
						}
					}
					while (tmp.size()<10);
					
					//沒有任何結果時, random
					if (tmp.size()<10)
					{
						rs = commonJdbcTemplate.queryForList("SELECT id FROM Poi WHERE countyId = '"+county+"' and type = '"+json.getReturnType()+"' ORDER by rand() limit 0,20");
						for (Map<String, Object> i : rs)
						{
							if (!tmp.contains(i.get("id").toString()))
								tmp.add(i.get("id").toString());
							if (tmp.size()==10)
								break;
						}
					}
					
					result = tmp.toArray(new String[tmp.size()]);
				}
				
				
			}
			
			
		}
		
				
		return result;
      
	}
	private String[] integrated(HashMap<String,info> record,normalize n)
	{
		double cb,ar,ch;
		HashMap<String,Double> tmp = new HashMap<String,Double>();
		for (String r : record.keySet())
		{
			info i = record.get(r);
			if ((i.cb - n.cbMin)==0 || n.cbValue==0)
				cb = 0;
			else
				cb = ((i.cb - n.cbMin) / n.cbValue);
			if ((i.ar - n.arMin)==0 || n.arValue==0)
				ar = 0;
			else
				ar = ((i.ar - n.arMin) / n.arValue);
			if ((i.checkins - n.checkinsMin)==0 || n.checkinsValue==0)
				ch = 0;
			else
				ch = ((i.checkins - n.checkinsMin) / n.checkinsValue);
			
			tmp.put(r, 0.5*cb + 0.1*ar + 0.4*ch);
		}
		
		List<Map.Entry<String, Integer>> rank = sortDouble(tmp);
		String result[] = new String[10];
		int j = 0;
		for (Map.Entry<String, Integer> entry:rank) 
	    {
			result[j++] = entry.getKey();
			if (j==10)
				break;
	    }
		return result;
	}
	private List<Map.Entry<String, Integer>> sortDouble(HashMap<String,Double> a)
	{
		HashMap<String,Integer> tmp = new HashMap<String,Integer>();
		for (String aa : a.keySet())
			tmp.put(aa, (int)(a.get(aa) * 100000));
		List<Map.Entry<String, Integer>> list_Data = new ArrayList<Map.Entry<String, Integer>>(tmp.entrySet());

		Collections.sort(list_Data, new Comparator<Map.Entry<String, Integer>>()
		{
            public int compare(Map.Entry<String, Integer> entry1,
                               Map.Entry<String, Integer> entry2){
                return (entry2.getValue() - entry1.getValue());
            }
        });
		
		return list_Data;
	}
	private class normalize
	{
		double cbMax=0,cbMin=0;
		double arMax=0,arMin=0;
		double checkinsMax=0,checkinsMin=0;		
		
		double cbValue,arValue,checkinsValue;
		
	}
	private class info
	{
		private info(double c,double a,double ch)
		{
			this.cb = c;
			this.ar = a;
			this.checkins =ch;
		}
		double cb,ar,checkins;
	}
	
	private class defaultResult
	{
		double distance;
		int checkins;
	}
	private String askGoogle_all(double px,double py) throws IOException
	{
		HashMap<String,String> county = new HashMap<String,String>();
		List<Map<String, Object>> rs = commonJdbcTemplate.queryForList("SELECT id,name FROM County");
		for (Map<String, Object> i : rs) 
			county.put(i.get("name").toString().replace("臺", "台"), i.get("id").toString());
		
		String html = request("http://maps.google.com/maps/api/geocode/json?latlng="+py+","+px+"&language=zh-TW&sensor=true","UTF-8");
		
		Pattern p = Pattern.compile("\"long_name\" : \".{2}[縣市]\","); 
		Matcher m = p.matcher(html);
		
		String city="";
		if (m.find())
		{
			city = Parser(m.group(),"\"long_name\" : \"","\",",1);
			if (!county.containsKey(city))
				return "all";
			else
				return county.get(city);
		}
		else
		{
			return "all";
		}
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
	
	private String request(String url,String type) throws IOException
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
	
	private String[] FinStBestPOI(RecommendInput json) throws IOException, ParseException
	{
		
		ArrayList<String> result = new ArrayList<String>();
		List<Map<String, Object>> rs;
		if (json.getCountyId().size()==0) //全部縣市隨機Top推薦
		{
			//String city = askGoogle_all(json.getGps().getLng(), json.getGps().getLat());
			int value = 100000;
			do
			{
				//themeId NOT LIKE 'FO%' and 
				
				//SELECT IFNULL(fb_id,UUID()) AS uniq,id FROM recommendation WHERE  checkins >= 0 and type in (2) GROUP BY uniq ORDER by rand() limit 0,10
				rs = analyticsjdbc.queryForList("SELECT IFNULL(fb_id,UUID()) AS uniq,id FROM recommendation WHERE  checkins >= "+value+" and type in ("+json.getReturnType()+") GROUP BY uniq ORDER by rand() limit 0,10");
				for (Map<String, Object> i : rs)
				{
					if (!result.contains(i.get("id").toString()))
						result.add(i.get("id").toString());
					if (result.size()==10)
						break; 
				}
					
				value-=5000;
				if (value < 0)
				{
					break;
				}
			}
			while (result.size()<10);
		}
		else //指定縣市的Top景點推薦
		{
			int value = 100000;
			do
			{
				//themeId NOT LIKE 'FO%' and 
				rs = analyticsjdbc.queryForList("SELECT id,type FROM recommendation WHERE countyId = '"+json.getCountyId().get(0)+"' and checkins >= "+value+" and type in ("+json.getReturnType()+") GROUP BY fb_id ORDER by rand() limit 0,10");
				for (Map<String, Object> i : rs)
				{
					if (i.get("type").toString().equals("2"))
					{
						if (checkExpire(i.get("id").toString()))
							continue;
					}
					if (!result.contains(i.get("id").toString()))
						result.add(i.get("id").toString());
					if (result.size()==10)
						break;
				}
					
				value-=5000;
				if (value < 0)
				{
					break;
				}
			}
			while (result.size()<10);
			
		}
		return result.toArray(new String[result.size()]);
	}
	private boolean checkExpire(String poiId) throws IOException, ParseException
	{
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		Date now = new Date();
		String html = request("http://api.vztaiwan.com/api/Pois/" + poiId, "UTF-8");
		Pattern p = Pattern.compile("\"endTime\":\".+?\"}");
		Matcher m = p.matcher(html);
		
		String str;
		while (m.find())
		{
			str = m.group().replace("\"endTime\":\"", "").replace("\"}", "");
			if (sdf.parse(str).before(now))
				return true;
		}
		
		
		return false;
		
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
					+ "WHERE B.weekday = '"+ti.weekday+"' and ("+hour+"_Oclock = 1 and "+(hour+1)+"_Oclock = 1 and "+(hour+2)+"_Oclock=1) and A.type <> '4' "
					+ "and A.checkins IS NOT NULL and A.Place_Id = B.place_id GROUP BY A.fb_id");	
		}
		else
		{
			if (24-hour==1)
			{
				sqlresult = Query("SELECT A.place_id,checkins,px,py FROM scheduling AS A, OpenTimeArray AS B "
						+ "WHERE B.weekday = '"+ti.weekday+"' and ("+hour+"_Oclock = 1 and 0_Oclock = 1 and 1_Oclock=1) and A.type <> '4' "
						+ "and A.checkins IS NOT NULL and A.Place_Id = B.place_id GROUP BY A.fb_id");
			}
			else if (24-hour==2)
			{
				sqlresult = Query("SELECT A.place_id,checkins,px,py FROM scheduling AS A, OpenTimeArray AS B "
						+ "WHERE B.weekday = '"+ti.weekday+"' and ("+hour+"_Oclock = 1 and "+(hour+1)+"_Oclock = 1 and 0_Oclock=1) and A.type <> '4' "
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
			sqlresult = Query("SELECT A.place_id,A.checkins,A.px,A.py FROM scheduling AS A, OpenTimeArray AS B "
					+ "WHERE B.weekday = '"+ti.weekday+"' "
					+ "and A.checkins IS NOT NULL and A.Place_Id = B.place_id and A.type <> '4' GROUP BY fb_id ORDER BY checkins DESC limit 0,30");
			for (PoiCheckins p : sqlresult)
			{
				limit++;
				if (limit==30)
					limit = p.getCheckins();
			}
			//30個裡面隨機挑選
			sqlresult = Query("SELECT A.place_id,A.checkins,A.px,A.py FROM scheduling AS A, OpenTimeArray AS B "
					+ "WHERE B.weekday = '"+ti.weekday+"' "
					+ "and A.checkins IS NOT NULL and A.Place_Id = B.place_id and A.checkins > 5000 and A.type <> '4' GROUP BY fb_id ORDER BY RAND()");
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
		
		System.out.println("SELECT * FROM scheduling WHERE place_id = '"+result[0]+"' or place_id = '"+result[1]+"' or place_id = '"+result[2]+"' or place_id = '"+result[3]+"' or place_id = '"+result[4]+"'");
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
	
	private List<Map.Entry<String, Integer>> locationsort(HashMap<String,defaultResult> a)
	{
		HashMap<String,Integer> tmp = new HashMap<String,Integer>();
		for (String s : a.keySet())
			tmp.put(s, (int)(a.get(s).distance * 10000));
		List<Map.Entry<String, Integer>> list_Data = new ArrayList<Map.Entry<String, Integer>>(tmp.entrySet());

		Collections.sort(list_Data, new Comparator<Map.Entry<String, Integer>>()
		{
            public int compare(Map.Entry<String, Integer> entry1,
                               Map.Entry<String, Integer> entry2){
                return (entry1.getValue() - entry2.getValue());
            }
        });
		
		int i=0;
		for (Map.Entry<String, Integer> entry:list_Data) 
	    {
			System.out.println(entry.getKey() + "->" + entry.getValue());
			i++;
	    }
		
		System.out.println(i);
		
		return list_Data;
	}
	
	private List<Map.Entry<String, Integer>> sort(HashMap<String,Integer> a)
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
	
	
	private class place_info
	{
		String id;
		String gps;
		int checkins;
		private place_info(String id,String location,int checkin)
		{
			this.id = id;
			this.gps = location;
			this.checkins = checkin;
		}
	}
	private List<place_info> Query2(String q) {

		
		List<place_info> results = jdbcTemplate.query(
				q,
				new RowMapper<place_info>() {
					@Override
					public place_info mapRow(ResultSet rs, int rowNum)
							throws SQLException {
						return new place_info(rs.getString("id"),rs.getString("location2"),rs.getInt("checkins"));
					}
				});
		return results;
		
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