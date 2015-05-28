package tw.org.iii.st.analytics.spring;

import java.beans.PropertyVetoException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.web.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

import com.mchange.v2.c3p0.ComboPooledDataSource;

@ComponentScan({ "tw.org.iii.st.analytics.controller" })
@EnableAutoConfiguration
public class Application extends SpringBootServletInitializer {

	@Autowired
	Environment environment;

	@Bean(name = "datasource")
	public ComboPooledDataSource dataSource() throws PropertyVetoException {
		ComboPooledDataSource dataSource = new ComboPooledDataSource();
		dataSource.setDriverClass(environment
				.getRequiredProperty("c3p0.driver"));
		dataSource.setJdbcUrl(environment.getRequiredProperty("c3p0.url"));
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

	@Override
	protected SpringApplicationBuilder configure(
			SpringApplicationBuilder application) {
		return application.sources(Application.class);
	}

	public static void main(String[] args) throws Exception {
		SpringApplication.run(Application.class, args);
	}

}
