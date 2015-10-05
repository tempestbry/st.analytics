package tw.org.iii.st.analytics.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.sql.DataSourceDefinition;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import tw.org.iii.model.GeoPoint;
import tw.org.iii.model.RecommendInput;
import tw.org.iii.model.SchedulingInput;
import tw.org.iii.model.TourEvent;
import tw.org.iii.st.analytics.spring.Application;

/**
 * Hello~~
 * */

@RestController
@RequestMapping("/Scheduling")
public class Scheduling {
	@Autowired
	@Qualifier("poiNames")
	private HashMap<String,String> poiNames;
	
	@Autowired
	@Qualifier("hualienJdbcTempplate")
	private JdbcTemplate jdbcTemplate;
	
	@Autowired
	@Qualifier("stJdbcTemplate")
	private JdbcTemplate stJdbcTemplate;

	@Autowired
	@Qualifier("analyticsJdbcTemplate")
	private JdbcTemplate analyticsjdbc;
	
	@Autowired
	@Qualifier("STScheduling")
	private STScheduling stScheduling;
	
	private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
	
	
	@RequestMapping("/QuickPlan")
	private @ResponseBody
	List<TourEvent> StartPlan(@RequestBody SchedulingInput json) throws ParseException, ClassNotFoundException, SQLException, IOException {

		List<TourEvent> finalResult = new ArrayList<TourEvent>();

	    long diff = json.getEndTime().getTime()-json.getStartTime().getTime();
	    long diffHours = diff / (60 * 60 * 1000);		


		if (json.getCityList().size()==1 && json.getCityList().get(0).contains("TW18"))
		{
			System.out.println("hualien");
			//行程規劃結果
			ArrayList<TourEvent> PlanResult = new ArrayList<TourEvent>();
			int lastTime;
			
			HashMap<String,String> mapping = startMapping(); //建立新舊preference比照表
			ArrayList<String> repeat = new ArrayList<String>();
			String city = getCity(json.getCityList()); //取得city
			String preference = getPreference(json.getPreferenceList(),mapping); //取得preference
			String weekday = getWeekday(json.getStartTime()); //知道當天星期幾
			
			int freetime = FreeTime(json.getStartTime(), json.getEndTime()); //取得旅程總時間
			int index=0;
			
			
			/**test*/
			System.out.println(json.getStartTime());
			System.out.println(json.getEndTime());
//			Calendar cal = Calendar.getInstance(); // creates calendar
//			cal.setTime(si.getStartTime()); // sets calendar time/date
//			//cal.add(Calendar.HOUR_OF_DAY, 8); // adds one hour
//			si.setStartTime(cal.getTime());
//			System.out.println(si.getStartTime());
//			
//			System.out.println(si.getEndTime());
//			cal = Calendar.getInstance(); // creates calendar
//			cal.setTime(si.getEndTime()); // sets calendar time/date
//			//cal.add(Calendar.HOUR_OF_DAY, 8); // adds one hour
//			si.setEndTime(cal.getTime());
//			System.out.println(si.getEndTime());

			
			tourInfo ti = new tourInfo(index,freetime,json.getStartTime(),json.getEndTime(),json.getGps().getLng(),json.getGps().getLat());
			ti.weekday = weekday;
			ti.preference = preference;
			ti.city = city;
			ti.startPOI = json.getStartPoiId();
			ti.endPOI = json.getEndPoiId();
			
			try
			{
				ti.flag = askGoogle(ti.px,ti.py);
			}
			catch (IOException e)
			{
			}
			
			if (json.getEndPoiId() != null || !"".equals(json.getEndPoiId()))
				ti.repeat.add(json.getEndPoiId());
			//一般行程規劃(當日)
			if (json.getTourType() == null || "".equals(json.getTourType()) || !json.getTourType().contains("play-")) {
//				SimpleDateFormat sdFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
//				TourEvent test = new TourEvent();
//				test.setPoiId("C1_315081200H_000101");
//				test.setStartTime(sdFormat.parse("2015/07/20 10:43:28"));
//				test.setEndTime(sdFormat.parse("2015/07/20 15:43:28"));
//				PlanResult.add(test);
//				
				
				PlanResult.addAll(findTop(ti));
				ti.repeat.add(PlanResult.get(0).getPoiId()); //將選過的景點加入repeat清單
				ti.index = PlanResult.size(); //index推進
				
				//freetime = FreeTime(PlanResult.get(index - 1).getEndTime(),si.getEndTime() );
				PlanResult = getOtherPOI(PlanResult,ti);

				for (TourEvent t : PlanResult) {
					List<String> name = placeName("SELECT name FROM Detail WHERE poiId = '"+t.getPoiId()+"'");
					System.out.println(name.get(0) + "," + t.getPoiId() + "," + t.getStartTime() + ","
							+ t.getEndTime());
				}

			} else { //任務模式
				PlanResult.addAll(getMission1(ti,json.getTourType()));
				for (TourEvent r : PlanResult)
					ti.repeat.add(r.getPoiId()); //將選過的景點加入repeat清單
				ti.index = PlanResult.size(); //index推進
				
				
				//freetime = FreeTime(PlanResult.get(2).getEndTime(),si.getEndTime());
				PlanResult = getOtherPOI(PlanResult,ti);
				for (TourEvent t : PlanResult) {
					System.out.println(t.getPoiId() + "," + t.getStartTime() + ","
							+ t.getEndTime());
				}

			}

			finalResult = new ArrayList<TourEvent>();
			finalResult.addAll(PlanResult);
			
			return finalResult;
		}
		else if (json.getCityList().size()==1 && (diffHours) < 11)
		{
			finalResult = OneDayScheduling(json);
			return finalResult;
		}
		else
		{
//			
			System.out.println("multi day");
			finalResult = stScheduling.scheduling(json);
			return finalResult;
			//return null;
		}
		
		
	}
	private List<TourEvent> OneDayScheduling(SchedulingInput json) throws ParseException, IOException
	{
		List<TourEvent> tourResult = new ArrayList<TourEvent>();
		Date freeTime = json.getStartTime();
		ArrayList<String> repeat = new ArrayList<String>();
		//確認縣市
		if (json.getCityList().get(0).equals("all"))
		{
			if (json.getMustPoiList().size()==0) //沒有必去景點
			{
				if (json.getGps()==null)
				{
					List<Map<String, Object>> result = analyticsjdbc.queryForList("SELECT DISTINCT county FROM st_scheduling order by rand()");
					List<String> c = new ArrayList<String>();
					c.add(result.get(0).get("county").toString());
					json.setCityList(c);
				}
				else
				{
					List<String> c = new ArrayList<String>();
					String cc = askGoogle_all(json.getGps().getLng(), json.getGps().getLat());
					if (cc.equals("all"))
					{
						List<Map<String, Object>> result = analyticsjdbc.queryForList("SELECT DISTINCT county FROM st_scheduling order by rand()");
						c.add(result.get(0).get("county").toString());
						json.setCityList(c);
					}
					else
					{
						c.add(cc);
						json.setCityList(c);
					}

				}
			}
			else //有必去景點
			{
				List<String> must = json.getMustPoiList();
				if (must.size()>1) //超過一個must poi
				{
					if (distance(must)) //必去景點之間是否差超過100公里
					{
						String query = "";
						for (int i=0;i<must.size()-1;i++)
							query+="'" + must.get(i) + "',";
						query += "'" + must.get(must.size()-1) + "'";
						List<Map<String, Object>> result = analyticsjdbc.queryForList("SELECT * FROM st_scheduling WHERE poiId in ("+query+")");
						Date start = json.getStartTime();
						for (int i=0;i<result.size();i++)
						{
							TourEvent t = new TourEvent();
							t.setPoiId(result.get(i).get("poiId").toString());
							t.setStartTime(start);
							t.setEndTime(addTime(start,(int)result.get(i).get("stay_time")));
							try
							{
								double dis = Euclid_Distance(result.get(i).get("location").toString(),result.get(i).get("location").toString());
								start = addTime(t.getEndTime(),(dis/0.6));
							}
							catch (Exception e)
							{
								e.printStackTrace();
							}
							tourResult.add(t);
						}
						
						return tourResult;
					}
					else
					{
						List<String> c = new ArrayList<String>();
						List<Map<String, Object>> result = analyticsjdbc.queryForList("SELECT county FROM st_scheduling WHERE poiId ='"+must.get(0)+"'");
						c.add(result.get(0).get("county").toString());
						json.setCityList(c);
					}
				}
			}
			
		}
		
		
		HashMap<String,String> mapping = startMapping();
		String pre = getPreference(json.getPreferenceList(),mapping);
		int index=0;
		if (json.getMustPoiList().size()==0) //沒有必去景點
		{
			if (json.getGps()==null)
				tourResult.add(index++,FindTop(json.getCityList().get(0),pre,json.getStartTime(),json.getLooseType()));
			else
				tourResult.add(index++,findTopNear(json,pre,json.getLooseType()));
			repeat.add(tourResult.get(index-1).getPoiId());
			freeTime = tourResult.get(index-1).getEndTime();
			System.out.println(freeTime);
			while (FreeTime(freeTime,json.getEndTime()) >= 1)
			{
				tourResult.add(otherPOI(tourResult.get(index-1),pre,json.getLooseType(),repeat,false));
				index++;
				repeat.add(tourResult.get(index-1).getPoiId());
				freeTime = tourResult.get(index-1).getEndTime();
				System.out.println(freeTime);
			}
		}
		else
		{
			
		}
		
		
		return tourResult;

	}
	private boolean distance(List<String> poi)
	{
		List<Map<String, Object>> result;
		
		for (int i=0;i<poi.size();i++)
		{
			for (int j=i+1;j<poi.size();j++)
			{
				result = analyticsjdbc.queryForList("SELECT distance FROM euclid_distance_0826 WHERE id = '"+poi.get(i)+"' and arrival_id = '"+poi.get(j)+"'");
				if ((int)result.get(0).get("distance")>=100)
					return true;
			}
		}
		return false;
	}
	private String askGoogle_all(double px,double py) throws IOException
	{
		try
		{
			HashMap<String,String> county = new HashMap<String,String>();
			List<Map<String, Object>> rs = stJdbcTemplate.queryForList("SELECT id,name FROM County");
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
		catch (Exception e)
		{
			return "all";
		}
		
	}
	private TourEvent findTopNear(SchedulingInput json,String pre,int type) throws ParseException
	{
		List<Map<String, Object>> result;
		TourEvent poi = new TourEvent();
		int value = 100000;
		double time;
		boolean flag = false;
		
		double stay;
		do
		{
			result = analyticsjdbc.queryForList("SELECT poiId,stay_time,location FROM st_scheduling WHERE county = '"+json.getCityList().get(0)+"' and preference in ("+pre+") and checkins >= "+value+" order by rand()");
			if (result.size()>0)
			{
				for (Map<String, Object> r : result)
				{
					String[] location = r.get("location").toString().split("\\(|\\)| ");
					double latitude = Double.parseDouble(location[1]);
					double longitude = Double.parseDouble(location[2]);
					time = (int)(Distance(latitude,longitude,json.getGps().getLat(),json.getGps().getLng()) / 0.6);
					if (time<=10)
					{
						poi.setPoiId(r.get("poiId").toString());
						poi.setStartTime(json.getStartTime());
						try{
							stay = Double.parseDouble(r.get("stay_time").toString()) + (type*30);
							
						}catch (Exception e){e.printStackTrace();stay = 90 + (type*30);}
						poi.setEndTime(addTime(json.getStartTime(),stay));
						flag = true;
						break;
					}
				}
				if (flag)
					break;
			}
			value-=10000;
		}while (poi.getPoiId()==null && value>=0);
		
		if (flag)
			return poi;
		else
			return FindTop(json.getCityList().get(0),pre,json.getStartTime(),json.getLooseType());
		
	}
	
	
	private TourEvent otherPOI(TourEvent before,String pre,int type,ArrayList<String> repeat,boolean eatTime) throws ParseException
	{
		int value = 50000,distance_value=3;
		List<Map<String, Object>> result;
		TourEvent poi = new TourEvent();
		double stay;
		do
		{
			result = analyticsjdbc.queryForList("SELECT arrival_id,time,stay_time FROM euclid_distance_0826 WHERE id = '"+before.getPoiId()+"' and checkins >= "+value+" and distance<="+distance_value+" and preference in ("+pre+") ORDER BY rand()");
			for (Map<String, Object> r : result)
			{
				if (checkRule(repeat,r.get("arrival_id").toString()))
				{
					poi.setPoiId(r.get("arrival_id").toString());
					try{
						if (!eatTime)
							poi.setStartTime(addTime(before.getEndTime(),Double.parseDouble(r.get("time").toString())));
						else
							poi.setStartTime(addTime(addTime(before.getEndTime(),120),Double.parseDouble(r.get("time").toString())));
						try
						{
							stay = Double.parseDouble(r.get("stay_time").toString()) + (type * 30);
						}
						catch (Exception e)
						{
							stay =90 + (type * 30);
						}
						
							if (stay<=30)
								stay+=30;
						poi.setEndTime(addTime(poi.getStartTime(),stay));
					}catch (Exception e){e.printStackTrace();}
					break;
				}
				if (poi.getPoiId()!=null)
					break;
			}
			distance_value++;
			value-=10000;
		}while (("".equals(poi.getPoiId()) || poi.getPoiId()==null) && value>=0);
		
		if ("".equals(poi.getPoiId()) || poi.getPoiId()==null) //上面條件都沒符合的
		{
			result = analyticsjdbc.queryForList("SELECT arrival_id,time,stay_time FROM euclid_distance_0826 WHERE id = '"+before.getPoiId()+"' ORDER BY distance limit 0,20");	
			for (Map<String, Object> r : result)
			{
				if (!repeat.contains(r.get("arrival_id").toString()))
				{
					poi.setPoiId(r.get("arrival_id").toString());
					try{
						poi.setStartTime(addTime(before.getEndTime(),Double.parseDouble(r.get("time").toString())));
						
						try
						{
							stay = Double.parseDouble(result.get(0).get("stay_time").toString()) + (type * 30);
						}
						catch (Exception e)
						{
							stay = 90 + (type * 30);
						}				
						if (stay==30)
							stay+=30;
						poi.setEndTime(addTime(poi.getStartTime(),stay));
					}catch (Exception e){e.printStackTrace();}
					break;
				}
				if (poi.getPoiId()!=null)
					break;
			}
		}
		if (poi.getPoiId()==null) //莫名其妙的掛了
		{
//			result = analyticsjdbc.queryForList("SELECT county FROM st_scheduling WHERE poiId = '"+before.getPoiId()+"'");	
			result = analyticsjdbc.queryForList("SELECT poiId,location,stay_time FROM st_scheduling WHERE county = '"+result.get(0).get("county").toString()+"' ORDER BY rand()");
			for (Map<String, Object> r : result)
			{
				if (!repeat.contains(r.get("poiId").toString()))
				{
					List<Map<String, Object>> result1 = analyticsjdbc.queryForList("SELECT poiId,location,stay_time FROM st_scheduling WHERE poiId = '"+before.getPoiId()+"'");
					String[] location = r.get("location").toString().split("\\(|\\)| ");
					double latitude = Double.parseDouble(location[1]);
					double longitude = Double.parseDouble(location[2]);
					
					location = result1.get(0).get("location").toString().split("\\(|\\)| ");
					double latitude1 = Double.parseDouble(location[1]);
					double longitude1 = Double.parseDouble(location[2]);
				
					double time;
					try
					{
						time = (int)(Distance(latitude,longitude,latitude1,longitude1) / 0.75);
					}
					catch (Exception e)
					{
						time = 30;
					}
					poi.setPoiId(r.get("poiId").toString());
					poi.setStartTime(addTime(before.getEndTime(),time));
					poi.setEndTime(addTime(poi.getStartTime(),90));
				}
			}
		}
		
		return poi;
	}
	private String getPre(List<String> pre)
	{
		String str="";
		for (int i=0;i<pre.size()-1;i++)
		{
			str+="'" + pre.get(i) + "',";
		}
		str+="'" + pre.get(pre.size()-1) + "'";
		
		return str;
	}
	
	
	//避免遇到名稱一樣或相似的POI
	private boolean checkRule(ArrayList<String> repeat,String pid)
	{
		if (repeat.contains(pid) || repeat.contains(poiNames.get(pid)) || poiNames.get(pid).contains("夜市") || poiNames.get(pid).contains("會館"))
			return false;
		for (String r : repeat)
		{
			if (sim(r,poiNames.get(pid))>=0.4)
				return false;
		}
		return true;
	}
	private double sim(String a,String b)
	{
		ArrayList<String> aa = new ArrayList<String>(); //字串a的變化組合
		ArrayList<String> bb = new ArrayList<String>(); //字串b的變化組合
		aa.add(a);
		bb.add(b);
		Pattern pattern = Pattern.compile("[\\(（].*[\\)）]");
		//取出括號內的東西
		Matcher m = pattern.matcher(a);
		if (m.find())
			aa.add(m.group().replaceAll("[\\(（\\)）]", "")); //存入字串a的括號內內容
		m = pattern.matcher(b);
		if (m.find())
			bb.add(m.group().replaceAll("[\\(（\\)）]", ""));
		
		if (!aa.contains(a.replaceAll("[\\(（].*[\\)）]", ""))) //存入括號以外的內容
			aa.add(a.replaceAll("[\\(（].*[\\)）]", ""));
		if (!bb.contains(b.replaceAll("[\\(（].*[\\)）]", ""))) 
			bb.add(b.replaceAll("[\\(（].*[\\)）]", ""));
		
		ArrayList<String> itera,iterb;
		double count=0,similar,max=0,uni;
		
		
		for (String A : aa)
		{
			itera = word(A);
			for (String B : bb)
			{
				iterb = word(B);
				uni = union(A,B);
				count = intersection(itera,iterb);
				similar = count / uni;			
				if (max < similar)
					max = similar;
			}
		}

		return max;
		
	}
	private ArrayList<String> word(String a)
	{
		String c;
		ArrayList<String> tmp = new ArrayList<String>();
		for (int i=0;i<a.length();i++)
		{
			c = a.charAt(i)+"";
			tmp.add(c);
		}
			
		return tmp;
	}
	private double intersection(ArrayList<String> a,ArrayList<String> b) //計算交集數
	{
		double count = 0;
		for (String aa : a) //計算交集數
		{
			if (b.contains(aa))
				count++;
		}
		
		return count;
		
	}
	private double union(String a,String b) //計算聯集字數
	{
		ArrayList<String> tmp = new ArrayList<String>();
		String c;
		for (int i=0;i<a.length();i++)
		{
			c = a.charAt(i)+"";
			if (!tmp.contains(c)) //計算聯集數
				tmp.add(c);
		}
		for (int i=0;i<b.length();i++)
		{
			c = b.charAt(i)+"";
			if (!tmp.contains(c))
				tmp.add(c);
		}
		
		return tmp.size();
		
		
	}
	private TourEvent FindTop(String city,String pre,Date startTime,int type) throws ParseException
	{
		
		int value = 100000;
		List<Map<String, Object>> result;
		TourEvent poi = new TourEvent();
		double stay;
		do
		{
			result = analyticsjdbc.queryForList("SELECT poiId,stay_time FROM st_scheduling WHERE county = '"+city+"' and preference in ("+pre+") and checkins >= "+value+" order by rand() limit 0,1");
			if (result.size()>0)
			{
				poi.setPoiId(result.get(0).get("poiId").toString());
				poi.setStartTime(startTime);
				try{
					stay = Double.parseDouble(result.get(0).get("stay_time").toString()) + (type*30);
					
				}catch (Exception e){e.printStackTrace(); 	stay = 90 + (type*30);}
				
				poi.setEndTime(addTime(startTime,stay));
				break;
			}
				
	
			value-=10000;
		}while (poi.getPoiId()==null && value>=0);
	
		if (poi.getPoiId()==null)
		{
			result = analyticsjdbc.queryForList("SELECT poiId,stay_time FROM st_scheduling WHERE county = '"+city+"' order by rand() limit 0,1");
			if (result.size()>0)
			{
				poi.setPoiId(result.get(0).get("poiId").toString());
				poi.setStartTime(startTime);
				try{
					stay = Double.parseDouble(result.get(0).get("stay_time").toString()) + (type*30);
					
				}catch (Exception e){e.printStackTrace(); 	stay = 90 + (type*30);}
				poi.setEndTime(addTime(startTime,stay));
				
			}
		}
		
		return poi;
	}
	private double Euclid_Distance(String start,String end)
	{
		double wd1,jd1,wd2,jd2;
		String spl[] = start.split(" ");
		wd1 = Double.parseDouble(spl[0].split("POINT\\(")[1]);
		jd1 = Double.parseDouble(spl[0].split("\\)")[0]);
		
		spl = end.split(" ");
		wd2 = Double.parseDouble(spl[0].split("POINT\\(")[1]);
		jd2 = Double.parseDouble(spl[0].split("\\)")[0]);
		
		
		double x,y,out;
		double PI=3.14159265;
		double R=6.371229*1e6;
	
		x=(jd2-jd1)*PI*R*Math.cos( ((wd1+wd2)/2) *PI/180)/180;
		y=(wd2-wd1)*PI*R/180;
		out=Math.hypot(x,y);
		return out/1000;
	}
	
	
	
	
	
	
	









	
	
	
	
	
	private String getPreference(List<String> p,HashMap<String,String> mapping)
	{
		String preference="";
		// 將preference寫入條件
		for (int i = 0; i < p.size() - 1; i++)
			preference+="'" + (p.get(i).contains("TH") ? mapping.get(p.get(i)) : p.get(i)) + "',";
		preference+="'" + (p.get(p.size() - 1).contains("TH") ?  mapping.get(p.get(p.size() - 1)) : p.get(p.size() - 1)) + "'";
//			preference += "A.preference = '" + (p.get(i).contains("TH") ? mapping.get(p.get(i)) : p.get(i)) + "' or ";
//		preference += "A.preference = '" + (p.get(p.size() - 1).contains("TH") ?  mapping.get(p.get(p.size() - 1)) : p.get(p.size() - 1)) + "'";
		
		
		return preference;
		
	}
	private String getCity(List<String> c)
	{
		String city="";
		//將city寫入條件
		for (int i = 0; i < c.size() - 1; i++)
			city += "A.county = '"+c.get(i)+"' or ";
		city += "A.county = '"+c.get(c.size()-1)+"'";
		return city;
	}
	private HashMap<String,String> startMapping()
	{
		HashMap<String,String> mapping = new HashMap<String,String>();
		for (int i=1;i<9;i++)
			mapping.put("TH" + (i+9), "PF" + i);
		
		return mapping;
	}
	
	private ArrayList<TourEvent> findTop(tourInfo ti) throws ParseException,SQLException 
	{
		
		ArrayList<TourEvent> result = new ArrayList<TourEvent>();
		TourEvent te;

		Calendar cal;
		Date date = ti.startTime;
		cal = Calendar.getInstance();
		cal.setTime(date);		

		// 找出Top (打卡數>30000, 星期幾跟時段要符合, 偏好, 縣市, 經緯度不為0 or null取40個景點候選做random)
		List<Map<String, Object>> rs = jdbcTemplate.queryForList("SELECT A.place_id,px,py,stay_time FROM scheduling AS A,OpenTimeArray AS B WHERE A.place_id = B.place_id and A.checkins > 10000 and B.weekday = '"
				+ ti.weekday
				+ "' and "
				+ date.getHours()
				+ "_Oclock = 1 and ("
				+ ti.preference + ") and ("
				+ ""+ti.city+") and (px IS not null and px <> 0) and (py IS not null and py <>0) GROUP BY fb_id ORDER BY rand() limit 0,40");

		double dis = 0;
		for (Map<String, Object> i : rs) {
			//是否有設定出發POI
			if ("".equals(ti.startPOI) || ti.startPOI == null) {
				dis = Distance((double)i.get("py"), (double)i.get("px"), ti.py, ti.px);
			} else {
				List<GPS> gp = QGPS("SELECT px,py FROM scheduling WHERE place_id = '"+ ti.startPOI + "'");
				if (gp.get(0).getX() == 0 || gp.get(0).getY() == 0) //POI沒經緯度則以User所在點做起點
					dis = Distance((double)i.get("py"), (double)i.get("px"), ti.py, ti.px);
				else
					dis = Distance(gp.get(0).getY(), gp.get(0).getX(),ti.py, ti.px);
			}

			int time = (int) (dis / 0.7);
			
			if (ti.flag) //在花東以外
			{
				te = new TourEvent();
				te.setPoiId(i.get("place_id").toString());
				te.setStartTime(addTime(cal.getTime(),time));
				te.setEndTime(addTime(te.getStartTime(), (int)i.get("stay_Time")));
				result.add(te);
				break;
			}
			else //在花東
			{
				if (time < 45 && time < (ti.freeTime * 60)) { //行車時間45分鐘內可到且空閒時間也夠
					te = new TourEvent();
					te.setPoiId(i.get("place_id").toString());
					te.setStartTime(addTime(cal.getTime(),time));
					te.setEndTime(addTime(te.getStartTime(), (int)i.get("stay_Time")));
					result.add(te);
					break;
				}
			}
			
		}

		//當上面無結果時，則解放行車時間、打卡數的條件
		if (result.size()==0)
		{
			rs = jdbcTemplate.queryForList("SELECT A.place_id,px,py,stay_time FROM scheduling AS A,OpenTimeArray AS B WHERE A.place_id = B.place_id and B.weekday = '"
						+ ti.weekday
						+ "' and "
						+ date.getHours()
						+ "_Oclock = 1 and ("
						+ ti.preference + ") and ("
						+ ""+ti.city+") and (px IS not null and px <> 0) and (py IS not null and py <>0) GROUP BY fb_id ORDER BY rand() limit 0,40");
			HashMap<String,Integer> tmp = new HashMap<String,Integer>();
			HashMap<String,TourEvent> _tmp = new HashMap<String,TourEvent>();
			for (Map<String, Object> i : rs)
			{
				if ("".equals(ti.startPOI) || ti.startPOI == null) {
					dis = Distance((double)i.get("py"), (double)i.get("px"), ti.py, ti.px);
				} else {
					List<GPS> gp = QGPS("SELECT px,py FROM scheduling WHERE place_id = '"+ ti.startPOI + "'");
					if (gp.get(0).getX() == 0 || gp.get(0).getY() == 0) //POI沒經緯度則以User所在點做起點
						dis = Distance((double)i.get("py"), (double)i.get("px"), ti.py, ti.px);
					else
						dis = Distance(gp.get(0).getY(), gp.get(0).getX(),ti.py, ti.px);
				}


				int time = (int) (dis / 0.7);
				
				te = new TourEvent();
				te.setPoiId(i.get("place_id").toString());
				te.setStartTime(addTime(cal.getTime(),time));
				te.setEndTime(addTime(te.getStartTime(), (int)i.get("stay_Time")));
				_tmp.put(i.get("place_id").toString(), te);
				tmp.put(i.get("place_id").toString(), time);
			}
			//找最近可到的景點作為起始點
			List<Map.Entry<String, Integer>> rank = sort(tmp);
			result.add(_tmp.get(rank.get(0).getKey()));
		}
		return result;
	}
	private boolean askGoogle(double px,double py) throws IOException
	{
		String county[] = {"台東","花蓮"};
		
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
	
	
	private ArrayList<TourEvent> getMission1(tourInfo ti,String playType) throws ParseException, SQLException
	{
		String type = playType.split("play-")[1];
		
		
		GeoPoint gpt = new GeoPoint();
		ArrayList<TourEvent> result = new ArrayList<TourEvent>();
		TourEvent te = null;
		
		//起始時間
		Calendar cal;
		Date date = ti.startTime;
		cal = Calendar.getInstance();
		cal.setTime(date);
		
		//取得任務代碼
		List<Map<String, Object>> list = stJdbcTemplate.queryForList("SELECT DISTINCT sceneId FROM Scene AS A,ScenePoi AS B WHERE A.Id = B.sceneId and A.playId = "+type+" ORDER BY B.sceneId");
		
		String[] location;
		
		boolean flag=true;
		for(Map map : list)
		{
			System.out.println(map.get("sceneId"));
			int item=0;
			while (true)
			{
				List<Map<String, Object>> rs = stJdbcTemplate.queryForList("SELECT A.poiId,ASTEXT(B.location) AS AAA,B.name FROM ScenePoi AS A,PoiFinalView AS B WHERE sceneId = "+map.get("sceneId")+" and A.poiId = B.id ORDER BY rand() LIMIT 0,1");
				te = new TourEvent();
				te.setPoiId(rs.get(item).get("poiId").toString());
				//System.out.println(rs.get(0).get("name"));
				//取得經緯度
				location = rs.get(0).get("AAA").toString().split("\\(|\\)| ");
				double lat = 0;
				double lng = 0;
				lat = Double.parseDouble(location[1]);
				lng = Double.parseDouble(location[2]);
				gpt.setLat(lat);
				gpt.setLng(lng);
				
				if (flag)
				{
					double dis = Distance(gpt.getLat(), gpt.getLng(), ti.py, ti.px);
					int time = (int) (dis / 0.7);
					te.setStartTime(addTime(sdf.parse(sdf.format(cal.getTime())), time));
					te.setEndTime(addTime(te.getStartTime(), 30));
					flag = false;	
				}
				else
				{
					te.setStartTime(addTime(
							result.get(ti.index - 1).getEndTime(),
							BetweenTime(result.get(ti.index - 1).getPoiId(), rs.get(0).get("poiId").toString())));
					te.setEndTime(addTime(te.getStartTime(), 30));
				}
				try
				{
					result.add(ti.index++, te);
					break;
				}
				catch (Exception e)
				{
					e.printStackTrace();
					item++;
					continue;
				}
				
			}
			
		}
		
		return result;
	}

	private ArrayList<TourEvent> getOtherPOI(ArrayList<TourEvent> PlanResult,tourInfo ti) throws SQLException, ParseException {
		//String start = PlanResult.get(index - 1).getPoiId();
		
		TourEvent te;
		int lastTime = ti.freeTime * 60;

		while (true) {
			//取得上一個景點後找出在有限時間內還可以去的景點
			te = costTime(PlanResult, ti, lastTime, 10000);
			if ("".equals(te.getPoiId()) || te.getPoiId() == null) //如果沒結果
			{
				if (lastTime > 30) //剩餘時間還大於半小時
				{
					te = costTime(PlanResult, ti, lastTime, 5000);
					if ("".equals(te.getPoiId()) || te.getPoiId() == null) //還是找不到符合結果
					{

						if (!"".equals(ti.endPOI) && ti.endPOI != null) 
						{
							te.setPoiId(ti.endPOI);
							te.setStartTime(addTime(PlanResult.get(ti.index - 1).getEndTime(),toDestination(PlanResult.get(ti.index - 1).getPoiId(), ti.endPOI) == 100000 ? 30
												: toDestination(
													PlanResult.get(ti.index - 1).getPoiId(), ti.endPOI)));
							te.setEndTime(addTime(PlanResult.get(0).getStartTime(), ti.freeTime*60));
							PlanResult.add(ti.index++, te);
							break;
						}
					} else {
						PlanResult.add(ti.index++, te);
						ti.repeat.add(te.getPoiId());
						lastTime = getLastTime(PlanResult,ti.freeTime * 60);
						//start = te.getPoiId();
						continue;
					}
				}
				if (!ti.repeat.contains(te.getPoiId())) 
				{
					if (!"".equals(ti.endPOI) && ti.endPOI != null) 
					{
						te.setPoiId(ti.endPOI);
						te.setStartTime(addTime(PlanResult.get(ti.index - 1).getEndTime(),toDestination(PlanResult.get(ti.index - 1).getPoiId(),ti.endPOI) == 100000 ? 30
										: toDestination(PlanResult.get(ti.index - 1).getPoiId(), ti.endPOI)));
						te.setEndTime(ti.endTime);
						PlanResult.add(ti.index++, te);
					}
				}
				break;
			} else {
				PlanResult.add(ti.index,te);
				ti.repeat.add(te.getPoiId());
				ti.index++;
				lastTime = getLastTime(PlanResult,ti.freeTime * 60);
			}

		}
		
		return PlanResult;
	}
	private int getLastTime(ArrayList<TourEvent> PlanResult,int freetime)
	{
		int time = FreeTime_minute(PlanResult.get(0).getStartTime(),PlanResult.get(PlanResult.size()-1).getEndTime());
		
		return freetime - time;
	}
	private TourEvent costTime(ArrayList<TourEvent> PlanResult,tourInfo ti,int lastTime,int checkin) throws SQLException, ParseException {
		int destinationTime;
		TourEvent te = new TourEvent();

		List<Map<String, Object>> rs = jdbcTemplate.queryForList("SELECT A.arrival_id, A.time, (A.time + A.stay_time) AS totaltime FROM "
				+ "OpenTimeArray AS B, euclid_distance AS A WHERE A.Id = '"
				+ PlanResult.get(ti.index-1).getPoiId()
				+ "' AND ("
				+ ti.preference
				+ ") AND "
				+ "("+ti.city+") AND "
				+ "B.Place_Id = A.arrival_id AND (A.time + A.stay_time) < "
				+ lastTime
				+ " AND A.checkins > "
				+ checkin
				+ " AND B.weekday = '"
				+ ti.weekday
				+ "' "
				+ "AND B."
				+ PlanResult.get(ti.index-1).getEndTime().getHours()
				+ "_Oclock = 1 ORDER BY RAND() DESC");
		
		
		
		for (Map<String, Object> i : rs) // 找出目前所在景點可考慮去的鄰近景點候選清單
		{
			//沒有目的地
			if ("".equals(ti.endPOI) || ti.endPOI == null) {
				if (!ti.repeat.contains(i.get("arrival_id").toString()) && ((double)i.get("totaltime")) < lastTime) // 達成任務就離開
				{
					te.setPoiId(i.get("arrival_id").toString());
					te.setStartTime(addTime(PlanResult.get(ti.index-1).getEndTime(), (double)i.get("time")));
					te.setEndTime(addTime(PlanResult.get(ti.index-1).getEndTime(), (double)i.get("totaltime")));
					break;
				}
			} else {
				destinationTime = toDestination(i.get("arrival_id").toString(), ti.endPOI);
				if (!ti.repeat.contains(i.get("arrival_id").toString()) && (destinationTime + (double)i.get("totaltime")) < lastTime) // 達成任務就離開
				{
					te.setPoiId(i.get("arrival_id").toString());
					te.setStartTime(addTime(PlanResult.get(ti.index-1).getEndTime(), (double)i.get("time")));
					te.setEndTime(addTime(PlanResult.get(ti.index-1).getEndTime(), (double)i.get("totaltime")));
					break;
				}
			}

		}

		return te;
	}

	private int toDestination(String now, String destination) throws SQLException {
		
		List<Map<String, Object>> rs = jdbcTemplate.queryForList("SELECT (time+stay_time) AS totalTime FROM euclid_distance AS A WHERE id = '"
				+ now + "' and arrival_id = '" + destination + "'");
		
		if (rs.size() > 0)
		{
			int result = (int)(double)rs.get(0).get("totalTime");
			return result;
		}
		else {
			return 100000;
		}
	}

	

	
	
	private int BetweenTime(String id, String arrival) throws SQLException {
		List<Integer> rs = QTime("SELECT time FROM googledirection_hybrid WHERE id = '"
				+ id + "' and arrival_id = '" + arrival + "'");
		if (rs.size() == 0) {
			List<Integer> rs1 = QTime("SELECT time FROM euclid_distance WHERE id = '"
					+ id + "' and arrival_id = '" + arrival + "'");
			try
			{
				return rs1.get(0);
			}
			catch (Exception e)
			{
				return 0;
			}
			

		} else {
			return rs.get(0);
		}
	}

	private Date addTime(Date d, double minute) throws ParseException {
		Calendar cal;
		cal = Calendar.getInstance();
		cal.setTime(d);
		cal.add(Calendar.MINUTE, (int)minute);
		return sdf.parse(sdf.format(cal.getTime()));
	}

	private double Distance(double wd1, double jd1, double wd2, double jd2) // 緯度,
																			// 經度這樣放
	{
		double x, y, out;
		double PI = 3.14159265;
		double R = 6.371229 * 1e6;

		x = (jd2 - jd1) * PI * R * Math.cos(((wd1 + wd2) / 2) * PI / 180) / 180;
		y = (wd2 - wd1) * PI * R / 180;
		out = Math.hypot(x, y);
		return out / 1000;
	}
	private int FreeTime_minute(Date start, Date end) {
		Date d1 = start;
		Date d2 = end;
		long diff = d2.getTime() - d1.getTime();
		long diffHours = (diff / (60 * 1000)) + (diff % (60 * 1000));
		return (int) diffHours;
	}
	
	private int FreeTime(Date start, Date end) {
		Date d1 = start;
		Date d2 = end;
		long diff = d2.getTime() - d1.getTime();
		long diffHours = diff / (60 * 60 * 1000) % 24;
		return (int) diffHours;
	}

	private static List<Map.Entry<String, Integer>> sort(HashMap<String,Integer> a)
	{
		List<Map.Entry<String, Integer>> list_Data = new ArrayList<Map.Entry<String, Integer>>(a.entrySet());

		Collections.sort(list_Data, new Comparator<Map.Entry<String, Integer>>()
		{
            public int compare(Map.Entry<String, Integer> entry1,
                               Map.Entry<String, Integer> entry2){
                return (entry1.getValue() - entry2.getValue());
            }
        });
		
		return list_Data;
	}
	private String getWeekday(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
		switch (dayOfWeek) {
		case Calendar.SUNDAY:
			return "Op_Sunday";
		case Calendar.MONDAY:
			return "Op_Monday";
		case Calendar.TUESDAY:
			return "Op_Tuesday";
		case Calendar.WEDNESDAY:
			return "Op_Wednesday";
		case Calendar.THURSDAY:
			return "Op_Thursday";
		case Calendar.FRIDAY:
			return "Op_Friday";
		case Calendar.SATURDAY:
			return "Op_Saturday";
		default:
			return "";
		}
	}

	/**SQL Query*/

	private List<GPS> QGPS(String q) {
		List<GPS> results = jdbcTemplate.query(q, new RowMapper<GPS>() {
			@Override
			public GPS mapRow(ResultSet rs, int rowNum) throws SQLException {
				return new GPS(rs.getDouble("px"), rs.getDouble("py"));
			}
		});
		return results;

	}

	private List<String> placeName(String q) {
		List<String> results = stJdbcTemplate.query(q, new RowMapper<String>() {
			@Override
			public String mapRow(ResultSet rs, int rowNum) throws SQLException {
				return rs.getString("name");
			}
		});
		return results;

	}
	
	private List<String> QRegion(String q) {
		List<String> results = jdbcTemplate.query(q, new RowMapper<String>() {
			@Override
			public String mapRow(ResultSet rs, int rowNum) throws SQLException {
				return rs.getString("region");
			}
		});
		return results;

	}

	private List<Integer> QTime(String q) {
		List<Integer> results = jdbcTemplate.query(q, new RowMapper<Integer>() {
			@Override
			public Integer mapRow(ResultSet rs, int rowNum) throws SQLException {
				return rs.getInt("time");
			}
		});
		return results;

	}
	
	
	/**SQL Model*/
	
	
	private class tourInfo
	{
		Date startTime,endTime;
		int index;
		int freeTime;
		double px,py;
		String weekday;
		String preference;
		String city;
		String startPOI,endPOI;
		ArrayList<String> repeat = new ArrayList<String>();
		boolean flag;
		private tourInfo(int i,int free,Date d,Date e,double x,double y)
		{
			this.startTime = d;
			this.endTime = e;
			this.index = i;
			this.freeTime = free;
			this.px = x;
			this.py = y;
		}
	}
	
	private class GPS {
		double px, py;

		private GPS(double x, double y) {
			this.px = x;
			this.py = y;
		}

		private void setX(double x) {
			this.px = x;
		}

		private double getX() {
			return px;
		}

		private void setY(double y) {
			this.py = y;
		}

		private double getY() {
			return py;
		}
	}
	
	
	
}
