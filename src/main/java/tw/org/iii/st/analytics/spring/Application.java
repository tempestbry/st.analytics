package tw.org.iii.st.analytics.spring;

import java.beans.PropertyVetoException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.web.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import tw.org.iii.st.analytics.controller.STScheduling;
//import tw.org.iii.st.analytics.controller.STScheduling;
import tw.org.iii.st.analytics.cronjob.UpdateRecommendation;

import com.chenlb.mmseg4j.ComplexSeg;
import com.chenlb.mmseg4j.Dictionary;
import com.chenlb.mmseg4j.Seg;
import com.mchange.v2.c3p0.ComboPooledDataSource;

@Configuration
@ComponentScan({ "tw.org.iii.st.analytics.controller" })
@EnableAutoConfiguration
public class Application extends SpringBootServletInitializer {

	@Autowired
	Environment environment;

	@Bean(name = "datasource")
	@Primary
	public ComboPooledDataSource dataSourceHualien() throws PropertyVetoException {
		ComboPooledDataSource dataSource = new ComboPooledDataSource();
		dataSource.setDriverClass(environment
				.getRequiredProperty("c3p0.driver"));
		dataSource.setJdbcUrl(environment.getRequiredProperty("c3p0.url.hualien"));
		dataSource.setUser(environment.getRequiredProperty("c3p0.user"));
		dataSource
				.setPassword(environment.getRequiredProperty("c3p0.password"));
		dataSource.setInitialPoolSize(environment.getRequiredProperty(
				"c3p0.initialPoolSize", Integer.class));
		dataSource.setMaxPoolSize(environment.getRequiredProperty(
				"c3p0.maxPoolSize", Integer.class));
		dataSource.setMinPoolSize(environment.getRequiredProperty(
				"c3p0.minPoolSize", Integer.class));
		dataSource.setAcquireIncrement(environment.getRequiredProperty(
				"c3p0.acquireIncrement", Integer.class));
		dataSource.setMaxStatements(environment.getRequiredProperty(
				"c3p0.maxStatements", Integer.class));
		dataSource.setMaxIdleTime(environment.getRequiredProperty(
				"c3p0.maxIdleTime", Integer.class));
		return dataSource;
	}
	
	@Bean(name = "datasourceST")
	public ComboPooledDataSource dataSourceST() throws PropertyVetoException {
		ComboPooledDataSource dataSource = new ComboPooledDataSource();
		dataSource.setDriverClass(environment
				.getRequiredProperty("c3p0.driver"));
		dataSource.setJdbcUrl(environment.getRequiredProperty("c3p0.url.st"));
		dataSource.setUser(environment.getRequiredProperty("c3p0.user"));
		dataSource
				.setPassword(environment.getRequiredProperty("c3p0.password"));
		dataSource.setInitialPoolSize(environment.getRequiredProperty(
				"c3p0.initialPoolSize", Integer.class));
		dataSource.setMaxPoolSize(environment.getRequiredProperty(
				"c3p0.maxPoolSize", Integer.class));
		dataSource.setMinPoolSize(environment.getRequiredProperty(
				"c3p0.minPoolSize", Integer.class));
		dataSource.setAcquireIncrement(environment.getRequiredProperty(
				"c3p0.acquireIncrement", Integer.class));
		dataSource.setMaxStatements(environment.getRequiredProperty(
				"c3p0.maxStatements", Integer.class));
		dataSource.setMaxIdleTime(environment.getRequiredProperty(
				"c3p0.maxIdleTime", Integer.class));
		return dataSource;
	}
	@Bean(name = "datasourceAnalytics")
	public ComboPooledDataSource dataSourceAnalytics() throws PropertyVetoException {
		ComboPooledDataSource dataSource = new ComboPooledDataSource();
		dataSource.setDriverClass(environment
				.getRequiredProperty("c3p0.driver"));
		dataSource.setJdbcUrl(environment.getRequiredProperty("c3p0.url.analytics"));
		dataSource.setUser(environment.getRequiredProperty("c3p0.user"));
		dataSource
				.setPassword(environment.getRequiredProperty("c3p0.password"));
		dataSource.setInitialPoolSize(environment.getRequiredProperty(
				"c3p0.initialPoolSize", Integer.class));
		dataSource.setMaxPoolSize(environment.getRequiredProperty(
				"c3p0.maxPoolSize", Integer.class));
		dataSource.setMinPoolSize(environment.getRequiredProperty(
				"c3p0.minPoolSize", Integer.class));
		dataSource.setAcquireIncrement(environment.getRequiredProperty(
				"c3p0.acquireIncrement", Integer.class));
		dataSource.setMaxStatements(environment.getRequiredProperty(
				"c3p0.maxStatements", Integer.class));
		dataSource.setMaxIdleTime(environment.getRequiredProperty(
				"c3p0.maxIdleTime", Integer.class));
		return dataSource;
	}
	
	
	@Bean(name="stJdbcTemplate") 
	public JdbcTemplate stJdbcTemplate() throws PropertyVetoException{
		return new JdbcTemplate(dataSourceST()); 
	}
	
	
	@Bean(name="hualienJdbcTempplate")
	public JdbcTemplate hualienJdbcTempplate() throws PropertyVetoException{
		return  new JdbcTemplate(dataSourceHualien());
	}

	@Bean(name="analyticsJdbcTemplate")
	public JdbcTemplate analyticsJdbcTemplate() throws PropertyVetoException{
		return  new JdbcTemplate(dataSourceAnalytics());
	}
	
	@Bean
	public JobDetailFactoryBean jobDetailFactoryBean() throws PropertyVetoException{
		JobDetailFactoryBean jobbean = new JobDetailFactoryBean();
		jobbean.setJobClass(UpdateRecommendation.class);
		
		Map<String, Object> map = new HashMap();
		map.put("stJdbcTemplate", stJdbcTemplate());
		map.put("datasource", hualienJdbcTempplate());		
		jobbean.setJobDataAsMap(map);
		return jobbean;
	}
	
	
	@Bean(name="STScheduling")
	public STScheduling stscheduling(){
		return new STScheduling();
	}
	

	
	
	@Bean(name="readTerms")
	public HashMap<String,String> readTerms()
	{
		JdbcTemplate analytics = null;
		try {
			analytics = new JdbcTemplate(dataSourceAnalytics());
		} catch (PropertyVetoException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		HashMap<String,String> term = new HashMap<String,String>();
		List<Map<String, Object>> rs = analytics.queryForList("SELECT * FROM poiName_new");
		for (Map<String, Object> r : rs)
		{
			if (r.get("name").toString().length()==1)
				continue;
			term.put(r.get("name").toString(), r.get("poiId").toString());
		}
		return term;
	}
	
//	@Bean(name="loadDic")
//	public Seg loadDic()
//	{
//		
//	    Dictionary dic = Dictionary.getInstance();
//	    
//	    String classpath = System.getProperty("java.class.path");
//		System.out.println(classpath);
//		String p[] = classpath.split(";");
//		
//		String exampleString = p[0].substring(0,p[0].lastIndexOf("\\")) + "\\" + "word.dic";
////       for (String t : term.keySet())
////       {
////    	   exampleString = t;
//    	   try 
//    	   {
//	            InputStream stream = new ByteArrayInputStream(exampleString.getBytes(StandardCharsets.UTF_8));
//	            Dictionary.load(stream, new Dictionary.FileLoading() {
//	                @Override
//	                public void row(String s, int i) {
//	                }
//	            });
//
//
//	       } catch (Exception e) {
//	            e.printStackTrace();
//	        }
////       }
//	       
//	        
//	   Seg seg = new ComplexSeg(dic);
//	   return seg;
//	}
	
	/*@Bean
	public CronTriggerFactoryBean cronTriggerFactoryBean() throws PropertyVetoException{
		CronTriggerFactoryBean trigger = new CronTriggerFactoryBean();
		trigger.setJobDetail(jobDetailFactoryBean().getObject());
		trigger.setStartDelay(3000);
		trigger.setCronExpression("1 0 2 * * ?");
		return trigger;
	} 
	
	@Bean
	public SchedulerFactoryBean  schedulerFactory() throws Exception{
		
		
		SchedulerFactoryBean bean = new SchedulerFactoryBean();
		bean.setTriggers(cronTriggerFactoryBean().getObject());
		return bean;
	}*/

	@Override
	protected SpringApplicationBuilder configure(
			SpringApplicationBuilder application) {
		return application.sources(Application.class);
	}

	public static void main(String[] args) throws Exception {
		SpringApplication.run(Application.class, args);
	}

}
