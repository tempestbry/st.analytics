package tw.org.iii.st.analytics.controller;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.chenlb.mmseg4j.MMSeg;
import com.chenlb.mmseg4j.Seg;
import com.chenlb.mmseg4j.Word;


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
	
//	@Autowired
//	@Qualifier("loadDic")
//	private Seg seg;
	
	@RequestMapping("/StartIdentify")
	private @ResponseBody
	List<String> startIdentify(@RequestBody String comment)
	{
//		String segResult = segmentation(comment);
//		System.out.println(segResult);
		List<String> poiId = new ArrayList<String>();
		for (String t : terms.keySet())
		{
			if (comment.contains(t))
			{
				if (!poiId.contains(terms.get(t)))
				{
					String spl[] = terms.get(t).split(";");
					for (String id : spl)
						poiId.add(id);
				}
					
			}
		}
		return poiId;
	}
//	private String segmentation(String comment)
//	{
//		  Reader input = new StringReader(comment);
//	      StringBuilder sb = new StringBuilder();
//	      MMSeg mmSeg = new MMSeg(input, seg);
//	      Word word = null;
//	      boolean first = true;
//	      try 
//	      {
//	          while ((word = mmSeg.next()) != null)
//	          {
//	              if (!first) 
//	                    sb.append("|");
//	              String w = word.getString();
//	              sb.append(w);
//	              first = false;
//	          }
//	      } catch (IOException e) {
//	            e.printStackTrace();
//	      }
//
//	      System.out.println(sb.toString());
//	      return sb.toString();
//	}
	
	
}
