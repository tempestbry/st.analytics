package tw.org.iii.st.analytics.controller;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import tw.org.iii.model.SchedulingInput;
import tw.org.iii.model.TourEvent;

/**
 * Hello~~
 * */

@RestController
@RequestMapping("/Scheduling")
public class Scheduling {
	@Autowired
	JdbcTemplate jdbcTemplate;

	private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
	private SchedulingInput si;
	private int lastTime;
	private ArrayList<TourEvent> PlanResult = new ArrayList<TourEvent>();
	private ArrayList<String> repeat = new ArrayList<String>();
	private int index;
	private String preference;
	private String weekday;

	@RequestMapping("/QuickPlan")
	private @ResponseBody
	List<TourEvent> StartPlan(@RequestBody SchedulingInput json)
			throws ParseException, ClassNotFoundException, SQLException {

	
		si = json;
		weekday = getWeekday(si.getStartTime());

		preference = "";
		List<String> p = si.getPreferenceList();
		
		// 將preference寫入條件
		if (p.size()==0)
		{
			for (int i = 1; i < 7; i++)
				preference += "A.preference = 'PF" + i + "' or ";
			preference += "A.preference = 'PF7'";
		}
		else
		{
			for (int i = 0; i < p.size() - 1; i++)
				preference += "A.preference = '" + p.get(i) + "' or ";
			preference += "A.preference = '" + p.get(p.size() - 1) + "'";
		}
	

		// 取得旅程總時間
		int freetime = FreeTime(si.getStartTime(), si.getEndTime() );
		
		if ("".equals(si.getEndPoiId()) || si.getEndPoiId() == null) {
			PlanResult.addAll(findTop(freetime));
			freetime = FreeTime(PlanResult.get(index - 1).getEndTime(),
					si.getEndTime() );
			getOtherPOI(freetime);

			for (TourEvent t : PlanResult) {
				System.out.println(t.getPoiId() + "," + t.getStartTime() + ","
						+ t.getEndTime());
			}
			System.out.println(lastTime);
		} else {
			PlanResult.addAll(getMission(freetime));
			freetime = FreeTime(PlanResult.get(2).getEndTime(),
					si.getEndTime());
			getOtherPOI(freetime);
			for (TourEvent t : PlanResult) {
				System.out.println(t.getPoiId() + "," + t.getStartTime() + ","
						+ t.getEndTime());
			}
			System.out.println(lastTime);
		}

		ArrayList<TourEvent> finalResult = new ArrayList<TourEvent>();
		finalResult.addAll(PlanResult);
		close();
		return finalResult;
	}

	
	private ArrayList<TourEvent> findTop(int freetime) throws ParseException,
			SQLException {
		List<schedulingInfo> rs;
		ArrayList<TourEvent> result = new ArrayList<TourEvent>();
		TourEvent te;

		Calendar cal;
		Date date = si.getStartTime();
		cal = Calendar.getInstance();
		cal.setTime(date);

		// 找出Top
		rs = Query("SELECT A.place_id,A.preference,px,py,stay_time FROM scheduling AS A,OpenTimeArray AS B WHERE A.place_id = B.place_id and A.checkins > 30000 and B.weekday = '"
				+ weekday
				+ "' and "
				+ date.getHours()
				+ "_Oclock = 1 and ("
				+ preference + ") GROUP BY fb_id ORDER BY rand() limit 0,40");
		double dis = 0;
		for (schedulingInfo i : rs) {
			//是否有設定出發POI
			if ("".equals(si.getStartPoiId()) || si.getStartPoiId() == null) {
				dis = Distance(i.getPy(), i.getPx(), si.getGps().getLng(), si
						.getGps().getLat());
			} else {
				List<GPS> gp = QGPS("SELECT px,py FROM scheduling WHERE place_id = '"
						+ si.getStartPoiId() + "'");
				if (gp.get(0).getX() == 0 || gp.get(0).getY() == 0) //POI沒經緯度則以User所在點做起點
					dis = Distance(i.getPy(), i.getPx(), si.getGps().getLng(),
							si.getGps().getLat());
				else
					dis = Distance(i.getPy(), i.getPx(), gp.get(0).getY(), gp
							.get(0).getX());
			}

			Date startTime = si.getStartTime();
			if (!(startTime.getHours() >= 11 && startTime.getHours() <= 13) //非用餐時段暫不提供餐廳景點(餐廳打卡數都偏高容易誤推)
					&& !(startTime.getHours() >= 17 && startTime.getHours() <= 19)) {
				if (i.getPreference().equals("PF1")
						|| "".equals(i.getPreference())
						|| i.getPreference() == null)
					continue;
			}
			int time = (int) (dis / 0.7);
			if (time < 45 && time < (freetime * 60)) { //行車時間45分鐘內可到且空閒時間也夠
				te = new TourEvent();
				te.setPoiId(i.getPlaceID());

				te.setStartTime(addTime(sdf.parse(sdf.format(cal.getTime())),
						time));
				te.setEndTime(addTime(te.getStartTime(), i.getStayTime()));

				result.add(index++, te);
				break;
			}
		}
		
		
		//當上面無結果時，則解放行車時間的條件
		if (result.size()==0)
		{
			HashMap<String,Integer> tmp = new HashMap<String,Integer>();
			HashMap<String,TourEvent> _tmp = new HashMap<String,TourEvent>();
			for (schedulingInfo i : rs) {
				if ("".equals(si.getStartPoiId()) || si.getStartPoiId() == null) {
					dis = Distance(i.getPy(), i.getPx(), si.getGps().getLng(), si
							.getGps().getLat());
				} else {
					List<GPS> gp = QGPS("SELECT px,py FROM scheduling WHERE place_id = '"
							+ si.getStartPoiId() + "'");
					if (gp.get(0).getX() == 0 || gp.get(0).getY() == 0)
						dis = Distance(i.getPy(), i.getPx(), si.getGps().getLng(),
								si.getGps().getLat());
					else
						dis = Distance(i.getPy(), i.getPx(), gp.get(0).getY(), gp
								.get(0).getX());
				}

				Date startTime = si.getStartTime();
				if (!(startTime.getHours() >= 11 && startTime.getHours() <= 13)
						&& !(startTime.getHours() >= 17 && startTime.getHours() <= 19)) {
					if (i.getPreference().equals("PF1")
							|| "".equals(i.getPreference())
							|| i.getPreference() == null)
						continue;
				}
				int time = (int) (dis / 0.7);
				
				te = new TourEvent();
				te.setPoiId(i.getPlaceID());

				te.setStartTime(addTime(sdf.parse(sdf.format(cal.getTime())),
						time));
				te.setEndTime(addTime(te.getStartTime(), i.getStayTime()));
				_tmp.put(i.getPlaceID(), te);
				tmp.put(i.getPlaceID(), time);
			}
			//找最近可到的景點作為起始點
			List<Map.Entry<String, Integer>> rank = sort(tmp);
			result.add(_tmp.get(rank.get(0).getKey()));
			index++;
		}
		return result;
	}

	private ArrayList<TourEvent> getMission(int freetime) throws SQLException,ClassNotFoundException, ParseException 
	{
		index = 0;
		ArrayList<TourEvent> result = new ArrayList<TourEvent>();
		TourEvent te;
		
		Calendar cal;
		Date date = si.getStartTime();
		cal = Calendar.getInstance();
		cal.setTime(date);
		
		// 取得要先破關的行政區
		List<String> region = QRegion("SELECT DISTINCT region FROM scheduling WHERE mission IS NOT NULL ORDER BY rand()");
		String r = region.get(0);

		// 亂數選行政區中A關卡
		List<schedulingInfo> rs = Query("SELECT A.place_id,A.preference,stay_time,px,py FROM scheduling AS A,OpenTimeArray AS B WHERE A.region = '"
				+ r
				+ "' and"
				+ " A.mission = 'A' and A.Place_Id = B.place_id and B.weekday = '"
				+ weekday
				+ "' and B."
				+ date.getHours()
				+ "_Oclock = 1 ORDER BY rand()");
		
		te = new TourEvent();
		if ("".equals(rs.get(0).getPlaceID()) || rs.get(0).getPlaceID() == null) {
			// 重新挑選行政區
			region = QRegion("SELECT DISTINCT region FROM scheduling WHERE mission IS NOT NULL");
			for (String s : region) {
				r = s;
				List<schedulingInfo> rs1 = Query("SELECT A.place_id,A.preference,stay_time,px,py FROM scheduling AS A,OpenTimeArray AS B WHERE A.region = '"
						+ r
						+ "' and"
						+ " A.mission = 'A' and A.Place_Id = B.place_id and B.weekday = '"
						+ weekday
						+ "' and B."
						+ date.getHours()
						+ "_Oclock = 1 ORDER BY rand()");
				if ("".equals(rs1.get(0).getPlaceID())
						|| rs1.get(0).getPlaceID() == null) {
					te.setPoiId(rs1.get(0).getPlaceID());
					break;
				}
			}
		
			// 還是找不到->拿掉營業時間條件
			if ("".equals(te.getPoiId()) || te.getPoiId() == null) {
				region = QRegion("SELECT DISTINCT region FROM scheduling WHERE mission IS NOT NULL ORDER BY rand()");
				r = region.get(0);
		
				List<schedulingInfo> rs1 = Query("SELECT A.place_id,A.preference,stay_time,px,py FROM scheduling AS A,OpenTimeArray AS B WHERE A.region = '"
						+ r
						+ "' and"
						+ " A.mission = 'A' and A.Place_Id = B.place_id ORDER BY rand()");
				te.setPoiId(rs1.get(0).getPlaceID());
			}
		} else {
			te.setPoiId(rs.get(0).getPlaceID());
		}
		
		// 取得user所在地與第一個景點的行車時間
		double dis;
		if ("".equals(si.getStartPoiId()) || si.getStartPoiId() == null) {
			dis = Distance(rs.get(0).getPy(), rs.get(0).getPx(), si.getGps()
					.getLat(), si.getGps().getLng());
		} else {
			List<GPS> gp = QGPS("SELECT px,py FROM scheduling WHERE place_id = '"
					+ si.getStartPoiId() + "'");
			if (gp.get(0).getX() == 0 || gp.get(0).getY() == 0)
				dis = Distance(rs.get(0).getPy(), rs.get(0).getPx(), si
						.getGps().getLng(), si.getGps().getLat());
			else
				dis = Distance(rs.get(0).getPy(), rs.get(0).getPx(), gp.get(0)
						.getY(), gp.get(0).getX());
		}
		
		int time = (int) (dis / 0.7);
		te.setStartTime(addTime(sdf.parse(sdf.format(cal.getTime())), time));
		te.setEndTime(addTime(te.getStartTime(), 30));
		
		result.add(index++, te);
		repeat.add(te.getPoiId());
		
		// 亂數選行政區中B關卡 (營業時間依照上一個景點離開時間+1小時判斷)
		rs = Query("SELECT A.place_id,A.preference,stay_time,px,py FROM scheduling AS A,OpenTimeArray AS B WHERE A.region = '"
				+ r
				+ "' and A.mission = 'B' and "
				+ "A.Place_Id = B.place_id and B.weekday = '"
				+ weekday
				+ "' and B."
				+ (result.get(index - 1).getEndTime().getHours() + 1)
				+ "_Oclock = 1 ORDER BY rand()");
		//未符合條件(拿掉時間條件)
		if (rs.size()==0)
		{
			rs = Query("SELECT A.place_id,A.preference,stay_time,px,py FROM scheduling AS A,OpenTimeArray AS B WHERE A.region = '"
					+ r
					+ "' and"
					+ " A.mission = 'B' and A.Place_Id = B.place_id ORDER BY rand()");
		}
		te = new TourEvent();
		te.setPoiId(rs.get(0).getPlaceID());
		te.setStartTime(addTime(
				result.get(index - 1).getEndTime(),
				BetweenTime(result.get(index - 1).getPoiId(), rs.get(0)
						.getPlaceID())));
		te.setEndTime(addTime(te.getStartTime(), 30));
		
		result.add(index++, te);
		repeat.add(te.getPoiId());
		
		// 亂數選行政區中C關卡 (營業時間依照上一個景點離開時間+1小時判斷)
		rs = Query("SELECT A.place_id,A.preference,stay_time,px,py FROM scheduling AS A,OpenTimeArray AS B WHERE A.region = '"
				+ r
				+ "' and A.mission = 'C' and "
				+ "A.Place_Id = B.place_id and B.weekday = '"
				+ weekday
				+ "' and B."
				+ (result.get(index - 1).getEndTime().getHours() + 1)
				+ "_Oclock = 1 ORDER BY rand()");
		if (rs.size()==0)
		{
			rs = Query("SELECT A.place_id,A.preference,stay_time,px,py FROM scheduling AS A,OpenTimeArray AS B WHERE A.region = '"
					+ r
					+ "' and"
					+ " A.mission = 'C' and A.Place_Id = B.place_id ORDER BY rand()");
		}
		te = new TourEvent();
		te.setPoiId(rs.get(0).getPlaceID());
		te.setStartTime(addTime(
				result.get(index - 1).getEndTime(),
				BetweenTime(result.get(index - 1).getPoiId(), rs.get(0).getPlaceID())));
		te.setEndTime(addTime(te.getStartTime(), 30));
		
		result.add(index++, te);
		repeat.add(te.getPoiId());
		return result;
		
	}
	
	private void getOtherPOI(int time) throws SQLException, ParseException {
		String start = PlanResult.get(index - 1).getPoiId();
		// ResultSet rs;

		// rs =
		// sc.select_table("SELECT A.place_id,stay_time,px,py FROM scheduling AS A,OpenTimeArray AS B WHERE A.region = '"+r+"' and A.mission = 'A' and A.stay_time < "+(freetime*20)+" and "
		// + "A.Place_Id = B.place_id and B.weekday = '"+weekday
		// +"' and B."+date.getHours()+"_Oclock = 1 ORDER BY rand()");
		// rs.next();
		//
		// result
		TourEvent te;
		lastTime = time * 60;

		while (true) {
			//取得上一個景點後找出在有限時間內還可以去的景點
			te = costTime(PlanResult.get(index - 1).getEndTime(), start,
					si.getEndPoiId(), 45, 10000);
			if ("".equals(te.getPoiId()) || te.getPoiId() == null) {
				if (lastTime > 60) {
					te = costTime(PlanResult.get(index - 1).getEndTime(),
							start, si.getEndPoiId(), 60, 5000);
					if ("".equals(te.getPoiId()) || te.getPoiId() == null) {
						if (!repeat.contains(te.getPoiId())) {
							if (!"".equals(si.getEndPoiId())
									&& si.getEndPoiId() != null) {
								te.setPoiId(si.getEndPoiId());
								te.setStartTime(addTime(
										PlanResult.get(index - 1).getEndTime(),
										toDestination(PlanResult.get(index - 1)
												.getPoiId(), si.getEndPoiId()) == 100000 ? 30
												: toDestination(
														PlanResult.get(
																index - 1)
																.getPoiId(), si
																.getEndPoiId())));
								te.setEndTime(si.getEndTime());
								PlanResult.add(index++, te);
								break;
							}
						}
						break;
					} else {
						PlanResult.add(index++, te);
						start = te.getPoiId();
						continue;
					}
				}
				if (!repeat.contains(te.getPoiId())) {
					if (!"".equals(si.getEndPoiId()) && si.getEndPoiId() != null) 
					{
						te.setPoiId(si.getEndPoiId());
						te.setStartTime(addTime(PlanResult.get(index - 1).getEndTime(),toDestination(PlanResult.get(index - 1).getPoiId(), si.getEndPoiId()) == 100000 ? 30
										: toDestination(
												PlanResult.get(index - 1)
														.getPoiId(), si
														.getEndPoiId())));
						te.setEndTime(si.getEndTime());
						PlanResult.add(index++, te);
						break;
					}
				}
				break;
			} else {
				PlanResult.add(index++, te);
				start = te.getPoiId();
			}

		}

	}

	private TourEvent costTime(Date startTime, String now, String destination,
			int range, int checkin) throws SQLException, ParseException {
		List<SchedulingDis> rs;
		int destinationTime;
		TourEvent te = new TourEvent();
		rs = QueryDis("SELECT A.arrival_id, A.checkins, A.preference, A.time, (A.time + A.stay_time) AS totaltime FROM "
				+ "OpenTimeArray AS B, euclid_distance AS A WHERE A.Id = '"
				+ now
				+ "' AND ("
				+ preference
				+ ") AND "
				+ "B.Place_Id = A.arrival_id AND A.time < "
				+ range
				+ " AND A.checkins > "
				+ checkin
				+ " AND B.weekday = '"
				+ weekday
				+ "' "
				+ "AND B."
				+ startTime.getHours()
				+ "_Oclock = 1 ORDER BY RAND() DESC");
		for (SchedulingDis sd : rs) // 找出目前所在景點可考慮去的鄰近景點候選清單
		{
			//非用餐時段暫時不推餐廳
			if (!(startTime.getHours() >= 11 && startTime.getHours() <= 13)
					&& !(startTime.getHours() >= 17 && startTime.getHours() <= 19)) {
				if (sd.getPreference().equals("PF1"))
					continue;
			}
			//沒有目的地
			if ("".equals(destination) || destination == null) {
				if (!repeat.contains(sd.getPlaceID())
						&& (sd.getTotalTime()) < lastTime) // 達成任務就離開
				{
					te.setPoiId(sd.getPlaceID());
					te.setStartTime(addTime(PlanResult.get(index - 1)
							.getEndTime(), sd.getTime()));
					te.setEndTime(addTime(PlanResult.get(index - 1)
							.getEndTime(), sd.getTotalTime()));
					repeat.add(sd.getPlaceID());
					lastTime -= sd.getTotalTime();
					break;
				}
			} else {
				destinationTime = toDestination(sd.getPlaceID(), destination);
				if (!repeat.contains(sd.getPlaceID())
						&& (destinationTime + sd.getTotalTime()) < lastTime) // 達成任務就離開
				{
					te.setPoiId(sd.getPlaceID());
					te.setStartTime(addTime(PlanResult.get(index - 1)
							.getEndTime(), sd.getTime()));
					te.setEndTime(addTime(PlanResult.get(index - 1)
							.getEndTime(), sd.getTotalTime()));
					repeat.add(sd.getPlaceID());
					lastTime -= sd.getTotalTime();
					break;
				}
			}

		}

		return te;
	}

	private int toDestination(String now, String destination)
			throws SQLException {
		List<SchedulingDis> rs = QueryDis("SELECT A.arrival_id,A.preference,checkins,time,(time+stay_time) AS totalTime FROM euclid_distance AS A WHERE id = '"
				+ now + "' and arrival_id = '" + destination + "'");
		System.out
				.println("SELECT (time+stay_time) AS totalTime FROM euclid_distance AS A WHERE id = '"
						+ now + "' and arrival_id = '" + destination + "'");
		if (rs.size() > 0)
			return rs.get(0).getTotalTime();
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
			return rs1.get(0);

		} else {
			return rs.get(0);
		}
	}

	private Date addTime(Date d, int minute) throws ParseException {
		Calendar cal;
		cal = Calendar.getInstance();
		cal.setTime(d);
		cal.add(Calendar.MINUTE, minute);
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

	private int FreeTime(Date start, Date end) {
		Date d1 = start;
		Date d2 = end;
		long diff = d2.getTime() - d1.getTime();
		long diffHours = diff / (60 * 60 * 1000) % 24;
		return (int) diffHours;
	}

	private void close() {
		PlanResult.clear();
		repeat.clear();
		lastTime = 0;
		index = 0;
		preference = "";
		weekday = "";
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

	private List<schedulingInfo> Query(String q) {
		List<schedulingInfo> results = jdbcTemplate.query(q,
				new RowMapper<schedulingInfo>() {
					@Override
					public schedulingInfo mapRow(ResultSet rs, int rowNum)
							throws SQLException {
						return new schedulingInfo(rs.getString("A.place_id"),
								rs.getString("A.preference"), rs
										.getInt("stay_time"), rs
										.getDouble("px"), rs.getDouble("py"));
					}
				});
		return results;

	}
	private List<GPS> QGPS(String q) {
		List<GPS> results = jdbcTemplate.query(q, new RowMapper<GPS>() {
			@Override
			public GPS mapRow(ResultSet rs, int rowNum) throws SQLException {
				return new GPS(rs.getDouble("px"), rs.getDouble("py"));
			}
		});
		return results;

	}
	private List<SchedulingDis> QueryDis(String q) {
		List<SchedulingDis> results = jdbcTemplate.query(q,
				new RowMapper<SchedulingDis>() {
					@Override
					public SchedulingDis mapRow(ResultSet rs, int rowNum)
							throws SQLException {
						return new SchedulingDis(rs.getString("A.arrival_id"),
								rs.getString("A.preference"), rs
										.getInt("checkins"), rs.getInt("time"),
								rs.getInt("totalTime"));
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
	private class schedulingInfo {
		double px;

		double py;

		int stayTime;

		String place_id;

		String preference;

		public schedulingInfo(String id, String p, int time, double x, double y) {
			super();
			this.place_id = id;
			this.preference = p;
			this.stayTime = time;
			this.px = x;
			this.py = y;
		}

		public String getPlaceID() {
			return place_id;
		}

		public String getPreference() {
			return preference;
		}

		public int getStayTime() {
			return stayTime;
		}

		public double getPx() {
			return px;
		}

		public double getPy() {
			return py;
		}

	}
	private class SchedulingDis {
		String place_id;
		String preference;
		int checkins;
		int time;
		int totalTime;

		public SchedulingDis(String id, String p, int check, int time, int total) {
			super();
			this.place_id = id;
			this.preference = p;
			this.checkins = time;
			this.time = time;
			this.totalTime = total;
		}

		public SchedulingDis() {
			super();
		}

		public String getPlaceID() {
			return place_id;
		}

		public void setPlaceID(String id) {
			this.place_id = id;
		}

		public String getPreference() {
			return preference;
		}

		public void setPreference(String p) {
			this.preference = p;
		}

		public int getTime() {
			return time;
		}

		public void setTime(int time) {
			this.time = time;
		}

		public int getCheckins() {
			return checkins;
		}

		public void setCheckins(int check) {
			this.checkins = check;
		}

		public int getTotalTime() {
			return totalTime;
		}

		public void setTotalTime(int time) {
			this.totalTime = time;
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
