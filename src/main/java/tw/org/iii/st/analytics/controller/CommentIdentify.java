package tw.org.iii.st.analytics.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import tw.org.iii.model.CommentOutput;

import com.chenlb.mmseg4j.CharNode;
import com.chenlb.mmseg4j.ComplexSeg;
import com.chenlb.mmseg4j.Dictionary;
import com.chenlb.mmseg4j.MMSeg;
import com.chenlb.mmseg4j.Seg;
import com.chenlb.mmseg4j.Word;
import com.chenlb.mmseg4j.Dictionary.FileLoading;


@RestController
@RequestMapping("/CommentIdentify")
public class CommentIdentify 
{
	@Autowired
	@Qualifier("analyticsJdbcTemplate")
	private JdbcTemplate analytics;
	
	@Autowired
	@Qualifier("readTerms")
	private HashMap<String,String> terms;
	
	@Autowired
	@Qualifier("loadDic")
	private Seg seg;
	
	@Autowired
	@Qualifier("stJdbcTemplate")
	private JdbcTemplate stJdbcTemplate;
	
	@Autowired
	@Qualifier("stcJdbcTemplate")
	private JdbcTemplate stcJdbcTemplate;
	
	@RequestMapping("/StartIdentify")
	private @ResponseBody
	CommentOutput startIdentify(@RequestBody String comment)
	{

		String segResult = segmentation(comment.replaceAll("<.+?>", ","));
		String spl[] = segResult.split("\\|"),poi[];

		String str=""; //單一對應id的查詢字串
		String mstr=""; //多重對應id的查詢字串
		for (String s : spl)
		{
			if (terms.containsKey(s))
			{
				poi = terms.get(s).split(";"); //單一個Id
				if (poi.length==1)
					str+= "'" + poi[0] + "',";
				else //多重id (像是鼎泰豐)
				{
					for (String id : poi)
						mstr+= "'" + id + "',";
				}
				
			}
		}
		
		List<String> countyId = getCounty(comment);
		
		//輸出結果模型
		CommentOutput co = new CommentOutput();
		
		//單一Id結果
		List<Map<String, Object>> result;
		List<String> poiId = new ArrayList<String>();
		if (!"".equals(str))
		{
			result = stcJdbcTemplate.queryForList("SELECT id,countyId FROM ST_V3_COMMON.Poi WHERE id in ("+str.substring(0,str.lastIndexOf(","))+") and type <> -1");
			if (countyId.size()==0)//若遊記中未提到縣市, 則全部景點都考量
			{
				for (Map<String, Object> r : result)
					poiId.add(r.get("id").toString());
			}
			else //若遊記中有提到縣市, 則限縮縣市
			{
				for (Map<String, Object> r : result)
					if (countyId.contains(r.get("countyId")))
						poiId.add(r.get("id").toString());			
			}
			co.setPoiList(poiId);
			poiId=new ArrayList<String>();
		}
		if (!"".equals(mstr))
		{
			result = stcJdbcTemplate.queryForList("SELECT id,countyId FROM ST_V3_COMMON.Poi WHERE id in ("+mstr.substring(0,mstr.lastIndexOf(","))+") and type <> -1");
			if (countyId.size()==0)//若遊記中未提到縣市, 則全部景點都考量
			{
				for (Map<String, Object> r : result)
					poiId.add(r.get("id").toString());
			}
			else //若遊記中有提到縣市, 則限縮縣市
			{
				for (Map<String, Object> r : result)
					if (countyId.contains(r.get("countyId")))
						poiId.add(r.get("id").toString());			
			}
			co.setPoiList(poiId);
		}
		
		return co;
	}
	private List<String> getCounty(String comment)
	{
		List<String> county = new ArrayList<String>();
		List<Map<String, Object>> result = analytics.queryForList("SELECT countyId,name FROM poiName_location");
		for (Map<String, Object> r : result)
		{
			if (comment.contains(r.get("name").toString()))
				county.add(r.get("countyId").toString());
		}
		return county;
	}
	private String segmentation(String comment)
	{
		  Reader input = new StringReader(comment);
	      StringBuilder sb = new StringBuilder();
	      MMSeg mmSeg = new MMSeg(input, seg);
	      Word word = null;
	      boolean first = true;
	      try 
	      {
	          while ((word = mmSeg.next()) != null)
	          {
	              if (!first) 
	                    sb.append("|");
	              String w = word.getString();
	              sb.append(w);
	              first = false;
	          }
	      } catch (IOException e) {
	            e.printStackTrace();
	      }

	      return sb.toString();
	}
	
	
	
	/**
	 *   Load Dic
	 * */
	@RequestMapping("/LoadDic")
	private @ResponseBody Seg loadDic() {

		HashMap<String, String> terms = readTerms();
		StringBuilder sb = new StringBuilder();
		for (String t : terms.keySet()) {
				sb.append(t + "\n");
		}

		Dictionary dic = Dictionary.getInstance();
		String exampleString = sb.toString();
		// System.out.println(exampleString);
		try {
				InputStream stream = new ByteArrayInputStream(exampleString.getBytes(StandardCharsets.UTF_8));
				//System.out.println(exampleString.getBytes(StandardCharsets.UTF_8));
				Dictionary.load(stream, new WordsFileLoading(dic.getDict()));

		} catch (Exception e) {
				e.printStackTrace();
		}

		Seg seg = new ComplexSeg(dic);
		return seg;
	}
	private static class WordsFileLoading implements FileLoading {

		final Map<Character, CharNode> dic;

		/**
		 * @param dic 加载的词，保存在此结构中。
		 */
		public WordsFileLoading(Map<Character, CharNode> dic) {
				this.dic = dic;
		}

		public void row(String line, int n) {
				//System.out.println(line);
				if (line.length() < 2) {
						return;
				}
				CharNode cn = dic.get(line.charAt(0));
				if (cn == null) {
						cn = new CharNode();
						dic.put(line.charAt(0), cn);
				}
				cn.addWordTail(tail(line));
		}

		/**
		 * 取得 str 除去第一个char的部分
		 *
		 * @author chenlb 2009-3-3 下午10:05:26
		 */
		private static char[] tail(String str) {
				char[] cs = new char[str.length() - 1];
				str.getChars(1, str.length(), cs, 0);
				return cs;
		}
	}
	private HashMap<String, String> readTerms() {
		
		HashMap<String, String> term = new HashMap<String, String>();
		List<Map<String, Object>> rs = analytics.queryForList("SELECT * FROM poiName");
		for (Map<String, Object> r : rs) 
		{
				if (r.get("name").toString().length() == 1)
				{
					continue;
				}
				if (!term.containsKey(r.get("name").toString()))
					term.put(r.get("name").toString(), r.get("poiId").toString());
		}

		rs = stJdbcTemplate.queryForList("SELECT name,poiId FROM Detail");
		for (Map<String, Object> r : rs) 
		{
				if (r.get("name").toString().length() == 1 || r.get("name")==null || r.get("poiId")==null)
				{
					continue;
				}
				if (!term.containsKey(r.get("name").toString()))
					term.put(r.get("name").toString(), r.get("poiId").toString());
		}
		
		return term;
	}
}
