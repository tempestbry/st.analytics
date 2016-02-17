package tw.org.iii.st.analytics.cronjob;

import java.util.List;
import java.util.Map;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * 
 * @author ansonliu
 *
 */
public class UpdateRecommendation extends QuartzJobBean{
	
	private JdbcTemplate stJdbcTemplate;
	
	private JdbcTemplate hualienJdbcTemplate;

	
	
	public JdbcTemplate getStJdbcTemplate() {
		return stJdbcTemplate;
	}


	public void setStJdbcTemplate(JdbcTemplate stJdbcTemplate) {
		this.stJdbcTemplate = stJdbcTemplate;
	}




	public JdbcTemplate getHualienJdbcTemplate() {
		return hualienJdbcTemplate;
	}




	public void setHualienJdbcTemplate(JdbcTemplate hualienJdbcTemplate) {
		this.hualienJdbcTemplate = hualienJdbcTemplate;
	}




	@Override
	protected void executeInternal(JobExecutionContext arg0)
			throws JobExecutionException {
		// TODO Auto-generated method stub
		System.out.println("This is example cron job");
		
		/*List<Map<String, Object>> list = stJdbcTemplate.queryForList("SELECT * FROM PoiFinalView limit 20");
		for(Map map : list){
			System.out.println(map.get("id"));
		}*/
		
	}
}
