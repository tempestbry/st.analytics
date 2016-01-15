package tw.org.iii.st.analytics.controller;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.impl.DenseDoubleMatrix1D;


public class RecomendationBackend 
{
	@Autowired
	@Qualifier("analyticsJdbcTemplate")
	private JdbcTemplate analytics;
	
	
	public void StartBackend()
	{
		CheckExistPoi(); //檢查新增景點
		startSegDescription(); //將新景點描述斷詞
		List<String> feature = getFeature(95); //訂定門檻值, 取得特徵詞列表, 門檻值為分位數
		HashMap<String,double[]> vector = getVector(feature); //取得特徵向量
		HashMap<String,Integer> checkins = getCheckins(); //取得打卡數
		buildRecommendation(vector,checkins);
		
	}
	private void buildRecommendation(HashMap<String,double[]> vector,HashMap<String,Integer> checkins)
	{
		analytics.execute("TRUNCATE TABLE related_recommendation");
		
		
		List<Map<String, Object>> county = analytics.queryForList("SELECT DISTINCT countyId FROM ST_V3_COMMON.Poi");
		List<Map<String, Object>> poi;
		
		double similarity;
		DoubleMatrix1D a;
		DoubleMatrix1D b;
		//double cosineDistance = a.zDotProduct(b)/Math.sqrt(a.zDotProduct(a)*b.zDotProduct(b));
		
		String poiA,poiB;
		StringBuilder sb = new StringBuilder();
		sb.append("INSERT INTO related_recommendation (poiId,related_id,type,countyId,checkins,cb) VALUES ");
		int steps=1;
		for (Map<String, Object> c : county) //每一個縣市
		{
			poi = analytics.queryForList("SELECT id,type FROM ST_V3_COMMON.Poi WHERE type <> -1 and countyId = '"+c.get("countyId")+"'");
			//計算兩兩景點之間的分數
			for (Map<String, Object> p : poi)
			{
				poiA = p.get("id").toString();
				for (Map<String, Object> pp : poi)
				{
					poiB = pp.get("id").toString();
					if (poiA.equals(poiB)) //同樣的POI則continue
						continue;
					if (!vector.containsKey(poiA) || !vector.containsKey(poiB)) //若其中一個POI沒有特徵向量則continue
						continue;
					
					//計算兩向量之間的相似性
					a = new DenseDoubleMatrix1D(vector.get(poiA));
					b = new DenseDoubleMatrix1D(vector.get(poiB));
					if (Math.sqrt(a.zDotProduct(a)*b.zDotProduct(b))==0)
					{
						if (!checkins.containsKey(poiB)) //如果cb相似度為0且沒有打卡數則跳出
							continue;
						similarity = 0.0;
					}
					else
					{
						similarity = a.zDotProduct(b)/Math.sqrt(a.zDotProduct(a)*b.zDotProduct(b));
						if (similarity==0.0)
						{
							if (!checkins.containsKey(poiB)) //如果cb相似度為0且沒有打卡數則跳出
								continue;
						}
					}
						
					
					if (checkins.containsKey(poiB))
					{
						if (steps % 100==0)
						{
							sb.append("('"+poiA+"','"+poiB+"','"+pp.get("type")+"','"+c.get("countyId")+"','"+checkins.get(poiB)+"','"+similarity+"')");
							analytics.execute(sb.toString());
							sb.setLength(0);
							sb.append("INSERT INTO related_recommendation (poiId,related_id,type,countyId,checkins,cb) VALUES ");
						}
						else
						{
							sb.append("('"+poiA+"','"+poiB+"','"+pp.get("type")+"','"+c.get("countyId")+"','"+checkins.get(poiB)+"','"+similarity+"'),");
						}
						
//						analytics.execute("INSERT INTO related_recommendation (poiId,related_id,type,countyId,checkins,cb)"
//								+ " VALUES ('"+poiA+"','"+poiB+"','"+pp.get("type")+"','"+c.get("countyId")+"','"+checkins.get(poiB)+"','"+similarity+"') ON DUPLICATE KEY UPDATE checkins='"+checkins.get(poiB)+"',cb='"+similarity+"'");
					}
					else
					{
						if (steps % 100==0)
						{
							sb.append("('"+poiA+"','"+poiB+"','"+pp.get("type")+"','"+c.get("countyId")+"','0','"+similarity+"')");
							analytics.execute(sb.toString());
							sb.setLength(0);
							sb.append("INSERT INTO related_recommendation (poiId,related_id,type,countyId,checkins,cb) VALUES ");
						}
						else
						{
							sb.append("('"+poiA+"','"+poiB+"','"+pp.get("type")+"','"+c.get("countyId")+"','0','"+similarity+"'),");
						}
						
//						analytics.execute("INSERT INTO related_recommendation (poiId,related_id,type,countyId,checkins,cb)"
//								+ " VALUES ('"+poiA+"','"+poiB+"','"+pp.get("type")+"','"+c.get("countyId")+"','0','"+similarity+"') ON DUPLICATE KEY UPDATE checkins='0',cb='"+similarity+"'");
					}
					steps++;
				}
			}
		}
		analytics.execute(sb.toString().substring(0,sb.toString().lastIndexOf(",")));
//		analytics.execute(sb.toString());
		sb.setLength(0);
	}

	private HashMap<String,Integer> getCheckins()
	{
		HashMap<String,Integer> checkins = new HashMap<String,Integer>();
		
		//取得打卡數
		List<Map<String, Object>> result = analytics.queryForList("SELECT A.poiId,A.checkins FROM Poi_mapping AS A,ST_V3_COMMON.Poi AS B WHERE A.checkins IS NOT NULL and B.type <> -1 and A.poiId = B.id");
		for (Map<String, Object> r : result)
		{
			checkins.put(r.get("poiId").toString(),(int)r.get("checkins"));
		}
		
		return checkins;
		
	}
	private HashMap<String,double[]> getVector(List<String> feature)
	{
		HashMap<String,double[]> vector = new HashMap<String,double[]>();
		List<Map<String, Object>> result = analytics.queryForList("SELECT poiId,ckipTerms FROM description_ckip WHERE ckipTerms IS NOT NULL and ckipTerms <> ''");
		for (Map<String, Object> r : result)
		{
			vector.put(r.get("poiId").toString(), generateVector(r.get("ckipTerms").toString(),feature));
		}
		
		return vector;
	}
	private double[] generateVector(String content,List<String> feature)
	{
		double d[] = new double[feature.size()];
		Pattern p;
		Matcher m;
		int count;
		for (int i=0;i<feature.size();i++)
		{
			count=0;
			p = Pattern.compile("　"+feature.get(i)+"\\(.+?\\)");
			m = p.matcher(content);
			while (m.find()) //計算一個feature在content出現的次數
				count++;
			d[i] = count;
		}
		
		return d;
	}
	private class count
	{
		int tf=1;
	}
	private List<String> getFeature(double threshold)
	{
		List<Map<String, Object>> result = analytics.queryForList("SELECT ckipTerms FROM description_ckip WHERE ckipTerms IS NOT NULL and ckipTerms <> ''");
		String spl[],t;
		HashMap<String,count> termlist = new HashMap<String,count>();
		List<String> feature = new ArrayList<String>();
		for (Map<String, Object> r : result)
		{
			spl = r.get("ckipTerms").toString().split("　");
			for (String s : spl)
			{
				if (IsStopWord(s) || s.split("\\(")[0].length()==1) //停用字或長度為1的字則不使用
					continue;
				t = s.split("\\(")[0];
				if (!termlist.containsKey(t))
					termlist.put(t, new count());
				else
					termlist.get(t).tf++;
			}
		}
		feature = Filtering(termlist,threshold); //篩選門檻
		
		result = null;
		termlist = null;
		
		return feature;
	}
	private List<String> Filtering(HashMap<String,count> termlist,double threshold)
	{
		List<String> feature = new ArrayList<String>();
		double median;
		DescriptiveStatistics stats = new DescriptiveStatistics();
		for (String t : termlist.keySet())
		{
			stats.addValue(termlist.get(t).tf);
		}
		median = stats.getPercentile(threshold);

		for (String t : termlist.keySet())
		{
			if (termlist.get(t).tf<median)
				continue;
			else
				feature.add(t);
		}
		
		return feature;
	}
	private String speech[] = {"(Na)", "(Nb)","(Nc)", "(VA)", "(VJ)", "(VB)", "(VH)", "(VC)", "(VHC)", "(VK)"};
	private boolean IsStopWord(String term) //停用字判斷
	{
		for (String sp : speech)
			if (term.contains(sp))
				return false;
		return true;
	}
	private void calsimilarity()
	{
		double v[] = new double[10];
		double vv[] = new double[10];
		DoubleMatrix1D a = new DenseDoubleMatrix1D(v);
		DoubleMatrix1D b = new DenseDoubleMatrix1D(vv);
	}
	private void CheckExistPoi()
	{
		analytics.execute("INSERT INTO description_ckip (poiId,countyId,type) "
				+ "SELECT id,countyId,type FROM ST_V3_COMMON.Poi WHERE id NOT in (SELECT poiId FROM description_ckip)");
	}
	
	
	
	private void startSegDescription()
	{
		List<Map<String, Object>> result = analytics.queryForList("SELECT poiId,description FROM ST_V3_ZH_TW.Detail WHERE poiId in "
				+ "(SELECT poiId FROM description_ckip WHERE (ckipTerms IS NULL or ckipTerms = '') and type <> -1) and description IS NOT NULL and description <> '' and description <> '無' and description <> '無資料'");
		for (Map<String, Object> r : result)
		{
			try {
				analytics.execute("UPDATE description_ckip SET ckipTerms = '"+SendToCKIP(r.get("description").toString()).replace("'", "")+"' WHERE poiId = '"+r.get("poiId")+"'");
			} catch (DataAccessException | IOException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * CKIP斷詞
	 * */
	private String SendToCKIP(String text) throws IOException, InterruptedException
	{

		URL url = new URL("http://sunlight.iis.sinica.edu.tw/cgi-bin/text.cgi");
	    URLConnection urlConn = url.openConnection();
	    urlConn.setDoOutput(true);
	    
	    
	    OutputStreamWriter writer = new OutputStreamWriter(urlConn.getOutputStream());
//		    System.out.println(URLEncoder.encode(text, "Big5"));
	    writer.write("Submit=%B0e%A5X&query=" + URLEncoder.encode(text, "Big5"));
	    writer.flush();   

	    //讀取結果
	    BufferedReader br = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));
        String line = "",reurl="";
        while ((line = br.readLine()) != null) 
            reurl += line;
        br.close();

        Thread.sleep(500);
       
        return GetCKIPResult(reurl);

	}
	private String GetCKIPResult(String reurl) throws IOException, InterruptedException
	{
		 String id = reurl.substring(reurl.indexOf("/uwextract/pool") + "/uwextract/pool".length()+1, reurl.indexOf(".html"));
		 return parser(request("http://sunlight.iis.sinica.edu.tw/uwextract/show.php?id="+id+"&type=tag","Big5"));
	}
	private String parser(String text) throws IOException
	{
		try
		{
			return text.substring(text.indexOf("<pre>")+"<pre>".length(),text.indexOf("</pre>")).replace("--","");
		}
		catch (Exception e)
		{
			e.printStackTrace();
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
	        Thread.sleep(500);
		}
		catch (Exception e)
		{
			results.append("");
		}
		return results.toString();
	}

}
