package tw.org.iii.st.analytics.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import tw.org.iii.model.GeoPoint;
import tw.org.iii.model.SchedulingInput;
import tw.org.iii.model.TourEvent;

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

/**
 * Hello~~
 * */

@RestController
@RequestMapping("/Scheduling")
public class Scheduling {
	@Autowired
	@Qualifier("hualienJdbcTempplate")
	private JdbcTemplate jdbcTemplate;
	
	@Autowired
	@Qualifier("stJdbcTemplate")
	private JdbcTemplate stJdbcTemplate;

	private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
	
	@RequestMapping("/QuickPlan")
	private @ResponseBody
	List<TourEvent> StartPlan(@RequestBody SchedulingInput json) throws ParseException, ClassNotFoundException, SQLException {

		//行程規劃結果
		ArrayList<TourEvent> PlanResult = new ArrayList<TourEvent>();
		ArrayList<TourEvent> finalResult = new ArrayList<TourEvent>();

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
//		Calendar cal = Calendar.getInstance(); // creates calendar
//		cal.setTime(si.getStartTime()); // sets calendar time/date
//		//cal.add(Calendar.HOUR_OF_DAY, 8); // adds one hour
//		si.setStartTime(cal.getTime());
//		System.out.println(si.getStartTime());
//		
//		System.out.println(si.getEndTime());
//		cal = Calendar.getInstance(); // creates calendar
//		cal.setTime(si.getEndTime()); // sets calendar time/date
//		//cal.add(Calendar.HOUR_OF_DAY, 8); // adds one hour
//		si.setEndTime(cal.getTime());
//		System.out.println(si.getEndTime());

		
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
//			SimpleDateFormat sdFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
//			TourEvent test = new TourEvent();
//			test.setPoiId("C1_315081200H_000101");
//			test.setStartTime(sdFormat.parse("2015/07/20 10:43:28"));
//			test.setEndTime(sdFormat.parse("2015/07/20 15:43:28"));
//			PlanResult.add(test);
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
	private String getPreference(List<String> p,HashMap<String,String> mapping)
	{
		String preference="";
		// 將preference寫入條件
		if (p.size()==0)
		{
			for (int i = 1; i < 8; i++)
				preference += "A.preference = 'PF" + i + "' or ";
			preference += "A.preference = 'PF8'";
		}
		else
		{
			for (int i = 0; i < p.size() - 1; i++)
				preference += "A.preference = '" + (p.get(i).contains("TH") ? mapping.get(p.get(i)) : p.get(i)) + "' or ";
			preference += "A.preference = '" + (p.get(p.size() - 1).contains("TH") ?  mapping.get(p.get(p.size() - 1)) : p.get(p.size() - 1)) + "'";
		}
		
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
		List<Map<String, Object>> rs = jdbcTemplate.queryForList("SELECT A.place_id,px,py,stay_time FROM scheduling AS A,OpenTimeArray AS B WHERE A.place_id = B.place_id and A.checkins > 30000 and B.weekday = '"
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
