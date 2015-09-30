package tw.org.iii.st.analytics.controller;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tw.org.iii.model.SchedulingInput;
import tw.org.iii.model.TourEvent;

/**
 * 
 * @author simcoehsieh
 *
 */
public class STScheduling 
{
	@Autowired
	@Qualifier("stJdbcTemplate")
	private JdbcTemplate st;
	
	@Autowired
	@Qualifier("analyticsJdbcTemplate")
	private JdbcTemplate analytics;
	
	public STScheduling()
	{
		
	}
	public List<TourEvent> scheduling(SchedulingInput json){
				
		ArrayList<String> tourCity;
		if (checkAuto(json.getCityList()) && json.getMustPoiList().size() == 0)
			tourCity = AutoReco(json); //不限縣市
		else
			tourCity = ExistCity(json); //不限縣市

		try {
			return startPlan(tourCity,json);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		return null;
	}
	
	/**安排縣市組合*/
	private boolean checkAuto(List<String> city)
	{
		boolean flag = true;
		for (String c : city)
		{
			if (!c.equals("all"))
				return false;
		}
		return flag;	
	}
	private ArrayList<String> AutoReco(SchedulingInput json) //完全沒有必去景點跟必去縣市(任我排)
	{
		int days = json.getCityList().size();
		ArrayList<String> newResult = new ArrayList<String>(); //final 回傳縣市清單
		List<Map<String, Object>> rs = analytics.queryForList("SELECT DISTINCT region FROM County WHERE region <> 'W' order by RAND() limit 0,"+((days/3)+1)+"");
		ArrayList<HashMap<String,Integer>> order = new ArrayList<HashMap<String,Integer>>();
		
		int[] r = new int[rs.size()]; //縣市排序模式
		Arrays.fill(r,-1);
		
		int index=0;
		int len;
		for (Map<String, Object> i : rs) // 找出隨機挑選的區域 (北中南東台灣+外島), 每個區塊可停留三天
		{
			HashMap<String,Integer> tmp = new HashMap<String,Integer>();
			if (rs.size()==1)
				len = days;
			else
				len=3;
			List<Map<String, Object>> county = analytics.queryForList("SELECT id,`order` AS A FROM County WHERE region = '"+i.get("region")+"' ORDER BY RAND() limit 0,"+len+"");
			for (Map<String, Object> c : county)
				tmp.put(c.get("id").toString(), Integer.parseInt(c.get("A").toString())); //紀錄縣市和排序
			order.add(index,tmp);
			if (rs.size()>1) //是否有跨區域
			{
				try
				{
					List<Map<String, Object>> type = analytics.queryForList("SELECT type FROM regionsort WHERE Region = '"+rs.get(index).get("region")+"' and Next = '"+rs.get(index+1).get("region")+"'");
					switch ((int)type.get(0).get("type")) //sort方式 0=小到大, 1=大到小
					{
						case 0:
							if (r[index]==-1)
								r[index] = 0;
							r[index+1] = 0;
							break;
						case 1:
							if (r[index]==-1)
								r[index] = 1;
							r[index+1] = 1;
							break;
						case 2:
							if (r[index]==-1)
								r[index] = 0;
							r[index+1] = 1;
							break;
						case 3:
							if (r[index]==-1)
								r[index] = 1;
							r[index+1] = 0;
							break;
						default:
							System.out.println("error");
					}
				}
				catch (IndexOutOfBoundsException e)
				{}
				
			}
			else
				r[index] = 0;
			
			index++;
		}
		
		List<Map.Entry<String, Integer>> rank;
		int counter;
		for (int j=0;j<index;j++)
		{
			counter=0;
			if (r[j]==-1)
				System.out.println("error");
			rank = sort(order.get(j),r[j]);
			for (Map.Entry<String, Integer> rr : rank)
			{
				newResult.add(rr.getKey());
				counter++;
				if (counter == (days/index))
					break;
			}			
		}
		if (newResult.size()<days)
		{
			rank = sort(order.get(index-1),r[index-1]);
			for (Map.Entry<String, Integer> rr : rank)
			{
				if (!newResult.contains(rr.getKey()))
					newResult.add(rr.getKey());
				if (newResult.size()==days)
					break;
			}			
			if (newResult.size()<days)
			{
				int value = newResult.size()-1;
				for (int nn=newResult.size();nn<days;nn++)
				{
					newResult.add(newResult.get(value));
				}
			}
		}
		
		return newResult;
	}
	private static List<Map.Entry<String, Integer>> sort(HashMap<String,Integer> a,int type)
	{
		List<Map.Entry<String, Integer>> list_Data = new ArrayList<Map.Entry<String, Integer>>(a.entrySet());

		if (type == 0)
		{
			Collections.sort(list_Data, new Comparator<Map.Entry<String, Integer>>()
					{
			            public int compare(Map.Entry<String, Integer> entry1,
			                               Map.Entry<String, Integer> entry2){
			                return (entry1.getValue() - entry2.getValue());
			            }
			        });
		}
		else
		{
			Collections.sort(list_Data, new Comparator<Map.Entry<String, Integer>>()
					{
			            public int compare(Map.Entry<String, Integer> entry1,
			                               Map.Entry<String, Integer> entry2){
			                return (entry2.getValue() - entry1.getValue());
			            }
			        });
		}
	
		
		return list_Data;
	}
	private ArrayList<String> ExistCity(SchedulingInput json)
	{
		String str = "";
		List<String> must = json.getMustPoiList();
		ArrayList<String> mustCounty = new ArrayList<String>();
		if (must.size()>0)
		{
			for (int i=0;i<must.size()-1;i++)
				str+= "poiId = '" + must.get(i) + "' or ";
			str+= "poiId = '" + must.get(must.size()-1) + "'";
			mustCounty = getMustCounty(str); //取得必去景點所在的縣市
		}
		
		 
			

		mustCounty = POIGroupAndSort(json.getCityList(),mustCounty);
		//mustCounty = order(mustCounty,json);
		
		
		return mustCounty;
	}
	private ArrayList<String> getMustCounty(String str)
	{
		ArrayList<String> county = new ArrayList<String>();
		List<Map<String, Object>> result = analytics.queryForList("SELECT DISTINCT county FROM st_scheduling WHERE "+str+"");
		for (Map<String, Object> r : result) //取得兩兩景點之間的時間與停留時間
			county.add(r.get("county").toString());
		
		return county;
	}
	
	private ArrayList<String> POIGroupAndSort(List<String> n,ArrayList<String>  m)
	{
		HashMap<String,Integer> region = new HashMap<String,Integer>();
		region.put("TW1",0); region.put("TW2",0); region.put("TW3",0); //北北基
		region.put("TW4",1); region.put("TW5",1); region.put("TW6",1); region.put("TW7",1);//桃竹苗
		region.put("TW8",2); region.put("TW9",2); region.put("TW10",2); //中彰投
		region.put("TW11",3); region.put("TW12",3); region.put("TW13",3); region.put("TW14",3); //雲嘉南
		region.put("TW15",4); region.put("TW16",4); //高屏
		region.put("TW17",5); //宜花東
		region.put("TW18",5); region.put("TW19",5);
		region.put("TW20",6); region.put("TW21",6); region.put("TW22",6); //外島
		
		HashMap<Integer,ArrayList<String>> group = new HashMap<Integer,ArrayList<String>>();
		for (String mm : m)
		{
			if (!group.containsKey(region.get(mm)))
			{
				ArrayList<String> tmp = new ArrayList<String>();
				tmp.add(mm);
				group.put(region.get(mm), tmp);
			}
			else
			{
				group.get(region.get(mm)).add(mm);
			}
		}
		
		for (Integer g : group.keySet())
		{
			System.out.print("Region : " + g + " -> ");
			for (String gg : group.get(g))
				System.out.print(gg + ",");
			System.out.println();
		}
		
		ArrayList<String> result = insert(region,group,n);
		
		return result;
	}
	private class info
	{
		String county;
		int index;
	}
	private ArrayList<String> fillCounty(String county,int days,HashMap<String,Integer> region,ArrayList<String> repeat)
	{
		ArrayList<String> tmp = new ArrayList<String>();
		HashMap<Integer,ArrayList<String>> all = new HashMap<Integer,ArrayList<String>>();
		for (String r : region.keySet())
		{
			if (!all.containsKey(region.get(r)))
			{
				if (!repeat.contains(r))
				{
					ArrayList<String> arr = new ArrayList<String>();
					arr.add(r);
					all.put(region.get(r),arr);
				}
			
			}
			else
			{
				if (!repeat.contains(r))
				{
					all.get(region.get(r)).add(r);
				}
				
			}
			
			if (region.get(r)==region.get(county) && !county.equals(r) && !repeat.contains(r))
			{
				tmp.add(r);
				days--;
				if (days==0)
					break;
			}
			if (days==0)
				break;
		}
			
		
		int ran = 0;
		ran = (int)(Math.random()*2);
		
		if (days>0)
		{
			for (int i=1;i<=6;i++)
			{
				if (ran==0)
				{
					try
					{
						for (String a : all.get(region.get(county)+i))
						{
							if (!repeat.contains(a))
							{
								tmp.add(a);
								days--;
							}
							if (days==0)
								break;
						}
					}
					catch (Exception e)
					{	
						for (String a : all.get(region.get(county)-i))
						{
							if (!repeat.contains(a))
							{
								tmp.add(a);
								days--;
							}
							if (days==0)
								break;
						}
					}
					
				}
				else
				{
					try
					{
						for (String a : all.get(region.get(county)-i))
						{
							if (!repeat.contains(a))
							{
								tmp.add(a);
								days--;
							}
							if (days==0)
								break;
						}
					}
					catch (Exception e)
					{
						for (String a : all.get(region.get(county)+i))
						{
							if (!repeat.contains(a))
							{
								tmp.add(a);
								days--;
							}
							if (days==0)
								break;
						}
					}
					
				}

				if (days==0)
					break;
			}
		}
		
		return tmp;
	}
	private ArrayList<String> insert(HashMap<String,Integer> region,HashMap<Integer,ArrayList<String>> group,List<String> now)
	{
		ArrayList<String> result = new ArrayList<String>();
		ArrayList<info> index = getCityIndex(now.toArray(new String[now.size()]));

		
		if (index.size()==0)
		{
			result = Default(group);
			if (result.size()<now.size())
			{
				result.addAll(fillCounty(result.get(result.size()-1),now.size()-result.size(),region,result));
			}
			return result;
		}
		
		
		int tmp=0;
		
		ArrayList<String> candi;

		int target;
		int days;
		int start=0;

		for (int i=0;i<index.size();i++)
		{	
			tmp = 0;
			target= index.get(i).index; //第一階段方向指標
			days = index.get(i).index - start;
			candi = getCandidate(region.get(index.get(i).county),group,days,result);
			tmp = candi.size()-1;
			for (int j=start;j<target;j++) //候選清單加入result中
			{
				try
				{
					result.add(j,candi.get(tmp));
					
				}
				catch (Exception e) //候選清單不夠時自動填補
				{
					result.addAll(fillCounty(candi.get(tmp+1),target-j,region,result));
				}
				tmp-=1;
			}
				
			if (tmp > -1)
			{
				for (int j=target-1;j>=0;j--)
				{
					result.set(j,result.get(j) + "+" + candi.get(tmp));
					tmp-=1;
					if (tmp < 0)
						break;
				}
					
			}
			result.add(index.get(i).index,index.get(i).county);
			start = target+1;
			
		}
		if (result.size() < now.size())
		{
			days = now.size()-result.size();
			candi = getCandidate(region.get(index.get(index.size()-1).county),group,days,result);
			tmp = candi.size()-1;
			for (int j=start;j<now.size();j++) //候選清單加入result中
			{
				try
				{
					result.add(j,candi.get(tmp));
					
				}
				catch (Exception e)
				{
					if (candi.size()==0)
					{
						result.addAll(fillCounty(result.get(result.size()-1),now.size()-j,region,result));
						break;
					}
					else
					{
						result.addAll(fillCounty(candi.get(tmp+1),now.size()-j,region,result));
					}
					
				}
				tmp-=1;
			}
				
			if (tmp > -1)
			{
				for (int j=start;j<now.size();j++)
				{
					result.set(j,result.get(j) + "+" + candi.get(tmp));
					tmp-=1;
					if (tmp < 0)
						break;
				}					
			}
			
		}
		
		
		
		int result_index=now.size();
		String temp[] = result.get(result.size()-1).split("\\+");
		if (region.get(index.get(index.size()-1).county) > region.get(temp[0])) //由南到北
		{
			for (int i=6;i>=0;i--)
			{
				if (group.containsKey(i))
				{
					String str="";
					ArrayList<String> arr = group.get(i);
					for (int j=0;j<arr.size()-1;j++)
					{
						if (!Contains(result,arr.get(j)))
							str+= arr.get(j) + "+";
					}
					if (!Contains(result,arr.get(arr.size()-1)))
						str+= arr.get(arr.size()-1);
					if (!"".equals(str))
					{
						result.add(result_index,str);
						result_index++;
					}
				
				}
			}
				
		}
		else //由北到南
		{
			for (int i=0;i<=6;i++)
			{
				if (group.containsKey(i))
				{
					String str="";
					ArrayList<String> arr = group.get(i);
					for (int j=0;j<arr.size()-1;j++)
					{
						if (!Contains(result,arr.get(j)))
							str+= arr.get(j) + "+";
					}
					if (!Contains(result,arr.get(arr.size()-1)))
						str+= arr.get(arr.size()-1);
					if (!"".equals(str))
					{
						result.add(result_index,str);
						result_index++;
					}				
				}
			}
		}
		
		
		
		for (String r : result)
			System.out.println(r);
		
		return result;
	}
	private ArrayList<String> Default(HashMap<Integer,ArrayList<String>> group)
	{
		int n=0,s=0;
		for (int i=0;i<=2;i++)
		{
			if (group.containsKey(i))
				n+=group.get(i).size();
		}
		for (int i=3;i<=5;i++)
		{
			if (group.containsKey(i))
				s+=group.get(i).size();
		}
		
		ArrayList<String> result = new ArrayList<String>();
		int result_index=0;
		if (n>=s) //由北到南排
		{
			for (int i=0;i<=6;i++)
			{
				if (group.containsKey(i))
				{
					String str="";
					ArrayList<String> arr = group.get(i);
					for (int j=0;j<arr.size()-1;j++)
					{
						if (!Contains(result,arr.get(j)))
							str+= arr.get(j) + "+";
					}
					if (!Contains(result,arr.get(arr.size()-1)))
						str+= arr.get(arr.size()-1);
					if (!"".equals(str))
					{
						result.add(result_index,str);
						result_index++;
					}				
				}
			}
		}
		else //由南到北排
		{
			for (int i=0;i<=6;i++)
			{
				if (group.containsKey(i))
				{
					String str="";
					ArrayList<String> arr = group.get(i);
					for (int j=0;j<arr.size()-1;j++)
					{
						if (!Contains(result,arr.get(j)))
							str+= arr.get(j) + "+";
					}
					if (!Contains(result,arr.get(arr.size()-1)))
						str+= arr.get(arr.size()-1);
					if (!"".equals(str))
					{
						result.add(result_index,str);
						result_index++;
					}				
				}
			}
		}
		
		return result;
	}
	private ArrayList<String> getCandidate(int region,HashMap<Integer,ArrayList<String>> group,int day,ArrayList<String> result)
	{
		ArrayList<String> candi_result = new ArrayList<String>();
		for (int i=0;i<6;i++)
		{
			if (group.containsKey(region-i))
			{
				for (String r : group.get(region-i))
				{
					if (!Contains(result,r))
						candi_result.add(r);
				}
					
			}
			if (candi_result.size()>=day)
				break;
			if (group.containsKey(region+i) && i!=0)
			{
				for (String r : group.get(region+i))
				{
					if (!Contains(result,r))
						candi_result.add(r);
				}
					
			}
			if (candi_result.size()>=day)
				break;
		}
		
		return candi_result;
	}
	private boolean Contains(ArrayList<String> a,String b)
	{
		for (String aa : a)
		{
			if (aa.contains(b))
				return true;
		}
		return false;
	}
	private ArrayList<info> getCityIndex(String now[])
	{
		ArrayList<info> tmp = new ArrayList<info>();
		for (int j=0;j<now.length;j++)
		{
			if (!now[j].equals("all"))
			{
				info i = new info();
				i.county = now[j];
				i.index = j;
				tmp.add(i);
			}
		}
		
		return tmp;
	}
	
	@Autowired
	@Qualifier("poiNames")
	private HashMap<String,String> poiNames;
//	private HashMap<String,String> getName()
//	{
//		List<Map<String, Object>> result = analytics.queryForList("SELECT poiId,Name FROM st_scheduling");
//		HashMap<String,String> poi = new HashMap<String,String>();
//		for (Map<String, Object> r : result)
//		{
//			poi.put(r.get("poiId").toString(),r.get("Name").toString());
//		}
//
//		return poi;
//	}
	
	
	/**開始每一天的行程規劃*/
	private List<TourEvent> startPlan(ArrayList<String> tourCity,SchedulingInput json) throws ParseException
	{
		Date start = json.getStartTime(),end = json.getStartTime(),freeTime;
		String pre = getPre(json.getPreferenceList()); //取得偏好條件
		
		
		switch (json.getLooseType())
		{
//		case -1:
//			dateStr = (start.getYear()+1900) + "-" + (start.getMonth()<10 ? "0" + start.getMonth() : start.getMonth()) + "-" + (start.getDay()<10 ? "0" + start.getDay() : start.getDay()) + " 07:30:00";
//			break;
//		case 0:
//			dateStr = (start.getYear()+1900) + "-" + (start.getMonth()<10 ? "0" + start.getMonth() : start.getMonth()) + "-" + (start.getDay()<10 ? "0" + start.getDay() : start.getDay()) + " 08:00:00";
//			break;
//		case 1:
//			dateStr = (start.getYear()+1900) + "-" + (start.getMonth()<10 ? "0" + start.getMonth() : start.getMonth()) + "-" + (start.getDay()<10 ? "0" + start.getDay() : start.getDay()) + " 08:30:00";
//			break;
//		default:
//			dateStr = (start.getYear()+1900) + "-" + (start.getMonth()<10 ? "0" + start.getMonth() : start.getMonth()) + "-" + (start.getDay()<10 ? "0" + start.getDay() : start.getDay()) + " 08:30:00";
		case -1:
			start = addTime(start,-30); //開始時間
			end = addTime(start,660); //結束時間
			break;
		case 0:
			end = addTime(start,660); //結束時間
			break;
		case 1:
			start = addTime(start,30); //開始時間
			end = addTime(start,660); //結束時間
			break;
		default:
			end = addTime(start,660); //結束時間
			break;
		}
		
		Date ori_start = start;
		
		HashMap<String,ArrayList<String>> group = new HashMap<String,ArrayList<String>>();
		if (json.getMustPoiList().size() > 0)
			group = getGroup(json.getMustPoiList()); //取得縣市必去景點
		
		List<TourEvent> tourResult = new ArrayList<TourEvent>();		
		
		int startIndex=0;
		
		int index=0;
		ArrayList<String> repeat = new ArrayList<String>();
		for (String t : tourCity) //每一天的行程
		{
			System.out.println("City : " + t);
			
			boolean eatTime = false;
			freeTime = ori_start;
			
			String spl[] = t.split("\\+");
			if (!mustCounty(spl,group)) //該縣市是否有必去景點
			{
				tourResult.add(index++,FindTop(t,pre,start,repeat,json.getLooseType()));
				
				//Id跟名稱同時過濾
				repeat.add(tourResult.get(index-1).getPoiId());
				repeat.add(poiNames.get(tourResult.get(index-1).getPoiId()));
				System.out.println(poiNames.get(tourResult.get(index-1).getPoiId()));
				freeTime = tourResult.get(index-1).getEndTime();
				
				while (FreeTime(freeTime,end) >= 1)
				{
					if (freeTime.getHours()>=11 && freeTime.getHours()<13)
						eatTime = true;
					else
						eatTime = false;
					tourResult.add(otherPOI(tourResult.get(index-1),pre,json.getLooseType(),repeat,eatTime));
					index++;
					
					//Id跟名稱同時過濾
					repeat.add(tourResult.get(index-1).getPoiId());
					repeat.add(poiNames.get(tourResult.get(index-1).getPoiId()));
					System.out.println(poiNames.get(tourResult.get(index-1).getPoiId()));
					freeTime = tourResult.get(index-1).getEndTime();
				}
			}
			else
			{
				if (spl.length>1) //多縣市
				{
					for (int i=1;i<=spl.length;i++) //每一個縣市
					{						
						if (group.containsKey(spl[i-1]))
							tourResult.addAll(MustResult(group.get(spl[i-1]),start,json.getLooseType()));
						else
							tourResult.add(FindTop(spl[i-1],pre,start,repeat,json.getLooseType()));
						index=tourResult.size();
						
						//Id跟名稱同時過濾
						repeat.add(tourResult.get(index-1).getPoiId());
						repeat.add(poiNames.get(tourResult.get(index-1).getPoiId()));
						System.out.println(poiNames.get(tourResult.get(index-1).getPoiId()));
						freeTime = tourResult.get(index-1).getEndTime();
						while (FreeTime(freeTime,end) >= (12-(4*i)))
						{
							if (freeTime.getHours()>=11 && freeTime.getHours()<13)
								eatTime = true;
							else
								eatTime = false;
							tourResult.add(otherPOI(tourResult.get(index-1),pre,json.getLooseType(),repeat,eatTime));
							index++;
							
							//Id跟名稱同時過濾
							repeat.add(tourResult.get(index-1).getPoiId());
							repeat.add(poiNames.get(tourResult.get(index-1).getPoiId()));
							System.out.println(poiNames.get(tourResult.get(index-1).getPoiId()));
							freeTime = tourResult.get(index-1).getEndTime();
						}
						start = addTime(tourResult.get(index-1).getEndTime(),60);
								
					}
				
				}
				else //一個縣市
				{
					tourResult.addAll(MustResult(group.get(spl[0]),start,json.getLooseType()));
					index=tourResult.size();
					
					//Id跟名稱同時過濾
					repeat.add(tourResult.get(index-1).getPoiId());
					repeat.add(poiNames.get(tourResult.get(index-1).getPoiId()));
					System.out.println(poiNames.get(tourResult.get(index-1).getPoiId()));
					freeTime = tourResult.get(index-1).getEndTime();
					while (FreeTime(freeTime,end) >= 1)
					{
						if (freeTime.getHours()>=11 && freeTime.getHours()<13)
							eatTime = true;
						else
							eatTime = false;
						tourResult.add(otherPOI(tourResult.get(index-1),pre,json.getLooseType(),repeat,eatTime));
						index++;
						
						//Id跟名稱同時過濾
						repeat.add(tourResult.get(index-1).getPoiId());
						repeat.add(poiNames.get(tourResult.get(index-1).getPoiId()));
						System.out.println(poiNames.get(tourResult.get(index-1).getPoiId()));
						freeTime = tourResult.get(index-1).getEndTime();
						
					}
				}
			}
//			for (int si = startIndex;si<index;si++)
//			{
//				System.out.println(poiNames.get(tourResult.get(si).getPoiId()));
//			}
			startIndex = index;
			ori_start = addTime(ori_start,1440);
			start = ori_start;
			freeTime = ori_start;
			end = addTime(end,1440);
		}
//		for (String ci : tourCity)
//			System.out.println(ci);
//		for (TourEvent te : tourResult)
//		{
//			System.out.println(te.getPoiId() + " : " + te.getStartTime());
//		}
		
		return tourResult;
		
	}
	private boolean mustCounty(String spl[],HashMap<String,ArrayList<String>> group)
	{
		for (String s : spl)
		{
			if (group.containsKey(s))
				return true;
		}
		return false;
	}
	private class must
	{
		int stay_time;
		int time;
	}
	private List<TourEvent> MustResult(ArrayList<String> poi,Date start,int type) throws ParseException
	{
		List<TourEvent> tour = new ArrayList<TourEvent>();
		if (poi.size()==1)
		{
			TourEvent t = new TourEvent();
			t.setPoiId(poi.get(0));
			t.setStartTime(start);
			t.setEndTime(addTime(start,30));
			tour.add(t);
			return tour;
		}
		
		
		String query="";
		for (String p : poi)
		{
			for (String pp : poi)
			{
				if (p.equals(pp))
					continue;
				query+= "(id='" + p + "' and arrival_id = '"+pp+"') and ";
			}
		}
		query = query.substring(0,query.lastIndexOf(" and"));
		
		int stay;
		
		HashMap<String,HashMap<String,must>> poiInfo = new HashMap<String,HashMap<String,must>>();
		List<Map<String, Object>> result = analytics.queryForList("SELECT id,arrival_id,time,stay_time FROM euclid_distance_0826 WHERE "+query+"");
		for (Map<String, Object> r : result) //取得兩兩景點之間的時間與停留時間
		{
			if (!poiInfo.containsKey(r.get("id").toString()))
			{
				HashMap<String,must> tmp = new HashMap<String,must>();
				must m = new must();
				try
				{
					stay = (int)r.get("stay_time") + (type*30);
				}
				catch (Exception e)
				{
					stay = 90 + (type*30);
				}
				m.stay_time = stay;
				m.time = (int)r.get("time");
				tmp.put(r.get("arrival_id").toString(), m);
				poiInfo.put(r.get("id").toString(),tmp);
			}
			else
			{
				must m = new must();
				try
				{
					stay = (int)r.get("stay_time") + (type*30);
				}
				catch (Exception e)
				{
					stay = 90 + (type*30);
				}
				m.stay_time = stay;
				m.time = (int)r.get("time");
				poiInfo.get(r.get("id").toString()).put(r.get("arrival_id").toString(), m);
			}
		}
		
		ArrayList<String[]> all = prefix(poi.toArray(new String[poi.size()]),poi.size(),0);
		int min=100000,sum;
		String minResult[] = null;
		for (String a[] : all)
		{
			sum=0;
			for (int i=0;i<a.length-1;i++)
				sum+=poiInfo.get(a[i]).get(a[i+1]).time;
			if (sum<min)
			{
				min = sum;
				minResult = a;
			}
				
		}
		
		int index=0;
		
		for (String m : minResult)
		{
			TourEvent t = new TourEvent();
			t.setPoiId(m);
			if (index==0)
			{
				t.setStartTime(start);
				t.setEndTime(addTime(start,30));
			}
			else
			{
				Date end = tour.get(tour.size()-1).getEndTime();
				double time = poiInfo.get(tour.get(tour.size()-1).getPoiId()).get(m).time;
				stay = poiInfo.get(tour.get(tour.size()-1).getPoiId()).get(m).stay_time;
				t.setStartTime(addTime(end,time));
				t.setEndTime(addTime(t.getStartTime(),stay));
			}
			tour.add(t);
			
		}
		
		return tour;
		
	}
	private ArrayList<String[]> prefix(String[] array, int n, int k) 
	{
		ArrayList<String[]> result = new ArrayList<String[]>();
		if (n == k) 
		{
			String[] out = new String[n];
			for (int i = 0; i < array.length; i++)
				out[i] = array[i];
			result.add(out);

		} 
		else 
		{
			for (int i = k; i < n; i++) 
			{
				swap(array, k, i);
				result.addAll(prefix(array, n, k + 1));
				swap(array, i, k);
			}
		}	
		return result;
	}
	 private void swap(String[] a, int x, int y) 
	 {
		 String temp = a[x];
		 a[x] = a[y];
		 a[y] = temp;
	 }
	
	
	private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private TourEvent otherPOI(TourEvent before,String pre,int type,ArrayList<String> repeat,boolean eatTime)
	{
		int value = 50000,time_value = 15;
		List<Map<String, Object>> result;
		TourEvent poi = new TourEvent();
		double stay;
		do
		{
			result = analytics.queryForList("SELECT arrival_id,time,stay_time FROM euclid_distance_0826 WHERE id = '"+before.getPoiId()+"' and checkins >= "+value+" and time <="+time_value+" and preference in ("+pre+") ORDER BY rand() limit 0,20");
			if (result.size()>0)
			{
				for (Map<String, Object> r : result)
				{
					if (!repeat.contains(r.get("arrival_id").toString()) && !repeat.contains(poiNames.get(r.get("arrival_id").toString())) && !poiNames.get(r.get("arrival_id")).contains("夜市"))
					{
						poi.setPoiId(r.get("arrival_id").toString());
						try{
							if (!eatTime)
								poi.setStartTime(addTime(before.getEndTime(),Double.parseDouble(result.get(0).get("time").toString())));
							else
								poi.setStartTime(addTime(addTime(before.getEndTime(),120),Double.parseDouble(result.get(0).get("time").toString())));
							//double stay = /*Double.parseDouble(result.get(0).get("stay_time").toString())*/90 + (type * 30);
							try
							{
								stay = Double.parseDouble(result.get(0).get("stay_time").toString()) + (type * 30);
							}
							catch (Exception e)
							{
								e.printStackTrace();
								stay = 90 + (type * 30);
							}
							
							if (stay<30) //停留時間低於半小時以半小時計
								stay = 30;
							poi.setEndTime(addTime(poi.getStartTime(),stay));
						}catch (Exception e){e.printStackTrace();}
						break;
					}
					
				}
				if (poi.getPoiId()!=null)
					break;
			}
			time_value+=5;
			value-=10000;
		}while (("".equals(poi.getPoiId()) || poi.getPoiId()==null) && value>=0);
		
		if ("".equals(poi.getPoiId()) || poi.getPoiId()==null) //上面條件都沒符合的
		{
			result = analytics.queryForList("SELECT arrival_id,time,stay_time FROM euclid_distance_0826 WHERE id = '"+before.getPoiId()+"' ORDER BY time limit 0,20");	
			for (Map<String, Object> r : result)
			{
				if (!repeat.contains(r.get("arrival_id").toString()) && !repeat.contains(poiNames.get(r.get("arrival_id").toString())) && !poiNames.get(r.get("arrival_id")).contains("夜市"))
				{
					poi.setPoiId(r.get("arrival_id").toString());
					try{
						poi.setStartTime(addTime(before.getEndTime(),Double.parseDouble(result.get(0).get("time").toString())));
						try
						{
							stay = Double.parseDouble(result.get(0).get("stay_time").toString()) + (type * 30);
						}
						catch (Exception e)
						{
							e.printStackTrace();
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
			result = analytics.queryForList("SELECT county FROM st_scheduling WHERE poiId = '"+before.getPoiId()+"'");	
			result = analytics.queryForList("SELECT poiId,location,stay_time FROM st_scheduling WHERE county = '"+result.get(0).get("county").toString()+"' ORDER BY rand()");
			for (Map<String, Object> r : result)
			{
				if (!repeat.contains(r.get("poiId").toString()) && !repeat.contains(poiNames.get(r.get("poiId").toString())) && !poiNames.get(r.get("poiId")).contains("夜市"))
				{
					List<Map<String, Object>> result1 = analytics.queryForList("SELECT poiId,location,stay_time FROM st_scheduling WHERE poiId = '"+before.getPoiId()+"'");
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
					poi.setEndTime(addTime(poi.getStartTime(),60));
				}
			}
		}
		
		return poi;
	}
	private double Distance(double wd1,double jd1,double wd2,double jd2)
	{
		double x,y,out;
		double PI=3.14159265;
		double R=6.371229*1e6;
	
		x=(jd2-jd1)*PI*R*Math.cos( ((wd1+wd2)/2) *PI/180)/180;
		y=(wd2-wd1)*PI*R/180;
		out=Math.hypot(x,y);
		return out/1000;
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
	private TourEvent FindTop(String city,String pre,Date startTime,ArrayList<String> repeat,int type)
	{
		
		int value = 100000;
		List<Map<String, Object>> result;
		TourEvent poi = new TourEvent();
		boolean flag = false;
		double stay;
		do
		{
			result = analytics.queryForList("SELECT poiId,stay_time FROM st_scheduling WHERE county = '"+city+"' and preference in ("+pre+") and checkins >= "+value+" order by rand()");
			for (Map<String, Object> r : result)
			{
				if (!repeat.contains(r.get("poiId")) && !repeat.contains(poiNames.get(r.get("poiId"))) && !poiNames.get(r.get("poiId")).contains("夜市"))
				{
					if (poiNames.get(r.get("poiId")).contains("夜市"))
						System.out.println("why");
					poi.setPoiId(result.get(0).get("poiId").toString());
					poi.setStartTime(startTime);
					
					try
					{
						stay = Double.parseDouble(result.get(0).get("stay_time").toString()) + (type*30);
					}
					catch (Exception e)
					{
						stay = 90 + (type*30);
					}
					
					try{
						poi.setEndTime(addTime(startTime,stay));
					}catch (Exception e){e.printStackTrace();}
					flag = true;
					break;
				}
			}
			if (flag)
				break;
			value-=10000;
		}while (poi.getPoiId()==null && value>=0);

		if (poi.getPoiId()==null)
		{
			result = analytics.queryForList("SELECT poiId,stay_time FROM st_scheduling WHERE county = '"+city+"' order by rand()");
			for (Map<String, Object> r : result)
			{
				if (!repeat.contains(r.get("poiId")) && ! repeat.contains(poiNames.get(r.get("poiId"))) && !poiNames.get(r.get("poiId")).contains("夜市"))
				{
					poi.setPoiId(result.get(0).get("poiId").toString());
					poi.setStartTime(startTime);
					
					try
					{
						stay = Double.parseDouble(result.get(0).get("stay_time").toString()) + (type*30);
					}
					catch (Exception e)
					{
						stay = 90 + (type*30);
					}
					poi.setEndTime(addTime(startTime,stay));
					flag = true;
					break;
				}
			}
		}
		
		return poi;
	}
	private Date addTime(Date d, double minute) {
		Calendar cal;
		cal = Calendar.getInstance();
		cal.setTime(d);
		cal.add(Calendar.MINUTE, (int)minute);
		try {
			return sdf.parse(sdf.format(cal.getTime()));
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	private int FreeTime(Date start, Date end) {
		Date d1 = start;
		Date d2 = end;
		long diff = d2.getTime() - d1.getTime();
		long diffHours = diff / (60 * 60 * 1000) % 24;
		return (int) diffHours;
	}
	private HashMap<String,ArrayList<String>> getGroup(List<String> county)
	{
		HashMap<String,ArrayList<String>> group = new HashMap<String,ArrayList<String>>();
		
		String str="";
		for (int i=0;i<county.size()-1;i++)
		{
			str+="'" + county.get(i) + "',";
		}
		str+="'" + county.get(county.size()-1) + "'";
		
		List<Map<String, Object>> result = analytics.queryForList("SELECT poiId,county FROM st_scheduling WHERE poiId in ("+str+")");
		for (Map<String, Object> r : result)
		{
			if (!group.containsKey(r.get("county")))
			{
				ArrayList<String> tmp = new ArrayList<String>();
				tmp.add(r.get("poiId").toString());
				group.put(r.get("county").toString(), tmp);
			}
			else
			{
				group.get(r.get("county")).add(r.get("poiId").toString());
			}
		}
		
		return group;
		
	}
	
	
	

}
